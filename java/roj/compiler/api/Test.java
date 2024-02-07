package roj.compiler.api;

import roj.collect.MyHashMap;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/9/23 0023 19:11
 */
@PrimitiveGeneric.User(to = "T", type = {int.class, float.class})
public class Test<T> {
	public static int ii;
	public static byte bb;
	public static char cc;
	public static double dd;
	public static <T1> T1 any() { return null; }

	public int it;
	private int pp;

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

	public static <T> T simpleGenericTest(List<? extends T> bound1, List<? super T> bound2, T bound3) {
		return null;
	}

	public static void longGenericTransferTest() {
		//XHashSet<Integer, Object> actions = XHashSet.noCreation(Object.class, "key", "_next", Hasher.identity()).create();
	}

	public static int mtest(int name1, String name2) {
		return 0;
	}

	public static void vtest(Object... args) {}
	public static void vtest(String... args) {}
	public static void vtest(String str, String... args) {}


	public static class CastA<A> {
		public A field1;
	}
	public static class CastB<B> extends CastA<List<B>> {
		private class C<CC> extends CastA<Void> {
			<AA extends Number> B genericMethod() {return null;};
		}
		private static class D<DD> extends CastA<Void> {}
	}

	public static class ACT implements AutoCloseable {
		public String read() { return "s"; }
		@Override
		public void close() throws IOException {
			System.out.println("关闭了");
		}
	}
}