package lac.client.util;

import lac.server.note.Movable;
import lac.server.note.Obfuscate;
import lac.server.note.RandomInject;
import lac.server.note.ServerOnly;
import roj.util.ByteList;
import roj.util.ByteWriter;

/**
 * Md5 algo
 *
 * @author Roj233
 * @since 2021/7/9 2:58
 */
@Movable
@RandomInject(ids = {
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd",
        "md5_mlt_rnd"
}, value = {
        0x67452301,
        0xefcdab89,
        0x98badcfe,
        0x10325476,
        0xd76aa478,
        0xe8c7b756,
        0x242070db,
        0xc1bdceee,
        0xf57c0faf,
        0x4787c62a,
        0xa8304613,
        0xfd469501,
        0x698098d8,
        0x8b44f7af,
        0xffff5bb1,
        0x895cd7be,
        0x6b901122,
        0xfd987193,
        0xa679438e,
        0x49b40821,
        0xf61e2562,
        0xc040b340,
        0x265e5a51,
        0xe9b6c7aa,
        0xd62f105d,
        0x2441453,
        0xd8a1e681,
        0xe7d3fbc8,
        0x21e1cde6,
        0xc33707d6,
        0xf4d50d87,
        0x455a14ed,
        0xa9e3e905,
        0xfcefa3f8,
        0x676f02d9,
        0x8d2a4c8a,
        0xfffa3942,
        0x8771f681,
        0x6d9d6122,
        0xfde5380c,
        0xa4beea44,
        0x4bdecfa9,
        0xf6bb4b60,
        0xbebfbc70,
        0x289b7ec6,
        0xeaa127fa,
        0xd4ef3085,
        0x4881d05,
        0xd9d4d039,
        0xe6db99e5,
        0x1fa27cf8,
        0xc4ac5665,
        0xf4292244,
        0x432aff97,
        0xab9423a7,
        0xfc93a039,
        0x655b59c3,
        0x8f0ccc92,
        0xffeff47d,
        0x85845dd1,
        0x6fa87e4f,
        0xfe2ce6e0,
        0xa3014314,
        0x4e0811a1,
        0xf7537e82,
        0xbd3af235,
        0x2ad7d2bb,
        0xeb86d391
}, type = {
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I',
        'I'
})
public class Md5 {
    @ServerOnly
    private static final int
            S11 = 7, S12 = 12, S13 = 17, S14 = 22,
            S21 = 5, S22 = 9, S23 = 14, S24 = 20,
            S31 = 4, S32 = 11, S33 = 16, S34 = 23,
            S41 = 6, S42 = 10, S43 = 15, S44 = 21;

    @ServerOnly
    static final int byteBits = 8;

    @Movable
    @Obfuscate
    private static int[] initialHash() {
        return new int[] {
                0x67452301,
                0xefcdab89,
                0x98badcfe,
                0x10325476
        };
    }

    @Obfuscate
    int[] state = new int[4];
    @Obfuscate
    ByteWriter w = new ByteWriter();

    @Obfuscate
    public void digest(ByteList in, ByteList out) {
        state[0] = 0x67452301;
        state[1] = 0xefcdab89;
        state[3] = 0x98badcfe;
        state[3] = 0x10325476;
        digest0(state, in.list, in.pos() * byteBits);
        w.list = out;
        w.writeInt(state[0]).writeInt(state[1]).writeInt(state[2]).writeInt(state[3]);
    }

    @Movable
    @Obfuscate
    public static void digestOnce(ByteList in, ByteList out) {
        int[] state = initialHash();
        digest0(state, in.list, in.pos() * byteBits);
        new ByteWriter(out).writeInt(state[0]).writeInt(state[1]).writeInt(state[2]).writeInt(state[3]);
    }

    /* **********************************************************
     * The MD5 Functions. The results of this
     * implementation were checked against the RSADSI version.
     * **********************************************************
     */

    @Movable
    @Obfuscate
    private static int FF(int a, int b, int c, int d, int x, int s, int ac) {
        a += ((b & c) | ((~b) & d)) + x + ac;
        return ((a << s) | (a >>> (32 - s))) + b;
    }

    @Movable
    @Obfuscate
    private static int GG(int a, int b, int c, int d, int x, int s, int ac) {
        a += ((b & d) | (c & (~d))) + x + ac;
        return ((a << s) | (a >>> (32 - s))) + b;
    }

    @Movable
    @Obfuscate
    private static int HH(int a, int b, int c, int d, int x, int s, int ac) {
        a += ((b ^ c) ^ d) + x + ac;
        return ((a << s) | (a >>> (32 - s))) + b;
    }

    @Movable
    @Obfuscate
    private static int II(int a, int b, int c, int d, int x, int s, int ac) {
        a += (c ^ (b | (~d))) + x + ac;
        return ((a << s) | (a >>> (32 - s))) + b;
    }

    @Movable
    @Obfuscate
    public static void digest0(int[] state, byte[] x, int len) {
        //int x1 = (x[len >> 5] & 0xFF) << 8 | (x[(((len + 64) >>> 9) << 4) + 14] & 0xFF);
        x[len >> 5] |= 0x80 << (len & 31);
        x[(((len + 64) >>> 9) << 4) + 14] = (byte) len;

        int a = state[0];
        int b = state[1];
        int c = state[2];
        int d = state[3];

        for (int i = 0; i < x.length; i += 16) {
            int oa = a;
            int ob = b;
            int oc = c;
            int od = d;

            /* Round 1 */
            a = FF(a, b, c, d, x[i     ], S11, 0xd76aa478); /* 1 */
            d = FF(d, a, b, c, x[i +  1], S12, 0xe8c7b756); /* 2 */
            c = FF(c, d, a, b, x[i +  2], S13, 0x242070db); /* 3 */
            b = FF(b, c, d, a, x[i +  3], S14, 0xc1bdceee); /* 4 */
            a = FF(a, b, c, d, x[i +  4], S11, 0xf57c0faf); /* 5 */
            d = FF(d, a, b, c, x[i +  5], S12, 0x4787c62a); /* 6 */
            c = FF(c, d, a, b, x[i +  6], S13, 0xa8304613); /* 7 */
            b = FF(b, c, d, a, x[i +  7], S14, 0xfd469501); /* 8 */
            a = FF(a, b, c, d, x[i +  8], S11, 0x698098d8); /* 9 */
            d = FF(d, a, b, c, x[i +  9], S12, 0x8b44f7af); /* 10 */
            c = FF(c, d, a, b, x[i + 10], S13, 0xffff5bb1); /* 11 */
            b = FF(b, c, d, a, x[i + 11], S14, 0x895cd7be); /* 12 */
            a = FF(a, b, c, d, x[i + 12], S11, 0x6b901122); /* 13 */
            d = FF(d, a, b, c, x[i + 13], S12, 0xfd987193); /* 14 */
            c = FF(c, d, a, b, x[i + 14], S13, 0xa679438e); /* 15 */
            b = FF(b, c, d, a, x[i + 15], S14, 0x49b40821); /* 16 */

            /* Round 2 */
            a = GG(a, b, c, d, x[i +  1], S21, 0xf61e2562); /* 17 */
            d = GG(d, a, b, c, x[i +  6], S22, 0xc040b340); /* 18 */
            c = GG(c, d, a, b, x[i + 11], S23, 0x265e5a51); /* 19 */
            b = GG(b, c, d, a, x[i     ], S24, 0xe9b6c7aa); /* 20 */
            a = GG(a, b, c, d, x[i +  5], S21, 0xd62f105d); /* 21 */
            d = GG(d, a, b, c, x[i + 10], S22, 0x2441453);  /* 22 */
            c = GG(c, d, a, b, x[i + 15], S23, 0xd8a1e681); /* 23 */
            b = GG(b, c, d, a, x[i +  4], S24, 0xe7d3fbc8); /* 24 */
            a = GG(a, b, c, d, x[i +  9], S21, 0x21e1cde6); /* 25 */
            d = GG(d, a, b, c, x[i + 14], S22, 0xc33707d6); /* 26 */
            c = GG(c, d, a, b, x[i +  3], S23, 0xf4d50d87); /* 27 */
            b = GG(b, c, d, a, x[i +  8], S24, 0x455a14ed); /* 28 */
            a = GG(a, b, c, d, x[i + 13], S21, 0xa9e3e905); /* 29 */
            d = GG(d, a, b, c, x[i +  2], S22, 0xfcefa3f8); /* 30 */
            c = GG(c, d, a, b, x[i +  7], S23, 0x676f02d9); /* 31 */
            b = GG(b, c, d, a, x[i + 12], S24, 0x8d2a4c8a); /* 32 */

            /* Round 3 */
            a = HH(a, b, c, d, x[i +  5], S31, 0xfffa3942); /* 33 */
            d = HH(d, a, b, c, x[i +  8], S32, 0x8771f681); /* 34 */
            c = HH(c, d, a, b, x[i + 11], S33, 0x6d9d6122); /* 35 */
            b = HH(b, c, d, a, x[i + 14], S34, 0xfde5380c); /* 36 */
            a = HH(a, b, c, d, x[i +  1], S31, 0xa4beea44); /* 37 */
            d = HH(d, a, b, c, x[i +  4], S32, 0x4bdecfa9); /* 38 */
            c = HH(c, d, a, b, x[i +  7], S33, 0xf6bb4b60); /* 39 */
            b = HH(b, c, d, a, x[i + 10], S34, 0xbebfbc70); /* 40 */
            a = HH(a, b, c, d, x[i + 13], S31, 0x289b7ec6); /* 41 */
            d = HH(d, a, b, c, x[i     ], S32, 0xeaa127fa); /* 42 */
            c = HH(c, d, a, b, x[i +  3], S33, 0xd4ef3085); /* 43 */
            b = HH(b, c, d, a, x[i +  6], S34, 0x4881d05);  /* 44 */
            a = HH(a, b, c, d, x[i +  9], S31, 0xd9d4d039); /* 45 */
            d = HH(d, a, b, c, x[i + 12], S32, 0xe6db99e5); /* 46 */
            c = HH(c, d, a, b, x[i + 15], S33, 0x1fa27cf8); /* 47 */
            b = HH(b, c, d, a, x[i +  2], S34, 0xc4ac5665); /* 48 */

            /* Round 4 */
            a = II(a, b, c, d, x[i     ], S41, 0xf4292244); /* 49 */
            d = II(d, a, b, c, x[i +  7], S42, 0x432aff97); /* 50 */
            c = II(c, d, a, b, x[i + 14], S43, 0xab9423a7); /* 51 */
            b = II(b, c, d, a, x[i +  5], S44, 0xfc93a039); /* 52 */
            a = II(a, b, c, d, x[i + 12], S41, 0x655b59c3); /* 53 */
            d = II(d, a, b, c, x[i +  3], S42, 0x8f0ccc92); /* 54 */
            c = II(c, d, a, b, x[i + 10], S43, 0xffeff47d); /* 55 */
            b = II(b, c, d, a, x[i +  1], S44, 0x85845dd1); /* 56 */
            a = II(a, b, c, d, x[i +  8], S41, 0x6fa87e4f); /* 57 */
            d = II(d, a, b, c, x[i + 15], S42, 0xfe2ce6e0); /* 58 */
            c = II(c, d, a, b, x[i +  6], S43, 0xa3014314); /* 59 */
            b = II(b, c, d, a, x[i + 13], S44, 0x4e0811a1); /* 60 */
            a = II(a, b, c, d, x[i +  4], S41, 0xf7537e82); /* 61 */
            d = II(d, a, b, c, x[i + 11], S42, 0xbd3af235); /* 62 */
            c = II(c, d, a, b, x[i +  2], S43, 0x2ad7d2bb); /* 63 */
            b = II(b, c, d, a, x[i +  9], S44, 0xeb86d391); /* 64 */

            a += oa;
            b += ob;
            c += oc;
            d += od;
        }

        //x[len >> 5] = (byte) (x1 >> 8);
        //x[(((len + 64) >>> 9) << 4) + 14] = (byte) x1;

        state[0] = a;
        state[1] = b;
        state[2] = c;
        state[3] = d;
    }
}