package roj.collect;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Roj234
 * @since 2026/1/22 17:01
 */
public class ListMap<K, V> extends AbstractMap<K, V> {
	protected final List<K> keys;
	protected final List<V> values;

	public ListMap(@NotNull List<K> keys, @NotNull List<V> values) {
		this.keys = keys;
		this.values = values;
	}

	public int size() {return keys.size();}
	public boolean containsKey(Object key) {
		//noinspection SuspiciousMethodCalls
		return keys.contains(key);
	}
	public boolean containsValue(Object value) {
		//noinspection SuspiciousMethodCalls
		return values.contains(value);
	}

	@Override
	public V get(Object key) {
		//noinspection SuspiciousMethodCalls
		int i = keys.indexOf(key);
		return i < 0 ? null : values.get(i);
	}

	@NotNull
	@Override
	public Set<Entry<K, V>> entrySet() {
		return new AbstractSet<>() {
			public int size() { return keys.size(); }
			public @NotNull Iterator<Entry<K, V>> iterator() {
				return new AbstractIterator<>() {
					int i;

					@Override
					protected boolean computeNext() {
						if (i == values.size()) return false;

						K key = keys.get(i);
						V val = values.get(i);
						i++;

						result = new SimpleImmutableEntry<>(key, val);
						return true;
					}
				};
			}
		};
	}
}