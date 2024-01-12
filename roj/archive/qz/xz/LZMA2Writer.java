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

	LZMA2Writer(OutputStream out, LZMA2Options options) {
		super(options);
		if (out == null) throw new NullPointerException();

		this.out = out;

		byte[] presetDict = options.getPresetDict();
		if (presetDict != null && presetDict.length > 0) {
			lz.setPresetDict(options.getDictSize(), presetDict);
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

				int used = lz.fillWindow0(buf, off, len);
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

	public void flush() throws IOException {
		if (closed != 0) throw new IOException("Stream finished or closed");

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
	public void finish() throws IOException {
		synchronized (this) {
			if ((closed &1) != 0) return;
			closed |= 1;
		}

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
			lzma.release();
			lzma = null;
			lz = null;
			rc.release();
			rc = null;
		}

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