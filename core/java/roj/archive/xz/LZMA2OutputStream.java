/*
 * LZMA2OutputStream
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.xz;

import roj.io.Finishable;
import roj.io.IOUtil;
import roj.io.UnsafeOutputStream;
import roj.reflect.Unsafe;
import roj.util.ArrayUtil;
import roj.util.FastFailException;

import java.io.IOException;
import java.io.OutputStream;

public final class LZMA2OutputStream extends LZMA2Encoder implements Finishable, UnsafeOutputStream {
	private volatile boolean closed;
	private boolean compressionDisabled;

	LZMA2OutputStream(OutputStream out, LZMA2Options options) {
		super(options);
		if (out == null) throw new NullPointerException();

		this.out = out;

		byte[] presetDict = options.getPresetDict();
		if (presetDict != null && presetDict.length > 0) {
			lzma.setPresetDict(options.getDictSize(), presetDict);
			state = PROP_RESET;
		} else {
			state = DICT_RESET;
		}
	}

	public final void setProps(LZMA2Options options) throws IOException {
		flush();

		if (state < PROP_RESET) state = PROP_RESET;
		props = options.getPropByte();
		lzma.propReset(options.getLc(), options.getLp(), options.getPb());
	}
	public final void setCompressionDisabled(boolean b) throws IOException {
		flush();
		this.compressionDisabled = b;
	}

	public final void write(byte[] buf, int off, int len) throws IOException {
		ArrayUtil.checkRange(buf, off, len);
		write0(buf, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET+off, len);
	}
	public final void write(long off, int len) throws IOException { write0(null, off, len); }
	public final void write0(Object buf, long off, int len) throws IOException {
		if (closed) throw new IOException("Stream finished or closed");

		try {
			while (len > 0) {
				if (closed) throw new FastFailException("Stream asynchronous closed.");

				int w = lzma.fillWindow0(buf, off, len);
				off += w;
				len -= w;

				if (lzma.encodeForLZMA2()) writeChunk(compressionDisabled);
			}
		} catch (Throwable e) {
			IOUtil.closeSilently(this);
			throw e;
		}
	}

	public void flush() throws IOException {
		if (closed) throw new IOException("Stream finished or closed");

		try {
			lzma.setFlushing();

			while (true) {
				lzma.encodeForLZMA2();
				if (lzma.getUncompressedSize() == 0) break;
				writeChunk(compressionDisabled);
			}

			out.flush();
		} catch (Throwable e) {
			IOUtil.closeSilently(this);
			throw e;
		}
	}

	/**
	 * Finishes the stream but not closes the underlying OutputStream.
	 */
	public void finish() throws IOException {
		synchronized (this) {
			if (closed) return;
			closed = true;
		}

		lzma.setFinishing();

		try {
			while (true) {
				lzma.encodeForLZMA2();
				if (lzma.getUncompressedSize() == 0) break;
				writeChunk(compressionDisabled);
			}

			out.write(0x00);
		} catch (Throwable e) {
			IOUtil.closeSilently(this);
			throw e;
		} finally {
			lzma.free();
			lzma = null;
			rc.free();
			rc = null;
		}

		if (out instanceof Finishable f) f.finish();
	}

	/**
	 * Finishes the stream and closes the underlying OutputStream.
	 */
	public void close() throws IOException {
		try {
			finish();
		} finally {
			IOUtil.closeSilently(out);
			out = null;
		}
	}
}