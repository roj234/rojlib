package roj.archive.zip.crack;

import roj.crypt.CRCAny;

/**
 * @author Roj234
 * @since 2022/11/12 0012 17:25
 */
final class CrcTable implements Macros {
	static final int[] table = CRCAny.CRC_32.getTable();
	static final int[] inverse = new int[256];

	static {
		for (int i = 0; i < 256; i++) {
			int crc = table[i];
			inverse[crc >>> 24] = crc << 8 ^ i;
		}
	}

	static int crc32(int crc, int b) {
		return (crc >>> 8) ^ table[(crc ^ b) & 0xff];
	}

	/// return CRC32^-1 using a lookup table
	static int crc32inv(int crc, int b) {
		return crc << 8 ^ inverse[crc >>> 24] ^ (b & 0xFF);
	}

	/// return Yi[24,32) from Zi and Z{i-1} using CRC32^-1
	static int getYi_24_32(int zi, int zim1) {
		return (crc32inv(zi, 0) ^ zim1) << 24;
	}

	/// return Z{i-1}[10,32) from Zi[2,32) using CRC32^-1
	static int getZim1_10_32(int zi_2_32) {
		return crc32inv(zi_2_32, 0) & MASK_10_32; // discard 10 least significant bits
	}
}
