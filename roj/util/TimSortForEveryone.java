package roj.util;

import roj.io.buf.BufferPool;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2023/11/6 0006 0:59
 */
public class TimSortForEveryone {
	public interface MyComparator {
		int compare(Object refLeft, long offLeft, long offRight);
	}

	public static BufferPool TimSort_BufferPool = new BufferPool(
		4096, 4096, 16777216, 1048576,
		0, 0, 0, 0,
		15, 15000);

	/**
	 * @implNote 对象数组的ArrayRef compSize固定为4，并且内存中保存的是int index
	 */
	public static void sort(int fromIndex, int toIndex, MyComparator cmp, ArrayRef... arrayRefs) {
		if (arrayRefs.length == 0) throw new IllegalArgumentException("no array to sort");
		if (arrayRefs.length == 1 && !arrayRefs[0].isObjectArray()) {
			ArrayRef op = arrayRefs[0];
			sort(fromIndex, toIndex, cmp, op.ref, op.addr, op.compSize);
			return;
		}

		int compSize = 0;
		for (ArrayRef op : arrayRefs) compSize += op.compSize;

		long halfCap = (long) (toIndex - fromIndex) * compSize;
		if (halfCap > Integer.MAX_VALUE/2) throw new IllegalArgumentException("array is too large to sort (as my implement is very naive)");

		DirectByteList temp = (DirectByteList) TimSort_BufferPool.allocate(true, (int) (halfCap << 1), 0);
		long addr = temp.address();

		try {
			int offset = 0;
			for (ArrayRef op : arrayRefs) {
				op.copyTo(fromIndex, toIndex - fromIndex, null, addr+offset, compSize);
				offset += op.compSize;
			}

			timSort(fromIndex, toIndex, cmp, null, addr, addr+halfCap, compSize);

			offset = 0;
			for (ArrayRef op : arrayRefs) {
				op.copyFrom(null, addr+offset, fromIndex, toIndex - fromIndex, compSize);
				offset += op.compSize;
			}
		} finally {
			BufferPool.reserve(temp);
		}
	}
	public static void sort(int leftIn, int rightEx, MyComparator cmp,
							Object ref, long off,
							int compSize) {
		DirectByteList temp = (DirectByteList) TimSort_BufferPool.allocate(true, compSize * (rightEx - leftIn), 0);
		try {
			timSort(leftIn, rightEx, cmp, ref, off, temp.address(), compSize);
		} finally {
			BufferPool.reserve(temp);
		}
	}

	// Iterative Timsort function to sort the array[0...n-1] (similar to merge sort)
	public static void timSort(int leftIn, int rightEx, MyComparator cmp,
							Object ref, long off,
							long addr, int compSize) {

		int minRun = minRunLength(MIN_MERGE);
		// Sort individual subarrays of size RUN
		for (int left = leftIn; left < rightEx; left += minRun) {
			insertionSort(left, Math.min((left + MIN_MERGE - 1), rightEx-1), cmp, ref, off, addr, compSize);
		}

		// Start merging from size RUN (or 32).
		// It will double next cycle
		for (int run = minRun; run < rightEx-leftIn; run <<= 1) {
			// Pick starting point of left sub array.
			// We are going to merge arr[left..left+size-1] and arr[left+size, left+2*size-1]
			// After every merge, we increase left by 2*size
			for (int left = leftIn; left < rightEx; left += run<<1) {
				// Find ending point of left sub array mid+1 is starting point of right sub array
				int mid = left + run - 1;
				int right = Math.min((left + 2 * run - 1), (rightEx - 1));

				// Merge sub array arr[left.....mid] & arr[mid+1....right]
				if (mid < right) merge(left, mid, right, cmp, ref, off, addr, compSize);
			}
		}
	}

	private static final int MIN_MERGE = 32;
	private static int minRunLength(int n) {
		assert n >= 0;

		// Becomes 1 if any 1 bit are shifted off
		int r = 0;
		while (n >= MIN_MERGE) {
			r |= (n & 1);
			n >>= 1;
		}
		return n + r;
	}

	// This function sorts array from left index to right index which is of size atmost RUN
	private static void insertionSort(int left, int right, MyComparator cmp,
									  Object ref, long off,
									  long temp, int compSize) {
		for (int i = left+1; i <= right; i++) {
			// int temp = arr[i];
			u.copyMemory(ref, off + (long)i*compSize, null, temp, compSize);

			int j = i-1;
			int val = cmp.compare(ref, off + (long)j*compSize, temp);
			if (val <= 0) continue;

			do {
				j--;
			} while (j >= left && cmp.compare(ref, off + (long)j*compSize, temp) > 0);

			j++;
			//lazy arr[j + 1] = arr[j];
			u.copyMemory(ref, off + (long)j*compSize, ref, off + (long)(j+1)*compSize, (long)(i-j)*compSize);

			//arr[j + 1] = temp;
			u.copyMemory(null, temp, ref, off + (long)j*compSize, compSize);
		}
	}

	// Merge function merges the sorted runs
	private static void merge(int l, int m, int r, MyComparator cmp,
					   Object ref, long off,
					   long work, int compSize) {
		off += (long)l*compSize;

		u.copyMemory(ref, off, null, work, compSize * (long)(r-l+1));

		long i = work;
		long j = work + (long)(m-l+1)*compSize;

		long len1 = m - l + 1, len2 = r - m;

		len1 = len1 * compSize + i;
		len2 = len2 * compSize + j;

		long prevI = i, prevJ = j, prevOff = off;
		// After comparing, we merge those two array in larger sub array
		while (i < len1 && j < len2) {
			// left[i] compareTo right[j]
			int v = cmp.compare(null, i, j);

			if (v <= 0) {
				if (prevJ != j) {
					u.copyMemory(null, prevJ, ref, prevOff, j - prevJ);
					prevJ = j;
					prevOff = off;
				}

				// (lazy) arr[k] = left[i];
				i += compSize;
			} else {
				if (prevI != i) {
					u.copyMemory(null, prevI, ref, prevOff, i - prevI);
					prevI = i;
					prevOff = off;
				}

				// (lazy) arr[k] = right[j];
				j += compSize;
			}

			off += compSize;
		}

		// Copy remaining elements, if any
		if (prevI != i) {
			u.copyMemory(null, prevI, ref, prevOff, i - prevI);
			u.copyMemory(null, j, ref, off, len2 - j);
		} else {
			u.copyMemory(null, prevJ, ref, prevOff, j - prevJ);
			u.copyMemory(null, i, ref, off, len1 - i);
		}
	}
}