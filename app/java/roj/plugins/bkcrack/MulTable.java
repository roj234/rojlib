package roj.plugins.bkcrack;

import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2022/11/12 17:38
 */
final class MulTable {
	/// \return MULT^-1 * x using a lookup table
	static int getMultinv(int x) {return mulProductInv[x & 0xFF];}

	/// \return a vector of bytes x such that
	/// msb(x*MULT^-1) is equal to msbprod or msbprod-1
	static byte[] getMsbProdFiber2(int msbprodinv) {return msbProduct2[msbprodinv & 0xFF];}

	/// \return a vector of bytes x such that
	/// msb(x*MULT^-1) is equal to msbprod, msbprod-1 or msbprod+1
	static byte[] getMsbProdFiber3(int msbprodinv) {return msbProduct3[msbprodinv & 0xFF];}

	static final int MULT = 0x08088405, MULTINV = 0xd94fa8cd;

	// lookup tables
	static final int[]  mulProductInv = new int[256];
	static final byte[][] msbProduct2 = new byte[256][];
	static final byte[][] msbProduct3 = new byte[256][];

	static {
		ByteList[] tmp2 = new ByteList[256];
		ByteList[] tmp3 = new ByteList[256];
		for (int i = 0; i < 256; i++) {
			tmp2[i] = new ByteList();
			tmp3[i] = new ByteList();
		}

		int prodinv = 0; // x * MULT^-1
		for(int i = 0; i < 256; i++, prodinv += MULTINV) {
			mulProductInv[i] = prodinv;

			tmp2[  prodinv >>> 24               ].put((byte) i);
			tmp2[((prodinv >>> 24) +   1) & 0xFF].put((byte) i);

			tmp3[((prodinv >>> 24) + 255) & 0xFF].put((byte) i);
			tmp3[  prodinv >>> 24               ].put((byte) i);
			tmp3[((prodinv >>> 24) +   1) & 0xFF].put((byte) i);
		}

		for (int i = 0; i < 256; i++) {
			msbProduct2[i] = tmp2[i].toByteArray();
			msbProduct3[i] = tmp3[i].toByteArray();
		}
	}
}