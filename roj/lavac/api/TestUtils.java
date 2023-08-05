package roj.lavac.api;

import roj.collect.MyHashMap;

import java.util.Map;

/**
 * @author Roj234
 * @since 2023/9/23 0023 19:11
 */
@PrimitiveGeneric.User(to = "T", type = {int.class, float.class})
public class TestUtils<T> {
	public T myValue;

	@PrimitiveGeneric.Method({"apply_int", "apply_float"})
	public T apply() { return myValue; }
	public int apply_int() { return 2; }
	public float apply_float() { return 1; }

	@Operator("+")
	public static <K, V> Map<K, V> add(Map<K, V> map1, Map<K, V> map2) {
		MyHashMap<K, V> out = new MyHashMap<>(map1);
		out.putAll(map2);
		return out;
	}
}
