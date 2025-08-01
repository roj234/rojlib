package roj.text;

import roj.archive.xz.LZMAInputStream;
import roj.util.ArrayCache;

import java.io.IOException;
import java.util.function.IntConsumer;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2023/4/27 15:48
 */
final class GB18030 extends FastCharset {
	static final FastCharset INSTANCE = new GB18030(), EXCEPTIONAL = new GB18030();
	private GB18030() {}

	@Override public String name() {return "GB18030";}
	@Override public FastCharset throwException(boolean doThrow) {return doThrow ? EXCEPTIONAL : INSTANCE;}

	private static final int TAB2 = 24066;
	private static final char[] TABLE, REVERSE_TABLE;

	static {
		TABLE = (char[]) U.allocateUninitializedArray(char.class, 63486);
		REVERSE_TABLE = (char[]) U.allocateUninitializedArray(char.class, 65408);
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

	@Override
	public long fastEncode(char[] s, int i, int end, Object ref, long addr, int outMax) {
		// +5% for ASCII, +11% for chinese
		int previ = i;
		while (i < end) {
			int c = s[i++];
			if (c <= 0x7F) {
				if (outMax == 0) break;
				outMax--;

				previ = i;
				U.putByte(ref, addr++, (byte) c);
				continue;
			}

			int cp;
			surrogateCheck: {
				if (isSurrogate(c)) {
					if (i == end) break;
					c = this == EXCEPTIONAL ? codepoint(c, s[i++]) : codepointNoExc(c, s[i++]);

					if (c > 0xFFFF) {
						cp = c + 123464 + TAB2;
						break surrogateCheck;
					}
				}
				cp = REVERSE_TABLE[c-128] - 1;
				assert cp >= 0;
			}

			if (cp < TAB2) { // two bytes
				if (outMax < 2) break;
				outMax -= 2;

				U.putByte(ref, addr++, (byte) (129 + (cp / 191)));
				U.putByte(ref, addr++, (byte) (cp % 191 + 64));
			} else { // four bytes
				if (outMax < 4) break;
				outMax -= 4;

				cp -= TAB2;

				U.putByte(ref, addr++, (byte) (129 + cp / 12600));
				cp %= 12600;
				U.putByte(ref, addr++, (byte) (48 + cp / 1260));
				cp %= 1260;
				U.putByte(ref, addr++, (byte) (129 + cp / 10));
				U.putByte(ref, addr++, (byte) (48 + cp % 10));
			}

			previ = i;
		}

		return ((long)previ << 32) | outMax;
	}

	@Override
	public long fastDecode(Object ref, long base, int pos, int end, char[] out, int off, int outMax) {
		long i = base+pos;
		outMax += off;

		int c;
		long previ = i;
		malformed: {
		for (long max = base+end; i < max && off < outMax; previ = i) {
			c = U.getByte(ref,i++);
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
			if (i == max) break;

			int c2 = U.getByte(ref,i++) & 255;
			if (c2 <= 57) {
				if (c2 < 48) break malformed;

				if (max-i < 2) break;

				int c3 = U.getByte(ref,i++) & 255;
				int c4 = U.getByte(ref,i++) & 255;

				if (c3 == 128 || c3 == 255 || c4 < 48 || c4 > 57) break malformed;

				int cp = (((c-129) * 10 + (c2-48)) * 126 + c3 - 129) * 10 + c4 - 48;

				if (cp <= 39419) {
					out[off++] = TABLE[TAB2+cp];
				} else {
					cp -= 123464;
					if (cp < 0 || cp > Character.MAX_CODE_POINT) break malformed;

					if (cp < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
						out[off++] = (char) cp;
						continue;
					}

					if (outMax-off < 2) break;

					out[off++] = Character.highSurrogate(cp);
					out[off++] = Character.lowSurrogate(cp);
				}
			} else {
				// double
				if (c2 == 127 || c2 == 255 || c2 < 64) break malformed;

				int cp = (c-129) * (255-64) + (c2-64);
				out[off++] = TABLE[cp];
			}
		}

		return ((previ-base) << 32) | off;}

		if (this == EXCEPTIONAL) throw new IllegalArgumentException((int) (previ-base) + " 附近解码错误");
		out[off++] = (char) c;
		return ((1+previ-base) << 32) | off;
	}

	@Override
	public void fastValidate(Object ref, long i, long max, IntConsumer cs) {
		int c;

		while (i < max) {
			c = U.getByte(ref,i++);
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

			int c2 = U.getByte(ref,i++) & 255;
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

				int c3 = U.getByte(ref,i++) & 255;
				if (c3 == 128 || c3 == 255) {
					cs.accept(MALFORMED - 4);
					i -= 2;
					continue;
				}

				int c4 = U.getByte(ref,i++) & 255;
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
			if (c <= 0x80) continue;

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
				cp = REVERSE_TABLE[c-128];
			}

			if (cp <= TAB2) len++;
			else len += 3;
		}

		return len;
	}

	@Override
	public int addBOM(Object ref, long addr) {U.put32UB(ref, addr, 0x84319533);return 4;}

	@Override
	public int encodeSize(int codepoint) {
		if (codepoint <= 0x80) return 1;
		var cp = REVERSE_TABLE[codepoint-0x80];
		if (cp == 0) return -1;
		return cp <= TAB2 ? 2 : 4;
	}
}