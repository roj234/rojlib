package roj.text;

import java.util.function.IntConsumer;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 */
final class UTF8 extends FastCharset {
	static final FastCharset INSTANCE = new UTF8(), EXCEPTIONAL = new UTF8();
	private UTF8() {}

	@Override public String name() {return "UTF-8";}
	@Override public FastCharset throwException(boolean doThrow) {return doThrow ? EXCEPTIONAL : INSTANCE;}
	@Override public long fastEncode(char[] s, int i, int end, Object ref, long addr, int outMax) {
		while (i < end) {
			int c = s[i];
			if (c > 0x7F) break;
			if (outMax == 0) break;
			i++;
			outMax--;
			U.putByte(ref, addr++, (byte) c);
		}

		int previ = i;
		while (i < end && outMax > 0) {
			int c = s[i++];
			if (isSurrogate(c)) {
				if (i == end) break;
				c = this == EXCEPTIONAL ? codepoint(c, s[i++]) : codepointNoExc(c, s[i++]);
			}

			if (c <= 0x7FF) {
				if (c > 0x7F) {
					if (outMax < 2) break;
					outMax -= 2;

					U.putByte(ref, addr++, (byte) (0xC0 | ((c >> 6) & 0x1F)));
					U.putByte(ref, addr++, (byte) (0x80 | (c & 0x3F)));
				} else {
					outMax--;

					U.putByte(ref, addr++, (byte) c);
				}
			} else {
				if (c > 0xFFFF) {
					if (outMax < 4) break;
					outMax -= 4;

					U.putByte(ref, addr++, (byte) (0xF0 | ((c >> 18) & 0x07)));
					U.putByte(ref, addr++, (byte) (0x80 | ((c >> 12) & 0x3F)));
				} else {
					if (outMax < 3) break;
					outMax -= 3;

					U.putByte(ref, addr++, (byte) (0xE0 | (c >> 12)));
				}
				U.putByte(ref, addr++, (byte) (0x80 | ((c >> 6) & 0x3F)));
				U.putByte(ref, addr++, (byte) (0x80 | (c & 0x3F)));
			}

			previ = i;
		}

		return ((long) previ << 32) | outMax;
	}
	@Override public long fastDecode(Object ref, long base, int pos, int end, char[] out, int off, int outMax) {
		long i = base+pos;
		long max = base+end;
		outMax += off;

		int c;
		while (i < max && off < outMax) {
			c = U.getByte(ref, i);
			if (c < 0) break;
			i++;
			out[off++] = (char) c;
		}

		long previ = i;
		malformed: {
		int c2, c3, c4;
		overflow:
		for (; i < max && off < outMax; previ = i) {
			c = U.getByte(ref, i++);
			switch ((c>>>4)&0xF) {
				case 0, 1, 2, 3, 4, 5, 6, 7:
					/* 0xxxxxxx*/
					out[off++] = (char) c;
					break;
				case 12, 13:
					if (i >= max) break overflow;

					c2 = U.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80) break malformed;

					// 11 110xxx xx ^
					// 11 111111 10xxxxxx
					// 00 001111 10000000
					out[off++] = (char) (c << 6 ^ c2 ^ 0b0000111110000000);
					break;
				case 14:
					if (i+1 >= max) break overflow;

					c2 = U.getByte(ref, i++);
					c3 = U.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80 || (c3 & 0xC0) != 0x80) break malformed;

					// 1110 xx xx ^
					//      11 10xxxx xx ^
					//      11 111111 10xxxxxx ^
					//      00 011111 10000000
					out[off++] = (char) (c << 12 ^ c2 << 6 ^ c3 ^ 0b0001111110000000);
					break;
				case 15:
					if (i+2 >= max) break overflow;

					c2 = U.getByte(ref, i++);
					c3 = U.getByte(ref, i++);
					c4 = U.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80 || (c3 & 0xC0) != 0x80 || (c4 & 0xC0) != 0x80
						// 11110xxx
						// 11111110xxxxxx
						// 11111111111110xxxxxx
						// 11111111111111111110xxxxxx
						//     1110000001111110000000 (bound = 2^22-1 | 2097151)
						|| (c4 = c << 18 ^ c2 << 12 ^ c3 << 6 ^ c4 ^ 0b1110000001111110000000) > Character.MAX_CODE_POINT) break malformed;

					if (c4 < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
						out[off++] = (char) c4;
						break;
					}

					if (outMax-off < 2) break overflow;

					out[off++] = Character.highSurrogate(c4);
					out[off++] = Character.lowSurrogate(c4);
					break;
				default: break malformed;
			}
		}

		return ((previ-base) << 32) | off;}

		if (this == EXCEPTIONAL) throw new IllegalArgumentException((int) (previ-base) + " 附近解码错误");
		out[off++] = REPLACEMENT;
		return ((1+previ-base) << 32) | off;
	}
	@Override public void fastValidate(Object ref, long i, long max, IntConsumer verifier) {
		int c, c2, c3, c4;
		while (i < max) {
			c = U.getByte(ref, i++) & 0xFF;
			switch (c >> 4) {
				case 0, 1, 2, 3, 4, 5, 6, 7 -> verifier.accept(c);
				case 12, 13 -> {
					if (i >= max) { verifier.accept(TRUNCATED); return; }
					c2 = U.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80) { i -= 1; verifier.accept(MALFORMED - 2); continue; }

					verifier.accept((char) (c << 6 ^ c2 ^ 0b1110011110000000));
				}
				case 14 -> {
					if (i + 1 >= max) { verifier.accept(TRUNCATED); return; }
					c2 = U.getByte(ref, i++);
					c3 = U.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80 || (c3 & 0xC0) != 0x80) { i -= 2; verifier.accept(MALFORMED - 3); continue; }

					verifier.accept((char) (c << 12 ^ c2 << 6 ^ c3 ^ 0b0001111110000000));
				}
				default -> verifier.accept(MALFORMED - 1);
				case 15 -> {
					if (i + 2 >= max) { verifier.accept(TRUNCATED); return; }
					c2 = U.getByte(ref, i++);
					c3 = U.getByte(ref, i++);
					c4 = U.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80 || (c3 & 0xC0) != 0x80 || (c4 & 0xC0) != 0x80
						|| (c4=((c << 18 ^ c2 << 12 ^ c3 << 6 ^ c4 ^ 0b1110000001111110000000) & 2097151)) > Character.MAX_CODE_POINT) { i -= 3; verifier.accept(MALFORMED - 4); continue; }

					verifier.accept(c4);
				}
			}
		}
	}
	@Override public int byteCount(CharSequence s, int i, int len) {
		int end = i+len;
		while (i < end) {
			int c = s.charAt(i++);
			if (isSurrogate(c)) {
				if (i == end) throw new IllegalStateException("Trailing high surrogate \\U+"+Integer.toHexString(c));
				c = codepoint(c,s.charAt(i++));
				len--;
			}

			if (c <= 0x7FF) {
				if (c > 0x7F) {
					len++;
				}
			} else {
				if (c > 0xFFFF) {
					len += 3;
				} else {
					len += 2;
				}
			}
		}
		return len;
	}
	@Override public int addBOM(Object ref, long addr) {U.put24UB(ref, addr, 0xEFBBBF);return 3;}
	@Override public int encodeSize(int codepoint) {return codepoint <= 0x7FF ? codepoint > 0x7F ? 2 : 1 : codepoint > 0xFFFF ? 4 : 3;}
}