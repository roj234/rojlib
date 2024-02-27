package roj.sound.mp3;

import org.jetbrains.annotations.NotNull;
import roj.concurrent.TaskPool;
import roj.io.source.Source;
import roj.sound.APETag;
import roj.sound.AudioDecoder;
import roj.sound.AudioMetadata;
import roj.sound.AudioOutput;
import roj.util.ArrayCache;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;

/**
 * MP3解码器
 * @author Roj234
 * @since 2024/2/18 23:37
 */
public final class MP3Decoder implements AudioDecoder {
	private static final int BUFFER_LEN = 2048;

	private Source in;
	private AudioOutput out;

	private ID3Tag tag;
	private TaskPool asyncSyh;

	private final Header header = new Header();
	private final byte[] buf = new byte[BUFFER_LEN];
	private int off, maxOff;
	private volatile boolean eof;
	private volatile long seekPos;

	byte[] pcm;
	int[] pcmOff = new int[2];

	public MP3Decoder() {}

	@Override
	public AudioMetadata open(Source in, AudioOutput out, boolean parseMetadata) throws IOException, LineUnavailableException {
		maxOff = off = 0;
		eof = false;
		seekPos = -1;

		this.in = in;
		this.out = out;

		in.seek(0);
		AudioMetadata metadata = parseMetadata ? parseTag() : null;

		header.reset();
		// 定位并解码第一帧
		nextHeader();
		if (!eof) out.init(header.getAudioFormat(), (header.getMode()==3?1:2)*header.getSamplingRate());

		return metadata;
	}
	private ID3Tag parseTag() throws IOException {
		if (tag == null) tag = new ID3Tag();
		else tag.clear();

		int len;
		if ((len = in.read(buf, 0, BUFFER_LEN)) <= 10) {
			eof = true;
			return null;
		}

		boolean hasTag = false;
		// ID3 v2
		int tagSize = tag.checkID3V2(buf, 0);
		if (tagSize > len) {
			if (tagSize > 1048576) throw new IOException("corrupt MP3 file (ID3v2 too large)");

			byte[] b = new byte[tagSize];
			System.arraycopy(buf, 0, b, 0, len);
			tagSize -= len;
			in.readFully(b, len, tagSize);
			tag.parseID3V2(b, 0, b.length);

			hasTag = true;
		} else if (tagSize > 10) {
			tag.parseID3V2(buf, 0, tagSize);
			off = tagSize;
			maxOff = len;
			hasTag = true;
		} else {
			int length = (int) in.length();

			// then try APE
			if (length >= 32) {
				in.seek(length-32);
				in.readFully(buf, 0, 32);

				APETag apeTag = new APETag();
				// TODO check APE tag footer
				int i = apeTag.checkFooter(buf, 0);
				if (i != 0) {
					hasTag = true;
				}
			}

			// try ID3 v1
			if (length >= 128) {
				in.seek(length - 128);
				in.readFully(buf, 0, 128);
				if (tag.parseID3V1(buf, 0)) hasTag = true;
			}
			in.seek(0);
		}

		return hasTag ? tag : null;
	}

	@Override
	public boolean isOpen() { return in != null; }

	@Override
	public void stop() {
		eof = true;
		in = null;
		out = null;
		synchronized (this) { notify(); }
		header.reset();
	}

	@Override
	public boolean isDecoding() { return !eof; }

	@Override
	public void decodeLoop() throws IOException {
		if (eof) return;

		Layer layer = switch (header.getLayer()) {
			case 1 -> new Layer1(header, this);
			case 2 -> new Layer2(header, this);
			case 3 -> new Layer3(header, this, asyncSyh == null ? TaskPool.Common() : asyncSyh);
			default -> throw new IOException("未预料的错误");
		};

		pcmOff[0] = 0;
		pcmOff[1] = 2;
		pcm = ArrayCache.getByteArray(header.getPcmSize(), false);
		try {
			while (!eof) {
				// 1. 解码一帧
				off = layer.decodeFrame(buf, off);

				// 3. 检测并处理时间跳转
				if (seekPos != -1) {
					in.seek(seekPos);
					off = maxOff = 0;
					seekPos = -1;
				}

				// 4. 定位到下一帧并解码帧头
				nextHeader();
			}
		} finally {
			layer.close();
			if (pcmOff[0] > 0) out.write(pcm, 0, pcmOff[0], true);
			ArrayCache.putArray(pcm);
		}
	}

	// 2. 输出到音频对象
	final void flush() {
		if (pcmOff[0] > 0) out.write(pcm, 0, pcmOff[0], true);
		pcmOff[0] = 0;
		pcmOff[1] = 2;
	}

	private void nextHeader() throws IOException {
		int len, chunk = 0;

		int maxOff = this.maxOff, off = this.off;
		byte[] buf = this.buf;
		Header header = this.header;
		Source in = this.in;

		while (!header.findSyncFrame(buf, off, maxOff)) {
			// buf内帧同步失败或数据不足一帧，刷新缓冲区buf
			off = header.offset();
			len = maxOff - off;

			if (len > 0) System.arraycopy(buf, off, buf, 0, len); // 把off的buf移到起始位置

			int read = in.read(buf, len, BUFFER_LEN - len);
			maxOff = len + read;
			off = 0;

			if (maxOff <= len || (chunk += read) > 0x10000) {
				eof = true;
				break;
			}
		}

		this.off = header.offset();
		this.maxOff = maxOff;
	}

	@Override
	public boolean isSeekable() { return isDecoding(); }
	@Override
	public void seek(double second) throws IOException {
		long targetFrame = Math.round(second / header.frameDuration);
		long seekPos = (long) (header.getFrameSizeAvg() * targetFrame);
		if (seekPos < 0) seekPos = 0;
		else {
			long l = in.length();
			if (seekPos > l) seekPos = l;
		}
		this.seekPos = seekPos;
		header.frames = (int) targetFrame;
	}

	@Override
	public double getCurrentTime() { return header.frames*(double)header.frameDuration; }
	@Override
	public double getDuration() {
		if (in != null) try {
			return in.length() / header.getFrameSizeAvg() * header.frameDuration;
		} catch (IOException ignored) {}
		return -1;
	}

	@Override
	@NotNull
	public String getDebugInfo() { return header.toString(); }
	public Header getHeader() { return header; }
}