package roj.collect;

import org.jetbrains.annotations.Nullable;
import roj.util.Helpers;

/**
 * @author Roj234
 * @since 2023/12/21 8:52
 */
public interface Hasher<K> {
	Hasher<?> DEFAULT = new Hasher<Object>() {
		@Override
		public int hashCode(@Nullable Object t) {
			if (t == null) return 0;

			int v = t.hashCode() * -1640531527;
			return (v ^ (v >>> 16));
		}
		@Override
		public boolean equals(Object from_argument, Object stored_in) { return from_argument == null ? stored_in == null : from_argument.equals(stored_in); }
	};
	static <T> Hasher<T> defaul() { return Helpers.cast(DEFAULT); }
	Hasher<?> IDENTITY = new Hasher<Object>() {
		@Override
		public int hashCode(@Nullable Object t) { return System.identityHashCode(t); }
		@Override
		public boolean equals(Object from_argument, Object stored_in) { return from_argument == stored_in; }
	};
	static <T> Hasher<T> identity() { return Helpers.cast(IDENTITY); }

	static <T> Hasher<T> array(Class<T> type) {return ArrayHasher.array(type);}

	int hashCode(@Nullable K k);
	boolean equals(K from_argument, Object stored_in);
}