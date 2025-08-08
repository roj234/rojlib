/*
 * LZMADecoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.xz.lzma;

import roj.archive.xz.lz.LZDecoder;
import roj.archive.xz.rangecoder.RangeDecoder;

import java.io.IOException;

public final class LZMADecoder extends LZMACoder {
	private final LZDecoder lz;
	private final RangeDecoder rc;

	public LZMADecoder(LZDecoder lz, RangeDecoder rc, int lc, int lp, int pb) {
		super(lc, lp, pb);
		this.lz = lz;
		this.rc = rc;
		reset();
	}

	/**
	 * Returns true if LZMA end marker was detected. It is encoded as
	 * the maximum match distance which with signed ints becomes -1. This
	 * function is needed only for LZMA1. LZMA2 doesn't use the end marker
	 * in the LZMA layer.
	 */
	public boolean endMarkerDetected() { return reps[0] == -1; }

	public void decode() throws IOException {
		lz.repeatPending();

		while (lz.hasSpace()) {
			int posState = lz.getPos() & posMask;

			if (rc.decodeBit(isMatch, posState + (state<<4)) == 0) {
				int i = getSubcoderIndex(lz.getByte(0), lz.getPos());
				decodeLiteral(literalProbs[i]);
			} else {
				int len = rc.decodeBit(isRep, state) == 0
					? decodeMatch(posState)
					: decodeRepMatch(posState);

				// NOTE: With LZMA1 streams that have the end marker,
				// this will throw CorruptedInputException. LZMAInputStream
				// handles it specially.
				lz.repeat(reps[0], len);
			}
		}

		rc.fill();
	}

	private void decodeLiteral(short[] probs) throws IOException {
		int symbol;
		if (state_isLiteral(state)) {
			symbol = rc.decodeBitTree(probs, 0x100);
		} else {
			symbol = 1;

			RangeDecoder rc = this.rc;
			int matchByte = lz.getByte(reps[0]);
			int offset = 0x100;
			int matchBit;
			int bit;

			do {
				matchByte <<= 1;
				matchBit = matchByte & offset;
				bit = rc.decodeBit(probs, offset + matchBit + symbol);
				symbol = (symbol << 1) | bit;
				offset &= (-bit) ^ ~matchBit;
			} while (symbol < 0x100);
		}

		lz.putByte(symbol);
		state = state_updateLiteral(state);
	}

	private int decodeMatch(int posState) throws IOException {
		state = state_updateMatch(state);

		reps[3] = reps[2];
		reps[2] = reps[1];
		reps[1] = reps[0];

		int len = decodeMatchLen(posState);
		int distSlot = rc.decodeBitTree(distSlots[getDistState(len)]);

		if (distSlot < DIST_MODEL_START) {
			reps[0] = distSlot;
		} else {
			int limit = (distSlot >> 1) - 1;
			int rep = (2 | (distSlot & 1)) << limit;

			if (distSlot < DIST_MODEL_END) {
				rep |= rc.decodeReverseBitTree(distSpecial[distSlot - DIST_MODEL_START]);
			} else {
				rep |= rc.decodeDirectBits(limit - ALIGN_BITS) << ALIGN_BITS;
				rep |= rc.decodeReverseBitTree(distAlign);
			}

			reps[0] = rep;
		}

		return len;
	}
	private int decodeMatchLen(int posState) throws IOException {
		RangeDecoder rc = this.rc;
		if (rc.decodeBit(choice, 0) == 0) return rc.decodeBitTree(low[posState]) + MATCH_LEN_MIN;
		if (rc.decodeBit(choice, 1) == 0) return rc.decodeBitTree(mid[posState]) + MATCH_LEN_MIN + LOW_SYMBOLS;
		return rc.decodeBitTree(high) + MATCH_LEN_MIN + LOW_SYMBOLS + MID_SYMBOLS;
	}

	private int decodeRepMatch(int posState) throws IOException {
		RangeDecoder rc = this.rc;
		int state = this.state;

		if (rc.decodeBit(isRep0, state) == 0) {
			if (rc.decodeBit(isRep0Long, posState + (state<<4)) == 0) {
				this.state = state_updateShortRep(state);
				return 1;
			}
		} else {
			int tmp;

			if (rc.decodeBit(isRep1, state) == 0) {
				tmp = reps[1];
			} else {
				if (rc.decodeBit(isRep2, state) == 0) {
					tmp = reps[2];
				} else {
					tmp = reps[3];
					reps[3] = reps[2];
				}

				reps[2] = reps[1];
			}

			reps[1] = reps[0];
			reps[0] = tmp;
		}

		this.state = state_updateLongRep(state);

		return decodeRepeatLen(posState);
	}
	private int decodeRepeatLen(int posState) throws IOException {
		RangeDecoder rc = this.rc;
		if (rc.decodeBit(choice, 2) == 0) return rc.decodeBitTree(low[posMask+1+posState]) + MATCH_LEN_MIN;
		if (rc.decodeBit(choice, 3) == 0) return rc.decodeBitTree(mid[posMask+1+posState]) + MATCH_LEN_MIN + LOW_SYMBOLS;
		return rc.decodeBitTree(high2) + MATCH_LEN_MIN + LOW_SYMBOLS + MID_SYMBOLS;
	}
}
