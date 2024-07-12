package roj.collect;

import java.util.Arrays;
import java.util.Comparator;

/**
 * @author Roj234
 * @since 2024/7/12 0012 8:50
 */
public final class OnlyBest<T> {
	private final Comparator<T> cmp;
	private final Object[] buf;
	private int count;

	public OnlyBest(int best, Comparator<T> cmp) {
		this.buf = new Object[best];
		this.cmp = cmp;
	}

	public Object[] get() {return buf;}

	@SuppressWarnings("unchecked")
	public boolean add(T t) {
		if (count > 0 && cmp.compare(t, (T) buf[count-1]) > 0) return false;
		int pos = Arrays.binarySearch((T[]) buf, 0, count, t, cmp);
		if (pos < 0) pos = -pos - 1;
		insertAt(pos, t);
		return true;
	}

	private void insertAt(int index, T t) {
		if (index < count) {
			int c = count;
			if (c == buf.length) c--;
			System.arraycopy(buf, index, buf, index + 1, c - index);
		}
		buf[index] = t;
		if (count < buf.length) count++;
	}
}