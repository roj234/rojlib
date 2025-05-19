package roj.http;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.concurrent.OperationDone;
import roj.util.Helpers;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author solo6975
 * @since 2021/10/23 21:20
 */
@SuppressWarnings({"rawtypes","unchecked"})
public class Multimap<K, V> extends MyHashMap<K, V> {
	public Multimap() {super();}
	public Multimap(int size) {super(size);}

	static final class Entry<K, V> extends MyHashMap.Entry<K, V> implements BiConsumer<String, String> {
		List<V> rest = Collections.emptyList();

		// awa, 省一个类, 我真无聊
		public final void accept(String k, String v) {
			if (this.key == null || this.key.equals(k)) {
				this.key = (K) v;
				throw OperationDone.INSTANCE;
			}
		}
	}

	public final V get(K key, int index) {
		var entry = (Entry) getEntry(key);
		if (entry == null) return null;
		return (V) (index == 0 ? entry.value : entry.rest.get(index-1));
	}
	public V getLast(K key) {
		var entry = (Entry) getEntry(key);
		if (entry == null) return null;
		return (V) (entry.rest.isEmpty() ? entry.value : entry.rest.get(entry.rest.size()-1));
	}
	public final List<V> getRest(K key) {
		var entry = (Entry) getEntry(key);
		if (entry == null) return Collections.emptyList();
		return entry.rest;
	}
	public final List<V> getAll(K key) {
		var entry = (Entry) getEntry(key);
		if (entry == null) return Collections.emptyList();

		SimpleList<V> list = new SimpleList<>(entry.rest.size()+1);
		list.add((V) entry.value);
		list.addAll(entry.rest);
		return list;
	}
	public final int getCount(K key) {
		var entry = (Entry) getEntry(key);
		if (entry == null) return 0;
		return 1+entry.rest.size();
	}

	@Override
	protected final AbstractEntry<K, V> useEntry() {
		var entry = new Entry();
		entry.key = entry.value = Helpers.cast(UNDEFINED);
		return entry;
	}

	@Override
	protected final void onDel(AbstractEntry<K, V> entry) {
		Entry e = (Entry) entry;
		e.key = null;
		e.next = null;
		e.rest = Collections.emptyList();
	}

	public void add(K key, V value) {
		Entry entry = (Entry) getOrCreateEntry(key);
		if (entry.getKey() == UNDEFINED) {
			entry.key = key;
			entry.value = value;
			size++;
		} else {
			if (entry.rest.isEmpty()) entry.rest = new LinkedList<>();
			entry.rest.add(value);
		}
	}

	public void set(K key, V value) {
		Entry entry = (Entry) getOrCreateEntry(key);
		if (entry.getKey() == UNDEFINED) {
			entry.key = key;
			size++;
		}
		entry.value = value;
		entry.rest = Collections.emptyList();
	}

	public void set(K key, List<V> value) {
		Entry entry = (Entry) getOrCreateEntry(key);
		if (entry.getKey() == UNDEFINED) {
			entry.key = key;
			size++;
		}
		if (value.size() == 1) {
			entry.rest = Collections.emptyList();
		} else {
			entry.rest = new SimpleList(value);
			entry.rest.remove(0);
		}
		entry.value = value.get(0);
	}
}