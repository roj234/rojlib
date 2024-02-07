package roj.math;

/**
 * @author Roj234
 * @since 2020/8/19 1:08
 */
public class MutableLong {
	public long value;

	public MutableLong() {}
	public MutableLong(long b) {
		this.value = b;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		MutableLong aLong = (MutableLong) o;

		return value == aLong.value;
	}

	@Override
	public int hashCode() {
		return (int) (value ^ (value >>> 32));
	}
}
