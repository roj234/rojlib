package roj.archive.qz.util;

import roj.collect.IntMap;
import roj.collect.ArrayList;
import roj.collect.LRUCache;
import roj.io.source.Source;
import roj.math.MathUtils;
import roj.reflect.Unsafe;
import roj.util.ArrayCache;
import roj.util.ArrayUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * 滚动哈希块去重算法
 * @author Roj234
 * @since 2025/1/1 7:50
 */
public final class BlockHash {
	public BlockHash(int base, int window, int blockSize) {
		this.base = base;
		this.window = window;
		this.windowPower = MathUtils.pow(base, window-1);
		this.blockSize = blockSize;
	}

	private final int base, window, windowPower, blockSize;
	private final IntMap<List<Value>> hashTab = new IntMap<>();

	private static final class Value {
		final Source source;
		final long offset;
		Value(Source source, long offset) {
			this.source = source;
			this.offset = offset;
		}
	}

	public static final class Match {
		public final Source other;
		public final long otherOffset;
		public final long selfOffset;
		public int length;

		public Match(Source other, long otherOffset, long selfOffset, int length) {
			this.other = other;
			this.otherOffset = otherOffset;
			this.selfOffset = selfOffset;
			this.length = length;
		}

		@Override
		public String toString() {
			return "Match{" +
					"other=" + other +
					", otherOffset=" + otherOffset +
					", selfOffset=" + selfOffset +
					", length=" + length +
					'}';
		}
	}

	private static final boolean DeDupOnly = true;

	public List<Match> addAndFind(Source src) throws IOException {
		int window = this.window;
		int windowPower = this.windowPower;
		List<Match> matches = new ArrayList<>();

		byte[] buf = ArrayCache.getByteArray(window * 2, true);
		int hash = 0;

		long offset = src.position();
		int next = blockSize+1;
		int r;
		while (true) {
			src.seek(offset);
			r = src.read(buf, window, window);

			for (int i = 0; i < r; i++) {
				// 移除窗口中第一个字符对哈希的贡献
				hash -= windowPower * (buf[i] & 0xFF);
				// 加上新的字符
				hash = (hash * base + (buf[i + window] & 0xFF));

				List<Value> found = hashTab.getOrDefault(hash, Collections.emptyList());
				int longest = 0;
				Value longestMatch = null;
				for (int j = 0; j < found.size(); j++) {
					Value match = found.get(j);
					int sameLen = compare(match, src, offset + i);
					if (longest < sameLen) {
						longest = sameLen;
						longestMatch = match;
					}
				}
				foundSameSource:
				if (longestMatch != null) {
					found = null;
					if (DeDupOnly) {
						for (Match match : matches) {
							if (match.other == longestMatch.source) {
								match.length += longest;
								break foundSameSource;
							}
						}
					}
					matches.add(new Match(longestMatch.source, longestMatch.offset, offset+i, longest));
				}

				if (--next == 0) {
					if (found != null)
						add(src, offset + i, hash);
					next = blockSize;
				}
			}

			if (r < window) break;

			System.arraycopy(buf, window, buf, 0, window);
			offset += window;
		}

		ArrayCache.putArray(buf);
		return matches;
	}

	private int getWindow() {return window;}
	private final LRUCache<Value, byte[]> cache = new LRUCache<>(1000000);
	private final Function<Value, byte[]> loadCache = value -> {
		byte[] b = new byte[getWindow()];
		try {
			var source = value.source;

			source.reopen();
			source.seek(value.offset);
			int len = (int) Math.min(getWindow(), source.length() - value.offset);
			source.readFully(b, 0, len);
			if (len != b.length) b = Arrays.copyOf(b, len);
			//source.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return b;
	};
	private final IntFunction<List<Value>> newArrayList = x -> new ArrayList<>();

	private int compare(Value hashValue, Source bb, long offsetb) {
		byte[] bufa = cache.computeIfAbsent(hashValue, loadCache);
		byte[] bufb = cache.computeIfAbsent(new Value(bb, offsetb), loadCache);

		int readable = Math.min(bufa.length, bufb.length);
		int firstDiff = ArrayUtil.compare(bufa, Unsafe.ARRAY_BYTE_BASE_OFFSET, bufb, Unsafe.ARRAY_BYTE_BASE_OFFSET, readable, ArrayUtil.LOG2_ARRAY_BYTE_INDEX_SCALE);
		return firstDiff >= 0 ? firstDiff : readable;
	}

	private void add(Source source, long offset, int hash) {
		hashTab.computeIfAbsentI(hash, newArrayList).add(new Value(source, offset));
	}
}