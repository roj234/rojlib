package roj.collect;

import roj.util.Helpers;

import java.util.Map;

/**
 * [Object]To[Primitive]的临时简易解决方案
 * @author Roj234
 * @since 2024/2/7 6:27
 */
public class XToDouble<T> implements Map.Entry<T, Double> {
	private static final XToDouble<?> DEF0 = defaultValue(0), DEF_1 = defaultValue(-1);
	public static <T1> XToDouble<T1> default0() { return Helpers.cast(DEF0); }
	public static <T1> XToDouble<T1> defaultM1() { return Helpers.cast(DEF_1); }
	public static <T1> XToDouble<T1> defaultValue(double def) {
		XToDouble<?> entry = new XToDouble<>(IntMap.UNDEFINED, def);
		entry.next = entry;
		return Helpers.cast(entry);
	}

	private static final XashMap.Builder<?, XToDouble<?>> BUILDER = Helpers.cast(XashMap.builder(Object.class, XToDouble.class, "key", "next"));
	public static <T1> XashMap<T1, XToDouble<T1>> newMap() { return Helpers.cast(BUILDER.create()); }
	public static <T1> XashMap<T1, XToDouble<T1>> newMap(int initialCapacity) { return Helpers.cast(BUILDER.createSized(initialCapacity)); }

	private XToDouble<?> next;

	public XToDouble() {}
	public XToDouble(T key) { this.key = key; }
	public XToDouble(T key, double val) { this.key = key; this.value = val; }

	private T key;
	public double value;

	public T getKey() { return key; }
	public void _setKey(T key) { this.key = key; }

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
		if (!(o instanceof XToDouble<?> aDouble)) return false;

		return key != null ? key.equals(aDouble.key) : aDouble.key == null;
	}

	@Override
	public int hashCode() { return key != null ? key.hashCode() : 0; }
}