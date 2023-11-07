package roj.util;

import roj.reflect.FieldAccessor;
import sun.misc.Unsafe;

import static roj.reflect.FieldAccessor.u;

/**
 * @author Roj234
 * @since 2023/11/6 0006 0:59
 */
public class TimSortForEveryone {
	public interface MyComparator {
		Unsafe u = FieldAccessor.u;
		int compare(Object refA, long posA, long posB);
	}

	public static void sort(Object ref, long off, int leftInclusive, int rightExclusive, int componentSize, MyComparator comparator) {
		TimSortForEveryone sort = new TimSortForEveryone();
		try {
			sort.timSort(ref, off, leftInclusive, rightExclusive, componentSize, comparator);
		} finally {
			sort.free();
		}
	}

	static final int MIN_MERGE = 32;

	private static int minRunLength(int n) {
		assert n >= 0;

		// Becomes 1 if any 1 bits are shifted off
		int r = 0;
		while (n >= MIN_MERGE) {
			r |= (n & 1);
			n >>= 1;
		}
		return n + r;
	}

	private long work, workLen;

	// This function sorts array from left index to right index which is of size atmost RUN
	private void insertionSort(Object ref, long off, int left, int right, int compSize, MyComparator cmp) {
		if (workLen < compSize) {
			work = work == 0 ? u.allocateMemory(compSize) : u.reallocateMemory(work, compSize);
			workLen = compSize;
		}

		for (int i = left + 1; i <= right; i++) {
			// int temp = arr[i];
			u.copyMemory(ref, off+i*compSize, null, work, compSize);

			int j = i - 1;
			while (j >= left && cmp.compare(ref, off+j*compSize, work) > 0) {
				//arr[j + 1] = arr[j];
				u.copyMemory(ref, off+j*compSize, ref, off+(j+1)*compSize, compSize);
				j--;
			}

			//arr[j + 1] = temp;
			u.copyMemory(null, work, ref, off+(j+1)*compSize, compSize);
		}
	}

	// Merge function merges the sorted runs
	private void merge(Object ref, long off, int l, int m, int r, int compSize, MyComparator cmp) {
		long cap = compSize * (r-l);
		if (workLen < cap) {
			work = work == 0 ? u.allocateMemory(cap) : u.reallocateMemory(work, cap);
			workLen = cap;
		}

		off += l*compSize;

		// |    left     X    |   right    |       ->   workLen
		// ^Left         ^Mid              ^Right
		u.copyMemory(ref, off, null, work, cap);

		long i = work;
		long j = work + m*compSize;

		long len1 = m - l + 1, len2 = r - m;

		len1 = len1 * compSize + i;
		len2 = len2 * compSize + j;

		// After comparing, we merge those two array in larger sub array
		while (i < len1 && j < len2) {
			// left[i] compareTo right[j]
			int v = cmp.compare(null, i, j);

			if (v <= 0) {
				// arr[k] = left[i];
				u.copyMemory(null, i, ref, off, compSize);
				i += compSize;
			}
			else {
				// arr[k] = right[j];
				u.copyMemory(null, j, ref, off, compSize);
				j += compSize;
			}

			off += compSize;
		}

		// Copy remaining elements of left, if any
		u.copyMemory(null, i, ref, off, len1 - i);

		// Copy remaining element of right, if any
		u.copyMemory(null, j, ref, off, len2 - j);
	}

	// Iterative Timsort function to sort the array[0...n-1] (similar to merge sort)
	public void timSort(Object ref, long off, int leftIn, int rightEx, int compSize, MyComparator cmp) {
		int minRun = minRunLength(MIN_MERGE);

		// Sort individual subarrays of size RUN
		for (; leftIn < rightEx; leftIn += minRun) {
			insertionSort(ref, off, leftIn, Math.min((leftIn + MIN_MERGE - 1), rightEx-1), compSize, cmp);
		}

		// Start merging from size RUN (or 32).
		// It will double next cycle
		for (int run = minRun; run < rightEx; run <<= 1) {
			// Pick starting point of left sub array.
			// We are going to merge arr[left..left+size-1] and arr[left+size, left+2*size-1]
			// After every merge, we increase left by 2*size
			for (int left = 0; left < rightEx; left += run<<1) {
				// Find ending point of left sub array mid+1 is starting point of right sub array
				int mid = left + run - 1;
				int right = Math.min((left + 2 * run - 1), (rightEx - 1));

				// Merge sub array arr[left.....mid] & arr[mid+1....right]
				if (mid < right) merge(ref, off, left, mid, right, compSize, cmp);
			}
		}
	}

	public void free() {
		if (work != 0) {
			u.freeMemory(work);
			work = workLen = 0;
		}
	}
}
