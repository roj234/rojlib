/*
 * LZMA2InputStream
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.qz.xz;

import roj.archive.qz.xz.lz.LZDecoder;
import roj.archive.qz.xz.lzma.LZMADecoder;
import roj.archive.qz.xz.rangecoder.RangeDecoder;
import roj.io.CorruptedInputException;
import roj.util.ArrayUtil;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Decompresses a raw LZMA2 stream (no XZ headers).
 */
public class LZMA2InputStream extends InputStream {
	/**
	 * Smallest valid LZMA2 dictionary size.
	 * <p>
	 * Very tiny dictionaries would be a performance problem, so
	 * the minimum is 4 KiB.
	 */
	public static final int DICT_SIZE_MIN = 4096;
	/**
	 * Largest dictionary size supported by this implementation.
	 * <p>
	 * The LZMA2 algorithm allows dictionaries up to one byte less than 4 GiB.
	 * This implementation supports only 16 bytes less than 2 GiB for raw
	 * LZMA2 streams, and for .xz files the maximum is 1.5 GiB. This
	 * limitation is due to Java using signed 32-bit integers for array
	 * indexing. The limitation shouldn't matter much in practice since so
	 * huge dictionaries are not normally used.
	 */
	public static final int DICT_SIZE_MAX = Integer.MAX_VALUE & ~15;

	private static final int COMPRESSED_SIZE_MAX = 1 << 16;

	private DataInputStream in;

	private LZDecoder lz;
	private RangeDecoder rc;
	private LZMADecoder lzma;

	private int uncompressedSize = -1;
	private int state;

	/**
	 * @return approximate memory requirements as kibibytes (KiB)
	 */
	public static int getMemoryUsage(int dictSize) {
		// The base state is around 30-40 KiB (probabilities etc.),
		// range decoder needs COMPRESSED_SIZE_MAX bytes for buffering,
		// and LZ decoder needs a dictionary buffer.
		return 40 + (COMPRESSED_SIZE_MAX + getDictSize(dictSize)) / 1024;
	}

	private static int getDictSize(int dictSize) {
		if (dictSize < DICT_SIZE_MIN || dictSize > DICT_SIZE_MAX) throw new IllegalArgumentException("Unsupported dictionary size " + dictSize);

		// Round dictionary size upward to a multiple of 16. This way LZMA
		// can use LZDecoder.getPos() for calculating LZMA's posMask.
		// Note that this check is needed only for raw LZMA2 streams; it is
		// redundant with .xz.
		return (dictSize + 15) & ~15;
	}

	/**
	 * Creates a new input stream that decompresses raw LZMA2 data
	 * from <code>in</code>.
	 * <p>
	 * The caller needs to know the dictionary size used when compressing;
	 * the dictionary size isn't stored as part of a raw LZMA2 stream.
	 * <p>
	 * Specifying a too small dictionary size will prevent decompressing
	 * the stream. Specifying a too big dictionary is waste of memory but
	 * decompression will work.
	 * <p>
	 * There is no need to specify a dictionary bigger than
	 * the uncompressed size of the data even if a bigger dictionary
	 * was used when compressing. If you know the uncompressed size
	 * of the data, this might allow saving some memory.
	 *
	 * @param in input stream from which LZMA2-compressed
	 * data is read
	 * @param dictSize LZMA2 dictionary size as bytes, must be
	 * in the range [<code>DICT_SIZE_MIN</code>,
	 * <code>DICT_SIZE_MAX</code>]
	 */
	public LZMA2InputStream(InputStream in, int dictSize) { this(in, dictSize, null); }
	public LZMA2InputStream(InputStream in, int dictSize, byte[] presetDict) {
		// Check for null because otherwise null isn't detect
		// in this constructor.
		if (in == null) throw new NullPointerException();

		this.in = new DataInputStream(in);
		this.rc = new RangeDecoder(COMPRESSED_SIZE_MAX);
		this.lz = new LZDecoder(getDictSize(dictSize), presetDict);

		if (presetDict != null && presetDict.length > 0) state = 0x11000000;
		else state = 0x11100000;
	}

	private byte[] b0;
	public int read() throws IOException {
		if (b0 == null) b0 = new byte[1];
		return read(b0, 0, 1) < 0 ? -1 : (b0[0] & 0xFF);
	}

	public int read(byte[] buf, int off, int len) throws IOException {
		ArrayUtil.checkRange(buf, off, len);
		if (len == 0) return 0;

		if (in == null) throw new IOException("Stream closed");

		if (state == 0) return -1;

		try {
			int read = 0;

			while (len > 0) {
				if (uncompressedSize <= 0) {
					nextChunk();
					if (state == 0) return read == 0 ? -1 : read;
				}

				int copySizeMax = Math.min(uncompressedSize, len);

				if ((state&0x80) == 0) {
					lz.copyUncompressed(in, copySizeMax);
				} else {
					lz.setLimit(copySizeMax);
					lzma.decode();
				}

				int copiedSize = lz.flush(buf, off);
				off += copiedSize;
				len -= copiedSize;
				read += copiedSize;
				uncompressedSize -= copiedSize;

				if (uncompressedSize == 0) if (!rc.isFinished() || lz.hasPending()) throw new CorruptedInputException("trailing compressed data");
			}

			return read;
		} catch (Throwable e) {
			try {
				close();
			} catch (Throwable ignored) {}
			throw e;
		}
	}

	@SuppressWarnings("fallthrough")
	private void nextChunk() throws IOException {
		int control = in.readUnsignedByte();
		state = control;

		if (control <= 0x7F) {
			switch (control) {
				default: throw new CorruptedInputException("invalid control byte");
				case 0: putArraysToCache(); return; // End of data
				case 1: uncompressedSize = 0; // uncompressed with dict reset
				case 2: // uncompressed
					if (uncompressedSize < 0) throw new CorruptedInputException("第一个块若是非压缩，则头必须是0x01");
					uncompressedSize = in.readUnsignedShort()+1;
				return;
			}
		}

		uncompressedSize = ((control & 0x1F) << 16) + in.readUnsignedShort() + 1;
		int cSize = in.readUnsignedShort()+1;

		switch (control >>> 5) {
			// LZMA, dict reset
			case 7: lz.reset();
			// LZMA, prop reset
			case 6: readProps(); break;
			// LZMA, state reset
			case 5:
				if (lzma == null) throw new CorruptedInputException("unexpected state reset");
				lzma.reset();
			break;
			// LZMA
			case 4:
				if (lzma == null) throw new CorruptedInputException("unexpected LZMA state");
			break;
		}

		rc.lzma2_manualFill(in, cSize);
	}

	private void readProps() throws IOException {
		int props = in.readUnsignedByte();

		if (props > (4 * 5 + 4) * 9 + 8) throw new CorruptedInputException("Invalid LZMA properties byte");

		int pb = props / (9 * 5);
		props -= pb * 9 * 5;
		int lp = props / 9;
		int lc = props - lp * 9;

		if (lc + lp > 4) throw new CorruptedInputException("Invalid LZMA properties byte");

		lzma = new LZMADecoder(lz, rc, lc, lp, pb);
	}

	public int available() throws IOException {
		if (in == null) throw new IOException("Stream closed");
		return (state&0x80) != 0 ? uncompressedSize : Math.min(uncompressedSize, in.available());
	}

	private synchronized void putArraysToCache() {
		if (lz != null) {
			lz.putArraysToCache();
			lz = null;

			rc.putArraysToCache();
			rc = null;
		}
	}

	public synchronized void close() throws IOException {
		putArraysToCache();
		if (in != null) {
			try {
				in.close();
			} finally {
				in = null;
			}
		}
	}
}
