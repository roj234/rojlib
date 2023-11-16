/*
 * LZMAOutputStream
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
import roj.archive.qz.xz.rangecoder.RangeEncoderToStream;
import roj.io.Finishable;
import roj.math.MathUtils;
import roj.util.ArrayUtil;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Compresses into the legacy .lzma file format or into a raw LZMA stream.
 *
 * @since 1.6
 */
public class LZMAOutputStream extends OutputStream implements Finishable {
	private OutputStream out;

	private LZEncoder lz;
	private final RangeEncoderToStream rc;
	private LZMAEncoder lzma;

	private final int props;
	private final boolean useEndMarker;
	private final long expectedUncompressedSize;
	private long processed = 0;

	private boolean finished = false;

	public LZMAOutputStream(OutputStream out, LZMA2Options options, boolean useHeader, boolean useEndMarker, long expectedUncompressedSize) throws IOException {
		if (out == null) throw new NullPointerException();

		// -1 indicates unknown and >= 0 are for known sizes.
		if (expectedUncompressedSize < -1) throw new IllegalArgumentException("Invalid expected input size (less than -1)");

		this.useEndMarker = useEndMarker;
		this.expectedUncompressedSize = expectedUncompressedSize;

		this.out = out;
		rc = new RangeEncoderToStream(out);

		int dictSize = options.getDictSize();
		if (expectedUncompressedSize >= 0 && dictSize > expectedUncompressedSize) {
			dictSize = MathUtils.getMin2PowerOf((int) expectedUncompressedSize);
		}

		lzma = LZMAEncoder.getInstance(rc, options.getLc(), options.getLp(), options.getPb(),
			options.getMode(), dictSize, 0, options.getNiceLen(), options.getMatchFinder(), options.getDepthLimit());

		lz = lzma.getLZEncoder();

		byte[] presetDict = options.getPresetDict();
		if (presetDict != null && presetDict.length > 0) {
			if (useHeader) throw new UnsupportedOptionsException("Preset dictionary cannot be used in .lzma files " +
				"(try a raw LZMA stream instead)");

			lz.setPresetDict(dictSize, presetDict);
		}

		props = options.getPropByte();

		if (useHeader) {
			// Props byte stores lc, lp, and pb.
			out.write(props);

			// Dictionary size is stored as a 32-bit unsigned little endian
			// integer.
			for (int i = 0; i < 4; ++i) {
				out.write(dictSize & 0xFF);
				dictSize >>>= 8;
			}

			// Uncompressed size is stored as a 64-bit unsigned little endian
			// integer. The max value (-1 in two's complement) indicates
			// unknown size.
			for (int i = 0; i < 8; ++i)
				out.write((int) (expectedUncompressedSize >>> (8 * i)) & 0xFF);
		}
	}

	/**
	 * Creates a new compressor for the legacy .lzma file format.
	 * <p>
	 * If the uncompressed size of the input data is known, it will be stored
	 * in the .lzma header and no end of stream marker will be used. Otherwise
	 * the header will indicate unknown uncompressed size and the end of stream
	 * marker will be used.
	 * <p>
	 * Note that a preset dictionary cannot be used in .lzma files but
	 * it can be used for raw LZMA streams.
	 *
	 * @param out output stream to which the compressed data
	 * will be written
	 * @param options LZMA compression options; the same class
	 * is used here as is for LZMA2
	 * @param inputSize uncompressed size of the data to be compressed;
	 * use <code>-1</code> when unknown
	 *
	 * @throws IOException may be thrown from <code>out</code>
	 */
	public LZMAOutputStream(OutputStream out, LZMA2Options options, long inputSize) throws IOException {
		this(out, options, true, inputSize == -1, inputSize);
	}

	/**
	 * Creates a new compressor for raw LZMA (also known as LZMA1) stream.
	 * <p>
	 * Raw LZMA streams can be encoded with or without end of stream marker.
	 * When decompressing the stream, one must know if the end marker was used
	 * and tell it to the decompressor. If the end marker wasn't used, the
	 * decompressor will also need to know the uncompressed size.
	 *
	 * @param out output stream to which the compressed data
	 * will be written
	 * @param options LZMA compression options; the same class
	 * is used here as is for LZMA2
	 * @param useEndMarker if end of stream marker should be written
	 *
	 * @throws IOException may be thrown from <code>out</code>
	 */
	public LZMAOutputStream(OutputStream out, LZMA2Options options, boolean useEndMarker) throws IOException {
		this(out, options, false, useEndMarker, -1);
	}

	/**
	 * Returns the LZMA lc/lp/pb properties encoded into a single byte.
	 * This might be useful when handling file formats other than .lzma
	 * that use the same encoding for the LZMA properties as .lzma does.
	 */
	public int getProps() { return props; }

	/**
	 * Gets the amount of uncompressed data written to the stream.
	 * This is useful when creating raw LZMA streams without
	 * the end of stream marker.
	 */
	public long getUncompressedSize() { return processed; }

	private final byte[] b1 = new byte[1];
	public void write(int b) throws IOException {
		b1[0] = (byte) b;
		write(b1, 0, 1);
	}

	public void write(byte[] buf, int off, int len) throws IOException {
		ArrayUtil.checkRange(buf, off, len);
		if (finished) throw new IOException("Stream finished or closed");

		if (expectedUncompressedSize != -1 && expectedUncompressedSize - processed < len)
			throw new IOException("Expected uncompressed size ("+expectedUncompressedSize+" bytes) was exceeded");

		processed += len;

		try {
			while (len > 0) {
				int used = lz.fillWindow(buf, off, len);
				off += used;
				len -= used;
				lzma.encodeForLZMA1();
			}
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
		finished = true;

		try {
			if (expectedUncompressedSize != -1 && expectedUncompressedSize != processed)
				throw new IOException("Expected uncompressed size ("+expectedUncompressedSize+") doesn't equal " +
					"the number of bytes written to the stream ("+processed+")");

			lz.setFinishing();
			lzma.encodeForLZMA1();

			if (useEndMarker) lzma.encodeLZMA1EndMarker();

			rc.finish();
		} catch (IOException e) {
			try {
				close();
			} catch (Throwable ignored) {}
			throw e;
		} finally {
			lzma.putArraysToCache();
			lzma = null;
			lz = null;
			rc.putArraysToCache();
		}
	}

	/**
	 * Finishes the stream and closes the underlying OutputStream.
	 */
	public synchronized void close() throws IOException {
		if (out == null) return;

		try {
			finish();
		} finally {
			if (out != null) {
				try {
					out.close();
				} finally {
					out = null;
				}
			}
		}
	}
}
