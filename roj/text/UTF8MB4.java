package roj.text;

import java.util.function.IntConsumer;

import static java.lang.Character.MAX_HIGH_SURROGATE;
import static java.lang.Character.MIN_HIGH_SURROGATE;
import static roj.reflect.FieldAccessor.u;

/**
 * @author Roj234
 */
public final class UTF8MB4 extends UnsafeCharset {
	public static final UnsafeCharset CODER = new UTF8MB4(), THROW_ON_FAIL = new UTF8MB4();

	@Override
	public String name() { return "UTF-8"; }

	@Override
	public long unsafeEncode(char[] s, int i, int len, Object ref, long addr, int max_len) {
		long max = addr+max_len;

		while (i < len && addr < max) {
			int c = s[i];
			if (c > 0x7F) break;
			i++;
			u.putByte(ref, addr++, (byte) c);
		}

		int previ;
		while (i < len && addr < max) {
			previ = i;

			int c = s[i++];
			if (c >= MIN_HIGH_SURROGATE && c <= MAX_HIGH_SURROGATE) {
				if (i == len) {
					if (i == s.length) throw new IllegalArgumentException("缺失surrogate pair");

					i--;
					break;
				}
				c = TextUtil.codepoint(c,s[i++]);
			}

			if (c <= 0x7FF) {
				if (c > 0x7F) {
					if (max-addr < 2) { i = previ; break; }
					u.putByte(ref, addr++, (byte) (0xC0 | ((c >> 6) & 0x1F)));
					u.putByte(ref, addr++, (byte) (0x80 | (c & 0x3F)));
				} else {
					u.putByte(ref, addr++, (byte) c);
				}
			} else {
				if (c > 0xFFFF) {
					if (max-addr < 4) { i = previ; break; }
					u.putByte(ref, addr++, (byte) (0xF0 | ((c >> 18) & 0x07)));
					u.putByte(ref, addr++, (byte) (0x80 | ((c >> 12) & 0x3F)));
				} else {
					if (max-addr < 3) { i = previ; break; }
					u.putByte(ref, addr++, (byte) (0xE0 | ((c >> 12) & 0x0F)));
				}
				u.putByte(ref, addr++, (byte) (0x80 | ((c >> 6) & 0x3F)));
				u.putByte(ref, addr++, (byte) (0x80 | (c & 0x3F)));
			}
		}

		return ((long) i << 32) | (max-addr);
	}

	@Override
	public long unsafeDecode(Object ref, long base, int pos, int end, char[] out, int off, int outMax) {
		if (pos < 0) throw new IllegalArgumentException("pos="+pos);

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
		int c2, c3, c4;
		truncate:
		while (i < max && off < outMax) {
			c = u.getByte(ref, i++);
			switch ((c>>>4)&0xF) {
				case 0: case 1: case 2: case 3:
				case 4: case 5: case 6: case 7:
					/* 0xxxxxxx*/
					out[off++] = (char) c;
					break;
				case 12: case 13:
					if (i >= max) { i--; break truncate; }

					c2 = u.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80) { i -= 1; break malformed; }

					//   0110xxxx ^
					// 1111111110xxxxxx
					// 1110011110000000
					out[off++] = (char) (c << 6 ^ c2 ^ 0b1110011110000000);
					break;
				case 14:
					if (i+1 >= max) { i--; break truncate; }

					c2 = u.getByte(ref, i++);
					c3 = u.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80 || (c3 & 0xC0) != 0x80) { i -= 2; break malformed; }

					// 1110xxxx ^
					//     1110xxxxxx ^
					//     1111111110xxxxxx ^
					//     0001111110000000
					out[off++] = (char) (c << 12 ^ c2 << 6 ^ c3 ^ 0b0001111110000000);
					break;
				case 15:
					if (i+2 >= max) { i--; break truncate; }

					c2 = u.getByte(ref, i++);
					c3 = u.getByte(ref, i++);
					c4 = u.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80 || (c3 & 0xC0) != 0x80 || (c4 & 0xC0) != 0x80) { i -= 3; break malformed; }

					// 11110xxx
					// 11111110xxxxxx
					// 11111111111110xxxxxx
					// 11111111111111111110xxxxxx
					//     1110000001111110000000 (bound = 2^22-1 | 2097151)
					c4 = (c << 18 ^ c2 << 12 ^ c3 << 6 ^ c4 ^ 0b1110000001111110000000) & 2097151;
					if (c4 > Character.MAX_CODE_POINT) { i -= 3; break malformed; }

					if (c4 < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
						out[off++] = (char) c4;
						break;
					}

					if (outMax-off < 2) { i -= 4; break truncate; }

					out[off++] = Character.highSurrogate(c4);
					out[off++] = Character.lowSurrogate(c4);
					break;
				default: break malformed;
			}
		}

		return ((i-base) << 32) | off;}

		if (this == THROW_ON_FAIL) throw new IllegalArgumentException((int) (i-base) + " 附近解码错误");
		out[off++] = INVALID;
		return ((i-base) << 32) | off;
	}

	@Override
	public void unsafeValidate(Object ref, long i, long max, IntConsumer cs) {
		int c, c2, c3, c4;
		loop:
		while (i < max) {
			c = u.getByte(ref, i++) & 0xFF;
			switch (c >> 4) {
				case 0: case 1: case 2: case 3:
				case 4: case 5: case 6: case 7:
					cs.accept(c);
					break;
				case 12: case 13:
					if (i >= max) { cs.accept(TRUNCATED); break loop; }

					c2 = u.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80) { i -= 1; cs.accept(MALFORMED - 2); continue; }

					cs.accept((char) (c << 6 ^ c2 ^ 0b1110011110000000));
					break;
				case 14:
					if (i+1 >= max) { cs.accept(TRUNCATED); break loop; }

					c2 = u.getByte(ref, i++);
					c3 = u.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80 || (c3 & 0xC0) != 0x80) { i -= 2; cs.accept(MALFORMED - 3); continue; }

					cs.accept((char) (c << 12 ^ c2 << 6 ^ c3 ^ 0b0001111110000000));
					break;
				default: cs.accept(MALFORMED - 1); break;
				case 15:
					if (i+2 >= max) { cs.accept(TRUNCATED); break loop; }

					c2 = u.getByte(ref, i++);
					c3 = u.getByte(ref, i++);
					c4 = u.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80 || (c3 & 0xC0) != 0x80 || (c4 & 0xC0) != 0x80) { i -= 3; cs.accept(MALFORMED - 4); continue; }

					c4 = (c << 18 ^ c2 << 12 ^ c3 << 6 ^ c4 ^ 0b1110000001111110000000) & 2097151;
					cs.accept(c4);
					break;
			}
		}
	}

	@Override
	public int byteCount(CharSequence s, int i, int len) {
		int end = i+len;
		while (i < end) {
			int c = s.charAt(i++);
			if (c >= MIN_HIGH_SURROGATE && c <= MAX_HIGH_SURROGATE) {
				if (i == end) throw new IllegalStateException("Trailing high surrogate \\u" + Integer.toHexString(c));
				c = TextUtil.codepoint(c,s.charAt(i++));
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
}
