package ilib.asm.rpl;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author solo6975
 * @since 2022/5/22 3:24
 */
public class FastIntCache {
	private static final ThreadLocal<FastIntCache> local = ThreadLocal.withInitial(FastIntCache::new);

	private int intCacheSize = 256;
	private final List<int[]> freeSmallArrays = Lists.newArrayList();
	private final List<int[]> inUseSmallArrays = Lists.newArrayList();
	private final List<int[]> freeLargeArrays = Lists.newArrayList();
	private final List<int[]> inUseLargeArrays = Lists.newArrayList();

	private static final LongAdder sumFree = new LongAdder(), sumInUse = new LongAdder(), sumFreeLarg = new LongAdder(), sumInUseLarg = new LongAdder();

	public static int[] func_76445_a(int size) {
		return local.get().allocate(size);
	}

	private int[] allocate(int size) {
		int[] arr;
		if (size <= 256) {
			if (freeSmallArrays.isEmpty()) {
				arr = new int[256];
			} else {
				sumFree.decrement();
				arr = freeSmallArrays.remove(freeSmallArrays.size() - 1);
			}
			sumInUse.increment();
			inUseSmallArrays.add(arr);
			return arr;
		} else if (size > intCacheSize) {
			sumFreeLarg.add(-freeLargeArrays.size());
			sumInUseLarg.add(-inUseLargeArrays.size() - 1);

			intCacheSize = size;
			freeLargeArrays.clear();
			inUseLargeArrays.clear();
			arr = new int[intCacheSize];
			inUseLargeArrays.add(arr);
			return arr;
		} else if (freeLargeArrays.isEmpty()) {
			arr = new int[intCacheSize];
			inUseLargeArrays.add(arr);
			sumInUseLarg.increment();
			return arr;
		} else {
			sumFreeLarg.decrement();
			sumInUseLarg.increment();
			arr = freeLargeArrays.remove(freeLargeArrays.size() - 1);
			inUseLargeArrays.add(arr);
			return arr;
		}
	}

	public static void func_76446_a() {
		local.get().freeAll();
	}

	private void freeAll() {
		if (!freeLargeArrays.isEmpty()) {
			sumFreeLarg.decrement();
			freeLargeArrays.remove(freeLargeArrays.size() - 1);
		}

		if (!freeSmallArrays.isEmpty()) {
			sumFree.decrement();
			freeSmallArrays.remove(freeSmallArrays.size() - 1);
		}

		sumFree.add(inUseSmallArrays.size());
		sumFreeLarg.add(inUseLargeArrays.size());
		sumInUse.add(-inUseSmallArrays.size());
		sumInUseLarg.add(-inUseLargeArrays.size());

		freeLargeArrays.addAll(inUseLargeArrays);
		freeSmallArrays.addAll(inUseSmallArrays);
		inUseLargeArrays.clear();
		inUseSmallArrays.clear();
	}

	public static String func_85144_b() {
		long freeLg = sumFreeLarg.sum();
		long freeSm = sumFree.sum();
		long usedSm = sumInUse.sum();
		long usedLg = sumInUseLarg.sum();
		return "大缓冲: " + freeLg + "/" + (usedLg + freeLg) + ", 小缓冲: " + freeSm + "/" + (usedSm + freeSm);
	}

}
