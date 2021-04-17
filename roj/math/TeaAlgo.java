package roj.math;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/2/7 19:30
 */
public class TeaAlgo {
    public static long[] Encrypt(long[] data, long[] key) {
        int n = data.length;
        if (n < 1) {
            return data;
        }
        long z = data[data.length - 1], y, sum = 0, e, p, q;
        q = 6 + 52 / n;
        while (q-- > 0) {
            sum += DELTA;
            e = (sum >> 2) & 3;
            for (p = 0; p < n - 1; p++) {
                y = data[(int) (p + 1)];
                z = data[(int) p] += (z >> 5 ^ y << 2) + (y >> 3 ^ z << 4)
                        ^ (sum ^ y) + (key[(int) (p & 3 ^ e)] ^ z);
            }
            y = data[0];
            z = data[n - 1] += (z >> 5 ^ y << 2) + (y >> 3 ^ z << 4)
                    ^ (sum ^ y) + (key[(int) (p & 3 ^ e)] ^ z);
        }
        return data;
    }

    public static long[] Decrypt(long[] data, long[] key) {
        int n = data.length;
        if (n < 1) {
            return data;
        }
        long z, y = data[0], sum, e, p, q;
        q = 6 + 52 / n;
        sum = q * DELTA;
        while (sum != 0) {
            e = (sum >> 2) & 3;
            for (p = n - 1; p > 0; p--) {
                z = data[(int) (p - 1)];
                y = data[(int) p] -= (z >> 5 ^ y << 2) + (y >> 3 ^ z << 4)
                        ^ (sum ^ y) + (key[(int) (p & 3 ^ e)] ^ z);
            }
            z = data[n - 1];
            y = data[0] -= (z >> 5 ^ y << 2) + (y >> 3 ^ z << 4) ^ (sum ^ y)
                    + (key[(int) (p & 3 ^ e)] ^ z);
            sum -= DELTA;
        }
        return data;
    }

    public static long[] ToLongArray(byte[] data) {
        int bl = data.length;
        int n = ((bl & 7) == 0 ? 0 : 1) + (bl >> 3);
        long[] result = new long[n];
        if((bl & 7) != 0)
            n--;

        int j = 0;
        for (int i = 0; i < n; i++) {
            result[i] =
                    (long)data[j++] << 56 |
                    (long)data[j++] << 48 |
                    (long)data[j++] << 40 |
                    (long)data[j++] << 32 |
                    data[j++] << 24 |
                    data[j++] << 16 |
                    data[j++] << 8  |
                    data[j++];
        }

        if((bl & 7) != 0) {
            long l = 0;

            int sh = 0;
            while (j < bl) {
                l |= (long)data[j++] << sh;
                sh += 8;
            }

            result[n] = l;
        }
        return result;
    }

    public static byte[] ToByteArray(long[] data) {
        byte[] bytes = new byte[data.length << 3];

        int j = 0;
        for (long l : data) {
            bytes[j++] = (byte) (l >>> 56);
            bytes[j++] = (byte) (l >>> 48);
            bytes[j++] = (byte) (l >>> 40);
            bytes[j++] = (byte) (l >>> 32);
            bytes[j++] = (byte) (l >>> 24);
            bytes[j++] = (byte) (l >>> 16);
            bytes[j++] = (byte) (l >>> 8);
            bytes[j++] = (byte) (l);
        }

        return bytes;
    }

    private static final long DELTA = 2654435769L;
}
