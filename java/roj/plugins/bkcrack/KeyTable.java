package roj.plugins.bkcrack;

import roj.collect.IntList;

import static roj.plugins.bkcrack.Macros.MASK_0_16;

/**
 * @author Roj234
 * @since 2022/11/12 17:27
 */
final class KeyTable {
	/// \return the keystream byte ki associated to a Zi value
	/// \note Only Zi[2,16) is used
	static byte getByte(int zi) {
		return keys[(zi & MASK_0_16) >>> 2];
	}

	/// \return a vector of Zi[2,16) values having given [10,16) bits
	/// such that getByte(zi) is equal to ki
	/// \note the vector contains one element on average
	static IntList getZi_2_16_vector(int ki, int zi_10_16) {
		return keysInvFilter[((ki&0xFF) << 6) | ((zi_10_16 & MASK_0_16) >>> 10)];
	}

	static boolean hasZi_2_16(int ki, int zi_10_16) {
		return (keysInvExist[ki&0xFF] & (1L << ((zi_10_16 & MASK_0_16) >>> 10))) != 0;
	}

	static final byte[] keys = new byte[1<<14];
	static final IntList[] keysInvFilter = new IntList[256 * 64];
	static final long[] keysInvExist = new long[256];
	static {
		for (int i = 0; i < keysInvFilter.length; i++) {
			keysInvFilter[i] = new IntList(1);
		}

		for(int z_2_16 = 0; z_2_16 < 1 << 16; z_2_16 += 4) {
			int k = ((z_2_16 | 2) * (z_2_16 | 3) >>> 8) & 0xFF;
			keys[z_2_16 >>> 2] = (byte) k;

			keysInvFilter[(k << 6) | (z_2_16 >>> 10)].add(z_2_16);
			keysInvExist[k] |= (1L << (z_2_16 >>> 10));
		}
	}
}