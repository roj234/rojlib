package roj.util;

import roj.RojLib;
import roj.io.CorruptedInputException;
import roj.io.MyDataInput;
import roj.reflect.ReflectionUtils;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.OutputStream;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2023/8/2 0002 6:08
 */
public final class BsDiff {
	public BsDiff() {impl = new JavaImpl();}

	private BsDiff(Impl prev) {impl = prev.copy(this);}
	public BsDiff parallel() {return new BsDiff(impl);}

	private Impl impl;

	public void setLeft(byte[] left) {
		if (impl instanceof JavaImpl ji) {
			ji.setLeft(left);
		} else {
			NativeImpl ni = (NativeImpl) impl;
			if (ni != null) ni.close();

			impl = new NativeImpl(this, left);
		}
	}
	public void setLeft(DynByteBuf left) {
		if (!RojLib.hasNative(RojLib.BSDIFF))
			throw new NativeException("native not ready");

		if (impl instanceof NativeImpl ni) ni.close();
		impl = new NativeImpl(this, left);
	}

	/**
	 * 你应该用LZMA之类的压缩patch
	 */
	public void makePatch(byte[] right, DynByteBuf patch) {
		patch.putIntLE(right.length);
		impl.makePatch(right, patch);
	}

	public int getDiffLength(byte[] right, int stopOn) { return getDiffLength(right, 0, right.length, stopOn); }
	/**
	 * @param stopOn 找到多少字节的差异时停止
	 * @return 找到的字节差异，或-1表示在完成前停止
	 */
	public int getDiffLength(byte[] right, int off, int end, int stopOn) {return impl.getDiffLength(right, off, end, stopOn);}

	public int getDiffLength(DynByteBuf right, int off, int end, int stopOn) {
		if (!RojLib.hasNative(RojLib.BSDIFF)) throw new NativeException("native not ready");
		return ((NativeImpl)impl).getDiffLength(right, off, end, stopOn);}

	public static long patch(DynByteBuf in, DynByteBuf patch, DynByteBuf out) throws IOException {
		int wrote = 0;
		int outputSize = patch.readIntLE();
		if (!out.ensureWritable(outputSize)) throw new IOException("failed to ensure writable ("+outputSize+")");

		Object arIn = in.array();
		long adIn = in._unsafeAddr() + in.rIndex;

		Object arPat = patch.array();
		long adPat = patch._unsafeAddr() + patch.rIndex;

		Object arOut = out.array();
		long adOut = out._unsafeAddr() + out.wIndex;
		out.wIndex += outputSize;

		while (wrote < outputSize) {
			in.rIndex = (int) (adIn - in._unsafeAddr());
			patch.rIndex = (int) (adPat - patch._unsafeAddr());

			int copyLen  = patch.readIntLE();
			int diffLen  = patch.readIntLE();
			int patchLen = patch.readIntLE();
			int skipLen  = patch.readIntLE();

			wrote += copyLen + diffLen + patchLen;
			if (wrote > outputSize) throw new CorruptedInputException("invalid patch");

			if (in.readableBytes() < copyLen+diffLen+skipLen) throw new IOException("in: no "+(copyLen+diffLen+skipLen)+" bytes readable");
			if (patch.readableBytes() < diffLen+patchLen) throw new CorruptedInputException("patch: no "+(diffLen+patchLen)+" bytes readable");

			adPat += 16;

			u.copyMemory(arIn, adIn, arOut, adOut, copyLen);
			adIn += copyLen;
			adOut += copyLen;

			while (diffLen-- > 0) {
				u.putByte(arOut, adOut++, (byte)toNormal(toPositive(u.getByte(arIn, adIn++)) - u.getByte(arPat, adPat++)));
			}

			u.copyMemory(arPat, adPat, arOut, adOut, patchLen);
			adPat += patchLen;
			adOut += patchLen;

			adIn += skipLen;
		}

		if (wrote != outputSize) throw new CorruptedInputException("invalid patch");
		return wrote;
	}
	public static long patchStream(DynByteBuf in, MyDataInput patch, OutputStream out) throws IOException {
		int wrote = 0;
		int outputSize = patch.readIntLE();
		byte[] tmp = ArrayCache.getByteArray(1024, false);

		while (wrote < outputSize) {
			int copyLen  = patch.readIntLE(); // copy in
			int diffLen  = patch.readIntLE(); // changed
			int patchLen = patch.readIntLE(); // copy patch
			int skipLen  = patch.readIntLE(); // skip

			wrote += copyLen + diffLen + patchLen;
			if (wrote > outputSize) throw new CorruptedInputException("invalid patch");

			if (copyLen > 0) {
				int www = in.wIndex;
				in.wIndex = in.rIndex + copyLen;

				in.writeToStream(out);

				in.rIndex += copyLen;
				in.wIndex = www;
			}

			int i = 0;
			while (diffLen-- > 0) {
				tmp[i] = (byte)toNormal(toPositive(in.readByte()) - patch.readByte());
				if (++i == tmp.length) {
					out.write(tmp, 0, i);
					i = 0;
				}
			}
			out.write(tmp, 0, i);

			while (patchLen > 0) {
				int toFill = Math.min(tmp.length, patchLen);
				patch.readFully(tmp, 0, toFill);
				out.write(tmp, 0, toFill);

				patchLen -= toFill;
			}

			in.rIndex += skipLen; // this can be negative
		}

		ArrayCache.putArray(tmp);

		if (wrote != outputSize) throw new CorruptedInputException("invalid patch");
		return wrote;
	}

	// (b & 0xFF) ^ 0x80
	private static int toPositive(byte b) { return b + 128; }
	private static int toNormal(int b) { return b - 128; }

	private static native long nCreate(long left, int length) throws NativeException;
	private static native long nCopy(long ptr) throws NativeException;
	private static native int nGetDiffLength(long ptr, long right, int rightLen, int maxDifference) throws NativeException;
	private static native int nGenPatch(long ptr, long right, int length, long buffer, int bufferLength) throws NativeException;
	private static native boolean nClose(long ptr);

	private sealed interface Impl {
		Impl copy(BsDiff gc);
		void makePatch(byte[] right, DynByteBuf patch);
		default void makeDiff(byte[] right, DynByteBuf patch) {throw new UnsupportedOperationException();}
		int getDiffLength(byte[] right, int off, int end, int stopOn);
	}
	private static final class NativeImpl implements Impl, Runnable {
		private static final long REFCOUNT_OFF = ReflectionUtils.fieldOffset(NativeImpl.class, "ref");

		private volatile int ref;
		private volatile boolean closePending;

		private final Object cleaner;
		private final long ptr;
		private final NativeMemory nLeft;

		NativeImpl(BsDiff gc, byte[] left) {
			int len = left.length;
			nLeft = new NativeMemory(len);
			long m = nLeft.address();

			try {
				u.copyMemory(left, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, m, len);
				ptr = nCreate(m, len);
				cleaner = NativeMemory.createCleaner(gc, this);
			} catch (Throwable e) {
				nLeft.release();
				throw e;
			}
		}
		NativeImpl(BsDiff gc, DynByteBuf left) {
			nLeft = null;
			ptr = nCreate(left.address(), left.wIndex());
			cleaner = NativeMemory.createCleaner(gc, this);
		}
		final void close() {
			closePending = true;
			checkClose(1);
		}

		@Override
		public Impl copy(BsDiff gc) {return new NativeImpl(gc, this);}
		private NativeImpl(BsDiff gc, NativeImpl from) {
			if (u.getAndAddInt(from, REFCOUNT_OFF, 1) < 0) throw new IllegalStateException("closed");

			this.ptr = nCopy(from.ptr);

			int v = u.getAndAddInt(from, REFCOUNT_OFF, -1);
			from.checkClose(v);

			this.nLeft = from.nLeft;
			cleaner = NativeMemory.createCleaner(gc, this);
		}

		@Override
		public void run() {nClose(ptr);}

		@Override
		public void makePatch(byte[] right, DynByteBuf patch) {
			if (u.getAndAddInt(this, REFCOUNT_OFF, 1) < 0) throw new IllegalStateException("closed");

			int len = right.length;
			long m = u.allocateMemory(len);

			try {
				u.copyMemory(right, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, m, len);
				nGenPatch(ptr, m, len, patch.address(), patch.unsafeWritableBytes());
			} finally {
				u.freeMemory(m);
				int v = u.getAndAddInt(this, REFCOUNT_OFF, -1);
				checkClose(v);
			}
		}

		@Override
		public int getDiffLength(byte[] right, int off, int end, int stopOn) {
			if (u.getAndAddInt(this, REFCOUNT_OFF, 1) < 0) throw new IllegalStateException("closed");

			int len = end - off;
			long m = u.allocateMemory(len);

			try {
				u.copyMemory(right, Unsafe.ARRAY_BYTE_BASE_OFFSET+off, null, m, len);
				return nGetDiffLength(ptr, m, len, stopOn);
			} finally {
				u.freeMemory(m);
				int v = u.getAndAddInt(this, REFCOUNT_OFF, -1);
				checkClose(v);
			}
		}

		//@Override
		public int getDiffLength(DynByteBuf right, int off, int end, int stopOn) {
			if (u.getAndAddInt(this, REFCOUNT_OFF, 1) < 0) throw new IllegalStateException("closed");

			int len = end - off;
			try {
				return nGetDiffLength(ptr, right.address()+off, len, stopOn);
			} finally {
				int v = u.getAndAddInt(this, REFCOUNT_OFF, -1);
				checkClose(v);
			}
		}

		private void checkClose(int v) {
			if (!closePending) return;
			if (v == 1 && u.compareAndSwapInt(this, REFCOUNT_OFF, 0, -999999)) {
				// to make Cleaner GC-able
				NativeMemory.cleanNativeMemory(cleaner);
			}
		}
	}
	private static final class JavaImpl implements Impl {
		JavaImpl() {}
		JavaImpl(JavaImpl prev) { sfx = prev.sfx; left = prev.left; }

		private byte[] left;
		private int[] sfx;

		@Override
		public Impl copy(BsDiff gc) {return new JavaImpl(this);}

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

		public void makePatch(byte[] right, DynByteBuf patch) {
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

				int i = 0;
				for (; i < lenF; i++) {
					if (left[lastPos+i] != right[lastScan+i]) break;
				}

				patch.putIntLE(i) // copyLen
					 .putIntLE(lenF-i) // diffLen
					 .putIntLE(scan - lastScan - lenF - lenB) // patchLen
					 .putIntLE(pos - lastPos - lenF - lenB); // skipLen

				ArrayRef range = patch.byteRangeW(lenF-i);
				for (int j = 0; j < lenF-i; j++) range.set(j, toPositive(left[lastPos + i + j]) - toPositive(right[lastScan + i + j]));

				if (overlap == -1) patch.put(right, lastScan + lenF, scan - lastScan - lenF - lenB);

				lastPos = pos-lenB;
				lastScan = scan-lenB;
				lastOffset = pos-scan;
			}
		}

		public int getDiffLength(byte[] right, int scan, int rightLen, int maxDifference) {
			int diffBytes = 0;

			byte[] left = this.left;
			int leftLen = left.length;

			int lastScan = scan;
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
					left, Unsafe.ARRAY_BYTE_BASE_OFFSET+i,
					right, Unsafe.ARRAY_BYTE_BASE_OFFSET+rightOff,
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
	}
}