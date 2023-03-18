/*
 * Binary Tree match finder with 2-, 3-, and 4-byte hashing
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.qz.xz.lz;

import roj.util.ArrayCache;

import static roj.reflect.FieldAccessor.u;

final class BT4 extends LZEncoder {
	private final long tree;
	private final int depthLimit;

	static int getMemoryUsage(int dictSize) { return Hash234.getMemoryUsage(dictSize) + dictSize / (1024 / 8) + 10; }

	BT4(int dictSize, int beforeSizeMin, int readAheadMax, int niceLen, int matchLenMax, int depthLimit, ArrayCache arrayCache) {
		super(dictSize, beforeSizeMin, readAheadMax, niceLen, matchLenMax, arrayCache);
		tree = mem.allocate(cyclicSize<<3);
		this.depthLimit = depthLimit > 0 ? depthLimit : 16 + niceLen / 2;
	}

	private int movePos() {
		int avail = movePos(niceLen, 4);

		if (avail != 0) {
			if (++lzPos == Integer.MAX_VALUE) {
				int normalizationOffset = Integer.MAX_VALUE - cyclicSize;
				hash.normalize(normalizationOffset);
				normalize(tree, cyclicSize*2, normalizationOffset);
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
		if (delta2 < cyclicSize && u.getByte(buf + readPos - delta2) == u.getByte(buf + readPos)) {
			lenBest = 2;
			mlen[0] = 2;
			mdist[0] = delta2 - 1;
			mcount = 1;
		}

		// See if the hash from the first three bytes found a match that
		// is different from the match possibly found by the two-byte hash.
		// Also here the hashing algorithm guarantees that if the first byte
		// matches, also the next two bytes do.
		if (delta2 != delta3 && delta3 < cyclicSize && u.getByte(buf + readPos - delta3) == u.getByte(buf + readPos)) {
			lenBest = 3;
			mdist[mcount++] = delta3 - 1;
			delta2 = delta3;
		}

		// If a match was found, see how long it is.
		if (mcount > 0) {
			while (lenBest < matchLenLimit && u.getByte(buf + readPos + lenBest - delta2) == u.getByte(buf + readPos + lenBest)) ++lenBest;

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

		long ptr0 = tree + (cyclicPos << 3) + 4;
		long ptr1 = tree + (cyclicPos << 3);
		int len0 = 0;
		int len1 = 0;

		while (true) {
			int delta = lzPos - currentMatch;

			// Return if the search depth limit has been reached or
			// if the distance of the potential match exceeds the
			// dictionary size.
			if (depth-- == 0 || delta >= cyclicSize) {
				u.putInt(ptr0, 0);
				u.putInt(ptr1, 0);
				return;
			}

			long pair = tree + ((cyclicPos - delta + (delta > cyclicPos ? cyclicSize : 0)) << 3);
			int len = Math.min(len0, len1);

			long pos = buf + readPos + len;
			long prevPos = pos - delta;

			if (u.getByte(prevPos) == u.getByte(pos)) {
				while (++len < matchLenLimit) {
					if (u.getByte(++prevPos) != u.getByte(++pos)) break;
				}

				if (len > lenBest) {
					lenBest = len;
					mlen[mcount] = len;
					mdist[mcount] = delta - 1;
					++mcount;

					if (len >= niceLenLimit) {
						u.putInt(ptr1, u.getInt(pair));
						u.putInt(ptr0, u.getInt(pair + 4));
						return;
					}
				}
			}

			if ((u.getByte(prevPos) & 0xFF) < (u.getByte(pos) & 0xFF)) {
				u.putInt(ptr1, currentMatch);
				ptr1 = pair + 4;
				currentMatch = u.getInt(ptr1);
				len1 = len;
			} else {
				u.putInt(ptr0, currentMatch);
				ptr0 = pair;
				currentMatch = u.getInt(ptr0);
				len0 = len;
			}
		}
	}

	private void skip(int niceLenLimit, int currentMatch) {
		int depth = depthLimit;

		long ptr1 = tree + (cyclicPos << 3);
		long ptr0 = ptr1 + 4;

		int len0 = 0;
		int len1 = 0;

		while (true) {
			int delta = lzPos - currentMatch;

			if (depth-- == 0 || delta >= cyclicSize) {
				u.putInt(ptr0, 0);
				u.putInt(ptr1, 0);
				return;
			}

			long pair = tree + ((cyclicPos - delta + (delta > cyclicPos ? cyclicSize : 0)) << 3);
			int len = Math.min(len0, len1);

			if (u.getByte(buf + readPos + len - delta) == u.getByte(buf + readPos + len)) {
				// No need to look for longer matches than niceLenLimit
				// because we only are updating the tree, not returning
				// matches found to the caller.
				do {
					if (++len == niceLenLimit) {
						u.putInt(ptr1, u.getInt(pair));
						u.putInt(ptr0, u.getInt(pair + 4));
						return;
					}
				} while (u.getByte(buf + readPos + len - delta) == u.getByte(buf + readPos + len));
			}

			if ((u.getByte(buf + readPos + len - delta) & 0xFF) < (u.getByte(buf + readPos + len) & 0xFF)) {
				u.putInt(ptr1, currentMatch);
				ptr1 = pair + 4;
				currentMatch = u.getInt(ptr1);
				len1 = len;
			} else {
				u.putInt(ptr0, currentMatch);
				ptr0 = pair;
				currentMatch = u.getInt(ptr0);
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
