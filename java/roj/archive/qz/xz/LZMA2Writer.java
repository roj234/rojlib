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

import roj.io.Finishable;
import roj.io.UnsafeOutputStream;
import roj.util.ArrayUtil;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.AsynchronousCloseException;

public final class LZMA2Writer extends LZMA2Out implements Finishable, UnsafeOutputStream {
	private volatile byte closed;
	private boolean compressionDisabled;

	LZMA2Writer(OutputStream out, LZMA2Options options) {
		super(options);
		if (out == null) throw new NullPointerException();

		this.out = out;

		byte[] presetDict = options.getPresetDict();
		if (presetDict != null && presetDict.length > 0) {
			lzma.lzPresetDict(options.getDictSize(), presetDict);
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
		write0(buf, (long)Unsafe.ARRAY_BYTE_BASE_OFFSET+off, len);
	}
	public final void write(long off, int len) throws IOException { write0(null, off, len); }
	public final void write0(Object buf, long off, int len) throws IOException {
		if (closed != 0) throw new IOException("Stream finished or closed");

		try {
			while (len > 0) {
				if (closed != 0) throw new AsynchronousCloseException();

				int w = lzma.lzFill0(buf, off, len);
				off += w;
				len -= w;

				if (lzma.encodeForLZMA2()) writeChunk(compressionDisabled);
			}
		} catch (Throwable e) {
			try {
				close();
			} catch (Throwable ignored) {}
			throw e;
		}
	}

	public void flush() throws IOException {
		if (closed != 0) throw new IOException("Stream finished or closed");

		try {
			lzma.lzFlush();

			while (true) {
				lzma.encodeForLZMA2();
				if (lzma.getUncompressedSize() == 0) break;
				writeChunk(compressionDisabled);
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
	public void finish() throws IOException {
		synchronized (this) {
			if ((closed &1) != 0) return;
			closed |= 1;
		}

		lzma.lzFinish();

		try {
			while (true) {
				lzma.encodeForLZMA2();
				if (lzma.getUncompressedSize() == 0) break;
				writeChunk(compressionDisabled);
			}

			out.write(0x00);
		} catch (Throwable e) {
			try {
				close();
			} catch (Throwable ignored) {}
			throw e;
		} finally {
			lzma.release();
			lzma = null;
			rc.release();
			rc = null;
		}

		try {
			if (out instanceof Finishable f)
				f.finish();
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
	public void close() throws IOException {
		synchronized (this) {
			if (closed >= 2) return;
			closed |= 2;
		}

		try {
			finish();
		} finally {
			out.close();
			out = null;
		}
	}
}