/*
 * LZMA2InputStream
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.xz;

import roj.archive.rangecoder.RangeDecoder;
import roj.archive.xz.lz.LZDecoder;
import roj.archive.xz.lzma.LZMADecoder;
import roj.io.CorruptedInputException;
import roj.io.MBInputStream;
import roj.reflect.Unsafe;
import roj.text.logging.Logger;
import roj.util.ArrayUtil;
import roj.util.Helpers;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import static roj.reflect.Unsafe.U;

/**
 * Decompresses a raw LZMA2 stream (no XZ headers).
 */
public sealed class LZMA2InputStream extends MBInputStream {
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

	LZDecoder lz;
	RangeDecoder rc;
	LZMADecoder lzma;

	int uncompressedSize;
	private byte state;

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

		if (presetDict != null && presetDict.length > 0) state = LZMA2Encoder.PROP_RESET;
		else state = LZMA2Encoder.DICT_RESET;
	}

	public int read(byte[] buf, int off, int len) throws IOException {
		ArrayUtil.checkRange(buf, off, len);
		return read0(buf, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET+off, len);
	}
	public int read(long addr, int len) throws IOException { return read0(null, addr, len); }
	public int read0(Object buf, long addr, int len) throws IOException {
		if (in == null) throw new IOException("Stream closed");
		if (state == -1) return -1;

		int read = 0;
		try {
			while (len > 0) {
				if (uncompressedSize <= 0) {
					nextChunk();
					if (state == -1) return read == 0 ? -1 : read;
				}

				int copySizeMax = Math.min(uncompressedSize, len);

				if (state == LZMA2Encoder.STATE_LZMA) {
					onDecompress();
					lz.setLimit(copySizeMax);
					lzma.decode();
				} else {
					lz.copyUncompressed(in, copySizeMax);
				}

				int copiedSize = lz.flush0(buf, addr);
				if (addr != 0) addr += copiedSize;
				len -= copiedSize;
				read += copiedSize;
				uncompressedSize -= copiedSize;

				if (uncompressedSize == 0) if (!rc.isFinished() || lz.hasPending()) throw new CorruptedInputException("trailing compressed data");
			}

			return read;
		} catch (Throwable e) {
			return onException(buf, addr, len, e) + read;
		}
	}

	void onDecompress() {}
	int onException(Object buf, long addr, int len, Throwable e) {
		try {
			close();
		} catch (Throwable ignored) {}
		Helpers.athrow(e);
		return 0;
	}

	public static final class ErrorRecovery extends LZMA2InputStream {
		private static final Logger LOGGER = Logger.getLogger("LZMA2");

		private int lastGoodPos;

		// 如果头坏，考虑搜RangeCoder起始时0x00，若都损坏，无法程序化修复
		private int zeroedSize;
		public long brokenSize;

		public ErrorRecovery(InputStream in, int dictSize) {super(in, dictSize);}
		public ErrorRecovery(InputStream in, int dictSize, byte[] presetDict) {super(in, dictSize, presetDict);}

		@Override
		public int read0(Object buf, long addr, int len) throws IOException {
			int zeroed = 0;
			if (zeroedSize > 0) {
				zeroed = Math.min(len, zeroedSize);
				if (addr != 0) U.setMemory(buf, addr, zeroed, (byte) 0);
				len -= zeroed;
				zeroedSize -= zeroed;
			}
			return super.read0(buf, addr, len) + zeroed;
		}

		@Override
		void onDecompress() {lastGoodPos = lz.getPos();}

		@Override
		int onException(Object buf, long addr, int len, Throwable e) {
			if (!(e instanceof CorruptedInputException)) Helpers.athrow(e);

			LOGGER.info("Error '{}', discarding {} bytes", e.getMessage(), uncompressedSize);

			int goodSize = lz.errorRecovery(lastGoodPos, uncompressedSize);
			rc.errorRecovery();

			int badSize = uncompressedSize - goodSize;

			zeroedSize = badSize;
			brokenSize += badSize;

			// skip this chunk
			uncompressedSize = 0;

			return goodSize;
		}

		@Override
		public void close() throws IOException {
			super.close();
			LOGGER.info("Discarded {} bytes to recovery", brokenSize);
		}
	}

	@Override
	public long skip(long n) throws IOException {
		long remain = n;

		while (remain > 0) {
			int r = read0(null, 0, (int)Math.min(Integer.MAX_VALUE, remain));
			if (r < 0) break;
			remain -= r;
		}

		return n - remain;
	}

	@SuppressWarnings("fallthrough")
	private void nextChunk() throws IOException {
		int control = in.readUnsignedByte();
		if (control <= 0x7F) {
			switch (control) {
				default: throw new CorruptedInputException("invalid control byte");
				case 0: putArraysToCache(); state = -1; return; // End of data
				case 1: lz.reset(); break; // uncompressed with dict reset
				case 2: // uncompressed
					if (state > LZMA2Encoder.STATE_RESET) throw new CorruptedInputException("excepting dict reset");
			}

			state = LZMA2Encoder.STATE_RESET;
			uncompressedSize = in.readUnsignedShort()+1;
			return;
		}

		uncompressedSize = ((control & 0x1F) << 16) + in.readUnsignedShort() + 1;
		int cSize = in.readUnsignedShort()+1;

		switch (control >>> 5) {
			// LZMA, dict reset
			case 7: lz.reset(); readProps(); break;
			// LZMA, prop reset
			case 6:
				if (state == LZMA2Encoder.DICT_RESET) throw new CorruptedInputException("excepting dict reset");
				readProps();
			break;
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

		state = LZMA2Encoder.STATE_LZMA;
		rc.manualFill(in, cSize);
	}

	private void readProps() throws IOException {
		int props = in.readUnsignedByte();

		if (props > (4 * 5 + 4) * 9 + 8) throw new CorruptedInputException("Invalid LZMA properties byte");

		int pb = props / (9 * 5);
		props -= pb * 9 * 5;
		int lp = props / 9;
		int lc = props - lp * 9;

		if (lc + lp > 4) throw new CorruptedInputException("Invalid LZMA properties byte");

		if (lzma == null) lzma = new LZMADecoder(lz, rc, lc, lp, pb);
		else lzma.propReset(lc, lp, pb);
	}

	public int available() throws IOException {
		return in == null || state < 0 ? 0 : state== LZMA2Encoder.STATE_LZMA ? uncompressedSize : Math.min(uncompressedSize, in.available());
	}

	private synchronized void putArraysToCache() {
		if (lz != null) {
			lz.free();
			lz = null;

			rc.finish();
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