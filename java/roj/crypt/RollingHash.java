package roj.crypt;

import roj.math.MathUtils;
import roj.reflect.Unaligned;

/**
 * @author Roj234
 * @since 2025/1/1 0001 7:50
 */
public class RollingHash {
	public static int[] polynomialRollingHash(String str, int window, int base) {return polynomialRollingHash(str, window, base, null);}
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