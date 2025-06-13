/*
 * LZMAEncoderFast
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.xz.lzma;

import roj.archive.xz.LZMA2Options;
import roj.archive.xz.lz.LZEncoder;
import roj.archive.xz.rangecoder.RangeEncoder;

final class LZMAEncoderFast extends LZMAEncoder {
	private static final int EXTRA_SIZE_BEFORE = 1;
	private static final int EXTRA_SIZE_AFTER = MATCH_LEN_MAX - 1;

	static int getMemoryUsage_(LZMA2Options options, int extraSizeBefore) { return LZEncoder.getMemoryUsage(options, Math.max(extraSizeBefore, EXTRA_SIZE_BEFORE), EXTRA_SIZE_AFTER, MATCH_LEN_MAX); }
	private static LZEncoder lz(LZMA2Options options, int extraSizeBefore) { return LZEncoder.getInstance(options, Math.max(extraSizeBefore, EXTRA_SIZE_BEFORE), EXTRA_SIZE_AFTER, MATCH_LEN_MAX); }
	LZMAEncoderFast(RangeEncoder rc, LZMA2Options options, int extraSizeBefore) { super(rc, lz(options, extraSizeBefore), options); }

	private boolean changePair(int smallDist, int bigDist) {
		return smallDist < (bigDist >>> 7);
	}

	int getNextSymbol() {
		// Get the matches for the next byte unless readAhead indicates
		// that we already got the new matches during the previous call
		// to this function.
		if (readAhead == -1) match();

		back = -1;

		// Get the number of bytes available in the dictionary, but
		// not more than the maximum match length. If there aren't
		// enough bytes remaining to encode a match at all, return
		// immediately to encode this byte as a literal.
		int avail = Math.min(lz.getAvail(), MATCH_LEN_MAX);
		if (avail < MATCH_LEN_MIN) return 1;

		// Look for a match from the previous four match distances.
		int bestRepLen = 0;
		int bestRepIndex = 0;
		for (int rep = 0; rep < REPS; ++rep) {
			int len = lz.getMatchLen(reps[rep], avail);
			if (len < MATCH_LEN_MIN) continue;

			// If it is long enough, return it.
			if (len >= niceLen) {
				back = rep;
				skip(len - 1);
				return len;
			}

			// Remember the index and length of the best repeated match.
			if (len > bestRepLen) {
				bestRepIndex = rep;
				bestRepLen = len;
			}
		}

		int mainLen = 0;
		int mainDist = 0;

		if (lz.mcount > 0) {
			mainLen = lz.mlen[lz.mcount - 1];
			mainDist = lz.mdist[lz.mcount - 1];

			if (mainLen >= niceLen) {
				back = mainDist + REPS;
				skip(mainLen - 1);
				return mainLen;
			}

			while (lz.mcount > 1 && mainLen == lz.mlen[lz.mcount - 2] + 1) {
				if (!changePair(lz.mdist[lz.mcount - 2], mainDist)) break;

				--lz.mcount;
				mainLen = lz.mlen[lz.mcount - 1];
				mainDist = lz.mdist[lz.mcount - 1];
			}

			if (mainLen == MATCH_LEN_MIN && mainDist >= 0x80) mainLen = 1;
		}

		if (bestRepLen >= MATCH_LEN_MIN) {
			if (bestRepLen + 1 >= mainLen || (bestRepLen + 2 >= mainLen && mainDist >= (1 << 9)) || (bestRepLen + 3 >= mainLen && mainDist >= (1 << 15))) {
				back = bestRepIndex;
				skip(bestRepLen - 1);
				return bestRepLen;
			}
		}

		if (mainLen < MATCH_LEN_MIN || avail <= MATCH_LEN_MIN) return 1;

		// Get the next match. Test if it is better than the current match.
		// If so, encode the current byte as a literal.
		match();

		if (lz.mcount > 0) {
			int newLen = lz.mlen[lz.mcount - 1];
			int newDist = lz.mdist[lz.mcount - 1];

			if ((newLen >= mainLen && newDist < mainDist) || (newLen == mainLen + 1 && !changePair(mainDist, newDist)) || newLen > mainLen + 1 || (newLen + 1 >= mainLen && mainLen >= MATCH_LEN_MIN + 1 && changePair(newDist, mainDist))) return 1;
		}

		int limit = Math.max(mainLen - 1, MATCH_LEN_MIN);
		for (int rep = 0; rep < REPS; ++rep)
			if (lz.getMatchLen(reps[rep], limit) == limit) return 1;

		back = mainDist + REPS;
		skip(mainLen - 2);
		return mainLen;
	}
}
