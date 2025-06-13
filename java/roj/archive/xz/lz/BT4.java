/*
 * Binary Tree match finder with 2-, 3-, and 4-byte hashing
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.xz.lz;

import static roj.reflect.Unaligned.U;

final class BT4 extends LZEncoder {
	BT4(int dictSize, int beforeSizeMin, int readAheadMax, int niceLen, int matchLenMax, int depthLimit) {
		super(dictSize, beforeSizeMin, readAheadMax, niceLen, matchLenMax, 3, depthLimit > 0 ? depthLimit : 16 + niceLen / 2);
	}

	private int movePos() {
		int avail = movePos(niceLen, 4);

		if (avail != 0) {
			if (++lzPos == Integer.MAX_VALUE) {
				int normalizationOffset = Integer.MAX_VALUE - cyclicSize;
				hash.normalize(normalizationOffset);
				normalize(base, cyclicSize*2, normalizationOffset);
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
			if (lenBest >= niceLenLimit) {
				skip(niceLenLimit, currentMatch);
				return;
			}
		}

		// Long enough match wasn't found so easily. Look for better matches
		// from the binary tree.
		if (lenBest < 3) lenBest = 3;

		int depth = depthLimit;

		long ptr0 = base + ((long) cyclicPos << 3) + 4;
		long ptr1 = base + ((long) cyclicPos << 3);
		int len0 = 0;
		int len1 = 0;

		while (true) {
			int delta = lzPos - currentMatch;

			// Return if the search depth limit has been reached or
			// if the distance of the potential match exceeds the
			// dictionary size.
			if (depth-- == 0 || delta >= cyclicSize) {
				U.putInt(ptr0, 0);
				U.putInt(ptr1, 0);
				return;
			}

			long pair = base + (((long) cyclicPos - delta + (delta > cyclicPos ? cyclicSize : 0)) << 3);
			int len = Math.min(len0, len1);

			long pos = buf + readPos + len;
			long prevPos = pos - delta;

			if (U.getByte(prevPos) == U.getByte(pos)) {
				while (++len < matchLenLimit) {
					if (U.getByte(++prevPos) != U.getByte(++pos)) break;
				}

				if (len > lenBest) {
					lenBest = len;
					mlen[mcount] = len;
					mdist[mcount] = delta - 1;
					++mcount;

					if (len >= niceLenLimit) {
						U.putInt(ptr1, U.getInt(pair));
						U.putInt(ptr0, U.getInt(pair + 4));
						return;
					}
				}
			}

			if ((U.getByte(prevPos) & 0xFF) < (U.getByte(pos) & 0xFF)) {
				U.putInt(ptr1, currentMatch);
				ptr1 = pair + 4;
				currentMatch = U.getInt(ptr1);
				len1 = len;
			} else {
				U.putInt(ptr0, currentMatch);
				ptr0 = pair;
				currentMatch = U.getInt(ptr0);
				len0 = len;
			}
		}
	}

	private void skip(int niceLenLimit, int currentMatch) {
		int depth = depthLimit;

		long ptr1 = base + ((long) cyclicPos << 3);
		long ptr0 = ptr1 + 4;

		int len0 = 0;
		int len1 = 0;

		while (true) {
			int delta = lzPos - currentMatch;

			if (depth-- == 0 || delta >= cyclicSize) {
				U.putInt(ptr0, 0);
				U.putInt(ptr1, 0);
				return;
			}

			long pair = base + (((long) cyclicPos - delta + (delta > cyclicPos ? cyclicSize : 0)) << 3);
			int len = Math.min(len0, len1);

			long myOff = buf + readPos + len;
			if (U.getByte(myOff - delta) == U.getByte(myOff)) {
				// No need to look for longer matches than niceLenLimit
				// because we only are updating the tree, not returning
				// matches found to the caller.
				do {
					myOff++;
					if (++len == niceLenLimit) {
						U.putInt(ptr1, U.getInt(pair));
						U.putInt(ptr0, U.getInt(pair + 4));
						return;
					}
				} while (U.getByte(myOff - delta) == U.getByte(myOff));
			}

			if ((U.getByte(myOff - delta) & 0xFF) < (U.getByte(myOff) & 0xFF)) {
				U.putInt(ptr1, currentMatch);
				ptr1 = pair + 4;
				currentMatch = U.getInt(ptr1);
				len1 = len;
			} else {
				U.putInt(ptr0, currentMatch);
				ptr0 = pair;
				currentMatch = U.getInt(ptr0);
				len0 = len;
			}
		}
	}

	public void skip(int len) {
		while (len-- > 0) {
			int niceLenLimit = niceLen;
			int avail = movePos();

			if (avail < niceLenLimit) {
				if (avail == 0) continue;

				niceLenLimit = avail;
			}

			hash.calcHashes(buf, readPos);
			int currentMatch = hash.getHash4Pos();
			hash.updateTables(lzPos);

			skip(niceLenLimit, currentMatch);
		}
	}
}