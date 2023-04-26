package roj.crypt;

import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
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

	public static <T extends Appendable> T encode(DynByteBuf in, T out) { return encode(in, out, B64_CHAR); }
	public static <T extends Appendable> T encode(DynByteBuf in, T out, byte[] tab) {
		try {
			int c = in.readableBytes() / 3;
			while (c-- > 0) {
				int bits = in.readMedium();

				out.append((char) tab[bits >> 18 & 0x3f]).append((char) tab[bits >> 12 & 0x3f])
				   .append((char) tab[bits >> 6 & 0x3f]).append((char) tab[bits & 0x3f]);
			}

			int r = in.readableBytes();
			if (r != 0) {
				int r1 = in.readUnsignedByte();
				out.append((char) tab[r1 >> 2]);
				if (r == 1) {
					out.append((char) tab[(r1 << 4) & 0x3f]);
				} else {
					int r2 = in.readUnsignedByte();
					out.append((char) tab[(r1 << 4) & 0x3f | (r2 >> 4)]).append((char) tab[(r2 << 2) & 0x3f]);
				}

				if (tab[64] != 0) {
					out.append((char) tab[64]);
					if (r == 1) out.append((char) tab[64]);
				}
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return out;
	}

	public static DynByteBuf decode(CharSequence s, DynByteBuf out) { return decode(s, 0, s.length(), out, B64_CHAR_REV); }
	public static DynByteBuf decode(CharSequence s, int i, int len, DynByteBuf out, byte[] tab) {
		do {
			int bits = tab[s.charAt(i++)] << 18 | tab[s.charAt(i++)] << 12;
			int h3 = i >= len ? 64 : tab[s.charAt(i++)];
			int h4 = i >= len ? 64 : tab[s.charAt(i++)];
			bits |= h3 << 6 | h4;

			if (h3 == 64) out.put((byte) (bits>>16));
			else if (h4 == 64) out.putShort(bits>>8);
			else out.putMedium(bits);
		} while (i < len);
		return out;
	}
}
