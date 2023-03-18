package roj.text;

import roj.util.ArrayGetter;

import static roj.util.ArrayGetter.*;

/**
 * KMP在汉字中表现很差，啊，但是BM牺牲了空间
 * @author Roj234
 * @since 2023/1/24 0024 4:02
 *
 * 建议使用DynByteBuf,应该会在不减慢多少速度的情况下减少很多内存占用
 */
public class FastMatcher {
	private Object skip;
	private ArrayGetter ag;
	private int min, max, dt;
	private CharSequence needle;

	public FastMatcher() {}
	public FastMatcher(CharSequence needle) { setPattern(needle); }

	public void setPattern(CharSequence s) {
		needle = s;

		if (s.length() <= 6) {
			ag = null;
			dt = 1;
			return;
		}

		int min = 0xFFFF, max = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < min) min = c;
			if (c > max) max = c;
		}

		int n = s.length()-1;
		if (s.length() < 256) {
			ag = BG;
			byte[] arr = new byte[max-min+1];
			for(int i = 0; i < arr.length; i++) arr[i] = (byte) s.length();
			for(int i = 0; i < s.length() - 1; i++)
				arr[s.charAt(i)-min] = (byte) (s.length() - i - 1);
			skip = arr;
		} else if (s.length() < 65536) {
			ag = CG;
			char[] arr = new char[max-min+1];
			for(int i = 0; i < arr.length; i++) arr[i] = (char) s.length();
			for(int i = 0; i < s.length() - 1; i++)
				arr[s.charAt(i)-min] = (char) (s.length() - i - 1);
			skip = arr;
		} else {
			ag = IG;
			int[] arr = new int[max-min+1];
			for(int i = 0; i < arr.length; i++) arr[i] = (char) s.length();
			for(int i = 0; i < s.length() - 1; i++)
				arr[s.charAt(i)-min] = (byte) (s.length() - i - 1);
			skip = arr;
		}
		this.dt = ag.get(skip, needle.charAt(n)-min);
		this.min = min;
		this.max = max;
	}

	public int nextIndex(int i) {
		return i+dt;
	}

	public int match(CharSequence t, int i) {
		CharSequence p = needle;
		int max = t.length()-p.length();

		if (ag == null) return TextUtil.gIndexOf(t, p, i, max+1);

		while(i <= max) {
			int off = p.length()-1;
			while(t.charAt(i+off) == p.charAt(off))
				if (--off < 0) return i;

			char c = t.charAt(i + p.length() - 1);
			if (c < min || c > this.max) i += p.length();
			else i += ag.get(skip, c-min);
		}
		return -1;
	}

	public CharSequence needle() { return needle; }
}
