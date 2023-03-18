/*
 * RangeDecoderFromStream
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.qz.xz.rangecoder;

import roj.archive.qz.xz.CorruptedInputException;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class RangeDecoderFromStream extends RangeDecoder {
	public final DataInputStream inData;

	public RangeDecoderFromStream(InputStream in) throws IOException {
		inData = new DataInputStream(in);

		if (inData.readUnsignedByte() != 0x00) throw new CorruptedInputException();

		code = inData.readInt();
		range = 0xFFFFFFFF;
	}

	public boolean isFinished() {
		return code == 0;
	}

	public void normalize() throws IOException {
		if ((range & TOP_MASK) == 0) {
			code = (code << SHIFT_BITS) | inData.readUnsignedByte();
			range <<= SHIFT_BITS;
		}
	}
}
