package roj.math;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class MutableInt extends Number implements Comparable<MutableInt> {
	private static final long serialVersionUID = 512176391864L;

	public int value;

	public MutableInt() {}
	public MutableInt(int v) {
		value = v;
	}
	public MutableInt(Number v) {
		value = v.intValue();
	}
	public MutableInt(String v) throws NumberFormatException {
		value = Integer.parseInt(v);
	}

	public int getValue() {
		return value;
	}

	public void setValue(int v) {
		value = v;
	}

	public void setValue(Number v) {
		value = v.intValue();
	}

	public void increment() {
		++value;
	}
	public int getAndIncrement() {
		return value++;
	}
	public int incrementAndGet() {
		++value;
		return value;
	}

	public void decrement() {
		--value;
	}
	public int getAndDecrement() {
		return value--;
	}
	public int decrementAndGet() {
		--value;
		return value;
	}

	public void add(int i) {
		value += i;
	}

	public int addAndGet(int i) {
		value += i;
		return value;
	}

	public int addAndGet(Number i) {
		value += i.intValue();
		return value;
	}

	public int getAndAdd(int i) {
		int last = value;
		value += i;
		return last;
	}

	public int getAndAdd(Number i) {
		int last = value;
		value += i.intValue();
		return last;
	}

	@Override
	public int intValue() {
		return value;
	}

	@Override
	public long longValue() {
		return value;
	}

	@Override
	public float floatValue() {
		return value;
	}

	@Override
	public double doubleValue() {
		return value;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof Number && ((Number) obj).doubleValue() == value;
	}

	@Override
	public int hashCode() {
		return value;
	}

	@Override
	public int compareTo(MutableInt o) {
		return Integer.compare(value, o.value);
	}

	@Override
	public String toString() {
		return Integer.toString(value);
	}
}
