package roj.text;

import roj.archive.qz.xz.LZMAInputStream;
import roj.collect.MyHashSet;
import roj.util.ArrayCache;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.function.IntConsumer;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2023/4/27 0027 15:48
 */
public final class GB18030 extends UnsafeCharset {
	public static final UnsafeCharset CODER = new GB18030(), THROW_ON_FAIL = new GB18030();

	@Override
	public String name() {return "GB18030";}

	private static final int TAB2 = 24066;
	private static final char[] TABLE = new char[63486];
	private static final char[] REVERSE_TABLE = new char[65408];

	static {
		try (var in = new LZMAInputStream(GB18030.class.getClassLoader().getResourceAsStream("roj/text/GB18030.lzma"))) {
			byte[] b = ArrayCache.getByteArray(4096, false);
			int off = 0;
			while (true) {
				int r = in.read(b);
				if (r < 0) break;

				for (int i = 0; i < r; i+=2) {
					// UTF-16BE
					char c = (char) (((b[i] & 0xFF) << 8) | (b[i+1] & 0xFF));
					int id = (off + i) >> 1;
					TABLE[id] = c;
					if (c != 0) {
						REVERSE_TABLE[c-128] = (char) (id+1);
					}
				}
				off += r;
			}
			ArrayCache.putArray(b);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static boolean isTwoByte(char c) {
		if (c <= 128) return false;
		var cp = REVERSE_TABLE[c-128] - 1;
		return cp >= 0 && cp < TAB2;
	}

	private static final MyHashSet<String> GB18030_IDS = new MyHashSet<>("GBK", "GB2312", "GB18030", "x-mswin-936");
	public static boolean is(Charset cs) { return cs != null && GB18030_IDS.contains(cs.name()); }

	@Override
	public long unsafeEncode(char[] s, int i, int end, Object ref, long addr, int outMax) {
		while (i < end) {
			int c = s[i];
			if (c > 0x7F) break;
			if (outMax == 0) return ((long) i << 32);

			i++;
			outMax--;

			u.putByte(ref, addr++, (byte) c);
		}

		while (i < end) {
			int c = s[i];

			if (c <= 0x7F) {
				if (outMax == 0) break;

				i++;
				outMax--;

				u.putByte(ref, addr++, (byte) c);
				continue;
			}

			int sum = 1;
			int cp;
			check:{
				if (isSurrogate(c)) {
					if (i+1 == end) break;

					c = codepoint(c, s[i+1]);
					sum++;
					if (c > 0xFFFF) {
						cp = c + 123464 + TAB2;
						break check;
					}
				}
				cp = REVERSE_TABLE[c-128] - 1;
				assert cp >= 0;
			}

			if (cp < TAB2) { // two bytes
				if (outMax < 2) break;
				outMax -= 2;

				u.putByte(ref, addr++, (byte) (129 + (cp / 191)));
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
			i += sum;
		}

		return ((long)i << 32) | outMax;
	}

	@Override
	public long unsafeDecode(Object ref, long base, int pos, int end, char[] out, int off, int outMax) {
		long i = base+pos;
		long max = base+end;
		outMax += off;

		int c;
		while (i < max && off < outMax) {
			c = u.getByte(ref, i);
			if (c < 0) break;
			i++;
			out[off++] = (char) c;
		}

		malformed: {
		while (i < max && off < outMax) {
			c = u.getByte(ref,i++);
			// US_ASCII
			if (c >= 0) {
				out[off++] = (char) c;
				continue;
			}

			c &= 0xFF;
			// ask windows for reason...
			if (c == 128) {
				out[off++] = '€';
				continue;
			}
			if (c == 255) break malformed;
			if (i == max) {
				i--;
				break;
			}

			int c2 = u.getByte(ref,i++) & 255;
			if (c2 <= 57) {
				if (c2 < 48) { i--; break malformed; }

				if (max-i < 2) {
					i -= 2;
					break;
				}

				int c3 = u.getByte(ref,i++) & 255;
				int c4 = u.getByte(ref,i++) & 255;

				if (c3 == 128 || c3 == 255 || c4 < 48 || c4 > 57) { i -= 3; break malformed; }

				int cp = (((c-129) * 10 + (c2-48)) * 126 + c3 - 129) * 10 + c4 - 48;

				if (cp <= 39419) {
					out[off++] = TABLE[TAB2+cp];
				} else {
					cp -= 123464;
					if (cp < 0 || cp > Character.MAX_CODE_POINT) { i -= 3; break malformed; }

					if (cp < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
						out[off++] = (char) cp;
						continue;
					}

					if (outMax-off < 2) return (i-base) << 32;

					out[off++] = Character.highSurrogate(cp);
					out[off++] = Character.lowSurrogate(cp);
				}
			} else {
				// double
				if (c2 == 127 || c2 == 255 || c2 < 64) {
					i--;
					break malformed;
				}

				int cp = (c-129) * (255-64) + (c2-64);
				out[off++] = TABLE[cp];
			}
		}

		return ((i-base) << 32) | off;}

		if (this == THROW_ON_FAIL) throw new IllegalArgumentException((int) (i-base) + " 附近解码错误");
		out[off++] = (char) c;
		return ((i-base) << 32) | off;
	}

	@Override
	public void unsafeValidate(Object ref, long i, long max, IntConsumer cs) {
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
				if (c == 128) c = '€';
				else c = MALFORMED;
				cs.accept(c);
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
					cp -= 123464;
					if (cp < 0 || cp > Character.MAX_CODE_POINT) { i -= 3; cs.accept(MALFORMED - 4); continue; }

					cs.accept(cp);
				}
			} else {
				// double
				if (c2 == 127 || c2 == 255 || c2 < 64) { i -= 1; cs.accept(MALFORMED - 2); continue; }

				int cp = (c-128) * (255-64) + (c2-64);
				cs.accept(TABLE[cp]);
			}
		}
	}

	@Override
	public int byteCount(CharSequence s, int i, int len) {
		int end = i+len;
		while (i < end) {
			int c = s.charAt(i++);
			if (c <= 0x7F) continue;

			int cp;
			check: {
				if (isSurrogate(c)) {
					if (i == end) throw new IllegalStateException("Trailing high surrogate \\U+"+Integer.toHexString(c));
					c = codepoint(c,s.charAt(i++));
					len--;

					if (c > 0xFFFF) {
						cp = c + 123464 + TAB2;
						break check;
					}
				}
				cp = REVERSE_TABLE[c-128] - 1;
			}

			if (cp < TAB2) len++;
			else len += 3;
		}

		return len;
	}
}