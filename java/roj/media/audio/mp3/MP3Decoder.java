package roj.media.audio.mp3;

import org.jetbrains.annotations.NotNull;
import roj.concurrent.TaskPool;
import roj.io.Finishable;
import roj.io.MyDataInputStream;
import roj.io.source.Source;
import roj.media.audio.APETag;
import roj.media.audio.AudioDecoder;
import roj.media.audio.AudioMetadata;
import roj.media.audio.AudioOutput;
import roj.reflect.ReflectionUtils;
import roj.text.logging.Logger;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;

import static roj.reflect.ReflectionUtils.u;

/**
 * MP3解码器
 * @author Roj234
 * @since 2024/2/18 23:37
 */
public final class MP3Decoder implements AudioDecoder {
	private static final Logger LOGGER = Logger.getLogger("MP3Decoder");
	private static final int BUFFER_LEN = 16384;
	private static final long STATE_OFFSET = ReflectionUtils.fieldOffset(MP3Decoder.class, "eof");

	private Source in;
	private AudioOutput out;

	private TaskPool asyncSyh;

	private final Header header = new Header();
	private final byte[] buf = ArrayCache.getByteArray(BUFFER_LEN, false);
	private int off, maxOff;
	private volatile int eof;
	private volatile long seekPos;

	byte[] pcm;
	final int[] pcmOff = new int[2];

	public MP3Decoder() {}

	@Override
	public AudioMetadata open(Source in, AudioOutput out, boolean parseMetadata) throws IOException, LineUnavailableException {
		if (eof == 1) throw new IllegalStateException("Already decoding");
		maxOff = off = 0;
		eof = 0;
		seekPos = -1;

		this.in = in;
		this.out = out;

		in.seek(0);
		AudioMetadata metadata = null;
		if (parseMetadata) {
			try {
				metadata = parseTag();
			} catch (Exception e) {
				LOGGER.error("解析音频元数据失败", e);
			}
		}

		header.reset((int)in.position()-off, (int)in.length());
		// 定位并解码第一帧
		nextHeader();
		if (eof == 0) out.init(header.getAudioFormat(), (header.getMode()==3?1:2)*header.getSamplingRate());

		return metadata;
	}
	private AudioMetadata parseTag() throws IOException {
		var id3 = new ID3Tag();
		AudioMetadata tag = null;

		int len;
		if ((len = in.read(buf, 0, Math.min(BUFFER_LEN, 4096))) <= 10) {
			eof = 2;
			return null;
		}

		var _buf = DynByteBuf.wrap(buf, 0, len);

		// ID3 v2
		int tagSize = id3.checkID3V2(_buf);
		if (tagSize > len) {
			in.seek(0);
			var st = MyDataInputStream.wrap(in.asInputStream());
			try {
				id3.parseID3V2(st);
			} finally {
				if (st instanceof Finishable f) f.finish();
			}
			in.seek(tagSize);
			tag = id3;
		} else if (tagSize > 10) {
			_buf.rIndex = 0;
			id3.parseID3V2(_buf);
			off = tagSize;
			maxOff = len;
			tag = id3;
		} else {
			int length = (int) in.length();

			// try ID3 v1
			if (length >= 128) {
				in.seek(length - 128);
				in.readFully(buf, 0, 128);
				if (id3.parseID3V1(buf, 0)) {
					tag = id3;
					length -= 128;
				}
			}

			// then try APE
			if (length >= 32) {
				in.seek(length-32);
				in.readFully(buf, 0, 32);

				APETag ape = new APETag();
				tagSize = ape.findFooter(buf, 0);
				if (tagSize != 0) {
					in.seek(length - 32 - Math.abs(tagSize));

					var st = MyDataInputStream.wrap(in.asInputStream());
					try {
						ape.parseTag(st, tagSize < 0);
					} finally {
						if (st instanceof Finishable f) f.finish();
					}

					tag = ape;
				}
			}

			in.seek(0);
		}

		return tag;
	}

	@Override public boolean isOpen() { return in != null; }

	@Override
	public void stop() {
		synchronized (this) {
			if (u.compareAndSwapInt(this, STATE_OFFSET, 1, 2)) {
				try {
					wait();
				} catch (InterruptedException ignored) {}
			}
		}
		in = null;
		out = null;
	}

	@Override
	public void close() {
		AudioDecoder.super.close();
		ArrayCache.putArray(buf);
	}

	@Override public boolean isDecoding() { return eof == 1; }

	@Override
	public void decodeLoop() throws IOException {
		if (!u.compareAndSwapInt(this, STATE_OFFSET, 0, 1)) return;

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
			while (eof == 1) {
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
			if (pcmOff[0] > 0 && out != null) out.write(pcm, 0, pcmOff[0], true);
			ArrayCache.putArray(pcm);
			eof = 3;
			synchronized (this) {notifyAll();}
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
				eof = 2;
				break;
			}
		}

		this.off = header.offset();
		this.maxOff = maxOff;
	}

	@Override public boolean isSeekable() { return isDecoding(); }
	@Override public void seek(double second) throws IOException {this.seekPos = header.seek(second);}

	@Override public double getCurrentTime() {return header.getTimePlayed();}
	@Override public double getDuration() {
		if (in != null) return header.getDuration();
		return -1;
	}

	@Override
	@NotNull
	public String getDebugInfo() { return header.toString(); }
	public Header getHeader() { return header; }
}