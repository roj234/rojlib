/*
 * RangeEncoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.qz.xz.rangecoder;

import roj.util.ArrayCache;

import java.io.IOException;
import java.io.OutputStream;

public class RangeEncoder extends RangeCoder {
	private static final int MOVE_REDUCING_BITS = 4;
	private static final int BIT_PRICE_SHIFT_BITS = 4;

	private static final int[] prices = new int[BIT_MODEL_TOTAL >>> MOVE_REDUCING_BITS];
	static {
		for (int i = (1 << MOVE_REDUCING_BITS) / 2; i < BIT_MODEL_TOTAL; i += (1 << MOVE_REDUCING_BITS)) {
			int w = i;
			int bitCount = 0;

			for (int j = 0; j < BIT_PRICE_SHIFT_BITS; ++j) {
				w *= w;
				bitCount <<= 1;

				while ((w & 0xFFFF0000) != 0) {
					w >>>= 1;
					++bitCount;
				}
			}

			prices[i >> MOVE_REDUCING_BITS] = (BIT_MODEL_TOTAL_BITS << BIT_PRICE_SHIFT_BITS) - 15 - bitCount;
		}
	}

	private long low;
	private int range;

	private int cacheSize;
	private byte cache;

	final byte[] buf;
	int bufPos;

	public RangeEncoder(int bufSize) {
		buf = ArrayCache.getByteArray(bufSize, false);
		reset();
	}

	public final synchronized void putArraysToCache() {
		if (cacheSize == -1) return;
		ArrayCache.putArray(buf);
		cacheSize = -1;
	}

	public final int lzma2_getPendingSize() { return bufPos + cacheSize + INIT_SIZE - 1; }
	public final void lzma2_write(OutputStream out) throws IOException { out.write(buf, 0, bufPos); }

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

		if ((range & TOP_MASK) == 0) {
			range <<= SHIFT_BITS;
			shiftLow();
		}
	}
	public static int getBitPrice(int prob, int bit) {
		// NOTE: Unlike in encodeBit(), here bit must be 0 or 1.
		assert bit == 0 || bit == 1;
		return prices[(prob ^ ((-bit) & (BIT_MODEL_TOTAL - 1))) >>> MOVE_REDUCING_BITS];
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
	public static int getBitTreePrice(short[] probs, int symbol) {
		int price = 0;
		symbol |= probs.length;

		do {
			int bit = symbol & 1;
			symbol >>>= 1;
			price += getBitPrice(probs[symbol], bit);
		} while (symbol != 1);

		return price;
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
	public static int getReverseBitTreePrice(short[] probs, int symbol) {
		int price = 0;
		int index = 1;
		symbol |= probs.length;

		do {
			int bit = symbol & 1;
			symbol >>>= 1;
			price += getBitPrice(probs[index], bit);
			index = (index << 1) | bit;
		} while (symbol != 1);

		return price;
	}

	public final void encodeDirectBits(int value, int count) throws IOException {
		do {
			range >>>= 1;
			low += range & -((value >>> --count) & 1);

			if ((range & TOP_MASK) == 0) {
				range <<= SHIFT_BITS;
				shiftLow();
			}
		} while (count != 0);
	}
	public static int getDirectBitsPrice(int count) { return count << BIT_PRICE_SHIFT_BITS; }
}
