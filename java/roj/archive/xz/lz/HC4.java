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

import static roj.reflect.Unaligned.U;

final class HC4 extends LZEncoder {
	HC4(int dictSize, int beforeSizeMin, int readAheadMax, int niceLen, int matchLenMax, int depthLimit) {
		// Use a default depth limit if no other value was specified.
		// The default is just something based on experimentation;
		// it's nothing magic.
		super(dictSize, beforeSizeMin, readAheadMax, niceLen, matchLenMax, 2, depthLimit>0 ? depthLimit : 4 + niceLen / 4);
	}

	/**
	 * Moves to the next byte, checks that there is enough available space,
	 * and possibly normalizes the hash tables and the hash chain.
	 *
	 * @return number of bytes available, including the current byte
	 */
	private int movePos() {
		int avail = movePos(4, 4);

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

	public void match() {
		mcount = 0;
		int matchLenLimit = matchLenMax;
		int niceLenLimit = niceLen;
		int avail = movePos();

		if (avail < matchLenLimit) {
			if (avail == 0) return;

			matchLenLimit = avail;
			if (niceLenLimit > avail) niceLenLimit = avail;
		}

		hash.calcHashes(buf, readPos);
		int delta2 = lzPos - hash.getHash2Pos();
		int delta3 = lzPos - hash.getHash3Pos();
		int currentMatch = hash.getHash4Pos();
		hash.updateTables(lzPos);

		U.putInt(base +((long) cyclicPos<<2), currentMatch);

		int lenBest = 0;

		// See if the hash from the first two bytes found a match.
		// The hashing algorithm guarantees that if the first byte
		// matches, also the second byte does, so there's no need to
		// test the second byte.
		if (delta2 < cyclicSize && U.getByte(buf + readPos - delta2) == U.getByte(buf + readPos)) {
			lenBest = 2;
			mlen[0] = 2;
			mdist[0] = delta2 - 1;
			mcount = 1;
		}

		// See if the hash from the first three bytes found a match that
		// is different from the match possibly found by the two-byte hash.
		// Also here the hashing algorithm guarantees that if the first byte
		// matches, also the next two bytes do.
		if (delta2 != delta3 && delta3 < cyclicSize && U.getByte(buf + readPos - delta3) == U.getByte(buf + readPos)) {
			lenBest = 3;
			mdist[mcount++] = delta3 - 1;
			delta2 = delta3;
		}

		// If a match was found, see how long it is.
		if (mcount > 0) {
			while (lenBest < matchLenLimit && U.getByte(buf + readPos + lenBest - delta2) == U.getByte(buf + readPos + lenBest)) ++lenBest;

			mlen[mcount - 1] = lenBest;

			// Return if it is long enough (niceLen or reached the end of
			// the dictionary).
			if (lenBest >= niceLenLimit) return;
		}

		// Long enough match wasn't found so easily. Look for better matches
		// from the hash chain.
		if (lenBest < 3) lenBest = 3;

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

	public void skip(int len) {
		assert len >= 0;

		while (len-- > 0) {
			if (movePos() != 0) {
				// Update the hash chain and hash tables.
				hash.calcHashes(buf, readPos);
				U.putInt(base +((long) cyclicPos<<2), hash.getHash4Pos());
				hash.updateTables(lzPos);
			}
		}
	}
}