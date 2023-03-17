package roj.crypt;

/**
 * @author solo6975
 * @since 2021/10/3 11:12
 */
public final class Conv {
	public static int b2i(byte[] src, int sOff) {
		return (src[sOff] & 0xFF) << 24 | (src[sOff + 1] & 0xFF) << 16 | (src[sOff + 2] & 0xFF) << 8 | (src[sOff + 3] & 0xFF);
	}

	public static long b2i_l(byte[] src, int sOff) {
		return 0xFFFFFFFFL & ((src[sOff] & 0xFF) << 24 | (src[sOff + 1] & 0xFF) << 16 | (src[sOff + 2] & 0xFF) << 8 | (src[sOff + 3] & 0xFF));
	}

	public static void i2b(byte[] dst, int dOff, int n) {
		dst[dOff++] = (byte) (n >> 24);
		dst[dOff++] = (byte) (n >> 16);
		dst[dOff++] = (byte) (n >> 8);
		dst[dOff  ] = (byte) n;
	}

	public static int[] b2i(byte[] src, int sOff, int len, int[] dst, int dOff) {
		int more = len & 3;
		if (dst.length - dOff < len / 4 + (more > 0 ? 1 : 0)) throw new ArrayIndexOutOfBoundsException();

		len -= more;
		len += sOff;
		while (sOff < len) {
			dst[dOff++] = (src[sOff] & 0xFF) << 24 | (src[sOff + 1] & 0xFF) << 16 | (src[sOff + 2] & 0xFF) << 8 | (src[sOff + 3] & 0xFF);
			sOff += 4;
		}
		if (more != 0) {
			len += more;
			int n = 0, sh = 24;
			while (sOff < len) {
				n |= (src[sOff++] & 0xFF) << sh;
				sh -= 8;
			}
			dst[dOff] = n;
		}
		return dst;
	}

	public static int[] b2i_LE(byte[] src, int sOff, int len, int[] dst, int dOff) {
		int more = len & 3;
		if (dst.length - dOff < len / 4 + (more > 0 ? 1 : 0)) throw new ArrayIndexOutOfBoundsException();

		len -= more;
		len += sOff;
		while (sOff < len) {
			dst[dOff++] = (src[sOff] & 0xFF) | (src[sOff + 1] & 0xFF) << 8 | (src[sOff + 2] & 0xFF) << 16 | (src[sOff + 3] & 0xFF) << 24;
			sOff += 4;
		}
		if (more != 0) {
			len += more;
			int n = 0, sh = 0;
			while (sOff < len) {
				n |= (src[sOff++] & 0xFF) << sh;
				sh += 8;
			}
			dst[dOff] = n;
		}
		return dst;
	}

	public static byte[] i2b(int[] src, int sOff, int len, byte[] dst, int dOff) {
		if (dst.length < len << 2) throw new ArrayIndexOutOfBoundsException();
		for (len += sOff; sOff < len; sOff++) {
			int n = src[sOff];
			dst[dOff] = (byte) (n >> 24);
			dst[dOff + 1] = (byte) (n >> 16);
			dst[dOff + 2] = (byte) (n >> 8);
			dst[dOff + 3] = (byte) n;
			dOff += 4;
		}
		return dst;
	}

	// Int Rotate Left
	public static int IRL(int n, int bit) {
		return (n << bit) | (n >>> bit);
	}

	public static int IRL1(int n, int bit) {
		return (n << bit) | (n >>> (32 - bit));
	}

	public static int[] reverse(int[] arr, int i, int length) {
		if (--length <= 0) return arr; // empty or one
		// i = 0, arr.length = 4, e = 2
		// swap 0 and 3 swap 1 and 2
		for (int e = Math.max((length + 1) >> 1, 1); i < e; i++) {
			int a = arr[i];
			arr[i] = arr[length - i];
			arr[length - i] = a;
		}
		return arr;
	}
}
