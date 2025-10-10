/*
 * RangeCoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.rangecoder;

import java.util.Arrays;

public interface RangeCoder {
	int INIT_SIZE = 5;

	int SHIFT_BITS = 8;
	int TOP_MASK = 0xFF000000;
	int BIT_MODEL_TOTAL_BITS = 11;
	int BIT_MODEL_TOTAL = 1 << BIT_MODEL_TOTAL_BITS;
	short PROB_INIT = BIT_MODEL_TOTAL >> 1;
	int MOVE_BITS = 5;

	static void initProbs(short[] probs) { Arrays.fill(probs, PROB_INIT); }
}
