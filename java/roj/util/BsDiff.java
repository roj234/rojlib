package roj.util;

import roj.io.CorruptedInputException;

import java.io.IOException;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2023/8/2 0002 6:08
 */
public class BsDiff {
	public BsDiff() {}
	public BsDiff(BsDiff prev) { sfx = prev.sfx; left = prev.left; }

	private byte[] left;
	private int[] sfx;

	public void setLeft(byte[] left) {
		this.left = left;
		int size = left.length;

		int[] bucket = ArrayCache.getIntArray(256, 256);
		// count
		for (int i = 0; i < left.length; i++) bucket[toPositive(left[i])]++;
		// cumulative sum
		for (int i = 1; i < bucket.length; i++) bucket[i] += bucket[i-1];
		// move
		System.arraycopy(bucket, 0, bucket, 1, bucket.length-1);
		bucket[0] = 0;

		sfx = new int[size];
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

	// TODO this patch format is DESIGNED to be compressed
	public void bsdiff(byte[] oldData, byte[] newData, DynByteBuf patch) {
		patch.putAscii("ENDSLEY/BSDIFF43").putIntLE(newData.length);
		setLeft(oldData);
		genPatch(newData, patch);
	}

	public void genPatch(byte[] right, DynByteBuf patch) {
		byte[] left = this.left;
		int leftLen = left.length, rightLen = right.length;

		int scan = 0, lastScan = 0;
		int pos = 0, lastPos = 0;
		int len = 0, lastOffset = 0;
		while (scan < rightLen) {
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

			patch.putIntLE(lenF)
				 .putIntLE(scan - lastScan - lenF - lenB)
				 .putIntLE(pos - lastPos - lenF - lenB);

			ArrayRef range = patch.byteRangeW(lenF);
			for (int i = 0; i < lenF; ++i) range.set(i, toPositive(left[lastPos + i]) - toPositive(right[lastScan + i]));

			if (overlap == -1) patch.put(right, lastScan + lenF, scan - lastScan - lenF - lenB);

			lastPos = pos-lenB;
			lastScan = scan-lenB;
			lastOffset = pos-scan;
		}
	}
	public int getDiffLength(byte[] right, int maxDifference) {
		int diffBytes = 0;

		byte[] left = this.left;
		int leftLen = left.length, rightLen = right.length;

		int scan = 0, lastScan = 0;
		int pos = 0, lastPos = 0;
		int len = 0, lastOffset = 0;
		while (scan < rightLen) {
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

			for (int i = 0; i < lenF; ++i) {
				if (left[lastPos+i] != right[lastScan+i]) {
					diffBytes++;
				}
			}

			if (overlap == -1) diffBytes += scan - lastScan - lenF - lenB;

			if (diffBytes > maxDifference) return -1;

			lastPos = pos-lenB;
			lastScan = scan-lenB;
			lastOffset = pos-scan;
		}

		return diffBytes;
	}

	private int len;
	private int search(byte[] right, int rightOff, byte[] left, int leftOff, int leftEnd) {
		loop:
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

			int i = sfx[mid], j = rightOff;
			int max = i + Math.min(left.length-i, right.length-rightOff);

			while (i < max) {
				if (left[i] < right[j]) {
					// 小于
					leftOff = mid;
					continue loop;
				}
				if (left[i] > right[j]) break;

				i++;
				j++;
			}

			// 大于和等于
			leftEnd = mid;
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

	public static long bspatch(DynByteBuf old, DynByteBuf patch, DynByteBuf out) throws IOException {
		if (!patch.readAscii(16).equals("ENDSLEY/BSDIFF43")) {
			throw new CorruptedInputException("header missing");
		}

		int outputSize = patch.readIntLE();
		long wrote = bspatch1(old, patch, out);
		if (wrote != outputSize) throw new IOException("patch: invalid output size");

		return wrote;
	}
	public static long bspatch1(DynByteBuf old, DynByteBuf patch, DynByteBuf out) throws IOException {
		long wrote = 0;

		while (patch.isReadable()) {
			int diffLen = patch.readIntLE();
			int extraLen = patch.readIntLE();
			int advance = patch.readIntLE();

			if (old.readableBytes() < diffLen) throw new IOException("in: no " + diffLen + " bytes readable");
			if (patch.readableBytes() < diffLen+extraLen) throw new CorruptedInputException("patch: no " + diffLen + " bytes readable");
			if (!out.ensureWritable(diffLen)) throw new IOException("out: no " + diffLen + " bytes writable");

			Object arIn = old.array();
			long adIn = old._unsafeAddr() + old.rIndex;

			Object arPat = patch.array();
			long adPat = patch._unsafeAddr() + patch.rIndex;

			Object arOut = out.array();
			long adOut = out._unsafeAddr() + out.wIndex();

			old.rIndex += diffLen;
			patch.rIndex += diffLen;
			out.wIndex(out.wIndex()+diffLen);

			wrote += diffLen;

			while (diffLen-- > 0) {
				u.putByte(arOut, adOut++,
					(byte)toNormal(
						toPositive(u.getByte(arIn, adIn++)) - u.getByte(arPat, adPat++)
								  )
						 );
			}

			out.put(patch, extraLen);
			patch.rIndex += extraLen;
			wrote += extraLen;

			old.rIndex += advance;
		}

		return wrote;
	}

	// (b & 0xFF) ^ 0x80
	private static int toPositive(byte b) { return b + 128; }
	private static int toNormal(int b) { return b - 128; }
}