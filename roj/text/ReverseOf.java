package roj.text;

/**
 * @author Roj234
 * @since 2022/11/14 0014 15:59
 */
public final class ReverseOf implements CharSequence {
	CharSequence origin;
	int s, e;

	public ReverseOf(CharSequence origin, int start, int end) {
		this.origin = origin;
		this.s = start;
		this.e = end;
	}

	public static CharSequence reverseOf(CharSequence sequence, int start, int end) {
		return new ReverseOf(sequence, start, end);
	}

	@Override
	public int length() {
		return e - s;
	}

	// abcdef
	//   |  |   2 -> 5
	// fedcba
	// |  |     0 -> 3 == 5 -> 2
	@Override
	public char charAt(int index) {
		return origin.charAt((e - s - 1) - index);
	}

	// fedcba
	//  | |   1 -> 3 == 4 -> 2
	@Override
	public CharSequence subSequence(int start, int end) {
		CharSequence v = origin.subSequence((e - s - 1) - end, (e - s - 1) - start);
		return new ReverseOf(v, 0, v.length());
	}

	@Override
	public String toString() {
		char[] reverse = new char[e - s];
		int j = s + reverse.length;
		for (int i = 0; i < reverse.length; i++) {
			reverse[i] = origin.charAt(--j);
		}
		return new String(reverse);
	}
}
