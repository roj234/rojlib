/*
 * 2-, 3-, and 4-byte hashing
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.qz.xz.lz;

import roj.util.NativeMemory;

import static roj.crypt.CRC32s.crcTab;
import static roj.reflect.ReflectionUtils.u;

final class Hash234 {
	private static final int HASH_2_SIZE = 1 << 10;
	private static final int HASH_2_MASK = HASH_2_SIZE - 1;

	private static final int HASH_3_SIZE = 1 << 16;
	private static final int HASH_3_MASK = HASH_3_SIZE - 1;

	private final int hash4Size, hash4Mask;

	private final NativeMemory hashTable;
	private final long hash2Table, hash3Table, hash4Table;
	private int hash2Value, hash3Value, hash4Value;

	private static int getHash4Size(int dictSize) {
		int h = dictSize - 1;
		h |= h >>> 1;
		h |= h >>> 2;
		h |= h >>> 4;
		h |= h >>> 8;
		h >>>= 1;
		h |= 0xFFFF;
		if (h > (1 << 24)) h >>>= 1;

		return h + 1;
	}

	// unit is bytes
	static long getMemoryUsage(int dictSize) { return (((long) HASH_2_SIZE + HASH_3_SIZE + getHash4Size(dictSize)) << 2) + 68; }
	Hash234(int dictSize) {
		hash4Size = getHash4Size(dictSize);
		hash4Mask = hash4Size-1;

		hashTable = new NativeMemory(true);
		hash2Table = hashTable.allocate(((long) HASH_2_SIZE + HASH_3_SIZE + hash4Size) << 2);
		hash3Table = hash2Table + (HASH_2_SIZE<<2);
		hash4Table = hash3Table + (HASH_3_SIZE<<2);
	}

	void reset() { u.setMemory(hash2Table, hashTable.length(), (byte) 0); }
	void release() { hashTable.release(); }

	void calcHashes(long buf, int off) {
		buf += off;

		int hash = crcTab[u.getByte(buf) & 0xFF] ^ (u.getByte(buf+1) & 0xFF);
		hash2Value = hash & HASH_2_MASK;

		hash ^= (u.getByte(buf+2) & 0xFF) << 8;
		hash3Value = hash & HASH_3_MASK;

		hash ^= crcTab[u.getByte(buf+3) & 0xFF] << 5;
		hash4Value = hash & hash4Mask;
	}

	int getHash2Pos() { return u.getInt(hash2Table+((long) hash2Value<<2)); }
	int getHash3Pos() { return u.getInt(hash3Table+((long) hash3Value<<2)); }
	int getHash4Pos() { return u.getInt(hash4Table+((long) hash4Value<<2)); }

	void updateTables(int pos) {
		u.putInt(hash2Table+((long) hash2Value<<2), pos);
		u.putInt(hash3Table+((long) hash3Value<<2), pos);
		u.putInt(hash4Table+((long) hash4Value<<2), pos);
	}

	void normalize(int normalizationOffset) {
		LZEncoder.normalize(hash2Table, HASH_2_SIZE, normalizationOffset);
		LZEncoder.normalize(hash3Table, HASH_3_SIZE, normalizationOffset);
		LZEncoder.normalize(hash4Table, hash4Size, normalizationOffset);
	}
}