package roj.archive.qz.xz;

import roj.archive.qz.xz.lzma.LZMAEncoder;
import roj.archive.qz.xz.rangecoder.RangeEncoder;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Roj234
 * @since 2023/11/17 2:10
 */
abstract class LZMA2Out extends OutputStream {
	static final int COMPRESSED_SIZE_MAX = 64 << 10;

	OutputStream out;

	RangeEncoder rc;
	LZMAEncoder lzma;

	byte props;

	byte state;
	static final byte STATE_LZMA = 0, STATE_RESET = 1, PROP_RESET = 2, DICT_RESET = 3;

	final byte[] chunkMeta = new byte[6];

	static int getMemoryUsage(LZMA2Options options) {
		// 64 KiB buffer for the range encoder + a little extra + LZMAEncoder
		return 70 + LZMAEncoder.getMemoryUsage(options, COMPRESSED_SIZE_MAX);
	}
	LZMA2Out(LZMA2Options options) {
		rc = new RangeEncoder(COMPRESSED_SIZE_MAX);
		lzma = LZMAEncoder.getInstance(rc, options, COMPRESSED_SIZE_MAX);
		props = options.getPropByte();
	}

	final void writeChunk(boolean compressionDisabled) throws IOException {
		int cSize = rc.finish();
		int uSize = lzma.getUncompressedSize();

		assert cSize > 0 : cSize;
		assert uSize > 0 : uSize;

		// +2 because the header of a compressed chunk is 2 bytes
		// bigger than the header of an uncompressed chunk.
		if (cSize + 2 < uSize && !compressionDisabled) writeLZMAChunk(uSize, cSize);
		else writeRawChunk();

		lzma.resetUncompressedSize();
		rc.reset();
	}
	private void writeLZMAChunk(int uSize, int cSize) throws IOException {
		--uSize; --cSize;

		int control = 0x80 | (state << 5) | (uSize >>> 16);
		chunkMeta[0] = (byte) control;
		chunkMeta[1] = (byte) (uSize >>> 8);
		chunkMeta[2] = (byte) uSize;
		chunkMeta[3] = (byte) (cSize >>> 8);
		chunkMeta[4] = (byte) cSize;

		if (state >= PROP_RESET) {
			chunkMeta[5] = props;
			out.write(chunkMeta, 0, 6);
		} else {
			out.write(chunkMeta, 0, 5);
		}

		rc.lzma2_write(out);

		state = STATE_LZMA;
	}
	private void writeRawChunk() throws IOException {
		lzma.reset();
		int uSize = lzma.getUncompressedSize();
		assert uSize > 0 : uSize;

		byte control = 0x02;

		// 仅为第一个块时 (这似乎没有意义，然而对于7-zip的C语言Lzma2Dec实现是必须的)
		if (state == DICT_RESET) {
			control = 0x01;
			state = PROP_RESET;
		}

		while (uSize > 0) {
			int chunkSize = Math.min(uSize, COMPRESSED_SIZE_MAX);
			chunkMeta[0] = control;
			chunkMeta[1] = (byte) ((chunkSize-1) >>> 8);
			chunkMeta[2] = (byte) (chunkSize-1);
			out.write(chunkMeta, 0, 3);
			lzma.lzCopy(out, uSize, chunkSize);
			uSize -= chunkSize;
			control = 0x02;
		}

		if (state == STATE_LZMA) state = STATE_RESET;
	}

	@Override
	public final void write(int b) throws IOException {
		chunkMeta[0] = (byte) b;
		write(chunkMeta, 0, 1);
	}
}