package roj.collect;

import roj.util.Helpers;

import java.util.Map;

/**
 * [Object]To[Primitive]的临时简易解决方案
 * @author Roj234
 * @since 2024/2/7 6:27
 */
public class ToDoubleEntry<T> implements Map.Entry<T, Double> {
	private static final ToDoubleEntry<?> DEF0 = create(0), DEF_1 = create(-1);
	public static <T1> ToDoubleEntry<T1> constantZero() { return Helpers.cast(DEF0); }
	public static <T1> ToDoubleEntry<T1> constantNeg1() { return Helpers.cast(DEF_1); }
	public static <T1> ToDoubleEntry<T1> create(double value) {
		ToDoubleEntry<?> entry = new ToDoubleEntry<>(IntMap.UNDEFINED, value);
		entry.next = entry;
		return Helpers.cast(entry);
	}

	private static final XashMap.Builder<?, ToDoubleEntry<?>> BUILDER = Helpers.cast(XashMap.builder(Object.class, ToDoubleEntry.class, "key", "next"));
	public static <T1> XashMap<T1, ToDoubleEntry<T1>> newMap() { return Helpers.cast(BUILDER.create()); }
	public static <T1> XashMap<T1, ToDoubleEntry<T1>> newMap(int initialCapacity) { return Helpers.cast(BUILDER.createSized(initialCapacity)); }

	private ToDoubleEntry<?> next;

	public ToDoubleEntry() {}
	public ToDoubleEntry(T key) { this.key = key; }
	public ToDoubleEntry(T key, double val) { this.key = key; this.value = val; }

	private T key;
	public double value;

	public T getKey() { return key; }

	@Override
	@Deprecated
	public Double getValue() { return value; }
	@Override
	@Deprecated
	public Double setValue(Double v) {
		Double p = value;
		value = v;
		return p;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ToDoubleEntry<?> aDouble)) return false;

		return key != null ? key.equals(aDouble.key) : aDouble.key == null;
	}

	@Override
	public int hashCode() { return key != null ? key.hashCode() : 0; }
}