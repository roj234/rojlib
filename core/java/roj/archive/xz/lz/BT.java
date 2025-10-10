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

import static roj.reflect.Unsafe.U;

final class BT extends LZEncoder {
	BT(int dictSize, int beforeSizeMin, int readAheadMax, int niceLen, int matchLenMax, int depthLimit, boolean hash5) {
		super(dictSize, beforeSizeMin, readAheadMax, niceLen, matchLenMax, 3, depthLimit > 0 ? depthLimit : 16 + niceLen / 2, hash5);
	}

	int advance() {
		int avail = movePos(niceLen, hash.size());

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

	void match(int currentMatch, int matchLenLimit, int lenBest, int niceLenLimit) {
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

	void skip(int niceLenLimit, int currentMatch) {
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
			int avail = advance();

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