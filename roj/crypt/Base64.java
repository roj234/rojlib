package roj.crypt;

import roj.text.CharList;
import roj.util.DynByteBuf;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2021/2/14 19:38
 */
public final class Base64 {
	public static final byte[] B64_CHAR = {
		'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
		'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
		'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
		'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/',
		'='};

	public static final byte[] B64_URL_SAFE = {
		'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
		'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
		'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
		'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_',
		0};

	public static final byte[] B64_CHAR_REV = reverseOf(B64_CHAR, new byte[128]);
	public static final byte[] B64_URL_SAFE_REV = reverseOf(B64_URL_SAFE, new byte[128]);

	public static byte[] reverseOf(byte[] in, byte[] out) {
		Arrays.fill(out, (byte) -1);
		for (int i = 0; i < in.length; i++)
			out[in[i]] = (byte) i;
		return out;
	}

	public static CharList encode(DynByteBuf in, CharList out) {
		return encode(in, out, B64_CHAR);
	}

	public static CharList encode(DynByteBuf in, CharList out, byte[] chars) {
		int e = in.wIndex();
		int r = e % 3; // 0, 1 or 2
		e -= r;

		int i = 0;
		while (i < e) {
			int bits = in.getU(i++) << 16 | in.getU(i++) << 8 | in.getU(i++);

			out.append((char) chars[bits >> 18 & 0x3f]).append((char) chars[bits >> 12 & 0x3f])
			   .append((char) chars[bits >> 6 & 0x3f]).append((char) chars[bits & 0x3f]);
		}

		if (r != 0) {
			int r1 = in.getU(i++);
			out.append((char) chars[r1 >> 2]);
			if (r == 1) {
				out.append((char) chars[(r1 << 4) & 0x3f]);
			} else {
				int r2 = in.getU(i);
				out.append((char) chars[(r1 << 4) & 0x3f | (r2 >> 4)]).append((char) chars[(r2 << 2) & 0x3f]);
			}

			if (chars[64] > 0) {
				for (r = 3 - r, i = 0; i < r; i++)
					out.append((char) chars[64]);
			}
		}

		return out;
	}

	public static DynByteBuf decode(CharSequence s, DynByteBuf out) {
		return decode(s, 0, s.length(), out, B64_CHAR_REV);
	}

	public static DynByteBuf decode(CharSequence s, int i, int len, DynByteBuf out, byte[] bytesRev) {
		do {
			int bits = bytesRev[s.charAt(i++)] << 18 | bytesRev[s.charAt(i++)] << 12;
			int h3 = i >= len ? 64 : bytesRev[s.charAt(i++)];
			int h4 = i >= len ? 64 : bytesRev[s.charAt(i++)];
			bits |= h3 << 6 | h4;

			int o1 = bits >> 16 & 0xff;
			int o2 = bits >> 8 & 0xff;

			if (h3 == 64) {
				out.put((byte) o1);
			} else if (h4 == 64) {
				out.put((byte) o1)
				   .put((byte) o2);
			} else {
				out.put((byte) o1)
				   .put((byte) o2)
				   .put((byte) bits);
			}
		} while (i < len);
		return out;
	}
}
