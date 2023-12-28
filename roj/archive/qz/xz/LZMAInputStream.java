/*
 * LZMAInputStream
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
import roj.archive.qz.xz.rangecoder.RangeDecoderFromStream;
import roj.io.CorruptedInputException;
import roj.io.MBInputStream;
import roj.util.ArrayUtil;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Decompresses legacy .lzma files and raw LZMA streams (no .lzma header).
 * <p>
 * <b>IMPORTANT:</b> 与原xz包不同的是，您只能使用PushbackInputStream来回退已读的字节
 *
 * @since 1.4
 */
public class LZMAInputStream extends MBInputStream {
	/**
	 * Largest dictionary size supported by this implementation.
	 * <p>
	 * LZMA allows dictionaries up to one byte less than 4 GiB. This
	 * implementation supports only 16 bytes less than 2 GiB. This
	 * limitation is due to Java using signed 32-bit integers for array
	 * indexing. The limitation shouldn't matter much in practice since so
	 * huge dictionaries are not normally used.
	 */
	public static final int DICT_SIZE_MAX = Integer.MAX_VALUE & ~15;

	private InputStream in;
	private LZDecoder lz;
	private RangeDecoderFromStream rc;
	private LZMADecoder lzma;

	private boolean endReached = false;
	private boolean relaxedEndCondition = false;

	/**
	 * Number of uncompressed bytes left to be decompressed, or -1 if
	 * the end marker is used.
	 */
	private long remainingSize;

	public static int getMemoryUsage(int dictSize, byte propsByte) throws UnsupportedOptionsException, CorruptedInputException {
		if (dictSize < 0 || dictSize > DICT_SIZE_MAX) throw new UnsupportedOptionsException("LZMA dictionary is too big for this implementation");

		int props = propsByte & 0xFF;
		if (props > (4 * 5 + 4) * 9 + 8) throw new CorruptedInputException("Invalid LZMA properties byte");

		props %= 9 * 5;
		int lp = props / 9;
		int lc = props - lp * 9;

		return getMemoryUsage(dictSize, lc, lp);
	}

	/**
	 * Gets approximate decompressor memory requirements as kibibytes for
	 * the given dictionary size, lc, and lp. Note that pb isn't needed.
	 *
	 * @param dictSize LZMA dictionary size as bytes, must be
	 * in the range [<code>0</code>,
	 * <code>DICT_SIZE_MAX</code>]
	 * @param lc number of literal context bits, must be
	 * in the range [0, 8]
	 * @param lp number of literal position bits, must be
	 * in the range [0, 4]
	 *
	 * @return approximate memory requirements as kibibytes (KiB)
	 */
	public static int getMemoryUsage(int dictSize, int lc, int lp) {
		if (lc < 0 || lc > 8 || lp < 0 || lp > 4) throw new IllegalArgumentException("Invalid lc or lp");

		// Probability variables have the type "short". There are
		// 0x300 (768) probability variables in each literal subcoder.
		// The number of literal subcoders is 2^(lc + lp).
		//
		// Roughly 10 KiB for the base state + LZ decoder's dictionary buffer
		// + sizeof(short) * number probability variables per literal subcoder
		//   * number of literal subcoders
		return 10 + getDictSize(dictSize) / 1024 + ((2 * 0x300) << (lc + lp)) / 1024;
	}

	static int getDictSize(int dictSize) {
		if (dictSize < 0 || dictSize > DICT_SIZE_MAX) throw new IllegalArgumentException("LZMA dictionary is too big for this implementation");

		// For performance reasons, use a 4 KiB dictionary if something
		// smaller was requested. It's a rare situation and the performance
		// difference isn't huge, and it starts to matter mostly when the
		// dictionary is just a few bytes. But we need to handle the special
		// case of dictSize == 0 anyway, which is an allowed value but in
		// practice means one-byte dictionary.
		//
		// Note that using a dictionary bigger than specified in the headers
		// can hide errors if there is a reference to data beyond the original
		// dictionary size but is still within 4 KiB.
		if (dictSize < 4096) dictSize = 4096;

		// Round dictionary size upward to a multiple of 16. This way LZMA
		// can use LZDecoder.getPos() for calculating LZMA's posMask.
		return (dictSize + 15) & ~15;
	}

	public LZMAInputStream(InputStream in) throws IOException { this(in, -1); }
	public LZMAInputStream(InputStream in, int memoryLimit) throws IOException {
		byte[] h = new byte[13];
		if (in.read(h) != 13) throw new EOFException();

		// Properties byte (lc, lp, and pb)
		byte propsByte = h[0];

		// Dictionary size is an unsigned 32-bit little endian integer.
		int dictSize = 0;
		for (int i = 0; i < 4; ++i)
			dictSize |= (h[i+1]&0xFF) << (8 * i);

		// Uncompressed size is an unsigned 64-bit little endian integer.
		// The maximum 64-bit value is a special case (becomes -1 here)
		// which indicates that the end marker is used instead of knowing
		// the uncompressed size beforehand.
		long uncompSize = 0;
		for (int i = 0; i < 8; ++i)
			uncompSize |= (long) (h[i+5]&0xFF) << (8 * i);

		// Check the memory usage limit.
		int memoryNeeded = getMemoryUsage(dictSize, propsByte);
		if (memoryLimit != -1 && memoryNeeded > memoryLimit) throw new MemoryLimitException(memoryNeeded, memoryLimit);

		initialize(in, uncompSize, propsByte, dictSize, null);
	}

	/**
	 * Creates a new input stream that decompresses raw LZMA data (no .lzma
	 * header) from <code>in</code>.
	 * <p>
	 * The caller needs to know if the "end of payload marker (EOPM)" alias
	 * "end of stream marker (EOS marker)" alias "end marker" present.
	 * If the end marker isn't used, the caller must know the exact
	 * uncompressed size of the stream.
	 * <p>
	 * The caller also needs to provide the LZMA properties byte that encodes
	 * the number of literal context bits (lc), literal position bits (lp),
	 * and position bits (pb).
	 * <p>
	 * The dictionary size used when compressing is also needed. Specifying
	 * a too small dictionary size will prevent decompressing the stream.
	 * Specifying a too big dictionary is waste of memory but decompression
	 * will work.
	 * <p>
	 * There is no need to specify a dictionary bigger than
	 * the uncompressed size of the data even if a bigger dictionary
	 * was used when compressing. If you know the uncompressed size
	 * of the data, this might allow saving some memory.
	 *
	 * @param in input stream from which compressed
	 * data is read
	 * @param uncompSize uncompressed size of the LZMA stream or -1
	 * if the end marker is used in the LZMA stream
	 * @param propsByte LZMA properties byte that has the encoded
	 * values for literal context bits (lc), literal
	 * position bits (lp), and position bits (pb)
	 * @param dictSize dictionary size as bytes, must be in the range
	 * [<code>0</code>, <code>DICT_SIZE_MAX</code>]
	 *
	 * @throws CorruptedInputException if <code>propsByte</code> is invalid or
	 * the first input byte is not 0x00
	 * @throws UnsupportedOptionsException dictionary size or uncompressed size is too
	 * big for this implementation
	 */
	public LZMAInputStream(InputStream in, long uncompSize, byte propsByte, int dictSize) throws IOException {
		initialize(in, uncompSize, propsByte, dictSize, null);
	}

	/**
	 * Creates a new input stream that decompresses raw LZMA data (no .lzma
	 * header) from <code>in</code>.
	 * <p>
	 * The caller needs to know if the "end of payload marker (EOPM)" alias
	 * "end of stream marker (EOS marker)" alias "end marker" present.
	 * If the end marker isn't used, the caller must know the exact
	 * uncompressed size of the stream.
	 * <p>
	 * The caller also needs to provide a <code>LZMA2Options</code> instance that contains
	 * the number of literal context bits (lc), literal position bits (lp),
	 * and position bits (pb).
	 * <p>
	 * The dictionary size used when compressing is also needed. Specifying
	 * a too small dictionary size will prevent decompressing the stream.
	 * Specifying a too big dictionary is waste of memory but decompression
	 * will work.
	 * <p>
	 * There is no need to specify a dictionary bigger than
	 * the uncompressed size of the data even if a bigger dictionary
	 * was used when compressing. If you know the uncompressed size
	 * of the data, this might allow saving some memory.
	 *
	 * @param in input stream from which compressed
	 * data is read
	 * @param uncompSize uncompressed size of the LZMA stream or -1
	 * if the end marker is used in the LZMA stream
	 * @param options metadata of compressed data
	 *
	 * @throws CorruptedInputException if the first input byte is not 0x00
	 * @throws UnsupportedOptionsException dictionary size or uncompressed size is too
	 * big for this implementation
	 */
	public LZMAInputStream(InputStream in, long uncompSize, LZMA2Options options) throws IOException {
		initialize(in, uncompSize, options.getLc(), options.getLp(), options.getPb(), options.getDictSize(), options.getPresetDict());
	}

	private void initialize(InputStream in, long uncompSize, byte propsByte, int dictSize, byte[] presetDict) throws IOException {
		// Validate the uncompressed size since the other "initialize" throws
		// IllegalArgumentException if uncompSize < -1.
		if (uncompSize < -1) throw new UnsupportedOptionsException("Uncompressed size is too big");

		// Decode the properties byte. In contrast to LZMA2, there is no
		// limit of lc + lp <= 4.
		int props = propsByte & 0xFF;
		if (props > (4 * 5 + 4) * 9 + 8) throw new CorruptedInputException("Invalid LZMA properties byte");

		int pb = props / (9 * 5);
		props -= pb * 9 * 5;
		int lp = props / 9;
		int lc = props - lp * 9;

		// Validate the dictionary size since the other "initialize" throws
		// IllegalArgumentException if dictSize is not supported.
		if (dictSize < 0 || dictSize > DICT_SIZE_MAX) throw new UnsupportedOptionsException("LZMA dictionary is too big for this implementation");

		initialize(in, uncompSize, lc, lp, pb, dictSize, presetDict);
	}

	private void initialize(InputStream in, long uncompSize, int lc, int lp, int pb, int dictSize, byte[] presetDict) throws IOException {
		// getDictSize validates dictSize and gives a message in
		// the exception too, so skip validating dictSize here.
		if (uncompSize < -1 || lc < 0 || lc > 8 || lp < 0 || lp > 4 || pb < 0 || pb > 4) throw new IllegalArgumentException();

		this.in = in;

		// If uncompressed size is known, use it to avoid wasting memory for
		// a uselessly large dictionary buffer.
		dictSize = getDictSize(dictSize);
		if (uncompSize >= 0 && dictSize > uncompSize) dictSize = getDictSize((int) uncompSize);

		lz = new LZDecoder(getDictSize(dictSize), presetDict);
		rc = new RangeDecoderFromStream(in);
		lzma = new LZMADecoder(lz, rc, lc, lp, pb);

		remainingSize = uncompSize;
	}

	/**
	 * Enables relaxed end-of-stream condition when uncompressed size is known.
	 * This is useful if uncompressed size is known but it is unknown if
	 * the end of stream (EOS) marker is present. After calling this function,
	 * both are allowed.
	 * <p>
	 * Note that this doesn't actually check if the EOS marker is present.
	 * This introduces a few minor downsides:
	 * <ul>
	 *   <li>Some (not all!) streams that would have more data than
	 *   the specified uncompressed size, for example due to data corruption,
	 *   will be accepted as valid.</li>
	 *   <li>After <code>read</code> has returned <code>-1</code> the
	 *   input position might not be at the end of the stream (too little
	 *   input may have been read).</li>
	 * </ul>
	 * <p>
	 * This should be called after the constructor before reading any data
	 * from the stream. This is a separate function because adding even more
	 * constructors to this class didn't look like a good alternative.
	 *
	 * @since 1.9
	 */
	public void enableRelaxedEndCondition() { relaxedEndCondition = true; }

	public int read(byte[] buf, int off, int len) throws IOException {
		ArrayUtil.checkRange(buf, off, len);
		if (len == 0) return 0;

		if (in == null) throw new IOException("Stream closed");

		if (endReached) return -1;

		try {
			int size = 0;

			while (len > 0) {
				// If uncompressed size is known and thus no end marker will
				// be present, set the limit so that the uncompressed size
				// won't be exceeded.
				int copySizeMax = len;
				if (remainingSize >= 0 && remainingSize < len) copySizeMax = (int) remainingSize;

				lz.setLimit(copySizeMax);

				// Decode into the dictionary buffer.
				try {
					lzma.decode();
				} catch (CorruptedInputException e) {
					// The end marker is encoded with a LZMA symbol that
					// indicates maximum match distance. This is larger
					// than any supported dictionary and thus causes
					// CorruptedInputException from LZDecoder.repeat.
					if (remainingSize != -1 || !lzma.endMarkerDetected()) throw e;

					endReached = true;

					// The exception makes lzma.decode() miss the last range
					// decoder normalization, so do it here. This might
					// cause an IOException if it needs to read a byte
					// from the input stream.
					rc.fill();
				}

				// Copy from the dictionary to buf.
				int copiedSize = lz.flush(buf, off);
				off += copiedSize;
				len -= copiedSize;
				size += copiedSize;

				if (remainingSize >= 0) {
					// Update the number of bytes left to be decompressed.
					remainingSize -= copiedSize;
					assert remainingSize >= 0;

					if (remainingSize == 0) endReached = true;
				}

				if (endReached) {
					// Checking these helps a lot when catching corrupt
					// or truncated .lzma files. LZMA Utils doesn't do
					// the second check and thus it accepts many invalid
					// files that this implementation and XZ Utils don't.
					if (lz.hasPending() || (!relaxedEndCondition && !rc.isFinished())) throw new CorruptedInputException("trailing compressed data");

					putArraysToCache();
					return size == 0 ? -1 : size;
				}
			}

			return size;
		} catch (Throwable e) {
			try {
				close();
			} catch (Throwable ignored) {}
			throw e;
		}
	}

	@Override
	public int available() throws IOException { return (int) Math.min(remainingSize, Integer.MAX_VALUE); }

	private synchronized void putArraysToCache() {
		if (lz != null) {
			lz.putArraysToCache();
			lz = null;
			rc.putArraysToCache();
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