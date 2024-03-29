/*
 * LZMA2OutputStream
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.qz.xz;

import roj.archive.qz.xz.lz.LZEncoder;
import roj.archive.qz.xz.lzma.LZMAEncoder;
import roj.archive.qz.xz.rangecoder.RangeEncoderToBuffer;
import roj.io.Finishable;
import roj.util.ArrayCache;

import java.io.IOException;
import java.io.OutputStream;

class LZMA2OutputStream extends OutputStream implements Finishable {
	static final int COMPRESSED_SIZE_MAX = 64 << 10;

	private final ArrayCache arrayCache;

	private OutputStream out;

	private LZEncoder lz;
	private RangeEncoderToBuffer rc;
	private LZMAEncoder lzma;

	private final int props; // Cannot change props on the fly for now.
	private boolean dictResetNeeded = true;
	private boolean stateResetNeeded = true;
	private boolean propsNeeded = true;

	private int pendingSize = 0;
	private boolean finished = false;

	private final byte[] chunkHeader = new byte[6];

	private static int getExtraSizeBefore(int dictSize) {
		return COMPRESSED_SIZE_MAX > dictSize ? COMPRESSED_SIZE_MAX - dictSize : 0;
	}

	static int getMemoryUsage(LZMA2Options options) {
		// 64 KiB buffer for the range encoder + a little extra + LZMAEncoder
		int dictSize = options.getDictSize();
		int extraSizeBefore = getExtraSizeBefore(dictSize);
		return 70 + LZMAEncoder.getMemoryUsage(options.getMode(), dictSize, extraSizeBefore, options.getMatchFinder());
	}

	LZMA2OutputStream(OutputStream out, LZMA2Options options, ArrayCache arrayCache) {
		if (out == null) throw new NullPointerException();

		this.arrayCache = arrayCache;
		this.out = out;
		rc = new RangeEncoderToBuffer(COMPRESSED_SIZE_MAX, arrayCache);

		int dictSize = options.getDictSize();
		int extraSizeBefore = getExtraSizeBefore(dictSize);
		lzma = LZMAEncoder.getInstance(rc, options.getLc(), options.getLp(), options.getPb(), options.getMode(), dictSize, extraSizeBefore, options.getNiceLen(), options.getMatchFinder(), options.getDepthLimit(), this.arrayCache);

		lz = lzma.getLZEncoder();

		byte[] presetDict = options.getPresetDict();
		if (presetDict != null && presetDict.length > 0) {
			lz.setPresetDict(dictSize, presetDict);
			dictResetNeeded = false;
		}

		props = options.getPropByte();
	}

	public void write(int b) throws IOException {
		chunkHeader[0] = (byte) b;
		write(chunkHeader, 0, 1);
	}

	public void write(byte[] buf, int off, int len) throws IOException {
		if (off < 0 || len < 0 || off + len < 0 || off + len > buf.length) throw new IndexOutOfBoundsException();

		if (finished) throw new IOException("Stream finished or closed");

		try {
			while (len > 0) {
				int used = lz.fillWindow(buf, off, len);
				off += used;
				len -= used;
				pendingSize += used;

				if (lzma.encodeForLZMA2()) writeChunk();
			}
		} catch (Throwable e) {
			try {
				close();
			} catch (Throwable ignored) {}
			throw e;
		}
	}

	private void writeChunk() throws IOException {
		int compressedSize = rc.finish();
		int uncompressedSize = lzma.getUncompressedSize();

		assert compressedSize > 0 : compressedSize;
		assert uncompressedSize > 0 : uncompressedSize;

		// +2 because the header of a compressed chunk is 2 bytes
		// bigger than the header of an uncompressed chunk.
		if (compressedSize + 2 < uncompressedSize) {
			writeLZMA(uncompressedSize, compressedSize);
		} else {
			lzma.reset();
			uncompressedSize = lzma.getUncompressedSize();
			assert uncompressedSize > 0 : uncompressedSize;
			writeUncompressed(uncompressedSize);
		}

		pendingSize -= uncompressedSize;
		lzma.resetUncompressedSize();
		rc.reset();
	}

	private void writeLZMA(int uncompressedSize, int compressedSize) throws IOException {
		int control;

		if (propsNeeded) {
			if (dictResetNeeded) control = 0x80 + (3 << 5);
			else control = 0x80 + (2 << 5);
		} else {
			if (stateResetNeeded) control = 0x80 + (1 << 5);
			else control = 0x80;
		}

		--uncompressedSize;
		control |= uncompressedSize >>> 16;
		chunkHeader[0] = (byte) control;
		chunkHeader[1] = (byte) (uncompressedSize >>> 8);
		chunkHeader[2] = (byte) uncompressedSize;
		chunkHeader[3] = (byte) ((compressedSize - 1) >>> 8);
		chunkHeader[4] = (byte) (compressedSize - 1);

		if (propsNeeded) {
			chunkHeader[5] = (byte) props;
			out.write(chunkHeader, 0, 6);
		} else {
			out.write(chunkHeader, 0, 5);
		}

		rc.write(out);

		propsNeeded = false;
		stateResetNeeded = false;
		dictResetNeeded = false;
	}

	private void writeUncompressed(int uncompressedSize) throws IOException {
		while (uncompressedSize > 0) {
			int chunkSize = Math.min(uncompressedSize, COMPRESSED_SIZE_MAX);
			chunkHeader[0] = (byte) (dictResetNeeded ? 0x01 : 0x02);
			chunkHeader[1] = (byte) ((chunkSize - 1) >>> 8);
			chunkHeader[2] = (byte) (chunkSize - 1);
			out.write(chunkHeader, 0, 3);
			lz.copyUncompressed(out, uncompressedSize, chunkSize);
			uncompressedSize -= chunkSize;
			dictResetNeeded = false;
		}

		stateResetNeeded = true;
	}

	private void writeEndMarker() throws IOException {
		assert !finished;
		finished = true;

		lz.setFinishing();

		try {
			while (pendingSize > 0) {
				lzma.encodeForLZMA2();
				writeChunk();
			}

			out.write(0x00);
		} catch (Throwable e) {
			try {
				close();
			} catch (Throwable ignored) {}
			throw e;
		} finally {
			lzma.putArraysToCache(arrayCache);
			lzma = null;
			lz = null;
			rc.putArraysToCache(arrayCache);
			rc = null;
		}
	}

	public void flush() throws IOException {
		if (finished) throw new IOException("Stream finished or closed");

		try {
			lz.setFlushing();

			while (pendingSize > 0) {
				lzma.encodeForLZMA2();
				writeChunk();
			}

			out.flush();
		} catch (Throwable e) {
			try {
				close();
			} catch (Throwable ignored) {}
			throw e;
		}
	}

	/**
	 * Finishes the stream but not closes the underlying OutputStream.
	 */
	public synchronized void finish() throws IOException {
		if (finished) return;

		writeEndMarker();

		try {
			if (out instanceof Finishable)
				((Finishable) out).finish();
		} catch (Throwable e) {
			try {
				close();
			} catch (Throwable ignored) {}
			throw e;
		}
	}

	/**
	 * Finishes the stream and closes the underlying OutputStream.
	 */
	public synchronized void close() throws IOException {
		if (out == null) return;

		if (!finished) {
			try {
				writeEndMarker();
			} catch (IOException ignored) {}
		}

		try {
			out.close();
		} finally {
			out = null;
		}
	}
}
