/*
 * LZMAEncoderNormal
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.qz.xz.lzma;

import roj.archive.qz.xz.LZMA2Options;
import roj.archive.qz.xz.lz.LZEncoder;
import roj.archive.qz.xz.rangecoder.RangeEncoder;
import roj.util.NativeMemory;
import sun.misc.Unsafe;

import static roj.reflect.ReflectionUtils.u;

final class LZMAEncoderNormal extends LZMAEncoder {
	private static final int OPTS = 4096;

	private static final int STATE_OFF = 0;
	private static final int REPS_OFF = STATE_OFF+1;
	private static final int PRICE_OFF = REPS_OFF + REPS * 4;
	private static final int OPTPREV_OFF = PRICE_OFF + 4;
	private static final int BACKPREV_OFF = OPTPREV_OFF + 4;
	private static final int PREV1ISLITERAL_OFF = BACKPREV_OFF + 4;
	private static final int HASPREV2_OFF = PREV1ISLITERAL_OFF + 1;
	private static final int OPTPREV2_OFF = HASPREV2_OFF + 1;
	private static final int BACKPREV2_OFF = OPTPREV2_OFF + 4;
	private static final int OPT_STRUCT_SIZE = BACKPREV2_OFF+4;

	private final NativeMemory mem;
	private final long opts;

	private int optCur = 0, optEnd = 0;

	private final int[] repLens = new int[REPS];
	private int nextState;

	static int getMemoryUsage_(LZMA2Options options, int extraSizeBefore) { return LZEncoder.getMemoryUsage(options, Math.max(extraSizeBefore, OPTS), OPTS, MATCH_LEN_MAX) + OPTS * OPT_STRUCT_SIZE / 1024; }
	private static LZEncoder lz(LZMA2Options options, int extraSizeBefore) { return LZEncoder.getInstance(options, Math.max(extraSizeBefore, OPTS), OPTS, MATCH_LEN_MAX); }
	LZMAEncoderNormal(RangeEncoder rc, LZMA2Options options, int extraSizeBefore) {
		super(rc, lz(options, extraSizeBefore), options);
		mem = new NativeMemory();
		opts = mem.allocate(OPTS*OPT_STRUCT_SIZE);
	}

	public void reset() {
		optCur = 0;
		optEnd = 0;
		super.reset();
	}

	// region UNSAFE OPS
	public void release() {
		super.release();
		mem.release();
	}

	private boolean oPrev1IsLiteral(int i) { return u.getByte(opts+ (long) i*OPT_STRUCT_SIZE+PREV1ISLITERAL_OFF) != 0; }
	private boolean oHasPrev2(int i) { return u.getByte(opts+ (long) i*OPT_STRUCT_SIZE+HASPREV2_OFF) != 0; }
	private int oGetOptPrev2(int i) { return u.getInt(opts+ (long) i*OPT_STRUCT_SIZE+OPTPREV2_OFF); }
	private int oGetBackPrev2(int i) { return u.getInt(opts+ (long) i*OPT_STRUCT_SIZE+BACKPREV2_OFF); }

	private int oGetOptPrev(int i) { return u.getInt(opts+ (long) i*OPT_STRUCT_SIZE+OPTPREV_OFF); }
	private void oSetOptPrev(int i, int v) { u.putInt(opts+ (long) i*OPT_STRUCT_SIZE+OPTPREV_OFF, v); }
	private int oGetBackPrev(int i) { return u.getInt(opts+ (long) i*OPT_STRUCT_SIZE+BACKPREV_OFF); }
	private void oSetBackPrev(int i, int v) { u.putInt(opts+ (long) i*OPT_STRUCT_SIZE+BACKPREV_OFF, v); }
	private int oGetPrice(int i) { return u.getInt(opts+ (long) i*OPT_STRUCT_SIZE+PRICE_OFF); }
	private static final int INFINITY_PRICE = 1 << 30;
	/**
	 * Resets the price.
	 */
	private void oReset(int pos) { u.putInt(opts+ (long) pos*OPT_STRUCT_SIZE+PRICE_OFF, INFINITY_PRICE); }
	private int oGetRep(int pos, int i) { return u.getInt(opts+ (long) pos*OPT_STRUCT_SIZE+REPS_OFF+((long) i<<2)); }
	private void oSetRep(int pos, int i, int v) { u.putInt(opts+ (long) pos*OPT_STRUCT_SIZE+REPS_OFF+((long) i<<2), v); }
	private int oGetState(int pos) { return u.getByte(opts+ (long) pos*OPT_STRUCT_SIZE+STATE_OFF); }
	private void oSetState(int pos, int v) { u.putByte(opts+ (long) pos*OPT_STRUCT_SIZE+STATE_OFF, (byte) v); }

	/**
	 * Sets to indicate one LZMA symbol (literal, rep, or match).
	 */
	private void ouSet1(int pos, int newPrice, int optCur, int back) {
		long addr = opts+ (long) pos*OPT_STRUCT_SIZE;
		u.putInt(addr+PRICE_OFF, newPrice);
		u.putInt(addr+OPTPREV_OFF, optCur);
		u.putInt(addr+BACKPREV_OFF, back);
		u.putByte(addr+PREV1ISLITERAL_OFF, (byte) 0);
	}
	/**
	 * Sets to indicate two LZMA symbols of which the first one is a literal.
	 */
	private void ouSet2(int pos, int newPrice, int optCur, int back) {
		long addr = opts+ (long) pos*OPT_STRUCT_SIZE;
		u.putInt(addr+PRICE_OFF, newPrice);
		u.putInt(addr+OPTPREV_OFF, optCur+1);
		u.putInt(addr+BACKPREV_OFF, back);
		u.putByte(addr+PREV1ISLITERAL_OFF, (byte) 1);
		u.putByte(addr+HASPREV2_OFF, (byte) 0);
	}
	/**
	 * Sets to indicate three LZMA symbols of which the second one
	 * is a literal.
	 */
	private void ouSet3(int pos, int newPrice, int optCur, int back2, int len2, int back) {
		long addr = opts+ (long) pos*OPT_STRUCT_SIZE;
		u.putInt(addr+PRICE_OFF, newPrice);
		u.putInt(addr+OPTPREV_OFF, optCur+len2+1);
		u.putInt(addr+BACKPREV_OFF, back);
		u.putByte(addr+PREV1ISLITERAL_OFF, (byte) 1);
		u.putByte(addr+HASPREV2_OFF, (byte) 1);
		u.putInt(addr+OPTPREV2_OFF, optCur);
		u.putInt(addr+BACKPREV2_OFF, back2);
	}
	// endregion

	/**
	 * Converts the opts array from backward indexes to forward indexes.
	 * Then it will be simple to get the next symbol from the array
	 * in later calls to <code>getNextSymbol()</code>.
	 */
	private int convertOpts() {
		optEnd = optCur;

		int optPrev = oGetOptPrev(optCur);

		do {
			int opt = optCur;

			if (oPrev1IsLiteral(opt)) {
				oSetOptPrev(optPrev,opt);
				oSetBackPrev(optPrev,-1);
				optCur = optPrev--;

				if (oHasPrev2(opt)) {
					oSetOptPrev(optPrev,optPrev + 1);
					oSetBackPrev(optPrev, oGetBackPrev2(opt));
					optCur = optPrev;
					optPrev = oGetOptPrev2(opt);
				}
			}

			int temp = oGetOptPrev(optPrev);
			oSetOptPrev(optPrev,optCur);
			optCur = optPrev;
			optPrev = temp;
		} while (optCur > 0);

		optCur = oGetOptPrev(0);
		back = oGetBackPrev(optCur);
		return optCur;
	}

	int getNextSymbol() {
		// If there are pending symbols from an earlier call to this
		// function, return those symbols first.
		if (optCur < optEnd) {
			int len = oGetOptPrev(optCur) - optCur;
			optCur = oGetOptPrev(optCur);
			back = oGetBackPrev(optCur);
			return len;
		}

		assert optCur == optEnd;
		optCur = 0;
		optEnd = 0;
		back = -1;

		if (readAhead == -1) match();

		// Get the number of bytes available in the dictionary, but
		// not more than the maximum match length. If there aren't
		// enough bytes remaining to encode a match at all, return
		// immediately to encode this byte as a literal.
		int avail = Math.min(lz.getAvail(), MATCH_LEN_MAX);
		if (avail < MATCH_LEN_MIN) return 1;

		// Get the lengths of repeated lz.m
		int repBest = 0;
		for (int rep = 0; rep < REPS; ++rep) {
			repLens[rep] = lz.getMatchLen(reps[rep], avail);

			if (repLens[rep] < MATCH_LEN_MIN) {
				repLens[rep] = 0;
				continue;
			}

			if (repLens[rep] > repLens[repBest]) repBest = rep;
		}

		// Return if the best repeated match is at least niceLen bytes long.
		if (repLens[repBest] >= niceLen) {
			back = repBest;
			skip(repLens[repBest] - 1);
			return repLens[repBest];
		}

		// Initialize mainLen and mainDist to the longest match found
		// by the match finder.
		int mainLen = 0;
		int mainDist;
		if (lz.mcount > 0) {
			mainLen = lz.mlen[lz.mcount - 1];
			mainDist = lz.mdist[lz.mcount - 1];

			// Return if it is at least niceLen bytes long.
			if (mainLen >= niceLen) {
				back = mainDist + REPS;
				skip(mainLen - 1);
				return mainLen;
			}
		}

		int curByte = lz.getByte(0);
		int matchByte = lz.getByte(reps[0] + 1);

		// If the match finder found no matches and this byte cannot be
		// encoded as a repeated match (short or long), we must be return
		// to have the byte encoded as a literal.
		if (mainLen < MATCH_LEN_MIN && curByte != matchByte && repLens[repBest] < MATCH_LEN_MIN) return 1;


		int pos = lz.getPos();
		int posState = pos & posMask;

		// Calculate the price of encoding the current byte as a literal.
		{
			int prevByte = lz.getByte(1);
			int literalPrice = getLiteralPrice(curByte, matchByte, prevByte, pos, state);
			ouSet1(1, literalPrice, 0, -1);
		}

		int anyMatchPrice = getAnyMatchPrice(state, posState);
		int anyRepPrice = getAnyRepPrice(anyMatchPrice, state);

		// If it is possible to encode this byte as a short rep, see if
		// it is cheaper than encoding it as a literal.
		if (matchByte == curByte) {
			int shortRepPrice = getShortRepPrice(anyRepPrice, state, posState);
			if (shortRepPrice < oGetPrice(1)) ouSet1(1, shortRepPrice, 0, 0);
		}

		// Return if there is neither normal nor long repeated match. Use
		// a short match instead of a literal if is is possible and cheaper.
		optEnd = Math.max(mainLen, repLens[repBest]);
		if (optEnd < MATCH_LEN_MIN) {
			assert optEnd == 0 : optEnd;
			back = oGetBackPrev(1);
			return 1;
		}


		// Update the lookup tables for distances and lengths before using
		// those price calculation functions. (The price function above
		// don't need these tables.)
		updatePrices();

		// Initialize the state and reps of this position in opts[].
		// updateOptStateAndReps() will need these to get the new
		// state and reps for the next byte.
		oSetState(0, state);
		u.copyMemory(
			reps, Unsafe.ARRAY_INT_BASE_OFFSET,
			null, opts+REPS_OFF,
			REPS << 2);

		// Initialize the prices for latter opts that will be used below.
		for (int i = optEnd; i >= MATCH_LEN_MIN; --i)
			oReset(i);

		// Calculate the prices of repeated matches of all lengths.
		for (int rep = 0; rep < REPS; ++rep) {
			int repLen = repLens[rep];
			if (repLen < MATCH_LEN_MIN) continue;

			int longRepPrice = getLongRepPrice(anyRepPrice, rep, state, posState);
			do {
				int price = longRepPrice + getRepeatPrice(repLen, posState);
				if (price < oGetPrice(repLen)) ouSet1(repLen, price, 0, rep);
			} while (--repLen >= MATCH_LEN_MIN);
		}

		// Calculate the prices of normal matches that are longer than rep0.
		{
			int len = Math.max(repLens[0] + 1, MATCH_LEN_MIN);
			if (len <= mainLen) {
				int normalMatchPrice = getNormalMatchPrice(anyMatchPrice, state);

				// Set i to the index of the shortest match that is
				// at least len bytes long.
				int i = 0;
				while (len > lz.mlen[i]) ++i;

				while (true) {
					int dist = lz.mdist[i];
					int price = getMatchAndLenPrice(normalMatchPrice, dist, len, posState);
					if (price < oGetPrice(len)) ouSet1(len, price, 0, dist + REPS);

					if (len == lz.mlen[i]) if (++i == lz.mcount) break;

					++len;
				}
			}
		}


		avail = Math.min(lz.getAvail(), OPTS - 1);

		// Get matches for later bytes and optimize the use of LZMA symbols
		// by calculating the prices and picking the cheapest symbol
		// combinations.
		while (++optCur < optEnd) {
			match();
			if (lz.mcount > 0 && lz.mlen[lz.mcount - 1] >= niceLen) break;

			--avail;
			++pos;
			posState = pos & posMask;

			updateOptStateAndReps();
			anyMatchPrice = oGetPrice(optCur) + getAnyMatchPrice(oGetState(optCur), posState);
			anyRepPrice = getAnyRepPrice(anyMatchPrice, oGetState(optCur));

			calc1BytePrices(pos, posState, avail, anyRepPrice);

			if (avail >= MATCH_LEN_MIN) {
				int startLen = calcLongRepPrices(pos, posState, avail, anyRepPrice);
				if (lz.mcount > 0) calcNormalMatchPrices(pos, posState, avail, anyMatchPrice, startLen);
			}
		}

		return convertOpts();
	}

	/**
	 * Updates the state and reps for the current byte in the opts array.
	 */
	private void updateOptStateAndReps() {
		int optCur = this.optCur;
		int optPrev = oGetOptPrev(optCur);
		assert optPrev < optCur;

		if (oPrev1IsLiteral(optCur)) {
			--optPrev;

			if (oHasPrev2(optCur)) {
				int pos = oGetOptPrev2(optCur);
				oSetState(optCur, oGetState(pos));
				if (oGetBackPrev2(optCur) < REPS) oSetState(optCur, state_updateLongRep(oGetState(optCur)));
				else oSetState(optCur, state_updateMatch(oGetState(optCur)));
			} else {
				oSetState(optCur, oGetState(optPrev));
			}

			oSetState(optCur, state_updateLiteral(oGetState(optCur)));
		} else {
			oSetState(optCur, oGetState(optPrev));
		}

		if (optPrev == optCur - 1) {
			// Must be either a short rep or a literal.
			assert oGetBackPrev(optCur) == 0 || oGetBackPrev(optCur) == -1;

			if (oGetBackPrev(optCur) == 0) oSetState(optCur, state_updateShortRep(oGetState(optCur)));
			else oSetState(optCur, state_updateLiteral(oGetState(optCur)));

			u.copyMemory(
				opts+ (long) optPrev*OPT_STRUCT_SIZE+REPS_OFF,
				opts+ (long) optCur*OPT_STRUCT_SIZE+REPS_OFF,
				REPS << 2);
		} else {
			int back;
			if (oPrev1IsLiteral(optCur) && oHasPrev2(optCur)) {
				optPrev = oGetOptPrev2(optCur);
				back = oGetBackPrev2(optCur);
				oSetState(optCur, state_updateLongRep(oGetState(optCur)));
			} else {
				back = oGetBackPrev(optCur);
				if (back < REPS) oSetState(optCur, state_updateLongRep(oGetState(optCur)));
				else oSetState(optCur, state_updateMatch(oGetState(optCur)));
			}

			if (back < REPS) {
				oSetRep(optCur, 0, oGetRep(optPrev, back));

				int rep;
				for (rep = 1; rep <= back; ++rep)
					oSetRep(optCur, rep, oGetRep(optPrev, rep-1));

				for (; rep < REPS; ++rep)
					oSetRep(optCur, rep, oGetRep(optPrev, rep));
			} else {
				oSetRep(optCur, 0, back - REPS);
				u.copyMemory(
					opts+ (long) optPrev*OPT_STRUCT_SIZE+REPS_OFF,
					opts+ (long) optCur*OPT_STRUCT_SIZE+REPS_OFF+4,
					(REPS-1) << 2);
			}
		}
	}

	/**
	 * Calculates prices of a literal, a short rep, and literal + rep0.
	 */
	private void calc1BytePrices(int pos, int posState, int avail, int anyRepPrice) {
		// This will be set to true if using a literal or a short rep.
		boolean nextIsByte = false;

		int curByte = lz.getByte(0);
		int matchByte = lz.getByte(oGetRep(optCur, 0)+1);

		// Try a literal.
		int literalPrice = oGetPrice(optCur) + getLiteralPrice(curByte, matchByte, lz.getByte(1), pos, oGetState(optCur));
		if (literalPrice < oGetPrice(optCur+1)) {
			ouSet1(optCur+1, literalPrice, optCur, -1);
			nextIsByte = true;
		}

		// Try a short rep.
		if (matchByte == curByte && (oGetOptPrev(optCur+1) == optCur || oGetBackPrev(optCur+1) != 0)) {
			int shortRepPrice = getShortRepPrice(anyRepPrice, oGetState(optCur), posState);
			if (shortRepPrice <= oGetPrice(optCur+1)) {
				ouSet1(optCur+1,shortRepPrice, optCur, 0);
				nextIsByte = true;
			}
		}

		// If neither a literal nor a short rep was the cheapest choice,
		// try literal + long rep0.
		if (!nextIsByte && matchByte != curByte && avail > MATCH_LEN_MIN) {
			int lenLimit = Math.min(niceLen, avail - 1);
			int len = lz.getMatchLen(1, oGetRep(optCur, 0), lenLimit);

			if (len >= MATCH_LEN_MIN) {
				nextState = state_updateLiteral(oGetState(optCur));
				int nextPosState = (pos + 1) & posMask;
				int price = literalPrice + getLongRepAndLenPrice(0, len, nextState, nextPosState);

				int i = optCur + 1 + len;
				while (optEnd < i) oReset(++optEnd);

				if (price < oGetPrice(i)) ouSet2(i,price, optCur, 0);
			}
		}
	}

	/**
	 * Calculates prices of long rep and long rep + literal + rep0.
	 */
	private int calcLongRepPrices(int pos, int posState, int avail, int anyRepPrice) {
		int startLen = MATCH_LEN_MIN;
		int lenLimit = Math.min(avail, niceLen);

		for (int rep = 0; rep < REPS; ++rep) {
			int len = lz.getMatchLen(oGetRep(optCur, rep), lenLimit);
			if (len < MATCH_LEN_MIN) continue;

			while (optEnd < optCur + len) oReset(++optEnd);

			int longRepPrice = getLongRepPrice(anyRepPrice, rep, oGetState(optCur), posState);

			for (int i = len; i >= MATCH_LEN_MIN; --i) {
				int price = longRepPrice + getRepeatPrice(i, posState);
				if (price < oGetPrice(optCur + i)) ouSet1(optCur + i, price, optCur, rep);
			}

			if (rep == 0) startLen = len + 1;

			int len2Limit = Math.min(niceLen, avail - len - 1);
			int len2 = lz.getMatchLen(len + 1, oGetRep(optCur, rep), len2Limit);

			if (len2 >= MATCH_LEN_MIN) {
				// Rep
				int price = longRepPrice + getRepeatPrice(len, posState);
				nextState = state_updateLongRep(oGetState(optCur));

				// Literal
				int curByte = lz.getByte(len, 0);
				int matchByte = lz.getByte(0); // lz.getByte(len, len)
				int prevByte = lz.getByte(len, 1);
				price += getLiteralPrice(curByte, matchByte, prevByte, pos + len, nextState);
				nextState = state_updateLiteral(nextState);

				// Rep0
				int nextPosState = (pos + len + 1) & posMask;
				price += getLongRepAndLenPrice(0, len2, nextState, nextPosState);

				int i = optCur + len + 1 + len2;
				while (optEnd < i) oReset(++optEnd);

				if (price < oGetPrice(i)) ouSet3(i,price, optCur, rep, len, 0);
			}
		}

		return startLen;
	}

	/**
	 * Calculates prices of a normal match and normal match + literal + rep0.
	 */
	private void calcNormalMatchPrices(int pos, int posState, int avail, int anyMatchPrice, int startLen) {
		LZEncoder lz = this.lz;

		// If the longest match is so long that it would not fit into
		// the opts array, shorten the lz.m
		if (lz.mlen[lz.mcount - 1] > avail) {
			lz.mcount = 0;
			while (lz.mlen[lz.mcount] < avail) ++lz.mcount;

			lz.mlen[lz.mcount++] = avail;
		}

		if (lz.mlen[lz.mcount - 1] < startLen) return;

		while (optEnd < optCur + lz.mlen[lz.mcount - 1]) oReset(++optEnd);

		int normalMatchPrice = getNormalMatchPrice(anyMatchPrice, oGetState(optCur));

		int match = 0;
		while (startLen > lz.mlen[match]) ++match;

		for (int len = startLen; ; ++len) {
			int dist = lz.mdist[match];

			// Calculate the price of a match of len bytes from the nearest
			// possible distance.
			int matchAndLenPrice = getMatchAndLenPrice(normalMatchPrice, dist, len, posState);
			if (matchAndLenPrice < oGetPrice(optCur + len)) ouSet1(optCur + len, matchAndLenPrice, optCur, dist + REPS);

			if (len != lz.mlen[match]) continue;

			// Try match + literal + rep0. First get the length of the rep0.
			int len2Limit = Math.min(niceLen, avail - len - 1);
			int len2 = lz.getMatchLen(len + 1, dist, len2Limit);

			if (len2 >= MATCH_LEN_MIN) {
				nextState = state_updateMatch(oGetState(optCur));

				// Literal
				int curByte = lz.getByte(len, 0);
				int matchByte = lz.getByte(0); // lz.getByte(len, len)
				int prevByte = lz.getByte(len, 1);
				int price = matchAndLenPrice + getLiteralPrice(curByte, matchByte, prevByte, pos + len, nextState);
				nextState = state_updateLiteral(nextState);

				// Rep0
				int nextPosState = (pos + len + 1) & posMask;
				price += getLongRepAndLenPrice(0, len2, nextState, nextPosState);

				int i = optCur + len + 1 + len2;
				while (optEnd < i) oReset(++optEnd);

				if (price < oGetPrice(i)) ouSet3(i, price, optCur, dist + REPS, len, 0);
			}

			if (++match == lz.mcount) break;
		}
	}
}