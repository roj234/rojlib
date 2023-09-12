package roj.collect;

import roj.util.Helpers;

import java.util.*;

/**
 * @author Roj234
 * @since 2023/9/14 0014 14:24
 */
final class _Generic_EntrySet extends AbstractSet<Object> {
	private final Map<?,?> map;

	public static <X extends Set<?>, T extends Map<?,?> & _Generic_Map<?>> X create(T map) { return Helpers.cast(new _Generic_EntrySet(map)); }
	private _Generic_EntrySet(Map<?,?> map) { this.map = map; }

	public final int size() { return map.size(); }
	public final void clear() { map.clear(); }

	public final Iterator<Object> iterator() {
		if (map.isEmpty()) return Collections.emptyIterator();

		_Generic_Map<?> m = (_Generic_Map<?>) map;
		Iterator<?> itr = m.__iterator();
		return Helpers.cast(itr != null ? itr : new EntryItr<>(m));
	}

	public final boolean contains(Object o) {
		if (!(o instanceof Map.Entry)) return false;
		Map.Entry<?,?> e = (Map.Entry<?,?>) o;
		Object b = map.get(e.getKey());
		if (b == null && !map.containsKey(e.getKey())) return false;
		return e.getValue().equals(b);
	}

	public final boolean remove(Object o) {
		if (!(o instanceof Map.Entry)) return false;
		Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
		return map.remove(e.getKey(), e.getValue());
	}
}
