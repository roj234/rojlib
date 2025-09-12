package roj.text.diff;

import roj.io.CorruptedInputException;
import roj.io.MyDataInput;
import roj.io.source.Source;
import roj.reflect.Unaligned;
import roj.reflect.litasm.FastJNI;
import roj.util.ArrayCache;
import roj.util.ArrayUtil;
import roj.util.DynByteBuf;
import roj.util.NativeArray;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2023/8/2 6:08
 */
public final class BsDiff {
	private byte[] base;
	private int[] suffixArray;
	private int alignment;

	public BsDiff() {}
	public BsDiff parallel() {
		BsDiff diff = new BsDiff();
		diff.base = base;
		diff.suffixArray = suffixArray;
		return diff;
	}

	public void setLeft(byte[] baseData) {
		this.base = baseData;
		this.suffixArray = new int[baseData.length];
		initializeSuffixArray(baseData, suffixArray, baseData.length);
	}
	public void setLeft(byte[] baseData, int offset, int length) {
		if (offset == 0 && length == baseData.length) {
			setLeft(baseData);
			return;
		}

		this.base = Arrays.copyOfRange(baseData, offset, offset+length);
		this.suffixArray = new int[length];
		initializeSuffixArray(baseData, suffixArray, length);
	}

	@FastJNI("IL_bsdiff_init")
	private static void initializeSuffixArray(final byte[] baseData, final int[] suffixArray, int size) {
		int[] frequencyBucket = ArrayCache.getIntArray(256, 256);
		// Count character frequencies
		for (int i = 0; i < baseData.length; i++) frequencyBucket[toUnsignedByte(baseData[i])]++;
		// Calculate cumulative sums
		for (int i = 1; i < frequencyBucket.length; i++) frequencyBucket[i] += frequencyBucket[i-1];
		// Shift buckets
		System.arraycopy(frequencyBucket, 0, frequencyBucket, 1, frequencyBucket.length-1);
		frequencyBucket[0] = 0;

		for (int i = 0; i < size; i++) {
			int byteValue = toUnsignedByte(baseData[i]);
			int bucketIndex = frequencyBucket[byteValue]++;
			suffixArray[bucketIndex] = i;
		}

		for (int i = 1; i < frequencyBucket.length; ++i) {
			if (frequencyBucket[i] != frequencyBucket[i-1] + 1) continue;
			suffixArray[frequencyBucket[i]-1] = -1;
		}

		if (frequencyBucket[0] == 1) suffixArray[0] = -1;

		int[] positionArray = ArrayCache.getIntArray(size, 0);
		for (int i = 0; i < size; ++i) positionArray[i] = frequencyBucket[toUnsignedByte(baseData[i])] - 1;

		ArrayCache.putArray(frequencyBucket);
		frequencyBucket = null;

		// Sort suffix array
		int stepSize = 1;
		while (suffixArray[0] != -size) {
			int currentIndex = 0, groupLength = 0;
			while (currentIndex < size) {
				if (suffixArray[currentIndex] < 0) {
					groupLength -= suffixArray[currentIndex];
					currentIndex -= suffixArray[currentIndex];
					continue;
				}

				if (groupLength > 0) suffixArray[currentIndex - groupLength] = -groupLength;

				int groupSize = positionArray[suffixArray[currentIndex]] - currentIndex + 1;
				sortSuffixGroup(suffixArray, positionArray, currentIndex, groupSize, stepSize);

				currentIndex += groupSize;
				groupLength = 0;
			}

			if (groupLength > 0) suffixArray[size - groupLength] = -groupLength;

			stepSize <<= 1;
			if (stepSize < 0) break;
		}

		for (int i = 0; i < size; ++i) suffixArray[positionArray[i]] = i;

		ArrayCache.putArray(positionArray);
	}
	private static void sortSuffixGroup(int[] suffixArray, int[] positionArray, int start, int length, int stepSize) {
		int temp;
		if (length < 16) {
			int i = start;
			int k;
			while (i < start + length) {
				int j;
				int pivotValue = POS(positionArray, suffixArray[i] + stepSize);
				k = i + 1;
				for (j = i + 1; j < start + length; ++j) {
					if (POS(positionArray, suffixArray[j] + stepSize) < pivotValue) {
						pivotValue = POS(positionArray, suffixArray[j] + stepSize);
						k = i;
					}
					if (POS(positionArray, suffixArray[j] + stepSize) != pivotValue) continue;
					temp = suffixArray[j];
					suffixArray[j] = suffixArray[k];
					suffixArray[k] = temp;
					++k;
				}
				for (j = i; j < k; ++j) {
					positionArray[suffixArray[j]] = k - 1;
				}
				if (k == i + 1) {
					suffixArray[i] = -1;
				}
				i = k;
			}
			return;
		}
		int pivotValue = POS(positionArray, suffixArray[start + length / 2] + stepSize);
		int smallerCount = 0;
		int equalCount = 0;
		for (int i = 0; i < length; ++i) {
			if (POS(positionArray, suffixArray[start + i] + stepSize) < pivotValue) {
				++smallerCount;
				continue;
			}
			if (POS(positionArray, suffixArray[start + i] + stepSize) != pivotValue) continue;
			++equalCount;
		}

		int smallerPosition = start + smallerCount;
		int equalPosition = smallerPosition + equalCount;
		int i = start;
		int j = i + smallerCount;
		int k = j + equalCount;
		while (i < smallerPosition) {
			if (POS(positionArray, suffixArray[i] + stepSize) < pivotValue) {
				++i;
				continue;
			}
			if (POS(positionArray, suffixArray[i] + stepSize) == pivotValue) {
				temp = suffixArray[i];
				suffixArray[i] = suffixArray[j];
				suffixArray[j] = temp;
				++j;
				continue;
			}
			temp = suffixArray[i];
			suffixArray[i] = suffixArray[k];
			suffixArray[k] = temp;
			++k;
		}
		while (j < equalPosition) {
			if (POS(positionArray, suffixArray[j] + stepSize) == pivotValue) {
				++j;
				continue;
			}
			temp = suffixArray[j];
			suffixArray[j] = suffixArray[k];
			suffixArray[k] = temp;
			++k;
		}

		if (smallerPosition > start) sortSuffixGroup(suffixArray, positionArray, start, smallerPosition - start, stepSize);
		for (i = smallerPosition; i < equalPosition; ++i) positionArray[suffixArray[i]] = equalPosition - 1;
		if (equalPosition == smallerPosition + 1) suffixArray[smallerPosition] = -1;

		if (equalPosition < start + length) sortSuffixGroup(suffixArray, positionArray, equalPosition, length - (equalPosition - start), stepSize);
	}
	private static int POS(int[] positionArray, int pos) { return pos < positionArray.length ? positionArray[pos] : -1; }

	public void setAlignment(int alignment) {this.alignment = alignment;}
	private int getAlignmentPadding(int value) {
		if (alignment <= 0) return 0;
		int alignMask = (1 << alignment) - 1;
		return (-value) & alignMask;
	}

	/**
	 * Generate patch data between base data and target data
	 * The patch should be compressed with algorithms like LZMA
	 */
	public void makePatch(byte[] targetData, DynByteBuf patch) {
		patch.putIntLE(targetData.length);
		var ctx = new context();
		ctx.rightLen = targetData.length;

		computeDifferences(ctx, targetData, (context, baseStart, baseEnd, targetStart, targetEnd, backwardMatchLength, forwardMatchLength) -> {
			int exactMatchLength = 0;
			for (; exactMatchLength < forwardMatchLength; exactMatchLength++) {
				if (base[baseStart+exactMatchLength] != targetData[targetStart+exactMatchLength]) break;
			}

			int diffLength = forwardMatchLength - exactMatchLength;
			int patchDataLength = targetEnd - targetStart - forwardMatchLength - backwardMatchLength;
			int skipLength = baseEnd - baseStart - forwardMatchLength - backwardMatchLength;

			int alignmentPadding = getAlignmentPadding(exactMatchLength);
			if (alignmentPadding > 0) {
				exactMatchLength -= alignmentPadding;
				diffLength += alignmentPadding;
			}

			patch.putIntLE(exactMatchLength).putIntLE(diffLength).putIntLE(patchDataLength).putIntLE(skipLength);

			NativeArray diffData = patch.byteRangeW(diffLength);
			for (int j = 0; j < diffLength; j++) {
				diffData.set(j, toUnsignedByte(base[baseStart + exactMatchLength + j]) - toUnsignedByte(targetData[targetStart + exactMatchLength + j]));
			}

			patch.put(targetData, targetStart + forwardMatchLength, patchDataLength);
			return false;
		});
	}

	public int calculateDiffLength(byte[] targetData, int maxDiff) {return calculateDiffLength(targetData, 0, targetData.length, maxDiff);}
	/**
	 * @param maxDiff Stop when this many bytes of difference are found
	 * @return Number of differing bytes found, or -1 if stopped before completion
	 */
	public int calculateDiffLength(byte[] targetData, int offset, int end, int maxDiff) {
		var ctx = new context();
		ctx.rightPos = offset;
		ctx.rightLen = end;

		computeDifferences(ctx, targetData, (context, baseStart, baseEnd, targetStart, targetEnd, backwardMatchLength, forwardMatchLength) -> {
			int diffCount = 0;
			for (int i = 0; i < forwardMatchLength+backwardMatchLength; i++) {
				if (base[baseStart+i] != targetData[targetStart+i]) diffCount++;
			}
			return (context.diffByteCount += diffCount) > maxDiff;
		});
		return ctx.diffByteCount > maxDiff ? -1 : ctx.diffByteCount;
	}

	public static final class context {
		public int rightLen;
		public int rightPos, lastRightPos, lastLeftPos, matchLength, lastOffset;
		public int diffByteCount;
	}
	public interface handler {
		boolean handle(context context, int baseStart, int baseEnd, int targetStart, int targetEnd, int backwardMatchLength, int forwardMatchLength);
	}
	public void computeDifferences(context ctx, byte[] right, handler h) {
		byte[] left = this.base;
		int leftLen = left.length, rightLen = ctx.rightLen;

		int rp = ctx.rightPos, lastrp = ctx.lastRightPos;
		int lp = 0, lastlp = ctx.lastLeftPos;
		int matchLen = ctx.matchLength, lastOffset = ctx.lastOffset;

		while (rp < rightLen) {
			int prevrp = rp; // to undo operation

			int matchCount = 0;
			int scanCounter = rp += matchLen;
			while (rp < rightLen) {
				lp = search(right, rp, left, 0, leftLen);
				matchLen = this.matchLen;
				while (scanCounter < rp + matchLen) {
					if (scanCounter + lastOffset < leftLen && left[scanCounter + lastOffset] == right[scanCounter]) {
						++matchCount;
					}
					++scanCounter;
				}
				if (matchLen == matchCount && matchLen != 0 || matchLen > matchCount + 8) break;
				if (rp + lastOffset < leftLen && left[rp + lastOffset] == right[rp]) {
					--matchCount;
				}
				++rp;
			}
			if (matchLen == matchCount && rp != rightLen) continue;

			if (alignment > 0) {
				lp -= getAlignmentPadding(lp);
				rp -= getAlignmentPadding(rp);
			}

			// find best forward match
			int forwardMatch = 0;
			{
				int scoreBest = 0;
				int scoreNow = 0, forwardMatchNow = 0;
				while (forwardMatchNow < rp - lastrp && forwardMatchNow < leftLen - lastlp) {
					if (right[lastrp + forwardMatchNow] == left[lastlp + forwardMatchNow]) scoreNow++;
					forwardMatchNow++;

					if (2 * scoreNow - forwardMatchNow > 2 * scoreBest - forwardMatch) {
						scoreBest = scoreNow;
						forwardMatch = forwardMatchNow;
					}
				}
			}

			// find best backward match
			int backwardMatch = 0;
			if (rp < rightLen) {
				int scoreBest = 0;
				int scoreNow = 0;

				// a <= b <=> a < b + 1
				for (int backwardMatchNow = 1; backwardMatchNow <= rp - lastrp && backwardMatchNow <= lp; backwardMatchNow++) {
					if (right[rp - backwardMatchNow] == left[lp - backwardMatchNow]) scoreNow++;
					if (2 * scoreNow - backwardMatchNow > 2 * scoreBest - backwardMatch) {
						scoreBest = scoreNow;
						backwardMatch = backwardMatchNow;
					}
				}
			}

			// find overlap score
			if (forwardMatch + backwardMatch > rp - lastrp) {
				int overlap = lastrp + forwardMatch - (rp - backwardMatch);
				int scoreNow = 0;
				int scoreBest = 0;
				int bestOverlap = 0;
				for (int i = 0; i < overlap; i++) {
					if (left[lastlp + forwardMatch - overlap + i] == right[lastrp + forwardMatch - overlap + i]) scoreNow++;
					if (left[lp - backwardMatch + i] == right[rp - backwardMatch + i]) scoreNow--;

					if (scoreNow > scoreBest) {
						scoreBest = scoreNow;
						bestOverlap = i;
					}
				}

				forwardMatch = forwardMatch - overlap + bestOverlap;
				backwardMatch -= bestOverlap;
			}

			// Apply alignment-aware adjustments to forwardMatch and backwardMatch
			if (alignment > 0) {
				forwardMatch -= getAlignmentPadding(forwardMatch);
				backwardMatch -= getAlignmentPadding(backwardMatch);
			}

			if (h.handle(ctx, lastlp, lp, lastrp, rp, backwardMatch, forwardMatch)) {
				rp = prevrp;
				break;
			}

			lastlp = lp-backwardMatch;
			lastrp = rp-backwardMatch;
			lastOffset = lp-rp;
		}

		ctx.rightPos = rp;
		ctx.lastRightPos = lastrp;
		ctx.lastLeftPos = lastlp;
		ctx.matchLength = matchLen;
		ctx.lastOffset = lastOffset;
	}

	private int matchLen;
	private int search(byte[] right, int rightOff, byte[] left, int leftOff, int leftEnd) {
		while (true) {
			int leftLen = leftEnd - leftOff;
			if (leftLen < 2) {
				int len1 = matchLen(left, suffixArray[leftOff], right, rightOff);
				if (leftLen > 0 && leftEnd < suffixArray.length) {
					int len2 = matchLen(left, suffixArray[leftEnd], right, rightOff);
					if (len2 >= len1) {
						matchLen = len2;
						return suffixArray[leftEnd];
					}
				}

				matchLen = len1;
				return suffixArray[leftOff];
			}

			// 二分查找 log2(n)
			int mid = leftLen/2 + leftOff;

			int i = suffixArray[mid];
			int len = Math.min(left.length-i, right.length-rightOff);
			int ret = ArrayUtil.compare(
					left, Unaligned.ARRAY_BYTE_BASE_OFFSET + i,
					right, Unaligned.ARRAY_BYTE_BASE_OFFSET + rightOff,
					len, ArrayUtil.LOG2_ARRAY_BYTE_INDEX_SCALE);

			if (ret >= 0 && left[i+ret] < right[rightOff+ret]) {
				// 小于
				leftOff = mid;
			} else {
				// 大于和等于
				leftEnd = mid;
			}
		}
	}
	private static int matchLen(byte[] lData, int lStart, byte[] rData, int rStart) {
		int i = lStart;
		while (i < lData.length && rStart < rData.length && lData[i] == rData[rStart]) {
			i++;
			rStart++;
		}
		return i - lStart;
	}

	public static long patch(Source in, MyDataInput patch, OutputStream out) throws IOException {
		int wrote = 0;
		int outputSize = patch.readIntLE();
		byte[] tmp = ArrayCache.getByteArray(1024 * 3, false);

		while (wrote < outputSize) {
			int copyLen  = patch.readIntLE(); // copy in
			int diffLen  = patch.readIntLE(); // changed
			int patchLen = patch.readIntLE(); // copy patch
			int skipLen  = patch.readIntLE(); // skip

			wrote += copyLen + diffLen + patchLen;
			if (wrote > outputSize) throw new CorruptedInputException("invalid patch");

			if (copyLen > 0) {
				if (out instanceof Source os) {
					long pos = in.position();
					os.put(in, pos, copyLen);
					in.seek(pos + copyLen);
				} else {
					while (copyLen > 0) {
						int count = Math.min(tmp.length, copyLen);

						in.readFully(tmp, 0, count);
						out.write(tmp, 0, count);

						copyLen -= count;
					}
				}
			}

			while (diffLen > 0) {
				int count = Math.min(1024, diffLen);

				in.readFully(tmp, 0, count);
				patch.readFully(tmp, 1024, count);
				for (int j = 0; j < count; j++) {
					tmp[2048 + j] = (byte)toNormal(toUnsignedByte(tmp[j]) - tmp[1024+j]);
				}

				out.write(tmp, 2048, count);
				diffLen -= count;
			}

			while (patchLen > 0) {
				int count = Math.min(tmp.length, patchLen);

				patch.readFully(tmp, 0, count);
				out.write(tmp, 0, count);

				patchLen -= count;
			}

			in.skip(skipLen); // might be negative
		}

		ArrayCache.putArray(tmp);

		if (wrote != outputSize) throw new CorruptedInputException("invalid patch");
		return wrote;
	}

	// (b & 0xFF) ^ 0x80
	private static int toUnsignedByte(byte b) { return b + 128; }
	private static int toNormal(int b) { return b - 128; }
}