package roj.crypt;

import roj.reflect.Unaligned;
import sun.misc.Unsafe;

import static java.lang.Integer.rotateLeft;

/**
 * @author Roj234
 * @since 2024/5/16 0016 1:44
 */
public class XXHash32 {
	private static final int BLOCK_SIZE = 16;
	private static final int P1 = 0x9e3779b1, P2 = 0x85ebca7, P3 = 0xc2b2ae3d, P4 = 0x27d4eb2f, P5 = 0x165667b1;

	public static int xxHash32(int seed, byte[] buf, int off, int len) {
		int a,b,c,d;
		// INIT STATE
		a = seed + P1 + P2;
		b = seed + P2;
		c = seed;
		d = seed - P1;
		// INIT STATE
		int end = off + len;
		// BLOCK
		while (end-off >= BLOCK_SIZE) {
			a = rotateLeft(a + getIntLE(buf, off) * P2, 13) * P1;
			b = rotateLeft(b + getIntLE(buf, off + 4) * P2, 13) * P1;
			c = rotateLeft(c + getIntLE(buf, off + 8) * P2, 13) * P1;
			d = rotateLeft(d + getIntLE(buf, off + 12) * P2, 13) * P1;

			off += BLOCK_SIZE;
		}
		// BLOCK

		int hash = len > BLOCK_SIZE
			? rotateLeft(a, 1) + rotateLeft(b, 7) + rotateLeft(c, 12) + rotateLeft(d, 18)
			: c + P5;

		hash += len;

		while (end-off >= 4) {
			hash = rotateLeft(hash + getIntLE(buf, off) * P3, 17) * P4;

			off += 4;
		}

		while (end-off > 0) {
			hash = rotateLeft(hash + (buf[off] & 255) * P5, 11) * P1;

			off++;
		}

		hash ^= hash >>> 15;
		hash *= P2;
		hash ^= hash >>> 13;
		hash *= P3;
		hash ^= hash >>> 16;

		return hash;
	}

	private static int getIntLE(byte[] b, int i) {return Unaligned.U.get32UL(b, Unsafe.ARRAY_BYTE_BASE_OFFSET+i);}
}