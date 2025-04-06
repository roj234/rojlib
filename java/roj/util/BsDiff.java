package roj.util;

import roj.io.CorruptedInputException;
import roj.io.MyDataInput;
import roj.io.source.Source;
import roj.reflect.Unaligned;
import roj.reflect.litasm.FastJNI;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2023/8/2 0002 6:08
 */
public final class BsDiff {
	private byte[] left;
	private int[] sfx;

	public BsDiff() {}
	public BsDiff parallel() {
		BsDiff diff = new BsDiff();
		diff.left = left;
		diff.sfx = sfx;
		return diff;
	}

	public void setLeft(byte[] left) {
		this.left = left;
		this.sfx = new int[left.length];
		implSetLeft(left, sfx, left.length);
	}
	public void setLeft(byte[] left, int off, int len) {
		if (off == 0 && len == left.length) {
			setLeft(left);
			return;
		}

		this.left = Arrays.copyOfRange(left, off, off+len);
		this.sfx = new int[len];
		implSetLeft(left, sfx, len);
	}
	@FastJNI("IL_bsdiff_init")
	private static void implSetLeft(final byte[] left, final int[] sfx, int size) {
		int[] bucket = ArrayCache.getIntArray(256, 256);
		// count
		for (int i = 0; i < left.length; i++) bucket[toPositive(left[i])]++;
		// cumulative sum
		for (int i = 1; i < bucket.length; i++) bucket[i] += bucket[i-1];
		// move
		System.arraycopy(bucket, 0, bucket, 1, bucket.length-1);
		bucket[0] = 0;

		for (int i = 0; i < size; i++) {
			int n = toPositive(left[i]);
			int v = bucket[n]++;
			sfx[v] = i;
		}

		for (int i = 1; i < bucket.length; ++i) {
			if (bucket[i] != bucket[i-1] + 1) continue;
			sfx[bucket[i]-1] = -1;
		}

		if (bucket[0] == 1) sfx[0] = -1;


		int[] V = ArrayCache.getIntArray(size, 0);
		for (int i = 0; i < size; ++i) V[i] = bucket[toPositive(left[i])] - 1;

		ArrayCache.putArray(bucket);
		bucket = null;

		// sort, 也许不是最优结果了？
		int h = 1;
		while (sfx[0] != -size) {
			int j = 0, len = 0;
			while (j < size) {
				if (sfx[j] < 0) {
					len -= sfx[j];
					j -= sfx[j];
					continue;
				}

				if (len > 0) sfx[j - len] = -len;

				int groupLen = V[sfx[j]] - j + 1;
				split(sfx, V, j, groupLen, h);

				j += groupLen;
				len = 0;
			}

			if (len > 0) sfx[size - len] = -len;

			h <<= 1;
			if (h < 0) break;
		}

		for (int i = 0; i < size; ++i) sfx[V[i]] = i;

		ArrayCache.putArray(V);
	}
	private static void split(int[] I, int[] V, int start, int len, int h) {
		int temp;
		if (len < 16) {
			int i = start;
			int k;
			while (i < start + len) {
				int j;
				int X = getV(V, I[i] + h);
				k = i + 1;
				for (j = i + 1; j < start + len; ++j) {
					if (getV(V, I[j] + h) < X) {
						X = getV(V, I[j] + h);
						k = i;
					}
					if (getV(V, I[j] + h) != X) continue;
					temp = I[j];
					I[j] = I[k];
					I[k] = temp;
					++k;
				}
				for (j = i; j < k; ++j) {
					V[I[j]] = k - 1;
				}
				if (k == i + 1) {
					I[i] = -1;
				}
				i = k;
			}
			return;
		}
		int X = getV(V, I[start + len / 2] + h);
		int smallCount = 0;
		int equalCount = 0;
		for (int i = 0; i < len; ++i) {
			if (getV(V, I[start + i] + h) < X) {
				++smallCount;
				continue;
			}
			if (getV(V, I[start + i] + h) != X) continue;
			++equalCount;
		}

		int smallPos = start + smallCount;
		int equalPos = smallPos + equalCount;
		int i = start;
		int j = i + smallCount;
		int k = j + equalCount;
		while (i < smallPos) {
			if (getV(V, I[i] + h) < X) {
				++i;
				continue;
			}
			if (getV(V, I[i] + h) == X) {
				temp = I[i];
				I[i] = I[j];
				I[j] = temp;
				++j;
				continue;
			}
			temp = I[i];
			I[i] = I[k];
			I[k] = temp;
			++k;
		}
		while (j < equalPos) {
			if (getV(V, I[j] + h) == X) {
				++j;
				continue;
			}
			temp = I[j];
			I[j] = I[k];
			I[k] = temp;
			++k;
		}

		if (smallPos > start) split(I, V, start, smallPos - start, h);
		for (i = smallPos; i < equalPos; ++i) V[I[i]] = equalPos - 1;
		if (equalPos == smallPos + 1) I[smallPos] = -1;

		if (equalPos < start + len) split(I, V, equalPos, len - (equalPos - start), h);
	}
	private static int getV(int[] V, int pos) { return pos < V.length ? V[pos] : -1; }

	/**
	 * 你应该用LZMA之类的压缩patch
	 */
	public void makePatch(byte[] right, DynByteBuf patch) {
		patch.putIntLE(right.length);
		var ctx1 = new context();
		ctx1.rightLen = right.length;

		_implDiff(ctx1, right, (ctx, scan, lastScan, pos, lastPos, lenB, lenF, overlap) -> {
			int i = 0;
			for (; i < lenF; i++) {
				if (left[lastPos+i] != right[lastScan+i]) break;
			}

			patch.putIntLE(i) // copyLen
				 .putIntLE(lenF-i) // diffLen
				 .putIntLE(scan - lastScan - lenF - lenB) // patchLen
				 .putIntLE(pos - lastPos - lenF - lenB); // skipLen

			NativeArray range = patch.byteRangeW(lenF-i);
			for (int j = 0; j < lenF-i; j++) range.set(j, toPositive(left[lastPos + i + j]) - toPositive(right[lastScan + i + j]));

			if (overlap == -1) patch.put(right, lastScan + lenF, scan - lastScan - lenF - lenB);
			return false;
		});
	}

	public int getDiffLength(byte[] right, int stopOn) {return getDiffLength(right, 0, right.length, stopOn);}
	/**
	 * @param stopOn 找到多少字节的差异时停止
	 * @return 找到的字节差异，或-1表示在完成前停止
	 */
	public int getDiffLength(byte[] right, int off, int end, int stopOn) {
		var ctx1 = new context();
		ctx1.scan = off;
		ctx1.rightLen = end;

		_implDiff(ctx1, right, (ctx, scan1, lastScan, pos, lastPos, lenB, lenF, overlap) -> {
			for (int i = 0; i < lenF; ++i) {
				if (left[lastPos+i] != right[lastScan+i]) {
					ctx._diffBytes++;
				}
			}

			if (overlap == -1) ctx._diffBytes += scan1 - lastScan - lenF - lenB;
			return ctx._diffBytes > stopOn;
		});
		return ctx1._diffBytes > stopOn ? -1 : ctx1._diffBytes;
	}

	public static final class context {
		public int rightLen;
		public int scan, lastScan, lastPos, len, lastOffset;
		public int _diffBytes;
	}
	public interface handle {
		boolean run(context context, int scan, int lastScan, int pos, int lastPos, int lenB, int lenF, int overlap);
	}
	public void _implDiff(context ctx, byte[] right, handle fn) {
		byte[] left = this.left;
		int leftLen = left.length, rightLen = ctx.rightLen;

		int scan = ctx.scan, lastScan = ctx.lastScan;
		int pos = 0, lastPos = ctx.lastPos;
		int len = ctx.len, lastOffset = ctx.lastOffset;
		int prevScan;
		while (scan < rightLen) {
			prevScan = scan;

			int match = 0;
			int scsc = scan += len;
			while (scan < rightLen) {
				pos = search(right, scan, left, 0, leftLen);
				len = this.len;
				while (scsc < scan + len) {
					if (scsc + lastOffset < leftLen && left[scsc + lastOffset] == right[scsc]) {
						++match;
					}
					++scsc;
				}
				if (len == match && len != 0 || len > match + 8) break;
				if (scan + lastOffset < leftLen && left[scan + lastOffset] == right[scan]) {
					--match;
				}
				++scan;
			}
			if (len == match && scan != rightLen) continue;
			int f = 0;
			int F2 = 0;
			int lenF = 0;
			int i2 = 0;
			while (i2 < scan - lastScan && i2 < leftLen - lastPos) {
				if (right[lastScan + i2] == left[lastPos + i2]) {
					++f;
				}
				if (2 * f - ++i2 <= 2 * F2 - lenF) continue;
				F2 = f;
				lenF = i2;
			}
			int b = 0;
			int B = 0;
			int lenB = 0;
			if (scan < rightLen) {
				for (int i3 = 1; i3 < scan - lastScan + 1 && i3 < pos + 1; ++i3) {
					if (right[scan - i3] == left[pos - i3]) {
						++b;
					}
					if (2 * b - i3 <= 2 * B - lenB) continue;
					B = b;
					lenB = i3;
				}
			}
			int overlap = -1;
			if (lenF + lenB > scan - lastScan) {
				overlap = lastScan + lenF - (scan - lenB);
				int s = 0;
				int S = 0;
				int lenS = 0;
				for (int i4 = 0; i4 < overlap; ++i4) {
					if (left[lastPos + lenF - overlap + i4] == right[lastScan + lenF - overlap + i4]) {
						++s;
					}
					if (left[pos - lenB + i4] == right[scan - lenB + i4]) {
						--s;
					}
					if (s <= S) continue;
					S = s;
					lenS = i4;
				}
				lenF = lenF - overlap + lenS;
				lenB -= lenS;
			}

			if (fn.run(ctx, scan, lastScan, pos, lastPos, lenB, lenF, overlap)) {
				scan = prevScan;
				break;
			}

			lastPos = pos-lenB;
			lastScan = scan-lenB;
			lastOffset = pos-scan;
		}

		ctx.scan = scan;
		ctx.lastScan = lastScan;
		ctx.lastPos = lastPos;
		ctx.len = len;
		ctx.lastOffset = lastOffset;
	}
	private int len;
	private int search(byte[] right, int rightOff, byte[] left, int leftOff, int leftEnd) {
		while (true) {
			int leftLen = leftEnd - leftOff;
			if (leftLen < 2) {
				int len1 = matchLen(left, sfx[leftOff], right, rightOff);
				if (leftLen > 0 && leftEnd < sfx.length) {
					int len2 = matchLen(left, sfx[leftEnd], right, rightOff);
					if (len2 >= len1) {
						len = len2;
						return sfx[leftEnd];
					}
				}

				len = len1;
				return sfx[leftOff];
			}

			// 二分查找 log2(n)
			int mid = leftLen/2 + leftOff;

			int i = sfx[mid];
			int len = Math.min(left.length-i, right.length-rightOff);
			int ret = ArrayUtil.vectorizedMismatch(
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
					tmp[2048 + j] = (byte)toNormal(toPositive(tmp[j]) - tmp[1024+j]);
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
	private static int toPositive(byte b) { return b + 128; }
	private static int toNormal(int b) { return b - 128; }
}