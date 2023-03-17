/*
 * RangeCoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.qz.xz.rangecoder;

import java.util.Arrays;

public abstract class RangeCoder {
	public static final int SHIFT_BITS = 8;
	public static final int TOP_MASK = 0xFF000000;
	public static final int BIT_MODEL_TOTAL_BITS = 11;
	public static final int BIT_MODEL_TOTAL = 1 << BIT_MODEL_TOTAL_BITS;
	public static final short PROB_INIT = BIT_MODEL_TOTAL >> 1;
	public static final int MOVE_BITS = 5;

	public static void initProbs(short[] probs) {
		Arrays.fill(probs, PROB_INIT);
	}
}
