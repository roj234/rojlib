package roj.collect;

import roj.util.Helpers;

import java.util.*;

/**
 * @author Roj234
 * @since 2023/9/14 14:24
 */
final class _LibEntrySet extends AbstractSet<Object> {
	private final Map<?,?> map;

	public static <X extends Set<?>, T extends Map<?,?> & _LibMap<?>> X create(T map) { return Helpers.cast(new _LibEntrySet(map)); }
	private _LibEntrySet(Map<?,?> map) { this.map = map; }

	public final int size() { return map.size(); }
	public final void clear() { map.clear(); }

	public final Iterator<Object> iterator() {
		if (map.isEmpty()) return Collections.emptyIterator();

		_LibMap<?> m = (_LibMap<?>) map;
		Iterator<?> itr = m.__iterator();
		return Helpers.cast(itr != null ? itr : new _LibEntryItr<>(m.__entries(), m));
	}

	public final boolean contains(Object o) {
		if (!(o instanceof Map.Entry<?, ?> e)) return false;
		Object b = map.get(e.getKey());
		if (b == null && !map.containsKey(e.getKey())) return false;
		return e.getValue().equals(b);
	}

	public final boolean remove(Object o) {
		if (!(o instanceof Map.Entry<?, ?> e)) return false;
		return map.remove(e.getKey(), e.getValue());
	}
}