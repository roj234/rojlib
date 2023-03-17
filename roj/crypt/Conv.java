package roj.crypt;

/**
 * @author solo6975
 * @since 2021/10/3 11:12
 */
public final class Conv {
	public static void i2b(byte[] dst, int dOff, int n) {
		dst[dOff++] = (byte) (n >> 24);
		dst[dOff++] = (byte) (n >> 16);
		dst[dOff++] = (byte) (n >> 8);
		dst[dOff  ] = (byte) n;
	}

	public static int IRL(int n, int bit) {
		return (n << bit) | (n >>> (32 - bit));
	}

	public static int[] reverse(int[] arr, int i, int length) {
		if (--length <= 0) return arr;

		for (int e = Math.max((length + 1) >> 1, 1); i < e; i++) {
			int a = arr[i];
			arr[i] = arr[length - i];
			arr[length - i] = a;
		}
		return arr;
	}
}
