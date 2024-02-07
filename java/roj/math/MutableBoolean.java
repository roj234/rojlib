package roj.math;

/**
 * @author Roj234
 * @since 2020/8/19 1:08
 */
public class MutableBoolean {
	public volatile boolean value;

	public MutableBoolean() {}
	public MutableBoolean(boolean b) {
		this.value = b;
	}

	public void set(boolean b) {
		this.value = b;
	}
	public boolean get() {
		return this.value;
	}

	public boolean getSet(boolean b) {
		boolean v = value;
		value = b;
		return v;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		MutableBoolean aBoolean = (MutableBoolean) o;

		return value == aBoolean.value;
	}

	@Override
	public int hashCode() {
		return (value ? 114514 : 1919810);
	}
}
