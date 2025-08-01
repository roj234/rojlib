package roj.compiler.test;

import roj.collect.HashMap;
import roj.compiler.api.Switchable;
import roj.compiler.plugins.annotations.Operator;
import roj.compiler.plugins.primgen.PrimitiveGeneric;

import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/9/23 19:11
 */
@PrimitiveGeneric.User(to = "T", type = {int.class, float.class})
@Switchable(suggest = true)
public class Test<T> {
	public static <T1> T1 any() { return null; }

	private int pp;

	public T myValue;

	@PrimitiveGeneric.Method({"applyi", "applyf"})
	public T apply() { return myValue; }
	private int applyi() { return 2; }
	private float applyf() { return 1; }

	@Operator("+")
	public static <K, V> Map<K, V> add(Map<K, V> map1, Map<K, V> map2) {
		HashMap<K, V> out = new HashMap<>(map1);
		out.putAll(map2);
		return out;
	}

	//XHashSet<Integer, Object> actions = XHashSet.noCreation(Object.class, "key", "_next", Hasher.identity()).create();
	public static <T> T simpleGenericTest(List<? extends T> bound1, List<? super T> bound2, T bound3) {
		return null;
	}

	public static class CastA<A> {
		public A field1;
	}
	public static class CastB<B> extends CastA<List<B>> {
		private class C<CC> extends CastA<Void> {
			<AA extends Number> B genericMethod() {return null;};
		}
		private static class D<DD> extends CastA<Void> {}
	}
}