/*
 * LZEncoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.xz.lz;

import roj.archive.xz.LZMA2Options;
import roj.reflect.Unsafe;
import roj.util.ArrayCache;
import roj.util.NativeMemory;

import java.io.IOException;
import java.io.OutputStream;

import static roj.reflect.Unsafe.U;

public abstract sealed class LZEncoder permits BT, HashChain {
	public static final int MF_HC4 = 0, MF_BT4 = 1;
	public static final int MF_HC5 = 2, MF_BT5 = 3;

	/**
	 * Number of bytes to keep available before the current byte
	 * when moving the LZ window.
	 */
	private final int keepSizeBefore;

	/**
	 * Number of bytes that must be available, the current byte included,
	 * to make hasEnoughData return true. Flushing and finishing are
	 * naturally exceptions to this since there cannot be any data after
	 * the end of the uncompressed input.
	 */
	private final int keepSizeAfter;

	final int matchLenMax;
	final int niceLen;
	final int depthLimit;

	final long buf, base;
	private final int bufSize; // To avoid buf.length with an array-cached buf.

	public final int[] mlen, mdist;
	public int mcount;

	int readPos = -1;
	private int readLimit = -1;
	private boolean finishing;
	private int writePos;
	private int pendingSize;

	final LZHash hash;
	private final NativeMemory nm;

	final int cyclicSize;
	int cyclicPos = -1;
	int lzPos;

	static void normalize(long addr, int len, int off) {
		for (int i = 0; i < len; ++i) {
			int val = U.getInt(addr)-off;
			if (val < 0) val = 0;
			U.putInt(addr, val);

			addr += 4;
		}
	}

	/**
	 * Gets the size of the LZ window buffer that needs to be allocated.
	 */
	private static int getBufSize(int dictSize, int extraSizeBefore, int extraSizeAfter, int matchLenMax) {
		int blockSize = extraSizeBefore + extraSizeAfter + matchLenMax;
		// 这个不影响结果，但是影响压缩速度...嗯，大概是减少复制内存的次数？
		int reserveSize = Math.min(dictSize / 2 + (256 << 10), 512 << 20);
		return dictSize+blockSize+reserveSize;
	}

	public static int getMemoryUsage(LZMA2Options options, int extraSizeBefore, int extraSizeAfter, int matchLenMax) {
		int dictSize = options.getDictSize();

		long m = 0;
		m += getBufSize(dictSize, extraSizeBefore, extraSizeAfter, matchLenMax);
		m += LZHash.getMemoryUsage(dictSize); // unit is byte
		m += (options.getMatchFinder()&1) == 0 ? ((long) dictSize + 1) << 2 : ((long) dictSize + 1) << 3;
		return (int) (m / 1024) + 3;
	}

	/**
	 * Creates a new LZEncoder.
	 * <p>
	 *
	 * @param extraSizeBefore number of bytes to keep available in the
	 * history in addition to dictSize
	 * @param extraSizeAfter number of bytes that must be available
	 * after current position + matchLenMax
	 * @param matchLenMax don't test for matches longer than
	 * <code>matchLenMax</code> bytes
	 */
	public static LZEncoder getInstance(LZMA2Options options, int extraSizeBefore, int extraSizeAfter, int matchLenMax) {
		int dictSize = options.getDictSize();
		int niceLen = options.getNiceLen();
		int depthLimit = options.getDepthLimit();

		var hash5 = (options.getMatchFinder()&2) != 0;
		if ((options.getMatchFinder()&1) == 0) return new HashChain(dictSize, extraSizeBefore, extraSizeAfter, niceLen, matchLenMax, depthLimit, hash5);
		else return new BT(dictSize, extraSizeBefore, extraSizeAfter, niceLen, matchLenMax, depthLimit, hash5);
	}

	/**
	 * Creates a new LZEncoder. See <code>getInstance</code>.
	 */
	LZEncoder(int dictSize, int extraSizeBefore, int extraSizeAfter, int niceLen, int matchLenMax, int mem2Shift, int depthLimit, boolean hash5) {
		bufSize = getBufSize(dictSize, extraSizeBefore, extraSizeAfter, matchLenMax);
		nm = new NativeMemory();

		// Subtracting 1 because the shortest match that this match
		// finder can find is 2 bytes, so there's no need to reserve
		// space for one-byte matches.
		mdist = ArrayCache.getIntArray(niceLen-1, false);
		mlen = ArrayCache.getIntArray(niceLen-1, false);

		keepSizeBefore = extraSizeBefore + dictSize;
		keepSizeAfter = extraSizeAfter + matchLenMax;

		this.matchLenMax = matchLenMax;
		this.niceLen = niceLen;
		this.depthLimit = depthLimit;

		// +1 because we need dictSize bytes of history + the current byte.
		cyclicSize = dictSize + 1;
		lzPos = cyclicSize;

		buf = nm.allocate(((long) cyclicSize << mem2Shift) + bufSize);
		base = buf+bufSize;

		hash = new LZHash(dictSize, hash5);
	}

	public final void free() {
		if (nm.free()) {
			ArrayCache.putArray(mdist);
			ArrayCache.putArray(mlen);

			hash.free();
		}
	}

	public final void reset() {
		mcount = 0;
		readPos = readLimit = -1;
		finishing = false;
		writePos = pendingSize = 0;

		cyclicPos = -1;
		lzPos = cyclicSize;

		hash.reset();
	}

	/**
	 * Sets a preset dictionary. If a preset dictionary is wanted, this
	 * function must be called immediately after creating the LZEncoder
	 * before any data has been encoded.
	 */
	public final int setPresetDict(int dictSize, Object ref, long off, int len) {
		assert !isStarted();
		assert writePos == 0;

		// If the preset dictionary buffer is bigger than the dictionary
		// size, copy only the tail of the preset dictionary.
		int copySize = Math.min(len, dictSize);
		long offset = off + len - copySize;
		U.copyMemory(ref, offset, null, buf, copySize);
		writePos += copySize;
		return copySize;
	}

	/**
	 * Moves data from the end of the buffer to the beginning, discarding
	 * old data and making space for new input.
	 */
	private void moveWindow() {
		// Align the move to a multiple of 16 bytes. LZMA2 needs this
		// because it uses the lowest bits from readPos to get the
		// alignment of the uncompressed data.
		int moveOffset = (readPos + 1 - keepSizeBefore) & ~15;
		int moveSize = writePos - moveOffset;
		U.copyMemory(buf+moveOffset, buf, moveSize);

		readPos -= moveOffset;
		readLimit -= moveOffset;
		writePos -= moveOffset;
	}

	/**
	 * Copies new data into the LZEncoder's buffer.
	 */
	public final int fillWindow(Object in, long off, int len) {
		assert !finishing;

		// Move the sliding window if needed.
		if (readPos >= bufSize - keepSizeAfter) moveWindow();

		// Try to fill the dictionary buffer. If it becomes full,
		// some input bytes may be left unused.
		if (len > bufSize - writePos) len = bufSize - writePos;

		U.copyMemory(in, off, null, buf+writePos, len);
		writePos += len;

		// Set the new readLimit but only if there's enough data to allow
		// encoding of at least one more byte.
		if (writePos >= keepSizeAfter) readLimit = writePos - keepSizeAfter;

		processPendingBytes();

		// Tell the caller how much input we actually copied into
		// the dictionary.
		return len;
	}

	/**
	 * Process pending bytes remaining from preset dictionary initialization
	 * or encoder flush operation.
	 */
	private void processPendingBytes() {
		// After flushing or setting a preset dictionary there will be
		// pending data that hasn't been ran through the match finder yet.
		// Run it through the match finder now if there is enough new data
		// available (readPos < readLimit) that the encoder may encode at
		// least one more input byte. This way we don't waste any time
		// looping in the match finder (and marking the same bytes as
		// pending again) if the application provides very little new data
		// per write call.
		if (pendingSize > 0 && readPos < readLimit) {
			readPos -= pendingSize;
			int oldPendingSize = pendingSize;
			pendingSize = 0;
			skip(oldPendingSize);
			assert pendingSize <= oldPendingSize;
		}
	}

	/**
	 * Returns true if at least one byte has already been run through
	 * the match finder.
	 */
	public final boolean isStarted() { return readPos != -1; }

	/**
	 * Marks that all the input needs to be made available in
	 * the encoded output.
	 */
	public final void setFlushing() {
		readLimit = writePos - 1;
		processPendingBytes();
	}

	/**
	 * Marks that there is no more input remaining. The read position
	 * can be advanced until the end of the data.
	 */
	public final void setFinishing() {
		readLimit = writePos - 1;
		finishing = true;
		processPendingBytes();
	}

	/**
	 * Tests if there is enough input available to let the caller encode
	 * at least one more byte.
	 */
	public final boolean hasEnoughData(int alreadyReadLen) { return readPos - alreadyReadLen < readLimit; }

	public final void copyUncompressed(OutputStream out, int backward, int len) throws IOException {
		byte[] arr = ArrayCache.getByteArray(len, false);
		U.copyMemory(null, buf+readPos+1-backward, arr, Unsafe.ARRAY_BYTE_BASE_OFFSET, len);
		out.write(arr,0,len);
		ArrayCache.putArray(arr);
	}

	/**
	 * Get the number of bytes available, including the current byte.
	 * <p>
	 * Note that the result is undefined if <code>getMatches</code> or
	 * <code>skip</code> hasn't been called yet and no preset dictionary
	 * is being used.
	 */
	public final int getAvail() {
		assert isStarted();
		return writePos - readPos;
	}

	/**
	 * Gets the lowest four bits of the absolute offset of the current byte.
	 * Bits other than the lowest four are undefined.
	 */
	public final int getPos() { return readPos; }

	/**
	 * Gets the byte from the given backward offset.
	 * <p>
	 * The current byte is at <code>0</code>, the previous byte
	 * at <code>1</code> etc. To get a byte at zero-based distance,
	 * use <code>getByte(dist + 1)<code>.
	 * <p>
	 * This function is equivalent to <code>getByte(0, backward)</code>.
	 */
	public final int getByte(int backward) { return U.getByte(buf+readPos-backward)&0xFF; }
	public final int getByte(int forward, int backward) { return U.getByte(buf+readPos+forward-backward)&0xFF; }

	/**
	 * Get the length of a match at the given distance.
	 *
	 * @param dist zero-based distance of the match to test
	 * @param lenLimit don't test for a match longer than this
	 *
	 * @return length of the match; it is in the range [0, lenLimit]
	 */
	public final int getMatchLen(int dist, int lenLimit) {
		int backPos = readPos - dist - 1;
		int len = 0;

		while (len < lenLimit && U.getByte(buf+readPos+len) == U.getByte(buf+backPos+len)) ++len;

		return len;
	}

	/**
	 * Get the length of a match at the given distance and forward offset.
	 *
	 * @param forward forward offset
	 * @param dist zero-based distance of the match to test
	 * @param lenLimit don't test for a match longer than this
	 *
	 * @return length of the match; it is in the range [0, lenLimit]
	 */
	public final int getMatchLen(int forward, int dist, int lenLimit) {
		int curPos = readPos + forward;
		int backPos = curPos - dist - 1;
		int len = 0;

		while (len < lenLimit && U.getByte(buf+curPos+len) == U.getByte(buf+backPos+len)) ++len;

		return len;
	}

	public final boolean verifyMatches() {
		int lenLimit = Math.min(getAvail(), matchLenMax);

		for (int i = 0; i < mcount; ++i)
			if (getMatchLen(mdist[i], lenLimit) != mlen[i]) return false;

		return true;
	}

	/**
	 * Moves to the next byte, checks if there is enough input available,
	 * and returns the amount of input available.
	 *
	 * @param requiredForFlushing minimum number of available bytes when
	 * flushing; encoding may be continued with
	 * new input after flushing
	 * @param requiredForFinishing minimum number of available bytes when
	 * finishing; encoding must not be continued
	 * after finishing or the match finder state
	 * may be corrupt
	 *
	 * @return the number of bytes available or zero if there
	 * is not enough input available
	 */
	final int movePos(int requiredForFlushing, int requiredForFinishing) {
		assert requiredForFlushing >= requiredForFinishing;

		++readPos;
		int avail = writePos - readPos;

		if (avail < requiredForFlushing) {
			if (avail < requiredForFinishing || !finishing) {
				++pendingSize;
				avail = 0;
			}
		}

		return avail;
	}

	/**
	 * Runs match finder for the next byte and returns the matches found.
	 */
	public void match() {
		mcount = 0;

		int matchLenLimit = matchLenMax;
		int niceLenLimit = niceLen;
		int avail = advance();

		if (avail < matchLenLimit) {
			if (avail == 0) return;

			matchLenLimit = avail;
			if (niceLenLimit > avail) niceLenLimit = avail;
		}

		hash.calcHashes(buf, readPos);
		int delta2 = lzPos - hash.getHash2Pos();
		int delta3 = lzPos - hash.getHash3Pos();
		int currentMatch = hash.getHash4Pos();
		hash.updateTables(lzPos);

		int cyclicSize = Math.min(this.cyclicSize, lzPos);
		int lenBest = 0;

		// See if the hash from the first two bytes found a match.
		// The hashing algorithm guarantees that if the first byte
		// matches, also the second byte does, so there's no need to
		// test the second byte.
		if (delta2 < cyclicSize && U.getByte(buf + readPos - delta2) == U.getByte(buf + readPos)) {
			lenBest = 2;
			mlen[0] = 2;
			mdist[0] = delta2 - 1;
			mcount = 1;
		}

		// See if the hash from the first three bytes found a match that
		// is different from the match possibly found by the two-byte hash.
		// Also here the hashing algorithm guarantees that if the first byte
		// matches, also the next two bytes do.
		if (delta2 != delta3 && delta3 < cyclicSize && U.getByte(buf + readPos - delta3) == U.getByte(buf + readPos)) {
			lenBest = 3;
			mdist[mcount++] = delta3 - 1;
			delta2 = delta3;
		}

		// If a match was found, see how long it is.
		if (mcount > 0) {
			while (lenBest < matchLenLimit && U.getByte(buf + readPos + lenBest - delta2) == U.getByte(buf + readPos + lenBest)) ++lenBest;

			mlen[mcount - 1] = lenBest;

			// Return if it is long enough (niceLen or reached the end of
			// the dictionary).
			if (lenBest >= niceLenLimit) {
				skip(niceLenLimit, currentMatch);
				return;
			}
		}

		// Long enough match wasn't found so easily. Look for better matches
		// from the binary tree.
		lenBest = Math.max(lenBest, hash.size()-1);

		match(currentMatch, matchLenLimit, lenBest, niceLenLimit);
	}

	abstract int advance();
	abstract void skip(int niceLenLimit, int currentMatch);
	abstract void match(int currentMatch, int matchLenLimit, int lenBest, int niceLenLimit);

	/**
	 * Skips the given number of bytes in the match finder.
	 */
	public abstract void skip(int len);
}