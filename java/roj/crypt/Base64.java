package roj.crypt;

import roj.text.CharList;
import roj.util.ArrayCache;
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
		var ob = ArrayCache.getCharArray(512, false);
		int i = 0;
		try {
			int c = in.readableBytes() / 3;
			while (c-- > 0) {
				int bits = in.readMedium();

				ob[i++] = (char) tab[bits >> 18 & 0x3f];
				ob[i++] = (char) tab[bits >> 12 & 0x3f];
				ob[i++] = (char) tab[bits >>  6 & 0x3f];
				ob[i++] = (char) tab[bits       & 0x3f];

				if (i == (512-4)) {
					out.append(new CharList.Slice(ob, 0, i));
					i = 0;
				}
			}

			int r = in.readableBytes();
			if (r != 0) {
				int b1 = in.readUnsignedByte();
				ob[i++] = (char) tab[b1 >> 2];

				if (r == 1) {
					ob[i++] = (char) tab[(b1 << 4) & 0x3f];
				} else {
					int r2 = in.readUnsignedByte();
					ob[i++] = (char) tab[(b1 << 4) & 0x3f | (r2 >> 4)];
					ob[i++] = (char) tab[(r2 << 2) & 0x3f];
				}

				if (tab[64] != 0) {
					ob[i++] = (char) tab[64];
					if (r == 1) ob[i++] = (char) tab[64];
				}
			}

			if (i != 0) out.append(new CharList.Slice(ob, 0, i));
		} catch (IOException e) {
			Helpers.athrow(e);
		} finally {
			ArrayCache.putArray(ob);
		}
		return out;
	}

	public static DynByteBuf decode(CharSequence s, DynByteBuf out) { return decode(s, 0, s.length(), out, B64_CHAR_REV); }
	public static DynByteBuf decode(CharSequence s, DynByteBuf out, byte[] tab) { return decode(s, 0, s.length(), out, tab); }
	public static DynByteBuf decode(CharSequence s, int off, int count, DynByteBuf out, byte[] tab) {
		while (count > 0) {
			if (tab[s.charAt(off + count - 1)] != 64) break;
			count--;
		}
		if (count <= 0) return out;

		int block = count >> 2;
		count -= block << 2;
		if (count == 1) throw new IllegalArgumentException("Invalid size");

		out.ensureWritable(block * 3);
		for (int j = 0; j < block; j++) {
			int bits = tab[s.charAt(off++)] << 18 | tab[s.charAt(off++)] << 12 | tab[s.charAt(off++)] << 6 | tab[s.charAt(off++)];
			if (bits < 0) throw new IllegalArgumentException("Invalid char");
			out.putMedium(bits);
		}

		if (count != 0) {
			int bits = tab[s.charAt(off++)] << 12 | tab[s.charAt(off++)] << 6;
			if (count == 2) out.put(bits>>10);
			else out.putShort((bits|tab[s.charAt(off)])>>2); // count == 3
		}

		return out;
	}
}