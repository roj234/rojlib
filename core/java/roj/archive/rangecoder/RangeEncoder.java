/*
 * RangeEncoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.rangecoder;

import roj.util.ArrayCache;

import java.io.IOException;
import java.io.OutputStream;

import static roj.archive.rangecoder.RangeCoder.*;

public sealed class RangeEncoder permits RangeEncoderToStream {
	public long low;
	public int range;

	private int cacheSize;
	private byte cache;

	final byte[] buf;
	int bufPos;

	public RangeEncoder(int bufSize) {
		buf = ArrayCache.getByteArray(bufSize, false);
		reset();
	}

	public final synchronized void free() {
		if (cacheSize == -1) return;
		ArrayCache.putArray(buf);
		cacheSize = -1;
	}

	public final int getPendingSize() { return bufPos + cacheSize + INIT_SIZE - 1; }
	public final void flushTo(OutputStream out) throws IOException { out.write(buf, 0, bufPos); }

	public final void reset() {
		low = 0;
		range = 0xFFFFFFFF;
		cache = 0x00;
		cacheSize = 1;
		bufPos = 0;
	}

	public int finish() throws IOException {
		for (int i = 0; i < INIT_SIZE; ++i)
			shiftLow();

		// no RangeEncoderToBuffer anymore, a buffer is great for anyone
		// however, LZMAOutputStream still don't use this
		return bufPos;
	}

	void flushNow() throws IOException {
		// RangeEncoderToStream does flush
		throw new ArrayIndexOutOfBoundsException("buffer overflow! "+buf.length);
	}

	public final void normalize() throws IOException {
		if ((range & TOP_MASK) == 0) {
			range <<= SHIFT_BITS;
			shiftLow();
		}
	}
	private void shiftLow() throws IOException {
		int lowHi = (int) (low >>> 32);

		if (lowHi != 0 || low < 0xFF000000L) {
			int temp = cache;

			byte[] buf = this.buf;
			int bufPos = this.bufPos;

			for (int i = cacheSize; i > 0; i--) {
				buf[bufPos++] = (byte) (temp + lowHi);

				if (bufPos == buf.length) {
					flushNow();
					bufPos = 0;
				}

				temp = 0xFF;
			}
			cacheSize = 0;

			this.bufPos = bufPos;

			cache = (byte) (low >>> 24);
		}

		++cacheSize;

		assert cacheSize <= 5;
		// low range = [000000 => 0xFEFFFFFFF]
		low = (low & 0x00FFFFFF) << 8;
	}
	//region LZMA2 specific
	public final void encodeBit(short[] probs, int index, int bit) throws IOException {
		int prob = probs[index];
		int bound = (range >>> BIT_MODEL_TOTAL_BITS) * prob;

		// NOTE: Any non-zero value for bit is taken as 1.
		if (bit == 0) {
			range = bound;
			probs[index] = (short) (prob + ((BIT_MODEL_TOTAL - prob) >>> MOVE_BITS));
		} else {
			low += bound & 0xFFFFFFFFL;
			range -= bound;
			probs[index] = (short) (prob - (prob >>> MOVE_BITS));
		}

		normalize();
	}
	public final void encodeBitTree(short[] probs, int symbol) throws IOException {
		int index = 1;
		int mask = probs.length;

		do {
			mask >>>= 1;
			int bit = symbol & mask;
			encodeBit(probs, index, bit);

			index <<= 1;
			if (bit != 0) index |= 1;
		} while (mask != 1);
	}
	public final void encodeReverseBitTree(short[] probs, int symbol) throws IOException {
		int index = 1;
		symbol |= probs.length;

		do {
			int bit = symbol & 1;
			symbol >>>= 1;
			encodeBit(probs, index, bit);
			index = (index << 1) | bit;
		} while (symbol != 1);
	}
	public final void encodeDirectBits(int value, int count) throws IOException {
		do {
			range >>>= 1;
			low += range & -((value >>> --count) & 1);

			normalize();
		} while (count != 0);
	}
	//endregion
	// PPMd
	public final void encode(int start, int size) {
		low += Integer.toUnsignedLong(start * range);
		range *= size;
	}

	public final void encodeFinal(int start, int size) throws IOException {
		encode(start, size);
		normalize();
		normalize();
	}

	/**
	 * Encodes a single bit based on a given prediction.
	 *
	 * @param bit The bit to encode (0 or 1).
	 * @param prob The probability for the bit to be 1, in the range [0, 2047].
	 * @see roj.archive.algorithms.EntropyModel
	 */
	public void encodeBit(int bit, int prob) throws IOException {
		prob = 2047 - prob;
		int bound = (range >>> BIT_MODEL_TOTAL_BITS) * prob;

		if (bit == 0) range = bound;
		else {
			low += bound & 0xFFFFFFFFL;
			range -= bound;
		}

		normalize();
	}

	/**
	 * Encodes a single bit based on a given prediction.
	 *
	 * @param bit The bit to encode (0 or 1).
	 * @param prob The probability for the bit to be <b>ZERO</b>, in the range [0, 1&lt;&lt;space].
	 * @param space The probability space
	 * @see roj.archive.algorithms.EntropyModel
	 */
	public void encodeBit(int bit, int prob, int space) throws IOException {
		int bound = (int) (((range&0xFFFFFFFFL) * prob) >>> space);

		if (bit == 0) range = bound;
		else {
			low += bound & 0xFFFFFFFFL;
			range -= bound;
		}

		normalize();
	}
}