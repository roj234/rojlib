/*
 * RangeCoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.xz.rangecoder;

import java.util.Arrays;

public abstract sealed class RangeCoder permits RangeDecoder, RangeEncoder {
	static final int INIT_SIZE = 5;

	static final int SHIFT_BITS = 8;
	static final int TOP_MASK = 0xFF000000;
	static final int BIT_MODEL_TOTAL_BITS = 11;
	static final int BIT_MODEL_TOTAL = 1 << BIT_MODEL_TOTAL_BITS;
	static final short PROB_INIT = BIT_MODEL_TOTAL >> 1;
	static final int MOVE_BITS = 5;

	public static void initProbs(short[] probs) { Arrays.fill(probs, PROB_INIT); }
}
