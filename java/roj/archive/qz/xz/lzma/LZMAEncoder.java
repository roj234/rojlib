/*
 * LZMAEncoder
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
import roj.util.ArrayUtil;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import static roj.archive.qz.xz.rangecoder.RangeEncoder.*;

public abstract class LZMAEncoder extends LZMACoder {
	public static final int MODE_FAST = 1, MODE_NORMAL = 2;

	/**
	 * LZMA2 chunk is considered full when its uncompressed size exceeds
	 * <code>LZMA2_UNCOMPRESSED_LIMIT</code>.
	 * <p>
	 * A compressed LZMA2 chunk can hold 2 MiB of uncompressed data.
	 * A single LZMA symbol may indicate up to MATCH_LEN_MAX bytes
	 * of data, so the LZMA2 chunk is considered full when there is
	 * less space than MATCH_LEN_MAX bytes.
	 */
	private static final int LZMA2_UNCOMPRESSED_LIMIT = (2 << 20) - MATCH_LEN_MAX;

	/**
	 * LZMA2 chunk is considered full when its compressed size exceeds
	 * <code>LZMA2_COMPRESSED_LIMIT</code>.
	 * <p>
	 * The maximum compressed size of a LZMA2 chunk is 64 KiB.
	 * A single LZMA symbol might use 20 bytes of space even though
	 * it usually takes just one byte or so. Two more bytes are needed
	 * for LZMA2 uncompressed chunks (see LZMA2OutputStream.writeChunk).
	 * Leave a little safety margin and use 26 bytes.
	 */
	private static final int LZMA2_COMPRESSED_LIMIT = (64 << 10) - 26;

	private static final int DIST_PRICE_UPDATE_INTERVAL = FULL_DISTANCES;
	private static final int ALIGN_PRICE_UPDATE_INTERVAL = ALIGN_SIZE;

	private final RangeEncoder rc;
	final LZEncoder lz;
	final int niceLen;

	private int distPriceCount = 0;
	private int alignPriceCount = 0;

	private final int distSlotPricesSize;
	private final int[] distSlotPrices;
	private final int[] fullDistPrices = new int[DIST_STATES*FULL_DISTANCES];
	private final int[] alignPrices = new int[ALIGN_SIZE];

	int back = 0;
	int readAhead = -1;
	private int uncompressedSize = 0;

	/**
	 * The prices are updated after at least
	 * <code>PRICE_UPDATE_INTERVAL</code> many lengths
	 * have been encoded with the same posState.
	 */
	private static final int PRICE_UPDATE_INTERVAL = 32;

	private int[] counters;
	private int[][] prices;

	public static int getMemoryUsage(LZMA2Options options, int extraSizeBefore) {
		int m = 22;
		if (options.getMode() == MODE_FAST) m += LZMAEncoderFast.getMemoryUsage_(options, extraSizeBefore);
		else m += LZMAEncoderNormal.getMemoryUsage_(options, extraSizeBefore);
		return m;
	}
	public static LZMAEncoder getInstance(RangeEncoder rc, LZMA2Options options, int minKeepBefore) {
		minKeepBefore -= options.getDictSize();
		if (options.getMode() == MODE_FAST) return new LZMAEncoderFast(rc, options, minKeepBefore);
		else return new LZMAEncoderNormal(rc, options, minKeepBefore);
	}

	public void release() { lz.release(); }

	/**
	 * Gets an integer [0, 63] matching the highest two bits of an integer.
	 * This is like bit scan reverse (BSR) on x86 except that this also
	 * cares about the second highest bit.
	 */
	private static int getDistSlot(int dist) {
		if (dist <= DIST_MODEL_START && dist >= 0) return dist;

		int n = dist;
		int i = 31;

		if ((n & 0xFFFF0000) == 0) {
			n <<= 16;
			i = 15;
		}

		if ((n & 0xFF000000) == 0) {
			n <<= 8;
			i -= 8;
		}

		if ((n & 0xF0000000) == 0) {
			n <<= 4;
			i -= 4;
		}

		if ((n & 0xC0000000) == 0) {
			n <<= 2;
			i -= 2;
		}

		if ((n & 0x80000000) == 0) --i;

		return (i << 1) + ((dist >>> (i - 1)) & 1);
	}

	/**
	 * Gets the next LZMA symbol.
	 * <p>
	 * There are three types of symbols: literal (a single byte),
	 * repeated match, and normal match. The symbol is indicated
	 * by the return value and by the variable <code>back</code>.
	 * <p>
	 * Literal: <code>back == -1</code> and return value is <code>1</code>.
	 * The literal itself needs to be read from <code>lz</code> separately.
	 * <p>
	 * Repeated match: <code>back</code> is in the range [0, 3] and
	 * the return value is the length of the repeated match.
	 * <p>
	 * Normal match: <code>back - REPS<code> (<code>back - 4</code>)
	 * is the distance of the match and the return value is the length
	 * of the match.
	 */
	abstract int getNextSymbol();

	LZMAEncoder(RangeEncoder rc, LZEncoder lz, LZMA2Options options) {
		super(options.getLc(), options.getLp(), options.getPb());
		this.rc = rc;
		this.lz = lz;
		this.niceLen = options.getNiceLen();

		int ii = 2 << options.getPb();
		counters = new int[ii];

		// Always allocate at least LOW_SYMBOLS + MID_SYMBOLS because
		// it makes updatePrices slightly simpler. The prices aren't
		// usually needed anyway if niceLen < 18.
		int lenSymbols = Math.max(niceLen - MATCH_LEN_MIN + 1, LOW_SYMBOLS + MID_SYMBOLS);
		prices = new int[ii][lenSymbols];

		distSlotPricesSize = getDistSlot(options.getDictSize()-1) + 1;
		distSlotPrices = new int[DIST_STATES*distSlotPricesSize];

		reset();
	}

	public void propReset(int lc, int lp, int pb) {
		int ii = 2 << pb;
		counters = new int[ii];
		int lenSymbols = Math.max(niceLen - MATCH_LEN_MIN + 1, LOW_SYMBOLS + MID_SYMBOLS);
		prices = new int[ii][lenSymbols];

		super.propReset(lc, lp, pb);
	}

	public void reset() {
		super.reset();

		// Reset counters to zero to force price update before
		// the prices are needed.
		Arrays.fill(counters, 0);

		distPriceCount = 0;
		alignPriceCount = 0;

		uncompressedSize += readAhead + 1;
		readAhead = -1;
	}

	public final int getUncompressedSize() { return uncompressedSize; }
	public final void resetUncompressedSize() { uncompressedSize = 0; }

	/**
	 * Compress for LZMA1.
	 */
	public final void encodeForLZMA1() throws IOException {
		if (!lz.isStarted() && !encodeInit()) return;

		while (encodeSymbol());
	}

	public final void encodeLZMA1EndMarker() throws IOException {
		// End of stream marker is encoded as a match with the maximum
		// possible distance. The length is ignored by the decoder,
		// but the minimum length has been used by the LZMA SDK.
		//
		// Distance is a 32-bit unsigned integer in LZMA.
		// With Java's signed int, UINT32_MAX becomes -1.
		int posState = (lz.getPos() - readAhead) & posMask;
		rc.encodeBit(isMatch, posState + (state<<4), 1);
		rc.encodeBit(isRep, state, 0);
		encodeMatch(-1, MATCH_LEN_MIN, posState);
	}

	/**
	 * Compresses for LZMA2.
	 *
	 * @return true if the LZMA2 chunk became full, false otherwise
	 */
	public boolean encodeForLZMA2() {
		// LZMA2 uses RangeEncoderToBuffer so IOExceptions aren't possible.
		try {
			if (!lz.isStarted() && !encodeInit()) return false;

			while (uncompressedSize <= LZMA2_UNCOMPRESSED_LIMIT && rc.lzma2_getPendingSize() <= LZMA2_COMPRESSED_LIMIT) if (!encodeSymbol()) return false;
		} catch (IOException e) {
			assert false;
		}

		return true;
	}

	private boolean encodeInit() throws IOException {
		assert readAhead == -1;
		if (!lz.hasEnoughData(0)) return false;

		// The first symbol must be a literal unless using
		// a preset dictionary. This code isn't run if using
		// a preset dictionary.
		skip(1);
		rc.encodeBit(isMatch, (state << 4), 0);
		//LITERAL_INIT
		// When encoding the first byte of the stream, there is
		// no previous byte in the dictionary so the encode function
		// wouldn't work.
		assert readAhead >= 0;
		encodeLiteral(literalProbs[0]);
		//LITERAL_INIT

		--readAhead;
		assert readAhead == -1;

		++uncompressedSize;
		assert uncompressedSize == 1;

		return true;
	}

	private boolean encodeSymbol() throws IOException {
		if (!lz.hasEnoughData(readAhead + 1)) return false;

		int len = getNextSymbol();

		assert readAhead >= 0;
		int posState = (lz.getPos() - readAhead) & posMask;

		int state = this.state;
		if (back == -1) {
			// Literal i.e. eight-bit byte
			assert len == 1;
			rc.encodeBit(isMatch, posState + (state<<4), 0);
			//LITERAL_ENCODE
			assert readAhead >= 0;
			int i = getSubcoderIndex(lz.getByte(1 + readAhead), lz.getPos() - readAhead);
			encodeLiteral(literalProbs[i]);
			//LITERAL_ENCODE
		} else {
			// Some type of match
			rc.encodeBit(isMatch, posState + (state<<4), 1);
			if (back < REPS) {
				// Repeated match i.e. the same distance
				// has been used earlier.
				assert lz.getMatchLen(-readAhead, reps[back], len) == len;
				rc.encodeBit(isRep, state, 1);
				encodeRepMatch(back, len, posState);
			} else {
				// Normal match
				assert lz.getMatchLen(-readAhead, back - REPS, len) == len;
				rc.encodeBit(isRep, state, 0);
				encodeMatch(back - REPS, len, posState);
			}
		}

		readAhead -= len;
		uncompressedSize += len;

		return true;
	}

	private void encodeLiteral(short[] probs) throws IOException {
		int symbol = lz.getByte(readAhead) | 0x100;

		if (state_isLiteral(state)) {
			int subencoderIndex;
			int bit;

			do {
				subencoderIndex = symbol >>> 8;
				bit = (symbol >>> 7) & 1;
				rc.encodeBit(probs, subencoderIndex, bit);
				symbol <<= 1;
			} while (symbol < 0x10000);

		} else {
			int matchByte = lz.getByte(reps[0] + 1 + readAhead);
			int offset = 0x100;
			int subencoderIndex;
			int matchBit;
			int bit;

			do {
				matchByte <<= 1;
				matchBit = matchByte & offset;
				subencoderIndex = offset + matchBit + (symbol >>> 8);
				bit = (symbol >>> 7) & 1;
				rc.encodeBit(probs, subencoderIndex, bit);
				symbol <<= 1;
				offset &= ~(matchByte ^ symbol);
			} while (symbol < 0x10000);
		}

		state = state_updateLiteral(state);
	}

	private void encodeMatch(int dist, int len, int posState) throws IOException {
		state = state_updateMatch(state);
		encodeLength(len, posState, 0);

		int distSlot = getDistSlot(dist);
		rc.encodeBitTree(distSlots[getDistState(len)], distSlot);

		if (distSlot >= DIST_MODEL_START) {
			int footerBits = (distSlot >>> 1) - 1;
			int base = (2 | (distSlot & 1)) << footerBits;
			int distReduced = dist - base;

			if (distSlot < DIST_MODEL_END) {
				rc.encodeReverseBitTree(distSpecial[distSlot - DIST_MODEL_START], distReduced);
			} else {
				rc.encodeDirectBits(distReduced >>> ALIGN_BITS, footerBits - ALIGN_BITS);
				rc.encodeReverseBitTree(distAlign, distReduced & ALIGN_MASK);
				--alignPriceCount;
			}
		}

		reps[3] = reps[2];
		reps[2] = reps[1];
		reps[1] = reps[0];
		reps[0] = dist;

		--distPriceCount;
	}

	private void encodeRepMatch(int rep, int len, int posState) throws IOException {
		RangeEncoder rc = this.rc;
		int state = this.state;

		if (rep == 0) {
			rc.encodeBit(isRep0, state, 0);
			rc.encodeBit(isRep0Long, posState + (state<<4), len == 1 ? 0 : 1);
		} else {
			int dist = reps[rep];
			rc.encodeBit(isRep0, state, 1);

			if (rep == 1) {
				rc.encodeBit(isRep1, state, 0);
			} else {
				rc.encodeBit(isRep1, state, 1);
				rc.encodeBit(isRep2, state, rep - 2);

				if (rep == 3) reps[3] = reps[2];
				reps[2] = reps[1];
			}

			reps[1] = reps[0];
			reps[0] = dist;
		}

		if (len == 1) {
			this.state = state_updateShortRep(state);
		} else {
			encodeLength(len, posState, 2);
			this.state = state_updateLongRep(state);
		}
	}

	final void match() {
		++readAhead;
		lz.match();
		assert lz.verifyMatches();
	}

	final void skip(int len) {
		readAhead += len;
		lz.skip(len);
	}

	final int getAnyMatchPrice(int state, int posState) { return getBitPrice(isMatch[posState + (state<<4)], 1); }
	final int getNormalMatchPrice(int anyMatchPrice, int state) { return anyMatchPrice + getBitPrice(isRep[state], 0); }
	final int getAnyRepPrice(int anyMatchPrice, int state) { return anyMatchPrice + getBitPrice(isRep[state], 1); }
	final int getShortRepPrice(int anyRepPrice, int state, int posState) { return anyRepPrice + getBitPrice(isRep0[state], 0) + getBitPrice(isRep0Long[posState + (state<<4)], 0); }

	final int getLongRepPrice(int anyRepPrice, int rep, int state, int posState) {
		int price = anyRepPrice;

		if (rep == 0) {
			price += getBitPrice(isRep0[state], 0) + getBitPrice(isRep0Long[posState + (state<<4)], 1);
		} else {
			price += getBitPrice(isRep0[state], 1);

			if (rep == 1) price += getBitPrice(isRep1[state], 0);
			else price += getBitPrice(isRep1[state], 1) + getBitPrice(isRep2[state], rep - 2);
		}

		return price;
	}

	final int getLiteralPrice(int curByte, int matchByte, int prevByte, int pos, int state) {
		int price = getBitPrice(isMatch[(pos & posMask) + (state<<4)], 0);

		int i = getSubcoderIndex(prevByte, pos);
		price += state_isLiteral(state) ? _getLiteralPrice(literalProbs[i], curByte) : _getLiteralPriceMatched(literalProbs[i], curByte, matchByte);

		return price;
	}
	private static int _getLiteralPrice(short[] probs, int symbol) {
		int price = 0;
		int subencoderIndex;
		int bit;

		symbol |= 0x100;

		do {
			subencoderIndex = symbol >>> 8;
			bit = (symbol >>> 7) & 1;
			price += getBitPrice(probs[subencoderIndex], bit);
			symbol <<= 1;
		} while (symbol < (0x100 << 8));

		return price;
	}
	private static int _getLiteralPriceMatched(short[] probs, int symbol, int matchByte) {
		int price = 0;
		int offset = 0x100;
		int subencoderIndex;
		int matchBit;
		int bit;

		symbol |= 0x100;

		do {
			matchByte <<= 1;
			matchBit = matchByte & offset;
			subencoderIndex = offset + matchBit + (symbol >>> 8);
			bit = (symbol >>> 7) & 1;
			price += getBitPrice(probs[subencoderIndex], bit);
			symbol <<= 1;
			offset &= ~(matchByte ^ symbol);
		} while (symbol < (0x100 << 8));

		return price;
	}

	final int getLongRepAndLenPrice(int rep, int len, int state, int posState) {
		int anyMatchPrice = getAnyMatchPrice(state, posState);
		int anyRepPrice = getAnyRepPrice(anyMatchPrice, state);
		int longRepPrice = getLongRepPrice(anyRepPrice, rep, state, posState);
		return longRepPrice + getRepeatPrice(len, posState);
	}

	final int getMatchAndLenPrice(int normalMatchPrice, int dist, int len, int posState) {
		int price = normalMatchPrice + getMatchPrice(len, posState);
		int distState = getDistState(len);

		if (dist < FULL_DISTANCES) {
			price += fullDistPrices[distState*FULL_DISTANCES+dist];
		} else {
			// Note that distSlotPrices includes also
			// the price of direct bits.
			int distSlot = getDistSlot(dist);
			price += distSlotPrices[distState*distSlotPricesSize+distSlot] + alignPrices[dist & ALIGN_MASK];
		}

		return price;
	}

	private void updateDistPrices() {
		distPriceCount = DIST_PRICE_UPDATE_INTERVAL;

		for (int state = 0; state < DIST_STATES; ++state) {
			int i = state*distSlotPricesSize;

			for (int slot = 0; slot < distSlotPricesSize; ++slot)
				distSlotPrices[i+slot] = getBitTreePrice(distSlots[state], slot);

			for (int slot = DIST_MODEL_END; slot < distSlotPricesSize; ++slot) {
				int count = (slot >>> 1) - 1 - ALIGN_BITS;
				distSlotPrices[i+slot] += getDirectBitsPrice(count);
			}

			System.arraycopy(distSlotPrices, i, fullDistPrices, state*FULL_DISTANCES, DIST_MODEL_START);
		}

		int dist = DIST_MODEL_START;
		for (int slot = DIST_MODEL_START; slot < DIST_MODEL_END; ++slot) {
			int footerBits = (slot >>> 1) - 1;
			int base = (2 | (slot & 1)) << footerBits;

			int limit = distSpecial[slot - DIST_MODEL_START].length;
			for (int i = 0; i < limit; ++i) {
				int distReduced = dist - base;
				int price = getReverseBitTreePrice(distSpecial[slot - DIST_MODEL_START], distReduced);

				for (int state = 0; state < DIST_STATES; ++state)
					fullDistPrices[state*FULL_DISTANCES+dist] = distSlotPrices[state*distSlotPricesSize+slot] + price;

				++dist;
			}
		}

		assert dist == FULL_DISTANCES;
	}

	/**
	 * Updates the lookup tables used for calculating match distance
	 * and length prices. The updating is skipped for performance reasons
	 * if the tables haven't changed much since the previous update.
	 */
	final void updatePrices() {
		if (distPriceCount <= 0) updateDistPrices();

		if (alignPriceCount <= 0) {
			alignPriceCount = ALIGN_PRICE_UPDATE_INTERVAL;

			for (int i = 0; i < ALIGN_SIZE; ++i)
				alignPrices[i] = getReverseBitTreePrice(distAlign, i);
		}

		int i = 0;
		for (; i <= posMask; i++) {
			if (counters[i] <= 0) {
				counters[i] = PRICE_UPDATE_INTERVAL;
				updateLengthPrices(i, 0);
			}
		}
		for (; i < counters.length; i++) {
			if (counters[i] <= 0) {
				counters[i] = PRICE_UPDATE_INTERVAL;
				updateLengthPrices(i, 2);
			}
		}
	}
	private void updateLengthPrices(int i_pos, int i_off) {
		int[] price = prices[i_pos];
		short[] high1 = i_off == 0 ? high : high2;

		int choice0Price = getBitPrice(choice[i_off], 0);

		int i = 0;
		for (; i < LOW_SYMBOLS; ++i)
			price[i] = choice0Price + getBitTreePrice(low[i_pos], i);

		choice0Price = getBitPrice(choice[i_off], 1);

		int choice1Price = getBitPrice(choice[++i_off], 0);
		for (; i < LOW_SYMBOLS + MID_SYMBOLS; ++i)
			price[i] = choice0Price + choice1Price + getBitTreePrice(mid[i_pos], i - LOW_SYMBOLS);

		choice1Price = getBitPrice(choice[i_off], 1);

		for (; i < price.length; ++i)
			price[i] = choice0Price + choice1Price + getBitTreePrice(high1, i - LOW_SYMBOLS - MID_SYMBOLS);
	}

	private void encodeLength(int len, int posState, int length_type) throws IOException {
		len -= MATCH_LEN_MIN;

		if (length_type > 0) posState += posMask+1;

		RangeEncoder rc = this.rc;
		if (len < LOW_SYMBOLS) {
			rc.encodeBit(choice, length_type, 0);
			rc.encodeBitTree(low[posState], len);
		} else {
			rc.encodeBit(choice, length_type, 1);
			len -= LOW_SYMBOLS;

			length_type++;

			if (len < MID_SYMBOLS) {
				rc.encodeBit(choice, length_type, 0);
				rc.encodeBitTree(mid[posState], len);
			} else {
				rc.encodeBit(choice, length_type, 1);
				rc.encodeBitTree(length_type == 1 ? high : high2, len - MID_SYMBOLS);
			}
		}

		--counters[posState];
	}

	final int getMatchPrice(int len, int posState) { return prices[posState][len - MATCH_LEN_MIN]; }
	final int getRepeatPrice(int len, int posState) { return getMatchPrice(len, posState+posMask+1); }

	public final void lzPresetDict(int dictSize, byte[] dict) {
		if (dict != null) lzPresetDict(dictSize, dict, 0, dict.length);
	}
	public final void lzPresetDict(int dictSize, byte[] dict, int off, int len) {
		ArrayUtil.checkRange(dict, off, len);
		lzPresetDict0(dictSize, dict, (long)Unsafe.ARRAY_BYTE_BASE_OFFSET+off, len);
	}
	public final void lzPresetDict0(int dictSize, Object ref, long off, int len) { lz.setPresetDict(dictSize, ref, off, len); }

	public final int lzFill(byte[] in, int off, int len) { return lzFill0(in, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET+off, len); }
	public final int lzFill(long addr, int len) { return lzFill0(null, addr, len); }
	public final int lzFill0(Object in, long off, int len) {return lz.fillWindow(in, off, len);}

	public final void lzCopy(OutputStream out, int backward, int len) throws IOException {lz.copyUncompressed(out, backward, len);}

	public final void lzFlush() {lz.setFlushing();}
	public final void lzFinish() {lz.setFinishing();}

	public final void lzReset() {lz.reset();}
}