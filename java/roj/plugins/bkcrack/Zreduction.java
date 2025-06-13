package roj.plugins.bkcrack;

import roj.collect.BitSet;
import roj.collect.IntList;

/**
 * @author Roj234
 * @since 2022/11/12 18:01
 */
final class Zreduction {
	static final int WAIT_SIZE  = 1 << 8, TRACK_SIZE = 1 << 16;

	final byte[] keystream;

	// After constructor or reduce(), contains Z[10,32) values.
	// After generate(), contains Zi[2,32) values.
	IntList ziList;
	int index;

	Zreduction(byte[] keystream) {
		this.keystream = keystream;
		this.ziList = new IntList(1<<22);
		this.index = keystream.length - 1;

		byte ki = keystream[index];
		for(int zi = 0; zi < 1<<22; zi++) {
			if(KeyTable.hasZi_2_16(ki, zi << 10)) ziList.add(zi << 10);
		}
	}

	/// Filter Zi[10,32) number using extra contiguous keystream
	void filter() {
		// variables to keep track of the smallest Zi[2,32) vector
		boolean tracking = false;
		IntList bestCopy = new IntList();
		int bestIndex = index, bestSize = TRACK_SIZE;

		// variables to wait for a limited number of steps when a small enough vector is found
		boolean waiting = false;
		int wait = 0;

		IntList zim1_10_32_vector = new IntList(1<<22);
		BitSet zim1_10_32_set = new BitSet(1<<22);

		for(int i = index; i >= Solver.MIN_CONTIGUOUS_PLAIN_LENGTH; i--) {
			zim1_10_32_vector.clear();
			zim1_10_32_set.clear();
			int number_of_zim1_2_32 = 0;

			// generate the Z{i-1}[10,32) values
			for(int zi_10_32 : ziList)
				for(int zi_2_16 : KeyTable.getZi_2_16_vector(keystream[i], zi_10_32)) {
				// get Z{i-1}[10,32) from CRC32^-1
				int zim1_10_32 = CrcTable.getZim1_10_32(zi_10_32 | zi_2_16);
				// collect without duplicates only those that are compatible with keystream{i-1}
				if(!zim1_10_32_set.contains(zim1_10_32 >>> 10) && KeyTable.hasZi_2_16(keystream[i-1], zim1_10_32)) {
					zim1_10_32_vector.add(zim1_10_32);
					zim1_10_32_set.add(zim1_10_32 >>> 10);
					number_of_zim1_2_32 += KeyTable.getZi_2_16_vector(keystream[i-1], zim1_10_32).size();
				}
			}

			// update smallest vector tracking
			if(number_of_zim1_2_32 <= bestSize) // new smallest number of Z[2,32) values
			{
				tracking = true;
				bestIndex = i-1;
				bestSize = number_of_zim1_2_32;
				waiting = false;
			}
			else if(tracking) // number of Z{i-1}[2,32) values is bigger than bestSize
			{
				if(bestIndex == i) // hit a minimum
				{
					// keep a copy of the vector because size is about to grow
					bestCopy = swap(bestCopy);

					if(bestSize <= WAIT_SIZE) {
						// enable waiting
						waiting = true;
						wait = bestSize * 4; // arbitrary multiplicative constant
					}
				}

				if(waiting && --wait == 0)
					break;
			}

			// put result in zi_vector
			zim1_10_32_vector = swap(zim1_10_32_vector);
		}

		if(tracking) {
			// put bestCopy in zi_vector only if bestIndex is not the index of zi_vector
			if(bestIndex != Solver.MIN_CONTIGUOUS_PLAIN_LENGTH - 1) swap(bestCopy);
			index = bestIndex;
		} else index = Solver.MIN_CONTIGUOUS_PLAIN_LENGTH - 1;
	}

	private IntList swap(IntList b) {
		IntList a = ziList;
		ziList = b;
		return a;
	}

	/// Extend Zi[10,32) values into Zi[2,32) values using keystream
	void generate() {
		int len = ziList.size();
		for(int i = 0; i < len; i++) {
			int zi = ziList.get(i);

			IntList zi_2_16 = KeyTable.getZi_2_16_vector(keystream[index], zi);
			for(int j = 1; j < zi_2_16.size(); j++) ziList.add(zi | zi_2_16.get(j));
			ziList.set(i, zi | zi_2_16.get(0));
		}
	}

	/// \return the generated Zi[2,32) values
    IntList getCandidates() {
		return ziList;
	}

	/// \return the index of the Zi[2,32) values relative to keystream
	int getIndex() {
		return index;
	}
}