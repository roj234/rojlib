package roj.io;

import roj.collect.IntMap;
import roj.collect.ArrayList;
import roj.io.source.Source;
import roj.math.MathUtils;
import roj.reflect.Unaligned;
import roj.util.ArrayCache;
import roj.util.ArrayUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * 滚动哈希块去重算法
 * @author Roj234
 * @since 2025/1/1 7:50
 */
public class RollingHashDedup {
	public RollingHashDedup(int base, int window, int blockSize) {
		this.base = base;
		this.window = window;
		this.windowPower = MathUtils.pow(base, window-1);
		this.blockSize = blockSize;
	}

	private final int base, window, windowPower, blockSize;
	private List<Source> sources = new ArrayList<>();
	private IntMap<List<Tab>> hashTab = new IntMap<>();

	static final class Tab {
		Source source;
		long offset;

		public Tab(Source source, long offset) {
			this.source = source;
			this.offset = offset;
		}

		public Source getSource() {return source;}
		public long getOffset() {return offset;}
	}
	public static final class Match {
		public final Source other;
		public final long otherOffset;
		public final long selfOffset;
		public final int length;

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

	public List<Match> deduplicate(Source src) throws IOException {
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

				List<Tab> found = hashTab.getOrDefault(hash, Collections.emptyList());
				for (int j = 0; j < found.size(); j++) {
					Tab match = found.get(j);
					if (compareBytes(match.source, match.offset, src, offset+i, window)) {
						matches.add(new Match(match.source, match.offset, offset+i, window));
						found = null;
						break;
					}
				}

				if (--next == 0) {
					if (found != null)
						addHash(src, offset + i, hash);
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

	private boolean compareBytes(Source aa, long offseta, Source bb, long offsetb, int length) {
		int bufSize = 4096;
		var bufa = ArrayCache.getByteArray(bufSize, false);
		var bufb = ArrayCache.getByteArray(bufSize, false);

		try {
			aa.seek(offseta);
			bb.seek(offsetb);

			while (length > 0) {
				int readable = Math.min(length, bufSize);
				aa.readFully(bufa, 0, readable);
				bb.readFully(bufb, 0, readable);
				int i = ArrayUtil.compare(bufa, Unaligned.ARRAY_BYTE_BASE_OFFSET, bufb, Unaligned.ARRAY_BYTE_BASE_OFFSET, readable, ArrayUtil.LOG2_ARRAY_BYTE_INDEX_SCALE);
				if (i >= 0) return false;
				length -= readable;
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			ArrayCache.putArray(bufa);
			ArrayCache.putArray(bufb);
		}

		return true;
	}

	private void addHash(Source source, long offset, int hash) {
		List<Tab> matches = hashTab.computeIfAbsentInt(hash, x -> new ArrayList<>());
		matches.add(new Tab(source, offset));
	}

	/**
	 * 多项式滚动哈希函数
	 * @param str 字符串
	 * @param window 窗口大小
	 * @param base 基数，好像没啥要求，不过基于实测（只测试了质数），19 101 809都是不错的值
	 * @return max(0, str.length - window) + 1个哈希值
	 */
	public static int[] polynomialRollingHash(String str, int window, int base, int[] result) {
		int n = str.length();
		if (n < window) window = n;

		if (result == null)
			result = (int[]) Unaligned.U.allocateUninitializedArray(int.class, n - window + 1);

		// 计算初始哈希值
		int hash = 0;
		for (int i = 0; i < window; i++) {
			hash = (hash * base + str.charAt(i));
		}
		result[0] = hash;

		// 实际是 n <= window
		if (window == n) return result;

		int window_power = MathUtils.pow(base, window-1);

		for (int i = 0; i < n - window;) {
			// 移除窗口中第一个字符对哈希的贡献
			hash -= window_power * str.charAt(i);

			hash = (hash * base + str.charAt(i + window));

			result[++i] = hash;
		}
		return result;
	}
}