package roj.collect;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Roj234
 * @since 2023/5/17 7:19
 */
public final class ListMap<K, V> extends AbstractMap<K, V> {
	final IntBiMap<K> index;
	final List<V> values;
	int size;

	public ListMap(IntBiMap<K> index, List<V> values) {
		this.index = index;
		this.values = values;
		this.size = values.size();
		for (int i = 0; i < values.size(); i++) {
			if (values.get(i) == null) size--;
		}
	}

	public int size() {return size;}
	@SuppressWarnings("unchecked")
	public boolean containsKey(Object key) {
		int id = index.getByValueOrDefault((K) key, -1);
		return id >= 0 && id < values.size() && values.get(id) != null; }
	public boolean containsValue(Object value) { //noinspection SuspiciousMethodCalls
		return value != null && values.contains(value); }

	@Override
	public V get(Object key) {
		int i = index.getByValueOrDefault(key.toString(), -1);
		return i < 0 || i >= values.size() ? null : values.get(i);
	}

	@NotNull
	@Override
	public Set<Entry<K, V>> entrySet() {
		return new AbstractSet<>() {
			public int size() { return size; }
			public Iterator<Entry<K, V>> iterator() {
				return new AbstractIterator<>() {
					int i = 0;

					@Override
					protected boolean computeNext() {
						if (i == values.size()) return false;

						K key;
						V val;
						do {
							key = index.get(i);
							val = values.get(i);
							i++;
						} while (val == null);

						result = new SimpleImmutableEntry<>(key, val);
						return true;
					}
				};
			}
		};
	}
}