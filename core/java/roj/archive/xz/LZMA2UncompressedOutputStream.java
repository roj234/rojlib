/*
 * UncompressedLZMA2OutputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.xz;

import roj.io.Finishable;
import roj.io.IOUtil;
import roj.io.MBOutputStream;
import roj.util.ArrayCache;
import roj.util.ArrayUtil;

import java.io.IOException;
import java.io.OutputStream;

import static roj.archive.xz.LZMA2Encoder.COMPRESSED_SIZE_MAX;

class LZMA2UncompressedOutputStream extends MBOutputStream implements Finishable {
	private OutputStream out;

	private final byte[] uncompBuf;
	private int uncompPos = 0;
	private boolean dictResetNeeded = true;

	private boolean finished = false;

	static int getMemoryUsage() {
		// uncompBuf + a little extra
		return 70;
	}

	LZMA2UncompressedOutputStream(OutputStream out) {
		if (out == null) throw new NullPointerException();
		this.out = out;
		uncompBuf = ArrayCache.getByteArray(COMPRESSED_SIZE_MAX, false);
	}

	@Override
	public void write(byte[] buf, int off, int len) throws IOException {
		ArrayUtil.checkRange(buf, off, len);
		if (finished) throw new IOException("Stream finished or closed");

		try {
			if (uncompPos > 0) {
				int copySize = Math.min(COMPRESSED_SIZE_MAX - uncompPos, len);
				System.arraycopy(buf, off, uncompBuf, uncompPos, copySize);
				off += copySize;
				len -= copySize;
				uncompPos += copySize;

				if (uncompPos == COMPRESSED_SIZE_MAX) writeChunk();
			}

			if (len == 0) return;

			while (len >= COMPRESSED_SIZE_MAX) {
				writeChunk(buf, off, COMPRESSED_SIZE_MAX);
				off += COMPRESSED_SIZE_MAX;
				len -= COMPRESSED_SIZE_MAX;
			}

			if (len > 0) {
				System.arraycopy(buf, off, uncompBuf, 0, len);
				uncompPos = len;
			}
		} catch (Throwable e) {
			IOUtil.closeSilently(out);
			throw e;
		}
	}

	private void writeChunk() throws IOException {
		writeChunk(uncompBuf, 0, uncompPos);
		uncompPos = 0;
	}
	private void writeChunk(byte[] uncompBuf, int off, int len) throws IOException {
		out.write(dictResetNeeded ? 0x01 : 0x02);
		int s = len-1;
		out.write(s >>> 8);
		out.write(s);
		out.write(uncompBuf, off, len);
		dictResetNeeded = false;
	}

	private void writeEndMarker() throws IOException {
		if (finished) return;
		finished = true;

		try {
			if (uncompPos > 0) writeChunk();
			out.write(0x00);
		} finally {
			ArrayCache.putArray(uncompBuf);
		}
	}

	@Override
	public void flush() throws IOException {
		if (finished) throw new IOException("Stream finished or closed");
		try {
			if (uncompPos > 0) writeChunk();
			out.flush();
		} catch (Throwable e) {
			IOUtil.closeSilently(out);
			throw e;
		}
	}

	@Override
	public synchronized void finish() throws IOException {
		if (!finished) {
			try {
				writeEndMarker();
				if (out instanceof Finishable f) {
					f.finish();
				}
			} catch (Throwable e) {
				IOUtil.closeSilently(out);
				throw e;
			}
		}
	}

	public synchronized void close() throws IOException {
		if (out != null) {
			try {
				if (!finished) {
					writeEndMarker();
				}
			} finally {
				IOUtil.closeSilently(out);
				out = null;
			}
		}
	}
}
