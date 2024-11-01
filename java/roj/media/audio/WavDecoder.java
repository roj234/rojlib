package roj.media.audio;

import org.jetbrains.annotations.Nullable;
import roj.collect.MyHashMap;
import roj.io.Finishable;
import roj.io.MyDataInputStream;
import roj.io.source.Source;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.util.Map;

import static roj.util.ByteList.FOURCC;

/**
 * @author Roj234
 * @since 2024/2/19 0019 3:25
 */
public class WavDecoder implements AudioDecoder {
	private static final int RIFF = FOURCC("RIFF");
	private static final int FACT = FOURCC("fact");
	private static final int DATA = FOURCC("data");
	private static final int LIST = FOURCC("LIST");
	private static final int INFO = FOURCC("INFO");
	private static final int INF0 = FOURCC("INF0");

	private final ByteList buf = new ByteList(19);
	private volatile Source in;
	private AudioFormat af;
	private AudioOutput out;
	private int bytePerSecond, dataBegin, dataEnd, sampleCount;

	@Override
	public @Nullable AudioMetadata open(Source in, AudioOutput out, boolean parseMetadata) throws IOException, LineUnavailableException {
		ByteList buf = this.buf;
		buf.clear();

		in.seek(0);
		in.read(buf, 20);

		if (buf.readInt() != RIFF) throw new IOException("not a RIFF file");
		int dataLen = buf.readIntLE();
		if (!buf.readAscii(8).equals("WAVEfmt ")) throw new IOException("not a WAVE file");
		int fmtLen = buf.readIntLE();
		buf.clear();
		in.read(buf, fmtLen);

		int format = buf.readUShortLE();
		if (format != 1) throw new IOException("compressed WAVE file (0x"+Integer.toHexString(format)+")");

		this.in = in;
		this.out = out;

		int channels = buf.readUShortLE();
		int sampleRate = buf.readIntLE();
		//每秒的数据量，波形音频数据传送速率，其值为通道数×每秒样本数×每样本的数据位数／8。播放软件利用此值可以估计缓冲区的大小。
		int frameSize = buf.readIntLE();
		int align = buf.readUShortLE();
		int sampleSize = buf.readUShortLE();

		assert align == ((sampleSize + 7) / 8) * channels;

		AudioFormat af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, sampleSize, channels, align, sampleRate, false);

		sampleCount = -1;
		while (true) {
			buf.clear();
			in.read(buf, 8);
			int type = buf.readInt(0);
			if (type == DATA) break;
			if (type == FACT) {
				buf.clear();
				in.read(buf, 4);
				sampleCount = buf.readIntLE();
			} else {
				in.skip(buf.readIntLE(4));
				//System.out.println("unknown wave block:"+buf.dump());
			}
		}

		out.init(af, frameSize / 2);

		this.af = af;
		bytePerSecond = frameSize;
		dataBegin = (int) in.position();
		dataEnd = dataBegin+buf.readIntLE(4);

		if (parseMetadata && dataLen+8 != dataEnd) {
			in.seek(dataEnd);

			try {
				while (in.length() - in.position() > 8) {
					buf.clear();
					in.read(buf, 8);

					int type = buf.readInt(0);
					int len = buf.readIntLE(4);

					if (type == LIST) {
						buf.clear();
						in.read(buf, len);
						int subType = buf.readInt();
						if (subType == INFO || subType == INF0) {
							return new WavListTag(buf);
						}
					} else if (buf.readAscii(8).equals("APETAGEX")) {
						APETag tag = new APETag();

						in.seek(in.length() - 8);
						var mdi = MyDataInputStream.wrap(in.asInputStream());
						try {
							tag.parseTag(mdi, false);
							return tag;
						} finally {
							if (mdi instanceof Finishable f) f.finish();
						}
					} else {
						in.skip(len);
						System.out.println("unknown wave block:" + buf.dump());
					}
				}
			} finally {
				in.seek(dataBegin);
			}
		}
		return null;
	}

	/**
	 * RIFF Tags
	 * https://exiftool.org/TagNames/RIFF.html#Info
	 * @since 2024/2/19 23:57
	 */
	public static final class WavListTag implements AudioMetadata {
		private final Map<String, String> tags;
		public WavListTag(Map<String, String> tags) { this.tags = tags; }
		public WavListTag(DynByteBuf b) {
			tags = new MyHashMap<>();
			int num = 0;
			while (b.isReadable()) {
				String fourcc = b.readAscii(4);
				int dataLen = b.readIntLE();
				String str;
				try {
					str = b.readUTF(dataLen);
				} catch (Exception e) {
					str = b.readAscii(dataLen);
				}
				tags.put(fourcc, str);

				if (num > 0 && b.readUnsignedByte() != num) break;
				num++;
			}
		}

		@Override
		public @Nullable String getTitle() { return tags.get("TITL"); }
		@Override
		public @Nullable String getArtist() { return tags.get("IART"); }
		@Override
		public @Nullable String getAlbum() { return null; }
		@Override
		public @Nullable String getYear() { return tags.get("YEAR"); }
		@Override
		public @Nullable String getCoder() { return tags.getOrDefault("CODE", tags.get("ISFT")); }
		@Override
		public @Nullable String getNamedAttribute(String name) { return tags.get(name); }

		@Override
		public String toString() { return "WavTag: "+tags.toString(); }
	}

	@Override
	public boolean isOpen() { return in != null; }

	@Override
	public void stop() {
		in = null;
		out = null;
		dataBegin = dataEnd = bytePerSecond = 0;
	}

	@Override
	public boolean isDecoding() {
		try {
			return in != null && in.position() < dataEnd;
		} catch (IOException e) {
			return false;
		}
	}
	@Override
	public void decodeLoop() throws IOException {
		Source in1 = in;
		while (in != null && in1.position() < dataEnd) {
			buf.clear();
			int len = (int) Math.min(bytePerSecond, dataEnd-in1.position());
			in1.read(buf, len);
			out.write(buf.list, 0, len, true);
		}
	}

	@Override
	public boolean isSeekable() { return isDecoding(); }
	@Override
	public void seek(double second) throws IOException {
		int pos = (int) (second * bytePerSecond);
		if (pos < 0) pos = 0;
		else if (pos > dataEnd) pos = dataEnd;
		// ~ ( fs - 1 ) => - fs
		in.seek(dataBegin + (pos & -af.getFrameSize()));
	}

	@Override
	public double getCurrentTime() {
		if (in != null) try {
			return (double) (in.position() - dataBegin) / bytePerSecond;
		} catch (IOException ignored) {}
		return -1;
	}

	@Override
	public double getDuration() { return in == null ? 0 : (double) dataEnd/bytePerSecond; }

	@Override
	public String getDebugInfo() { return "Wav RawPcm "+af+", bps="+bytePerSecond+", sampleCount="+sampleCount; }
}