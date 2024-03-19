package roj.math;

/**
 * 计划替换为CInteger的调用
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Deprecated
public class MutableInt extends Number implements Comparable<MutableInt> {
	public int value;

	public MutableInt() {}
	public MutableInt(int v) { value = v; }
	public MutableInt(Number v) { value = v.intValue(); }
	public MutableInt(String v) throws NumberFormatException { value = Integer.parseInt(v); }

	@Deprecated
	public int getValue() { return value; }
	@Deprecated
	public void setValue(int v) { value = v; }
	public void setValue(Number v) { value = v.intValue(); }

	public void increment() { value++; }
	public int getAndIncrement() { return value++; }
	public int incrementAndGet() { return ++value; }
	public void decrement() { value--; }
	public void add(int i) { value += i; }
	public int addAndGet(int i) { return value += i; }

	@Override
	public int intValue() { return value; }
	@Override
	public long longValue() { return value; }
	@Override
	public float floatValue() { return value; }
	@Override
	public double doubleValue() { return value; }
	@Override
	public boolean equals(Object obj) { return obj instanceof Number n && n.doubleValue() == value; }
	@Override
	public int hashCode() { return value; }
	@Override
	public int compareTo(MutableInt o) { return Integer.compare(value, o.value); }
	@Override
	public String toString() { return Integer.toString(value); }
}