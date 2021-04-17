/*
 * UncompressedLZMA2OutputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.qz.xz;

import roj.io.Finishable;
import roj.util.ArrayCache;

import java.io.IOException;
import java.io.OutputStream;

class UncompressedLZMA2OutputStream extends OutputStream implements Finishable {
	private final ArrayCache arrayCache;

	private OutputStream out;

	private final byte[] uncompBuf;
	private int uncompPos = 0;
	private boolean dictResetNeeded = true;

	private boolean finished = false;

	static int getMemoryUsage() {
		// uncompBuf + a little extra
		return 70;
	}

	UncompressedLZMA2OutputStream(OutputStream out, ArrayCache arrayCache) {
		if (out == null) throw new NullPointerException();

		this.out = out;

		// We only allocate one array from the cache. We will call
		// putArray directly in writeEndMarker and thus we don't use
		// ResettableArrayCache here.
		this.arrayCache = arrayCache;
		uncompBuf = arrayCache.getByteArray(LZMA2OutputStream.COMPRESSED_SIZE_MAX, false);
	}


	private byte[] b0;
	public void write(int b) throws IOException {
		if (b0 == null) b0 = new byte[1];
		b0[0] = (byte) b;
		write(b0, 0, 1);
	}

	public void write(byte[] buf, int off, int len) throws IOException {
		if (off < 0 || len < 0 || off + len < 0 || off + len > buf.length) throw new IndexOutOfBoundsException();

		if (finished) throw new IOException("Stream finished or closed");

		try {
			while (len > 0) {
				int copySize = Math.min(LZMA2OutputStream.COMPRESSED_SIZE_MAX - uncompPos, len);
				System.arraycopy(buf, off, uncompBuf, uncompPos, copySize);
				len -= copySize;
				uncompPos += copySize;

				if (uncompPos == LZMA2OutputStream.COMPRESSED_SIZE_MAX) writeChunk();
			}
		} catch (Throwable e) {
			try {
				close();
			} catch (Throwable ignored) {}
			throw e;
		}
	}

	private void writeChunk() throws IOException {
		out.write(dictResetNeeded ? 0x01 : 0x02);
		int s = uncompPos-1;
		out.write(s >>> 8);
		out.write(s);
		out.write(uncompBuf, 0, uncompPos);
		uncompPos = 0;
		dictResetNeeded = false;
	}

	private void writeEndMarker() throws IOException {
		if (finished) throw new IOException("Stream finished or closed");
		finished = true;

		try {
			if (uncompPos > 0) writeChunk();

			out.write(0x00);
		} finally {
			arrayCache.putArray(uncompBuf);
		}
	}

	public void flush() throws IOException {
		if (finished) throw new IOException("Stream finished or closed");

		try {
			if (uncompPos > 0) writeChunk();
			out.flush();
		} catch (Throwable e) {
			try {
				close();
			} catch (Throwable ignored) {}
			throw e;
		}
	}

	public synchronized void finish() throws IOException {
		if (!finished) {
			writeEndMarker();

			try {
				if (out instanceof Finishable)
					((Finishable) out).finish();
			} catch (IOException e) {
				try {
					close();
				} catch (Throwable ignored) {}
				throw e;
			}
		}
	}

	public synchronized void close() throws IOException {
		if (out != null) {
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
}
