package roj.text;

import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.reflect.Unaligned;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * @author Roj234
 * @since 2023/5/14 23:31
 */
public final class CharsetDetector implements AutoCloseable {
	private static final List<String> charsetNames = new ArrayList<>();
	private static final List<Scorer> scorers = new ArrayList<>();

	@FunctionalInterface
	public interface Scorer {
		int score(byte[] b, int off, int end);
	}

	public static synchronized void register(String charset, Scorer scorer) {
		charsetNames.add(charset);
		scorers.add(scorer);
	}

	static {
		register("UTF-8", CharsetDetector::scoreUTF8);
		register("UTF-16LE", CharsetDetector::scoreUTF16LE);
		register("UTF-16BE", CharsetDetector::scoreUTF16BE);
		register("GB18030", CharsetDetector::scoreGB18030);
		register("BIG5", CharsetDetector::scoreBIG5);
		register("Shift-JIS", CharsetDetector::scoreShiftJIS);
	}

	private byte[] b;
	private int skip, bLen;

	private Closeable in;
	private byte type;

	public CharsetDetector(Closeable in) { input(in); }
	public CharsetDetector input(Closeable in) {
		this.in = in;
		if (in instanceof ReadableByteChannel) {
			type = 1;
		} else if (in instanceof InputStream) {
			type = 0;
		} else if (in instanceof DynByteBuf) {
			type = 2;
		} else {
			throw new IllegalArgumentException("暂不支持"+in.getClass().getName());
		}
		return this;
	}

	public String detect() throws IOException {
		if (b == null) {
			b = ArrayCache.getByteArray(4096, false);
			type |= 0x40;
		}
		skip = 0;
		bLen = 0;

		int len = readUpto(4);
		if (len < 2) return "US-ASCII";

		// 填充至4字节以便BOM检测
		int a = len;
		while (a < 4) b[a++] = 1;

		String bomCharset = detectBOM(b);
		if (bomCharset != null) {
			System.arraycopy(b, skip, b, 0, bLen = len - skip);
			return bomCharset;
		}
		bLen = len;

		var scores = new int[charsetNames.size()];
		int minScore = 0;
		int maxScore = 0;
		int maxScoreIndex = 0;

		len = 0;
		while (true) {
			bLen += readUpto(b.length - bLen);

			for (int i = 0; i < scores.length; i++) {
				Scorer scorer = scorers.get(i);
				if (scores[i] > -128)
					scores[i] += scorer.score(b, len, bLen);
			}

			for (int i = 0; i < scores.length; i++) {
				int score = scores[i];
				if (score > maxScore) {
					maxScore = score;
					maxScoreIndex = i;
				}
				minScore = Math.min(minScore, score);
			}

			// 如果差异很大，可以中止
			if (maxScore - minScore > 100)
				return charsetNames.get(maxScoreIndex);

			// EOF
			if (len == bLen) break;
			len = bLen;

			if (bLen == b.length) {
				// 读取太多字节了，最多读取这么多够了
				if (bLen > 32767) break;

				byte[] b1 = ArrayCache.getByteArray(b.length << 1, false);
				System.arraycopy(b, 0, b1, 0, b.length);
				ArrayCache.putArray(b);
				b = b1;
			}
		}

		if (maxScore - minScore > bLen/10)
			return charsetNames.get(maxScoreIndex);

		throw new IOException("无法确定编码,"+charsetNames+","+Arrays.toString(scores));
		// fallback
		//return "ISO-8859-1";
	}

	private String detectBOM(byte[] b) {
		switch (b[0] & 0xFF) {
			case 0x00:
				if ((b[1] == (byte) 0x00) && (b[2] == (byte) 0xFE) && (b[3] == (byte) 0xFF)) {
					skip = 4;
					return "UTF-32BE";
				}
				break;
			case 0xFF:
				if (b[1] == (byte) 0xFE) {
					if ((b[2] == (byte) 0x00) && (b[3] == (byte) 0x00)) {
						skip = 4;
						return "UTF-32LE";
					} else {
						skip = 2;
						return "UTF-16LE";
					}
				}
				break;
			case 0xEF:
				if ((b[1] == (byte) 0xBB) && (b[2] == (byte) 0xBF)) {
					skip = 3;
					return "UTF-8";
				}
				break;
			case 0xFE:
				if ((b[1] == (byte) 0xFF)) {
					skip = 2;
					return "UTF-16BE";
				}
				break;
			case 0x84:
				// ZWNBSP in GB18030
				if (b[1] == (byte) 0x31 && (b[2] == (byte) 0x95) && (b[3] == (byte) 0x33)) {
					skip = 4;
					return "GB18030";
				}
		}
		return null;
	}

	private int readUpto(int max) throws IOException {
		switch (type & 3) {
			default:
			case 0: {
				InputStream in = (InputStream) this.in;
				int len = 0;
				while (len < max) {
					int r = in.read(b, bLen + len, max - len);
					if (r < 0) break;
					len += r;
				}
				return len;
			}
			case 1: {
				ReadableByteChannel in = ((ReadableByteChannel) this.in);
				ByteBuffer bb = ByteBuffer.wrap(b, bLen, max);
				while (true) {
					int r = in.read(bb);
					if (r < 0 || !bb.hasRemaining()) break;
				}
				return bb.position() - bLen;
			}
			case 2: {
				DynByteBuf in = (DynByteBuf) this.in;
				max = Math.min(in.readableBytes() - bLen, max);
				in.readFully(in.rIndex + bLen, b, bLen, max);
				return max;
			}
		}
	}

	public int skip() { return skip; }

	public byte[] buffer() { type &= ~0x40; return b; }
	public int limit() { return bLen; }

	@Override
	public void close() {
		if ((type & 0x40) != 0) {
			ArrayCache.putArray(b);
			b = null;
			type &= ~0x40;
		}
	}

	public static final int ASCII_SUCCESS = 1, DECODE_SUCCESS = 2, FREQUENCY_SUCCESS = 3, CONTROL_BYTE = -1, DECODE_FAILURE = 5;

	// 评分函数 for each encoding
	private static int scoreUTF8(byte[] data, int off, int end) {
		var verifier = new GreatScorer();
		FastCharset.UTF8().fastValidate(data, Unaligned.ARRAY_BYTE_BASE_OFFSET + off, Unaligned.ARRAY_BYTE_BASE_OFFSET + end, verifier);
		return verifier.score;
	}

	private static int scoreUTF16LE(byte[] data, int off, int end) {
		var verifier = new GreatScorer();
		FastCharset.UTF16LE().fastValidate(data, Unaligned.ARRAY_BYTE_BASE_OFFSET + off, Unaligned.ARRAY_BYTE_BASE_OFFSET + end, verifier);
		return verifier.score;
	}

	private static int scoreUTF16BE(byte[] data, int off, int end) {
		var verifier = new GreatScorer();
		FastCharset.UTF16BE().fastValidate(data, Unaligned.ARRAY_BYTE_BASE_OFFSET + off, Unaligned.ARRAY_BYTE_BASE_OFFSET + end, verifier);
		return verifier.score;
	}

	private static int scoreGB18030(byte[] data, int off, int end) {
		var verifier = new GreatScorer();
		FastCharset.GB18030().fastValidate(data, Unaligned.ARRAY_BYTE_BASE_OFFSET + off, Unaligned.ARRAY_BYTE_BASE_OFFSET + end, verifier);
		return verifier.score;
	}

	private static int scoreBIG5(byte[] data, int off, int end) {
		int score = 0;
		while (off < end) {
			int byte1 = data[off++] & 0xFF;
			if (byte1 <= 0x7F) {
				score += GreatScorer.isControl(byte1) ? CONTROL_BYTE : ASCII_SUCCESS;
				continue;
			} else if (byte1 >= 0xA1 && byte1 <= 0xFE) {
				if (off < end) {
					int byte2 = data[off] & 0xFF;
					if (byte2 >= 0x40 && byte2 <= 0x7E || byte2 >= 0xA1 && byte2 <= 0xFE) {
						score += DECODE_SUCCESS;
						off ++;
						continue;
					}
				} else {
					break;
				}
			}
			score -= DECODE_FAILURE;
		}
		return score;
	}

	private static int scoreShiftJIS(byte[] data, int off, int end) {
		int score = 0;
		while (off < end) {
			int byte1 = data[off++] & 0xFF;
			if (byte1 <= 0x7F) {
				score += GreatScorer.isControl(byte1) ? CONTROL_BYTE : ASCII_SUCCESS;
				continue;
			} else if (byte1 >= 0x81 && byte1 <= 0x9F || byte1 >= 0xE0 && byte1 <= 0xFC) {
				if (off < end) {
					int byte2 = data[off] & 0xFF;
					if (byte2 >= 0x40 && byte2 <= 0x7E || byte2 >= 0x80 && byte2 <= 0xFC) {
						score += DECODE_SUCCESS;
						off ++;
						continue;
					}
				} else {
					break;
				}
			}
			score -= DECODE_FAILURE;
		}
		return score;
	}

	static final class GreatScorer implements IntConsumer {
		private static final BitSet VERY_COMMON = new BitSet(40863);
		private static final BitSet COMMON = new BitSet(40718);

		static {
			try (var in = CharsetDetector.class.getClassLoader().getResourceAsStream("roj/text/CharsetDetector.txt")) {
				byte[] b = ArrayCache.getByteArray(1024, false);
				var list = new BitSet[] {VERY_COMMON, COMMON};
				int off = 0;
				var set = list[off];

				while (true) {
					int r = in.read(b);
					if (r < 0) break;

					for (int i = 0; i < r; i+=2) {
						// UTF-16LE
						char c = (char) ((b[i] & 0xFF) | ((b[i+1] & 0xFF) << 8));
						if (c == '\n') set = list[++off];
						else set.add(c);
					}
				}
				ArrayCache.putArray(b);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		static boolean isControl(int c) {return c < 32 && c != '\r' && c != '\n' && c != '\t';}

		public int score;

		@Override public void accept(int c) {
			if (c < 0) {
				if (c != FastCharset.TRUNCATED) score -= DECODE_FAILURE;
			} else if (isControl(c)) { // 控制字符
				score += CONTROL_BYTE;
			} else if (VERY_COMMON.contains(c) || c >= Character.MIN_SUPPLEMENTARY_CODE_POINT) { // 常用字或（正确解码的）表情符号
				score += FREQUENCY_SUCCESS;
			} else if (COMMON.contains(c)) {
				score += DECODE_SUCCESS;
			} else {
				score += ASCII_SUCCESS;
			}
		}
	}
}