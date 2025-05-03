package roj.text;

import roj.collect.MyBitSet;
import roj.io.IOUtil;
import roj.reflect.Unaligned;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.function.IntConsumer;

/**
 * @author Roj234
 * @since 2023/5/14 23:31
 */
public final class CharsetDetector implements IntConsumer, AutoCloseable {
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

		// avoid UTF-32 issue
		int a = len;
		while (a < 4) b[a++] = 1;

		String bomCharset = detectBOM(b);
		if (bomCharset != null) {
			System.arraycopy(b, skip, b, 0, bLen = len-skip);
			return bomCharset;
		}
		bLen = len;

		int utf8_score = 0, utf8_negate = 0;
		int gb18030_score = 0, gb18030_negate = 0;

		len = 0;
		while (true) {
			bLen += readUpto(b.length - bLen);

			ps = ns = 0;
			FastCharset.UTF8().fastValidate(b, Unaligned.ARRAY_BYTE_BASE_OFFSET + len, Unaligned.ARRAY_BYTE_BASE_OFFSET + bLen, this);
			utf8_score += ps - 5*ns;
			utf8_negate += ns;

			ps = ns = 0;
			FastCharset.GB18030().fastValidate(b, Unaligned.ARRAY_BYTE_BASE_OFFSET + len, Unaligned.ARRAY_BYTE_BASE_OFFSET + bLen, this);
			gb18030_score += ps - 5*ns;
			gb18030_negate += ns;

			if (Math.abs(utf8_score - gb18030_score) > 100) {
				// ASCII时相同
				if (utf8_score >= gb18030_score || utf8_negate * 3 < gb18030_negate * 2) return "UTF8";
				else if (gb18030_score > 0 && gb18030_negate * 3 < utf8_negate * 2) return "GB18030";
			}

			if (len == bLen) break;
			len = bLen;

			if (bLen == b.length) {
				if (bLen > 32767) break;

				byte[] b1 = ArrayCache.getByteArray(b.length << 1, false);
				System.arraycopy(b, 0, b1, 0, b.length);
				ArrayCache.putArray(b);
				b = b1;
			}
		}

		// nearly impossible hit here when file is large
		if (utf8_score >= gb18030_score || utf8_negate * 3 < gb18030_negate * 2) return "UTF8";
		else if (gb18030_score > 0 && gb18030_negate * 3 < utf8_negate * 2) return "GB18030";

		if (utf8_negate == gb18030_negate) return "UTF8";

		throw new IOException("无法确定编码,utf8_score="+utf8_score+",gbk_score="+gb18030_score);
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
					int r = in.read(b, bLen+len, max-len);
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
				return bb.position()-bLen;
			}
			case 2: {
				DynByteBuf in = (DynByteBuf) this.in;
				max = Math.min(in.readableBytes()-bLen, max);

				in.readFully(in.rIndex+bLen, b, bLen, max);
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

	//19968, 40863
	public static final MyBitSet 常用字 = new MyBitSet(40863);
	//19984, 40718
	public static final MyBitSet 次常用字 = new MyBitSet(40718);
	public static final MyBitSet 标点 = new MyBitSet(65507);
	public static final String 半角 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 `~!@#$%^&*()_+-={}|[]:\";'<>?,./";
	public static final String 全角;
	static {
		try (var in = CharsetDetector.class.getClassLoader().getResourceAsStream("roj/text/CharsetDetector.txt")) {
			byte[] b = ArrayCache.getByteArray(1024, false);
			var list = new MyBitSet[] {常用字, 次常用字, 标点};
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

		char[] arr = 半角.toCharArray();
		for (int i = 0; i < arr.length; i++) arr[i] += 65248;
		全角 = new String(arr);
	}

	// regexp: ([a-z])\1
	// 就这样

	public static CharSequence naturalize(CharSequence seq) {
		CharList tmp = IOUtil.getSharedCharBuf().append(seq);
		for (int i = 0; i < tmp.length(); i++) {
			tmp.set(i, naturalize(tmp.charAt(i)));
		}
		return tmp.toString();
	}
	public static char naturalize(char c) {
		if (c == '。') return '.';
		if (标点.contains(c)) return '!';

		int i = 全角.indexOf(c);
		if (i >= 0) c = 半角.charAt(i);
		return Character.toLowerCase(c);
	}

	private int ps, ns;
	@Override
	public void accept(int c) {
		if (c < 0) {
			if (c != FastCharset.TRUNCATED)
				ns += 10;
		} else if (c < 32 && c != '\r' && c != '\n' && c != '\t') { // control
			ns++;
		} else if (常用字.contains(c) || c >= Character.MIN_SUPPLEMENTARY_CODE_POINT) { // 表情: 至少验证更严格
			ps += 3;
		} else if (次常用字.contains(c)) {
			ps += 2;
		} else {
			ps++;
		}
	}
}