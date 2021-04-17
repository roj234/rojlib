package roj.text;

import java.util.function.IntConsumer;

import static java.lang.Character.MAX_HIGH_SURROGATE;
import static java.lang.Character.MIN_HIGH_SURROGATE;
import static roj.reflect.FieldAccessor.u;

/**
 * @author Roj234
 */
public final class UTF8MB4 extends UnsafeCharset {
	public static final UnsafeCharset CODER = new UTF8MB4();

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
					if (i >= max) {
						i--;
						break truncate;
					}

					c2 = u.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80) { i -= 2; break malformed; }

					/*
					110xxxx
					     10xxxxxx */
					out[off++] = (char) (c << 6 ^ c2 ^ 3968);
					break;
				case 14:
					if (i+1 >= max) {
						i--;
						break truncate;
					}

					c2 = u.getByte(ref, i++);
					c3 = u.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80 || (c3 & 0xC0) != 0x80) { i -= 3; break malformed; }

					/*
					1110xxxx
					      10xxxxxx
					            10xxxxxx */
					out[off++] = (char) (c << 12 ^ c2 << 6 ^ c3 ^ -123008);
					break;
				case 15:
					if (i+2 >= max) {
						i--;
						break truncate;
					}

					c2 = u.getByte(ref, i++);
					c3 = u.getByte(ref, i++);
					c4 = u.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80 || (c3 & 0xC0) != 0x80 || (c4 & 0xC0) != 0x80) {
						i -= 4;
						break malformed;
					}

					/* 11110xxx
					         10xxxxxx
					               10xxxxxx
					                     10xxxxxx */
					c4 = c << 18 ^ c2 << 12 ^ c3 << 6 ^ c4 ^ 3678080;
					assert c4 >= Character.MIN_SUPPLEMENTARY_CODE_POINT;

					if (outMax-off < 2) { i -= 4; break; }

					out[off++] = Character.highSurrogate(c4);
					out[off++] = Character.lowSurrogate(c4);
					break;
				default: i--; break malformed;
			}
		}

		return ((i-base) << 32) | off;}
		throw new IllegalArgumentException((int) (i-base) + " 附近解码错误");
	}

	@Override
	public void unsafeValidate(Object ref, long i, int len, IntConsumer cs) {
		long max = i+len;

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
					if (i >= max) {
						cs.accept(TRUNCATED);
						break loop;
					}

					c2 = u.getByte(ref, i++);

					if ((c2 & 0xC0) != 0x80) {
						cs.accept(MALFORMED - 2);
						i--;
						continue;
					}

					cs.accept(((c & 0x1F) << 6) | (c2 & 0x3F));
					break;
				case 14:
					if (i+1 >= max) {
						cs.accept(TRUNCATED);
						break loop;
					}

					c2 = u.getByte(ref, i++);
					c3 = u.getByte(ref, i++);
					if (((c2^c3) & 0xC0) != 0) {
						cs.accept(MALFORMED - 3);
						i-=2;
						continue;
					}

					cs.accept(((c & 0x0F) << 12) | ((c2 & 0x3F) << 6) | c3 & 0x3F);
					break;
				default: cs.accept(MALFORMED - 1); break;
				case 15:
					if (i+2 >= max) {
						cs.accept(TRUNCATED);
						break loop;
					}

					c2 = u.getByte(ref, i++);
					c3 = u.getByte(ref, i++);
					c4 = u.getByte(ref, i++);
					if (((c2^c3^c4) & 0xC0) != 0x80) {
						cs.accept(MALFORMED - 4);
						i-=3;
						continue;
					}

					c4 = ((c & 7) << 18) | ((c2 & 0x3F) << 12) | ((c3 & 0x3F) << 6) | c4 & 0x3F;
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
