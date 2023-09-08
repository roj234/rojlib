package roj.util;

import roj.archive.qz.xz.CorruptedInputException;
import roj.io.buf.NativeArray;

import java.io.IOException;

import static roj.reflect.FieldAccessor.u;

/**
 * @author Roj234
 * @since 2023/8/2 0002 6:08
 */
public class BsDiff {
	public BsDiff() {}
	public BsDiff(BsDiff prev) { sfx = prev.sfx; }

	public void bsdiff(byte[] oldData, byte[] newData, DynByteBuf patch) {
		patch.putAscii("ENDSLEY/BSDIFF43").putIntLE(newData.length);
		initSuffix(oldData);
		bsdiff1(oldData, newData, patch);
	}

	public void bsdiff1(byte[] oldData, byte[] newData, DynByteBuf patch) {
		int scan = 0;
		int lastScan = 0;
		int pos = 0;
		int lastPos = 0;
		int len = 0;
		int lastOffset = 0;
		while (scan < newData.length) {
			int i;
			int match = 0;
			int scsc = scan += len;
			while (scan < newData.length) {
				pos = search(scan, newData, oldData, 0, oldData.length);
				len = this.len;
				while (scsc < scan + len) {
					if (scsc + lastOffset < oldData.length && oldData[scsc + lastOffset] == newData[scsc]) {
						++match;
					}
					++scsc;
				}
				if (len == match && len != 0 || len > match + 8) break;
				if (scan + lastOffset < oldData.length && oldData[scan + lastOffset] == newData[scan]) {
					--match;
				}
				++scan;
			}
			if (len == match && scan != newData.length) continue;
			int f = 0;
			int F2 = 0;
			int lenF = 0;
			int i2 = 0;
			while (i2 < scan - lastScan && i2 < oldData.length - lastPos) {
				if (newData[lastScan + i2] == oldData[lastPos + i2]) {
					++f;
				}
				if (2 * f - ++i2 <= 2 * F2 - lenF) continue;
				F2 = f;
				lenF = i2;
			}
			int b = 0;
			int B = 0;
			int lenB = 0;
			if (scan < newData.length) {
				for (int i3 = 1; i3 < scan - lastScan + 1 && i3 < pos + 1; ++i3) {
					if (newData[scan - i3] == oldData[pos - i3]) {
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
					if (oldData[lastPos + lenF - overlap + i4] == newData[lastScan + lenF - overlap + i4]) {
						++s;
					}
					if (oldData[pos - lenB + i4] == newData[scan - lenB + i4]) {
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

			NativeArray range = patch.byteRangeW(lenF);
			for (i = 0; i < lenF; ++i) range.set(i, toPositive(oldData[lastPos + i]) - toPositive(newData[lastScan + i]));

			if (overlap == -1) patch.put(newData, lastScan + lenF, scan - lastScan - lenF - lenB);

			lastPos = pos - lenB;
			lastScan = scan - lenB;
			lastOffset = pos - scan;
		}
	}

	public int bscompare(byte[] oldData, byte[] newData, int limit) {
		int diffBytes = 0;

		int scan = 0;
		int lastScan = 0;
		int pos = 0;
		int lastPos = 0;
		int len = 0;
		int lastOffset = 0;
		while (scan < newData.length) {
			int i;
			int match = 0;
			int scsc = scan += len;
			while (scan < newData.length) {
				pos = search(scan, newData, oldData, 0, oldData.length);
				len = this.len;
				while (scsc < scan + len) {
					if (scsc + lastOffset < oldData.length && oldData[scsc + lastOffset] == newData[scsc]) {
						++match;
					}
					++scsc;
				}
				if (len == match && len != 0 || len > match + 8) break;
				if (scan + lastOffset < oldData.length && oldData[scan + lastOffset] == newData[scan]) {
					--match;
				}
				++scan;
			}
			if (len == match && scan != newData.length) continue;
			int f = 0;
			int F2 = 0;
			int lenF = 0;
			int i2 = 0;
			while (i2 < scan - lastScan && i2 < oldData.length - lastPos) {
				if (newData[lastScan + i2] == oldData[lastPos + i2]) {
					++f;
				}
				if (2 * f - ++i2 <= 2 * F2 - lenF) continue;
				F2 = f;
				lenF = i2;
			}
			int b = 0;
			int B = 0;
			int lenB = 0;
			if (scan < newData.length) {
				for (int i3 = 1; i3 < scan - lastScan + 1 && i3 < pos + 1; ++i3) {
					if (newData[scan - i3] == oldData[pos - i3]) {
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
					if (oldData[lastPos + lenF - overlap + i4] == newData[lastScan + lenF - overlap + i4]) {
						++s;
					}
					if (oldData[pos - lenB + i4] == newData[scan - lenB + i4]) {
						--s;
					}
					if (s <= S) continue;
					S = s;
					lenS = i4;
				}
				lenF = lenF - overlap + lenS;
				lenB -= lenS;
			}

			for (i = 0; i < lenF; ++i) {
				if (oldData[lastPos + i] != newData[lastScan + i]) {
					diffBytes++;
				}
			}

			if (overlap == -1) diffBytes += scan - lastScan - lenF - lenB;

			if (diffBytes > limit) return -1;

			lastPos = pos - lenB;
			lastScan = scan - lenB;
			lastOffset = pos - scan;
		}

		return diffBytes;
	}

	private int[] sfx = null;

	public void initSuffix(byte[] rawData) {
		int size = rawData.length;

		int[] bucket = ArrayCache.getDefaultCache().getIntArray(256, 256);
		// count
		for (int i = 0; i < rawData.length; i++) bucket[toPositive(rawData[i])]++;
		// cumulative sum
		for (int i = 1; i < bucket.length; i++) bucket[i] += bucket[i-1];
		// move
		System.arraycopy(bucket, 0, bucket, 1, bucket.length-1);
		bucket[0] = 0;

		sfx = new int[size];
		for (int i = 0; i < size; i++) {
			int n = toPositive(rawData[i]);
			int v = bucket[n]++;
			sfx[v] = i;
		}

		for (int i = 1; i < bucket.length; ++i) {
			if (bucket[i] != bucket[i-1] + 1) continue;
			sfx[bucket[i]-1] = -1;
		}

		if (bucket[0] == 1) sfx[0] = -1;


		int[] V = ArrayCache.getDefaultCache().getIntArray(size, 0);
		for (int i = 0; i < size; ++i) V[i] = bucket[toPositive(rawData[i])] - 1;

		ArrayCache.getDefaultCache().putArray(bucket);
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

		ArrayCache.getDefaultCache().putArray(V);
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

	private static int getV(int[] V, int pos) {
		return pos < V.length ? V[pos] : -1;
	}

	private int len;
	public int search(int index, byte[] newData, byte[] oldData, int start, int end) {
		if (end - start < 2) {
			int len1 = matchLen(oldData, sfx[start], newData, index);
			if (end != start && end < sfx.length) {
				int len2 = matchLen(oldData, sfx[end], newData, index);
				if (len2 >= len1) {
					len = len2;
					return sfx[end];
				}
			}

			len = len1;
			return sfx[start];
		} else {
			int mid = (end - start) / 2 + start;
			return arrayCompare(oldData, sfx[mid], newData, index, Math.min(oldData.length - sfx[mid], newData.length - index)) < 0 ?
				search(index, newData, oldData, mid, end) :
				search(index, newData, oldData, start, mid);
		}
	}

	private static int arrayCompare(byte[] lData, int lStart, byte[] rData, int rStart, int size) {
		int i = lStart;

		for(int j = rStart; i < lStart + size; i++, j++) {
			// better: icmp -> tableswitch
			if (lData[i] < rData[j]) return -1;
			if (lData[i] > rData[j]) return 1;
		}

		return 0;
	}

	private static int matchLen(byte[] lData, int lStart, byte[] rData, int rStart) {
		int i = lStart;
		for(int j = rStart; i < lData.length && j < rData.length && lData[i] == rData[j]; i++, j++);
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
			int offset = patch.readIntLE();

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

			old.rIndex += offset;
		}

		return wrote;
	}

	// (b & 0xFF) ^ 0x80
	private static int toPositive(byte b) { return b + 128; }
	private static int toNormal(int b) { return b - 128; }
}
