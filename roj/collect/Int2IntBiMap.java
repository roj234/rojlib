package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;
import roj.util.Int2IntFunction;

import javax.annotation.Nonnull;
import java.util.*;

public class Int2IntBiMap extends AbstractMap<Integer, Integer> implements MapLike<Int2IntMap.Entry>, Flippable<Integer, Integer> {
	public void setNullId(int nullId) {
		this.nullId = nullId;
	}

	public static class Entry extends Int2IntMap.Entry {
		protected Entry(int k, int v) {
			super(k, v);
		}

		protected Entry valueNext;

		@Override
		public Entry nextEntry() {
			return (Entry) next;
		}
	}

	static final class Inverse extends AbstractMap<Integer, Integer> implements Flippable<Integer, Integer> {
		private final Int2IntBiMap parent;

		private Inverse(Int2IntBiMap parent) {
			this.parent = parent;
		}

		public int size() {
			return parent.size();
		}

		public boolean containsKey(Object key) {
			return parent.containsValue(key);
		}

		public boolean containsValue(Object value) {
			return parent.containsKey(value);
		}

		public Integer get(Object key) {
			return parent.getByValue((Integer) key);
		}

		public Integer put(Integer key, Integer value) {
			return parent.putByValue(key, value);
		}

		@Override
		public void putAll(@Nonnull Map<? extends Integer, ? extends Integer> map) {
			for (Map.Entry<? extends Integer, ? extends Integer> entry : map.entrySet()) {
				parent.putByValue(entry.getKey(), entry.getValue());
			}
		}

		public Integer forcePut(Integer key, Integer value) {
			return parent.forcePutByValue(key, value);
		}

		public Integer remove(Object key) {
			return parent.removeByValue((Integer) key);
		}

		public void clear() {
			parent.clear();
		}

		@Nonnull
		public Set<Entry<Integer, Integer>> entrySet() {
			return new EntrySet(this.parent);
		}

		static class EntrySet extends AbstractSet<Map.Entry<Integer, Integer>> {
			private final Int2IntBiMap map;

			public EntrySet(Int2IntBiMap map) {
				this.map = map;
			}

			public final int size() {
				return map.size();
			}

			public final void clear() {
				map.clear();
			}

			@Nonnull
			public final Iterator<Map.Entry<Integer, Integer>> iterator() {
				if (isEmpty()) {return Collections.emptyIterator();}
				EntryItr<Int2IntMap.Entry> dlg = new EntryItr<>(map.entries, map);
				return new AbstractIterator<Entry<Integer, Integer>>() {

					@Override
					public boolean computeNext() {
						boolean next = dlg.hasNext();
						if (next) {
							Int2IntMap.Entry t = dlg.nextT();
							result = new SimpleImmutableEntry<>(t.v, t.k);
						}
						return next;
					}
				};
			}

			public final boolean contains(Object o) {
				if (!(o instanceof Entry)) return false;
				Int2IntMap.Entry e = (Int2IntMap.Entry) o;
				Int2IntMap.Entry comp = map.getValueEntry(e.k);
				return comp != null && comp.v == e.getIntValue();
			}

			public final boolean remove(Object o) {
				if (o instanceof Map.Entry) {
					HashBiMap.Entry<?, ?> e = (HashBiMap.Entry<?, ?>) o;
					return map.remove(e.v) != null;
				}
				return false;
			}
		}

		public Int2IntBiMap flip() {
			return parent;
		}
	}

	protected Entry[] entries;
	protected Entry[] valueEntries;

	protected int size = 0;

	int length = 2, mask = 1;

	float loadFactor = 0.8f;
	int nullId = -1;

	private final Inverse inverse = new Inverse(this);

	public Int2IntBiMap() {
		this(16);
	}

	public Int2IntBiMap(int size) {
		ensureCapacity(size);
	}

	public void ensureCapacity(int size) {
		if (size < length) return;
		length = MathUtils.getMin2PowerOf(size);
		mask = length - 1;

		resize();
	}


	public int getByValueOrDefault(int key, int def) {
		Entry entry = getValueEntry(key);
		return entry == null ? def : entry.k;
	}

	public int getOrDefaultInt(int key, int def) {
		Entry entry = getKeyEntry(key);
		return entry == null ? def : entry.v;
	}

	public Inverse flip() {
		return this.inverse;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public Set<Entry> selfEntrySet() {
		return new EntrySet(this);
	}
	public Set<Map.Entry<Integer, Integer>> entrySet() {
		return Helpers.cast(new EntrySet(this));
	}
	public int size() {
		return size;
	}

	@Override
	public void removeEntry0(Int2IntMap.Entry vEntry) {
		removeEntry((Entry) vEntry);
	}

	public int computeIfAbsent(int k, @Nonnull Int2IntFunction supplier) {
		Integer v = get(k);
		if (v == null) {
			put(k, v = supplier.apply(k));
		}
		return v;
	}

	@Override
	public Integer remove(Object key) {
		return remove((int) key);
	}

	@Override
	public Integer get(Object key) {
		return get((int) key);
	}

	@Override
	public boolean containsKey(Object key) {
		return containsKey((int) key);
	}

	@Override
	public Integer put(Integer key, Integer value) {
		return putInt(key, value);
	}

	@Override
	public Integer getOrDefault(Object key, Integer def) {
		return getOrDefaultInt((int) key, def);
	}

	@Override
	public Integer forcePut(Integer key, Integer e) {
		return forcePutInt(key, e);
	}

	protected void resize() {
		if (valueEntries != null && entries != null) {
			Entry[] newEntries = new Entry[length];
			Entry[] newValues = new Entry[length];

			Entry entry;
			Entry next;
			int i = 0, j = entries.length;

			for (; i < j; i++) {
				entry = entries[i];
				while (entry != null) {
					next = entry.nextEntry();
					int newIndex = indexFor(entry.k);
					Entry old = newEntries[newIndex];
					newEntries[newIndex] = entry;
					entry.next = old;
					entry = next;
				}

				entry = valueEntries[i];
				while (entry != null) {
					next = entry.valueNext;

					int newIndex = indexFor(entry.v);
					Entry old = newValues[newIndex];
					newValues[newIndex] = entry;
					entry.valueNext = old;

					entry = next;
				}
			}
			this.valueEntries = newValues;
			this.entries = newEntries;

		} else if (this.valueEntries != this.entries) throw new Error();
	}

	public Integer putInt(int key, int e) {
		return put0(key, e, false);
	}
	public Integer forcePutInt(int key, int e) {
		return put0(key, e, true);
	}

	public int putByValue(int key, int e) {
		return putByValue0(e, key, false);
	}
	public int forcePutByValue(int key, int e) {
		return putByValue0(e, key, true);
	}

	private int putByValue0(int v, int key, boolean replace) {
		if (size > length * loadFactor) {
			length <<= 1;
			mask = length - 1;
			resize();
		}

		Entry keyEntry = getKeyEntry(key);
		Entry valueEntry = getValueEntry(v);

		if (keyEntry != null) {
			if (keyEntry == valueEntry) {
				return key;
			}

			if (valueEntry != null) { // value 替换key
				removeEntry(keyEntry);
				// keyEntry will be deleted

				removeKeyEntry(valueEntry, valueEntry.k);

				int old = valueEntry.k;
				valueEntry.k = key;

				putKeyEntry(valueEntry);

				return old;
			} else {
				if (!replace) throw new IllegalArgumentException("Multiple value(" + v + ", " + keyEntry.v + ") bind to same key(" + keyEntry.k + ")! use forcePut()!");

				// key找到, 没找到value
				removeValueEntry(keyEntry, keyEntry.v);

				keyEntry.v = v;

				putValueEntry(keyEntry);

				return key;
			}
		} else {
			if (valueEntry != null) {

				// key没找到, 找到value
				int oldKey = valueEntry.k;
				removeKeyEntry(valueEntry, oldKey);

				valueEntry.k = key;

				putKeyEntry(valueEntry);

				return oldKey;
			} else {
				// 全为空
				putValueEntry(createEntry(key, v));

				return nullId;
			}
		}
	}

	private Integer put0(int key, int v, boolean replace) {
		if (size > length * loadFactor) {
			length *= 2;
			mask = length - 1;
			resize();
		}

		Entry keyEntry = getKeyEntry(key);
		Entry valueEntry = getValueEntry(v);

		// key替换value

		if (keyEntry != null) {
			if (keyEntry == valueEntry) {
				return v;
			}


			if (valueEntry != null) {
				// key和value都找到了
				removeEntry(valueEntry);
			}
			// key找到, 没找到value
			int oldV = keyEntry.v;

			removeValueEntry(keyEntry, oldV);

			keyEntry.v = v;
			putValueEntry(keyEntry);

			return oldV;
		} else {
			if (valueEntry != null) {
				if (!replace) throw new IllegalArgumentException("Multiple key(" + key + ", " + valueEntry.k + ") bind to same value(" + valueEntry.v + ")! use forcePut()!");

				// key没找到, 找到value
				removeKeyEntry(valueEntry, valueEntry.k);

				valueEntry.k = key;

				putKeyEntry(valueEntry);

				return v;
			} else {
				// 全为空
				putValueEntry(createEntry(key, v));
				return null;
			}
		}
	}

	public Integer remove(int id) {
		Entry entry = getKeyEntry(id);
		if (entry == null) return null;
		removeEntry(entry);
		return entry.v;
	}

	public int removeByValue(int v) {
		Entry entry = getValueEntry(v);
		if (entry == null) return nullId;
		removeEntry(entry);
		return entry.k;
	}

	public int getByValue(int key) {
		Entry entry = getValueEntry(key);
		return entry == null ? nullId : entry.k;
	}

	public Integer get(int id) {
		Entry entry = getKeyEntry(id);
		return entry == null ? null : entry.v;
	}

	public boolean containsKey(int i) {
		return getKeyEntry(i) != null;
	}

	public boolean containsValue(int v) {
		return getValueEntry(v) != null;
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		if (entries != null) {
			Arrays.fill(entries, null);
		}
		if (valueEntries != null) {
			Arrays.fill(valueEntries, null);
		}
	}

	protected void removeEntry(Entry toRemove) {
		removeKeyEntry(toRemove, toRemove.k);
		removeValueEntry(toRemove, toRemove.v);
		this.size--;
	}

	boolean removeKeyEntry(Entry entry, int index) {
		index = indexFor(index);
		if (entries == null) return false;
		Entry currentEntry;
		Entry prevEntry;
		if ((currentEntry = entries[index]) == null) {
			return false;
		}

		if (currentEntry == entry) {
			entries[index] = currentEntry.nextEntry();
			entry.next = null;
			return true;
		}

		while (currentEntry.next != null) {
			prevEntry = currentEntry;
			currentEntry = currentEntry.nextEntry();
			if (currentEntry == entry) {
				prevEntry.next = entry.next;
				entry.next = null;
				return true;
			}
		}
		return false;
	}

	boolean removeValueEntry(Entry entry, int v) {
		int index = indexFor(v);
		if (valueEntries == null) return false;
		Entry currentEntry;
		Entry prevEntry;
		if ((currentEntry = valueEntries[index]) == null) {
			return false;
		}

		if (currentEntry == entry) {
			valueEntries[index] = entry.valueNext;
			entry.valueNext = null;
			return true;
		}

		while (currentEntry.valueNext != null) {
			prevEntry = currentEntry;
			currentEntry = currentEntry.valueNext;
			if (currentEntry == entry) {
				prevEntry.valueNext = entry.valueNext;
				entry.valueNext = null;
				return true;
			}
		}
		return false;
	}

	void putValueEntry(Entry entry) {
		int index = indexFor(entry.v);
		if (valueEntries == null) valueEntries = new Entry[length];
		Entry currentEntry;
		if ((currentEntry = valueEntries[index]) == null) {
			valueEntries[index] = entry;
		} else {
			while (currentEntry.valueNext != null) {
				if (currentEntry == entry) return;
				currentEntry = currentEntry.valueNext;
			}
			currentEntry.valueNext = entry;
		}
	}

	void putKeyEntry(Entry entry) {
		int index = indexFor(entry.k);
		if (entries == null) entries = new Entry[length];
		Entry currentEntry;
		if ((currentEntry = entries[index]) == null) {
			entries[index] = entry;
		} else {
			while (currentEntry.next != null) {
				if (currentEntry == entry) return;
				currentEntry = currentEntry.nextEntry();
			}
			currentEntry.next = entry;
		}
	}

	protected Entry getValueEntry(int v) {
		if (valueEntries == null) return null;
		int id = indexFor(v);

		Entry entry = valueEntries[id];
		while (entry != null) {
			if (v == entry.v) {
				return entry;
			}
			entry = entry.valueNext;
		}
		return null;
	}

	protected Entry getKeyEntry(int id) {
		Entry entry = getEntryFirst(id, false);
		if (entry == null) return null;
		while (entry != null) {
			if (entry.k == id) return entry;
			entry = entry.nextEntry();
		}
		return null;
	}

	protected Entry createEntry(int id, int v) {
		Entry entry = getEntryFirst(id, true);
		size++;
		if (entry.k == id) {
			entry.v = v;
			return entry;
		}
		while (entry.next != null) {
			entry = entry.nextEntry();
		}
		Entry subEntry = new Entry(id, v);
		entry.next = subEntry;
		return subEntry;
	}

	int indexFor(int id) {
		return (id ^ (id >>> 16)) & mask;
	}

	Entry getEntryFirst(int id1, boolean create) {
		int id = indexFor(id1);
		if (entries == null) {
			if (!create) return null;
			entries = new Entry[length];
		}
		Entry entry;
		if ((entry = entries[id]) == null) {
			if (!create) return null;
			entries[id] = entry = new Entry(id1, 0);
		}
		return entry;
	}

	static class EntrySet extends AbstractSet<Entry> {
		private final Int2IntBiMap map;

		public EntrySet(Int2IntBiMap map) {
			this.map = map;
		}

		public final int size() {
			return map.size();
		}

		public final void clear() {
			map.clear();
		}

		@Nonnull
		public final Iterator<Entry> iterator() {
			return isEmpty() ? Collections.emptyIterator() : Helpers.cast(new EntryItr<>(map.entries, map));
		}

		public final boolean contains(Object o) {
			if (!(o instanceof Entry)) return false;
			Entry e = (Entry) o;
			int key = e.getIntKey();
			Entry comp = map.getKeyEntry(key);
			return comp != null && comp.v == e.getIntValue();
		}

		public final boolean remove(Object o) {
			if (o instanceof Map.Entry) {
				Entry e = (Entry) o;
				return map.remove(e.k) != null;
			}
			return false;
		}

		public final Spliterator<Entry> spliterator() {
			return Spliterators.spliterator(iterator(), size(), 0);
		}
	}
}