/*
 * Hash Chain match finder with 2-, 3-, and 4-byte hashing
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.xz.lz;

import static roj.reflect.Unsafe.U;

final class HashChain extends LZEncoder {
	HashChain(int dictSize, int beforeSizeMin, int readAheadMax, int niceLen, int matchLenMax, int depthLimit, boolean hash5) {
		// Use a default depth limit if no other value was specified.
		// The default is just something based on experimentation;
		// it's nothing magic.
		super(dictSize, beforeSizeMin, readAheadMax, niceLen, matchLenMax, 2, depthLimit>0 ? depthLimit : 4 + niceLen / 4, hash5);
	}

	/**
	 * Moves to the next byte, checks that there is enough available space,
	 * and possibly normalizes the hash tables and the hash chain.
	 *
	 * @return number of bytes available, including the current byte
	 */
	int advance() {
		int size = hash.size();
		int avail = movePos(size, size);

		if (avail != 0) {
			if (++lzPos == Integer.MAX_VALUE) {
				int normalizationOffset = Integer.MAX_VALUE - cyclicSize;
				hash.normalize(normalizationOffset);
				normalize(base, cyclicSize, normalizationOffset);
				lzPos -= normalizationOffset;
			}

			if (++cyclicPos == cyclicSize) cyclicPos = 0;
		}

		return avail;
	}

	@Override
	void match(int currentMatch, int matchLenLimit, int lenBest, int niceLenLimit) {
		U.putInt(base+((long) cyclicPos<<2), currentMatch);

		int depth = depthLimit;

		while (true) {
			int delta = lzPos - currentMatch;

			// Return if the search depth limit has been reached or
			// if the distance of the potential match exceeds the
			// dictionary size.
			if (depth-- == 0 || delta >= cyclicSize) return;

			currentMatch = U.getInt(base + ((long) (cyclicPos - delta + (delta > cyclicPos ? cyclicSize : 0)) <<2));

			// Test the first byte and the first new byte that would give us
			// a match that is at least one byte longer than lenBest. This
			// too short matches get quickly skipped.
			long offset = buf+readPos;
			if (U.getByte(offset + lenBest - delta) == U.getByte(offset + lenBest) &&
				U.getByte(offset - delta) == U.getByte(offset)) {
				// Calculate the length of the match.
				int len = 0;
				while (++len < matchLenLimit) if (U.getByte(offset + len - delta) != U.getByte(offset + len)) break;

				// Use the match if and only if it is better than the longest
				// match found so far.
				if (len > lenBest) {
					lenBest = len;
					mlen[mcount] = len;
					mdist[mcount] = delta - 1;
					++mcount;

					// Return if it is long enough (niceLen or reached the
					// end of the dictionary).
					if (len >= niceLenLimit) return;
				}
			}
		}
	}

	@Override
	void skip(int niceLenLimit, int currentMatch) {
		U.putInt(base+((long) cyclicPos<<2), currentMatch);
	}

	public void skip(int len) {
		assert len >= 0;

		while (len-- > 0) {
			if (advance() != 0) {
				// Update the hash chain and hash tables.
				hash.calcHashes(buf, readPos);
				U.putInt(base+((long) cyclicPos<<2), hash.getHash4Pos());
				hash.updateTables(lzPos);
			}
		}
	}
}