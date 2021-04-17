package roj.sql;

import roj.collect.AbstractIterator;
import roj.collect.IntBiMap;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * @author Roj234
 * @since 2023/5/17 0017 7:19
 */
class ListMap extends AbstractMap<String, String> implements Map<String, String> {
	final IntBiMap<String> index;
	final List<String> values;

	ListMap(IntBiMap<String> index, List<String> values) {
		this.index = index;
		this.values = values;
	}

	public int size() { return index.size(); }
	public boolean containsKey(Object key) { return index.containsKey(key); }
	public boolean containsValue(Object value) { return values.contains(value); }

	@Override
	public String get(Object key) {
		int i = index.getValueOrDefault(key.toString(), -1);
		return i < 0 ? null : values.get(i);
	}

	@Nonnull
	@Override
	public Set<Entry<String, String>> entrySet() {
		return new AbstractSet<Entry<String, String>>() {
			public int size() { return index.size(); }
			public Iterator<Entry<String, String>> iterator() {
				return new AbstractIterator<Entry<String, String>>() {
					int i = 0;

					@Override
					protected boolean computeNext() {
						if (i == index.size()) return false;
						String key = index.get(i);
						String val = values.get(i);
						i++;
						result = new SimpleImmutableEntry<>(key, val);
						return true;
					}
				};
			}
		};
	}
}
