/*
 * LZMACoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.qz.xz.lzma;

import static roj.archive.qz.xz.rangecoder.RangeCoder.initProbs;

abstract class LZMACoder {
	static final int LOW_SYMBOLS = 1 << 3, MID_SYMBOLS = 1 << 3, HIGH_SYMBOLS = 1 << 8;

	static final int POS_STATES_MAX = 1 << 4;

	static final int MATCH_LEN_MIN = 2;
	static final int MATCH_LEN_MAX = MATCH_LEN_MIN + LOW_SYMBOLS + MID_SYMBOLS + HIGH_SYMBOLS - 1;

	static final int DIST_STATES = 4, DIST_SLOTS = 1 << 6, DIST_MODEL_START = 4, DIST_MODEL_END = 14;
	static final int FULL_DISTANCES = 1 << (DIST_MODEL_END / 2);

	static final int ALIGN_BITS = 4, ALIGN_SIZE = 1 << ALIGN_BITS, ALIGN_MASK = ALIGN_SIZE - 1;

	static final int REPS = 4;

	int posMask;
	int lc;
	int literalPosMask;

	final int[] reps = new int[REPS];
	int state;

	final short[] isMatch = new short[STATES * POS_STATES_MAX];
	final short[] isRep = new short[STATES];
	//final short[] isRepBit = new short[STATES*4];
	final short[] isRep0 = new short[STATES], isRep1 = new short[STATES], isRep2 = new short[STATES];
	final short[] isRep0Long = new short[STATES * POS_STATES_MAX];
	final short[][] distSlots = new short[DIST_STATES][DIST_SLOTS];
	final short[][] distSpecial = {new short[2], new short[2], new short[4], new short[4], new short[8], new short[8], new short[16], new short[16], new short[32], new short[32]};
	final short[] distAlign = new short[ALIGN_SIZE];

	static int getDistState(int len) { return len < DIST_STATES + MATCH_LEN_MIN ? len - MATCH_LEN_MIN : DIST_STATES - 1; }

	private static final short[][] MY_EMPTY = new short[0][];

	short[][] literalProbs = MY_EMPTY;

	final short[] choice = new short[2 << 1];
	short[][] low = MY_EMPTY, mid;
	final short[] high = new short[HIGH_SYMBOLS], high2 = new short[HIGH_SYMBOLS];

	LZMACoder(int lc, int lp, int pb) { setProp0(lc, lp, pb); }
	public void propReset(int lc, int lp, int pb) { setProp0(lc, lp, pb); reset(); }
	private void setProp0(int lc, int lp, int pb) {
		this.lc = lc;
		literalPosMask = (1<<lp) - 1;

		if (literalProbs.length != (1 << (lc+lp)))
			literalProbs = new short[1 << (lc + lp)][0x300];

		posMask = (1<<pb) - 1;
		if (low.length != 2<<pb) {
			low = new short[2<<pb][LOW_SYMBOLS];
			mid = new short[2<<pb][MID_SYMBOLS];
		}
	}

	final int getSubcoderIndex(int prevByte, int pos) {
		int low = prevByte >> (8 - lc);
		int high = (pos & literalPosMask) << lc;
		return low + high;
	}

	public void reset() {
		reps[0] = 0;
		reps[1] = 0;
		reps[2] = 0;
		reps[3] = 0;
		state = LIT_LIT;

		initProbs(isMatch);
		initProbs(isRep);
		initProbs(isRep0);
		initProbs(isRep1);
		initProbs(isRep2);
		initProbs(isRep0Long);
		for (short[] probs : distSlots) initProbs(probs);
		for (short[] probs : distSpecial) initProbs(probs);
		initProbs(distAlign);

		for (short[] probs : literalProbs) initProbs(probs);

		initProbs(choice);
		for (short[] probs : low) initProbs(probs);
		for (short[] probs : mid) initProbs(probs);
		initProbs(high);
		initProbs(high2);
	}

	// region STATE
	static final int STATES = 12;
	static final int LIT_STATES = 7;
	static final int
		LIT_LIT = 0,
		MATCH_LIT_LIT = 1,
		REP_LIT_LIT = 2,
		SHORTREP_LIT_LIT = 3,
		MATCH_LIT = 4,
		REP_LIT = 5,
		SHORTREP_LIT = 6,
		LIT_MATCH = 7,
		LIT_LONGREP = 8,
		LIT_SHORTREP = 9,
		NONLIT_MATCH = 10,
		NONLIT_REP = 11;

	static int state_updateLiteral(int state) {
		if (state <= SHORTREP_LIT_LIT) return LIT_LIT;
		else if (state <= LIT_SHORTREP) return state-3;
		else return state-6;
	}
	static int state_updateMatch(int state) { return state < LIT_STATES ? LIT_MATCH : NONLIT_MATCH; }
	static int state_updateLongRep(int state) { return state < LIT_STATES ? LIT_LONGREP : NONLIT_REP; }
	static int state_updateShortRep(int state) { return state < LIT_STATES ? LIT_SHORTREP : NONLIT_REP; }
	static boolean state_isLiteral(int state) { return state < LIT_STATES; }
	// endregion
}