/*
 * RangeDecoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.rangecoder;

import roj.io.CorruptedInputException;
import roj.util.ArrayCache;

import java.io.DataInput;
import java.io.IOException;

import static roj.archive.rangecoder.RangeCoder.*;

public sealed class RangeDecoder permits RangeDecoderFromStream {
	final byte[] buf;
	int pos, len;

	public int range, code;

	public RangeDecoder(int inputSizeMax) { this(inputSizeMax - INIT_SIZE, false); }
	RangeDecoder(int bufLen, boolean noBuffer) {
		// We will use the *end* of the array so if the cache gives us
		// a bigger-than-requested array, we still want to use buf.length.
		buf = noBuffer ? new byte[1] : ArrayCache.getByteArray(bufLen, false);
		pos = len = buf.length;
	}

	public void finish() { ArrayCache.putArray(buf); }

	public void manualFill(DataInput in, int len) throws IOException {
		if (len < INIT_SIZE) throw new CorruptedInputException();

		if (in.readUnsignedByte() != 0x00) throw new CorruptedInputException();

		code = in.readInt();
		range = 0xFFFFFFFF;

		// Read the data to the end of the buffer. If the data is corrupt
		// and the decoder, reading from buf, tries to read past the end of
		// the data, ArrayIndexOutOfBoundsException will be thrown and
		// the problem is detected immediately.
		len -= INIT_SIZE;
		pos = buf.length - len;
		in.readFully(buf, pos, len);
	}

	public boolean isFinished() { return pos == len && code == 0; }
	int doFill() throws IOException { return -1; }

	public final void fill() throws IOException {
		if ((range & TOP_MASK) == 0) {
			if (pos == len) {
				pos = 0;
				len = doFill();
				if (len <= 0) throw new CorruptedInputException();
			}

			code = (code << SHIFT_BITS) | (buf[pos++] & 0xFF);
			range <<= SHIFT_BITS;
		}
	}

	public void errorRecovery() {
		pos = len;
		code = 0;
	}

	public final int decodeBit(short[] probs, int index) throws IOException {
		fill();

		int prob = probs[index];
		int bound = (range >>> BIT_MODEL_TOTAL_BITS) * prob;

		// Compare code and bound as if they were unsigned 32-bit integers.
		if ((code ^ 0x80000000) < (bound ^ 0x80000000)) {
			range = bound;
			probs[index] = (short) (prob + ((BIT_MODEL_TOTAL - prob) >>> MOVE_BITS));
			return 0;
		} else {
			range -= bound;
			code -= bound;
			probs[index] = (short) (prob - (prob >>> MOVE_BITS));
			return 1;
		}
	}
	public final int decodeBitTree(short[] probs) throws IOException { return decodeBitTree(probs, probs.length) - probs.length; }
	public final int decodeBitTree(short[] probs, int symbolLen) throws IOException {
		int symbol = 1;

		while (true) {
			symbol = (symbol << 1) | decodeBit(probs, symbol);
			if (symbol >= symbolLen) return symbol;
		}
	}
	public final int decodeReverseBitTree(short[] probs) throws IOException {
		int symbol = 1;
		int i = 0;
		int result = 0;

		do {
			int bit = decodeBit(probs, symbol);
			symbol = (symbol << 1) | bit;
			result |= bit << i++;
		} while (symbol < probs.length);

		return result;
	}
	public final int decodeDirectBits(int count) throws IOException {
		int result = 0;

		do {
			fill();

			range >>>= 1;
			int t = (code - range) >>> 31;
			code -= range & (t - 1);
			result = (result << 1) | (1 - t);
		} while (--count != 0);

		return result;
	}

	public final int getThreshold(int total) {
		range = Integer.divideUnsigned(range, total);
		return Integer.divideUnsigned(code, range);
	}

	public final void decode(int start, int size) throws IOException {
		code -= start * range;
		range *= size;
	}

	public final void decodeFinal(int start, int size) throws IOException {
		decode(start, size);
		fill();
		fill();
	}

	public final int decodeBit(int prob) throws IOException {
		fill();

		prob = 0x7ff - prob;
		int bound = (range >>> BIT_MODEL_TOTAL_BITS) * prob;

		if ((code ^ 0x80000000) < (bound ^ 0x80000000)) {
			range = bound;
			return 0;
		} else {
			range -= bound;
			code -= bound;
			return 1;
		}
	}

	public final int decodeBit(int prob, int space) throws IOException {
		fill();

		int bound = (int) (((range&0xFFFFFFFFL) * prob) >>> space);

		if ((code ^ 0x80000000) < (bound ^ 0x80000000)) {
			range = bound;
			return 0;
		} else {
			range -= bound;
			code -= bound;
			return 1;
		}
	}
}