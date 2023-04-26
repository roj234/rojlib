package roj.text;

import roj.archive.qz.xz.LZMAInputStream;
import roj.io.ChineseInputStream;
import roj.reflect.FieldAccessor;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.function.IntConsumer;

import static java.lang.Character.MAX_HIGH_SURROGATE;
import static java.lang.Character.MIN_HIGH_SURROGATE;
import static roj.reflect.FieldAccessor.u;

/**
 * @author Roj234
 * @since 2023/4/27 0027 15:48
 */
public class GB18030 extends UnsafeCharset {
	public static final UnsafeCharset CODER = new GB18030();

	@Override
	public String name() { return "GB18030"; }

	private static final int TAB2 = 24066;
	private static final char[] TABLE = new char[63494];
	private static final char[] REVERSE_TABLE = new char[65536];

	static {
		try (InputStream in = new LZMAInputStream(ChineseInputStream.class.getResourceAsStream("/META-INF/gb18030.lzma"))) {
			byte[] b = new byte[1024];
			long off = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? Unsafe.ARRAY_CHAR_BASE_OFFSET : 0;
			while (true) {
				// UTF16-LE chars
				int r = in.read(b);
				if (r < 0) break;
				if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
					FieldAccessor.u.copyMemory(b, Unsafe.ARRAY_BYTE_BASE_OFFSET, TABLE, off, r);
				} else {
					for (int i = 0; i < r; i+=2) {
						TABLE[((int)off+i) >> 1] = (char) ((b[i]&0xFF) | ((b[i+1]&0xFF) << 8));
					}
				}
				off += r;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		for (int i = 0; i < TABLE.length; i++) {
			REVERSE_TABLE[TABLE[i]] = (char) (i + 1);
		}
	}

	private static final Charset J_GB2312, J_GBK, J_GB18030;
	static {
		boolean b = Charset.isSupported("GB2312");
		J_GB2312 = b ? Charset.forName("GB2312") : null;
		b = Charset.isSupported("GBK");
		J_GBK = b ? Charset.forName("GBK") : null;
		b = Charset.isSupported("GB18030");
		J_GB18030 = b ? Charset.forName("GB18030") : null;
	}
	public static boolean is(Charset cs) {
		return cs != null && cs == J_GB2312 || cs == J_GBK || cs == J_GB18030;
	}

	public int byteCount(CharSequence s, int i, int len) {
		int end = i+len;
		while (i < end) {
			int c = s.charAt(i++);
			if (c <= 0x7F) continue;

			int cp;
			check: {
				if (c >= MIN_HIGH_SURROGATE && c <= MAX_HIGH_SURROGATE) {
					if (i == end) throw new IllegalStateException("Trailing high surrogate \\u" + Integer.toHexString(c));
					c = TextUtil.codepoint(c,s.charAt(i++));
					len--;

					if (c > 0xFFFF) {
						cp = c + (123464 + 0x10000);
						break check;
					}
				}
				cp = REVERSE_TABLE[c]-1;
			}

			if (cp < TAB2) len++;
			else len += 3;
		}

		return len;
	}

	@Override
	public long unsafeDecode(Object ref, long base, int pos, int end, char[] out, int off, int outMax) {
		if (pos < 0) throw new IllegalArgumentException("pos="+pos);

		long i = base+pos;
		long max = base+end;

		int c;
		while (i < max) {
			if (outMax == 0) return (i-base) << 32;

			c = u.getByte(ref, i);
			if (c < 0) break;
			i++;
			outMax--;
			out[off++] = (char) c;
		}

		malformed: {
		while (i < max) {
			if (outMax == 0) break;
			outMax--;

			c = u.getByte(ref,i++);
			// US_ASCII
			if (c >= 0) {
				out[off++] = (char) c;
				continue;
			}

			c &= 0xFF;
			if (c == 128 || c == 255) break malformed;
			if (i == max) {
				i--;
				outMax++;
				break;
			}

			int c2 = u.getByte(ref,i++) & 255;
			if (c2 < 48) break malformed;

			if (c2 <= 57) {
				if (max-i < 2) {
					i -= 2;
					outMax++;
					break;
				}

				int c3 = u.getByte(ref,i++) & 255;
				if (c3 == 128 || c3 == 255) break malformed;

				int c4 = u.getByte(ref,i++) & 255;
				if (c4 < 48 || c4 > 57) break malformed;

				int cp = (((c-129) * 10 + (c2-48)) * 126 + c3 - 129) * 10 + c4 - 48;

				if (cp <= 39419) {
					out[off++] = TABLE[TAB2+cp];
				} else {
					cp -= 123464 + Character.MIN_SUPPLEMENTARY_CODE_POINT;
					if (cp < 0 || cp > 1048575) break malformed;
					if (outMax == 0) return (i-base) << 32;
					outMax--;

					out[off++] = (char)((cp>>>10) + Character.MIN_HIGH_SURROGATE);
					out[off++] = (char)((cp&1023) + Character.MIN_LOW_SURROGATE);
				}
			} else {
				// double
				if (c2 == 127 || c2 == 255 || c2 < 64) break malformed;

				int cp = (c-128) * (255-64) + (c2-64);
				out[off++] = TABLE[cp];
			}
		}

		return ((i-base) << 32) | outMax;}
		throw new IllegalArgumentException((int) (i-base) + " 附近解码错误");
	}

	@Override
	public void unsafeValidate(Object ref, long i, int len, IntConsumer cs) {
		long max = i+len;
		int c;

		while (i < max) {
			c = u.getByte(ref,i++);
			// US_ASCII
			if (c >= 0) {
				cs.accept(c);
				continue;
			}

			c &= 0xFF;
			if (c == 128 || c == 255) {
				cs.accept(MALFORMED - 1);
				continue;
			}
			if (i == max) {
				cs.accept(TRUNCATED);
				break;
			}

			int c2 = u.getByte(ref,i++) & 255;
			if (c2 < 48) {
				cs.accept(MALFORMED - 2);
				i -= 1;
				continue;
			}

			if (c2 <= 57) {
				if (max-i < 2) {
					cs.accept(TRUNCATED);
					break;
				}

				int c3 = u.getByte(ref,i++) & 255;
				if (c3 == 128 || c3 == 255) {
					cs.accept(MALFORMED - 4);
					i -= 2;
					continue;
				}

				int c4 = u.getByte(ref,i++) & 255;
				if (c4 < 48 || c4 > 57) {
					cs.accept(MALFORMED - 4);
					i -= 3;
					continue;
				}

				int cp = (((c-129) * 10 + (c2-48)) * 126 + c3 - 129) * 10 + c4 - 48;

				if (cp <= 39419) {
					cs.accept(TABLE[TAB2+cp]);
				} else {
					cp -= 123464 + Character.MIN_SUPPLEMENTARY_CODE_POINT;
					if (cp < 0 || cp > 1048575) {
						cs.accept(MALFORMED - 4);
						i -= 3;
						continue;
					}

					cs.accept(cp);
					//out.append((char)((cp>>>10) + Character.MIN_HIGH_SURROGATE))
					//   .append((char)((cp&1023) + Character.MIN_LOW_SURROGATE));
				}
			} else {
				// double
				if (c2 == 127 || c2 == 255 || c2 < 64) {
					cs.accept(MALFORMED - 2);
					i -= 1;
					continue;
				}

				int cp = (c-128) * (255-64) + (c2-64);
				cs.accept(TABLE[cp]);
			}
		}
	}

	@Override
	public long unsafeEncode(char[] s, int i, int end, Object ref, long addr, int outMax) {
		while (i < end) {
			int c = s[i];
			if (c > 0x7F) break;
			i++;

			if (outMax == 0) return ((long) i << 32);
			outMax--;

			u.putByte(ref, addr++, (byte) c);
		}

		while (i < end) {
			int c = s[i++];

			if (c <= 0x7F) {
				if (outMax == 0) break;
				outMax--;

				u.putByte(ref, addr++, (byte) c);
				continue;
			}

			int cp;
			check:
			{
				if (c >= MIN_HIGH_SURROGATE && c <= MAX_HIGH_SURROGATE) {
					c = TextUtil.codepoint(c, s[i++]);
					if (c > 0xFFFF) {
						cp = c + 123464 + TAB2;
						break check;
					}
				}
				cp = REVERSE_TABLE[c] - 1;
				if (cp < 0) throw new AssertionError();
			}

			if (cp < TAB2) { // two bytes
				if (outMax < 2) break;
				outMax -= 2;

				u.putByte(ref, addr++, (byte) (128 + (cp / 191)));
				u.putByte(ref, addr++, (byte) (cp % 191 + 64));
			} else { // four bytes
				if (outMax < 4) break;
				outMax -= 4;

				cp -= TAB2;

				u.putByte(ref, addr++, (byte) (129 + cp / 12600));
				cp %= 12600;
				u.putByte(ref, addr++, (byte) (48 + cp / 1260));
				cp %= 1260;
				u.putByte(ref, addr++, (byte) (129 + cp / 10));
				u.putByte(ref, addr++, (byte) (48 + cp % 10));
			}
		}

		return ((long)i << 32) | outMax;
	}
}
