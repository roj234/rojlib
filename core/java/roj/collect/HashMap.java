package roj.collect;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.text.logging.Logger;
import roj.util.ArrayUtil;
import roj.util.FastFailException;
import roj.util.Helpers;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static roj.collect.IntMap.*;

/**
 * @author Roj234
 * @since 2021/6/18 11:5
 */
public class HashMap<K, V> extends AbstractMap<K, V> implements FindMap<K, V>, _LibMap<HashMap.AbstractEntry<K, V>> {
	static final int TREEIFY_THRESHOLD = 8, UNTREEIFY_THRESHOLD = 6, MIN_TREEIFY_CAPACITY = 64;

	public static abstract class AbstractEntry<K, V> implements Map.Entry<K, V>, _LibEntry {
		public K key;
		public final K getKey() { return key; }

		public AbstractEntry<K, V> next;
		public final AbstractEntry<K, V> __next() { return next; }
	}
	public static class Entry<K, V> extends AbstractEntry<K, V> {
		public V value;

		public Entry() {}
		public Entry(K key, V value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public V getValue() { return value; }
		@Override
		public V setValue(V value) {
			V old = this.value;
			this.value = value;
			return old;
		}

		@Override
		public String toString() {return String.valueOf(key)+'='+value;}
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			var entry = (Entry<?, ?>) o;
			if (!(key != null ? key.equals(entry.key) : entry.key == null)) return false;
			return value != null ? value.equals(entry.value) : entry.value == null;
		}
		@Override
		public int hashCode() {
			int hash = key != null ? key.hashCode() : 0;
			hash = hash ^ (value != null ? value.hashCode() : 0);
			return hash;
		}
	}
	private final class ListEntry extends AbstractEntry<Object[], Object> implements Comparator<Object> {
		int slot, len, lastPos;
		boolean isComp, failOnIdentity;

		ListEntry(boolean cmp) { this.isComp = cmp; }

		@Override
		public Iterator<AbstractEntry<Object[], Object>> __iterator() {
			return Helpers.cast(new ArrayIterator<>(key.clone(), 0, len));
		}

		public Object getValue() { return null; }
		public Object setValue(Object value) { return null; }

		AbstractEntry<?, ?> get(Object key) {
			lastPos = -1;

			AbstractEntry<?, ?>[] arr = Helpers.cast(this.key);
			int pos = ArrayUtil.binarySearch(arr, 0, len, key, this);
			AbstractEntry<?, ?> entry;
			if (pos < 0) {
				// full search
				lastPos = pos = -pos - 1;

				if (!failOnIdentity) return null;

				if (pos >= len) return null;
				for (int i = pos; i >= 0; i--) {
					entry = arr[i];
					if (key.equals(entry.key)) return entry;
				}
				for (int i = pos+1; i < len; i++) {
					entry = arr[i];
					if (key.equals(entry.key)) return entry;
				}
			} else {
				// is comparably equal
				entry = arr[pos];
				if (key.equals(entry.key)) {
					lastPos = pos;
					return entry;
				}
				int hash = System.identityHashCode(key);

				// but not exactly 'equals' (same hash, cmp state and identity hash)
				for (int i = pos-1; i >= 0; i--) {
					entry = arr[i];
					if (key.equals(entry.key)) {
						lastPos = i;
						return entry;
					}
					if (System.identityHashCode(entry.key) != hash) break;
				}
				for (int i = pos+1; i < len; i++) {
					entry = arr[i];
					if (key.equals(entry.key)) {
						lastPos = i;
						return entry;
					}
					if (System.identityHashCode(entry.key) != hash) break;
				}
			}

			return null;
		}
		void insert(Object key, AbstractEntry<?, ?> entry) {
			Object[] arr = this.key;
			int pos = lastPos;

			if (len == arr.length) this.key = arr = Arrays.copyOf(arr, len << 1);

			if (len - pos > 0) System.arraycopy(arr, pos, arr, pos+1, len-pos);
			arr[pos] = entry;
			len++;
		}
		void remove(AbstractEntry<?, ?> entry) {
			Object[] arr = key;
			int pos = lastPos;
			assert arr[pos] == entry;

			if (len-pos-1 > 0) System.arraycopy(arr, pos+1, arr, pos, len-pos-1);
			arr[--len] = null;
		}

		@Override
		@SuppressWarnings("unchecked")
		public int compare(Object o, Object o2) {
			failOnIdentity = false;

			AbstractEntry<?, ?> o1 = (AbstractEntry<?, ?>) o;
			int v = Integer.compare(hasher.hashCode((K) o1.key), hasher.hashCode((K) o2));
			if (v != 0) return v;
			if (isComp) return ((Comparable<?>) o1.key).compareTo(Helpers.cast(o2));
			v = Integer.compare(System.identityHashCode(o1.key), System.identityHashCode(o2));
			if (v != 0) failOnIdentity = true;
			return v;
		}
	}

	protected AbstractEntry<?, ?>[] entries;
	protected int size;
	private int nextResize, mask;

	private Hasher<K> hasher = Hasher.defaul();
	public void setHasher(Hasher<K> hasher) {
		this.hasher = hasher;
	}
	public static <K, V> HashMap<K, V> withCustomHasher(Hasher<K> hasher) {
		HashMap<K, V> map = new HashMap<>();
		map.hasher = hasher;
		return map;
	}

	public HashMap() { this(16); }
	public HashMap(int size) { ensureCapacity(size); }
	public HashMap(Map<K, V> map) { this.putAll(map); }

	public void ensureCapacity(int size) {
		if (size <= mask) return;
		int length = MathUtils.nextPowerOfTwo(size);

		if (entries != null) {
			mask = (length>>1) - 1;
			resize();
		} else {
			mask = length-1;
			nextResize = (int) (length * REFERENCE_LOAD_FACTOR);
		}
	}

	// GenericMap interface
	@Override public _LibEntry[] __entries() { return entries; }
	@Override public void __remove(AbstractEntry<K, V> entry) { remove(entry.key); }
	// GenericMap interface

	@Override public final int size() { return size; }
	@Override public final boolean containsKey(Object key) { return getEntry(key) != null; }
	@Override public final boolean containsValue(Object val) { return getValueEntry(val) != null; }
	@Override public final V get(Object key) { return getOrDefault(key, null); }
	@Override public V getOrDefault(Object key, V def) {
		AbstractEntry<K, V> entry = getEntry(key);
		if (entry == null) return def;
		return entry.getValue();
	}
	@Override public final Map.Entry<K, V> find(K key) { return getEntry(key); }
	@Override public V put(K key, V val) {
		AbstractEntry<K, V> entry = myCreateEntry(key, val);
		if (entry == null) return null;
		onPut(entry, val);
		return entry.setValue(val);
	}
	@Override public V putIfAbsent(K key, V val) {
		AbstractEntry<K, V> entry = myCreateEntry(key, val);
		if (entry == null) return null;
		return entry.getValue();
	}
	private AbstractEntry<K, V> myCreateEntry(K key, V val) {
		AbstractEntry<K, V> entry = getOrCreateEntry(key);
		if (entry.key == UNDEFINED) {
			entry.key = key;
			size++;

			onPut(entry, val);
			entry.setValue(val);
			return null;
		}
		return entry;
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		AbstractEntry<K, V> entry = getEntry(key);
		if (entry == null) return false;
		if (Objects.equals(oldValue, entry.getValue())) {
			onPut(entry, newValue);
			entry.setValue(newValue);
			return true;
		}
		return false;
	}
	@Override
	public V replace(K key, V val) {
		AbstractEntry<K, V> entry = getEntry(key);
		if (entry == null) return null;
		return entry.setValue(val);
	}
	@Override
	public final V remove(Object key) {
		AbstractEntry<K, V> entry = remove0(key, UNDEFINED);
		if (entry == null) return null;
		return entry.getValue();
	}
	@Override
	public final boolean remove(Object key, Object value) {return remove0(key, value) != null;}

	@SuppressWarnings("unchecked")
	protected AbstractEntry<K, V> remove0(Object k, Object v) {
		AbstractEntry<K, V> prev = null, entry = getFirst((K) k, false);
		if (entry == null) return null;

		chk:
		if (entry.getClass() == ListEntry.class) {
			ListEntry listEntry = (ListEntry) entry;
			prev = (AbstractEntry<K, V>) listEntry.get(k);
			if (prev == null) return null;
			if (v != UNDEFINED && !Objects.equals(v, entry.getValue())) return null;
			size--;
			listEntry.remove(prev);
			if (listEntry.len < UNTREEIFY_THRESHOLD) {
				entries[listEntry.slot] = null;
				untreeify(entry, mask, entries);
			}
			onDel(entry);
			return prev;
		} else {
			while (entry != null) {
				if (hasher.equals((K) k, entry.key)) break chk;
				prev = entry;
				entry = entry.next;
			}
			return null;
		}

		if (v != UNDEFINED && !Objects.equals(v, entry.getValue())) return null;

		size--;

		if (prev != null) prev.next = entry.next;
		else entries[hasher.hashCode((K) k)&mask] = entry.next;

		onDel(entry);
		return entry;
	}

	@Override
	public final V compute(K key, BiFunction<? super K, ? super V, ? extends V> mapper) {
		AbstractEntry<K, V> entry = getEntry(key);
		V newV = mapper.apply(key, entry == null ? null : entry.getValue());
		if (newV == null) {
			if (entry != null) remove(key);
			return null;
		} else if (entry == null) {
			entry = myCreateEntry(key, newV);
			if (entry == null) return newV;
		}

		onPut(entry, newV);
		entry.setValue(newV);
		return newV;
	}
	@Override
	public final V computeIfAbsent(K key, @NotNull Function<? super K, ? extends V> mapper) {
		AbstractEntry<K, V> entry = getOrCreateEntry(key);
		if (entry.key != UNDEFINED) return entry.getValue();
		entry.key = key;

		V v;
		try {
			v = mapper.apply(key);
		} catch (Exception e) {
			remove(key);
			throw e;
		}

		size++;
		onPut(entry, v);
		entry.setValue(v);
		return v;
	}
	@Override
	public final V computeIfPresent(K key, @NotNull BiFunction<? super K, ? super V, ? extends V> mapper) {
		AbstractEntry<K, V> entry = getEntry(key);
		if (entry == null) return null;
		V v = entry.getValue();
		if (v == null) return null;
		V newV = mapper.apply(key, v);
		if (newV == null) {
			remove(key);
			return null;
		}

		entry.setValue(newV);
		return newV;
	}

	@Override
	public void putAll(@NotNull Map<? extends K, ? extends V> map) {
		ensureCapacity(size+map.size());
		super.putAll(map);
	}

	@Override
	public void clear() {
		if (size == 0) return;
		size = 0;

		for (int i = 0; i < entries.length; i++) {
			AbstractEntry<?, ?> entry = entries[i];
			if (entry != null) {
				onDel(Helpers.cast(entry));
				entries[i] = null;
			}
		}
	}

	// keyset and values
	@Override
	@NotNull
	public Set<Map.Entry<K, V>> entrySet() { return _LibEntrySet.create(this); }
	@Override
	@SuppressWarnings("unchecked")
	public void forEach(BiConsumer<? super K, ? super V> action) {
		if (entries == null) return;
		for (AbstractEntry<?, ?> entry : entries) {
			while (entry != null) {
				action.accept((K)entry.key, (V)entry.getValue());
				entry = entry.next;
			}
		}
	}

	// equals and hashCode and toString

	protected void onPut(AbstractEntry<K, V> entry, V newV) {}
	protected void onGet(AbstractEntry<K, V> entry) {}
	protected void onDel(AbstractEntry<K, V> entry) {}

	@SuppressWarnings("unchecked")
	private void resize() {
		int length = (mask+1) << 1;
		if (length <= 0) return;

		AbstractEntry<?, ?>[] newEntries = new AbstractEntry<?, ?>[length];
		int newMask = length-1;

		int i = 0, j = entries.length;
		for (; i < j; i++) {
			AbstractEntry<K, V> entry = (AbstractEntry<K, V>) entries[i];
			if (entry == null) continue;

			if (entry.getClass() == ListEntry.class) {
				untreeify(entry, newMask, newEntries);
			} else {
				do {
					AbstractEntry<K, V> next = entry.next;
					int newKey = hasher.hashCode(entry.key) & newMask;
					entry.next = (AbstractEntry<K, V>) newEntries[newKey];
					newEntries[newKey] = entry;
					entry = next;
				} while (entry != null);
			}
		}

		this.entries = newEntries;
		this.mask = newMask;
		this.nextResize = (int) (length * REFERENCE_LOAD_FACTOR);
	}

	@SuppressWarnings("unchecked")
	public AbstractEntry<K, V> getValueEntry(Object v) {
		AbstractEntry<?, ?>[] ent = entries;
		if (ent == null) return null;
		for (int i = ent.length - 1; i >= 0; i--) {
			AbstractEntry<K, V> entry = (AbstractEntry<K, V>) ent[i];
			while (entry != null) {
				if (Objects.equals(v, entry.getValue())) return entry;
				entry = entry.next;
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public AbstractEntry<K, V> getEntry(Object key) {
		AbstractEntry<K, V> entry = getFirst((K)key, false);

		if (entry != null && entry.getClass() == ListEntry.class) {
			return (AbstractEntry<K, V>) ((ListEntry) entry).get(key);
		}

		while (entry != null) {
			if (hasher.equals((K)key, entry.key)) {
				onGet(entry);
				return entry;
			}

			entry = entry.next;
		}
		return null;
	}
	@SuppressWarnings("unchecked")
	public AbstractEntry<K, V> getOrCreateEntry(K key) {
		restart:
		for(;;) {
			AbstractEntry<K, V> entry = getFirst(key, true);
			if (entry.getClass() == ListEntry.class) {
				AbstractEntry<K, V> next = (AbstractEntry<K, V>) ((ListEntry) entry).get(key);
				if (next == null) {
					next = useEntry();
					((ListEntry) entry).insert(key, next);
				}
				return next;
			}

			if (entry.key == UNDEFINED) return entry;

			int loop = 0;
			while (true) {
				if (hasher.equals(key, entry.key)) return entry;

				if (entry.next == null) {
					if (loop >= TREEIFY_THRESHOLD && size > MIN_TREEIFY_CAPACITY && treeify(key, loop))
						continue restart;

					if (loop > REFERENCE_CHAIN_THRESHOLD && size > nextResize) {
						resize();
						continue restart;
					}

					AbstractEntry<K, V> next = useEntry();
					entry.next = next;
					return next;
				}

				loop++;
				entry = entry.next;
			}
		}
	}

	private boolean treeify(K key, int loop) {
		AbstractEntry<K, V> entry;
		if (hasher != Hasher.defaul()) Logger.FALLBACK.warn("Custom hasher "+hasher+"(A "+hasher.getClass().getName()+") generate many("+loop+") hash collisions for "+ key.getClass().getName(), new Throwable());
		if (!acceptTreeNode()) return false;

		AbstractEntry<K, V>[] arr = Helpers.cast(new AbstractEntry<?,?>[MathUtils.nextPowerOfTwo(loop +1)]);
		int i = 0;

		int slot = hasher.hashCode(key)&mask;
		entry = Helpers.cast(entries[slot]);
		while (entry != null) {
			arr[i++] = entry;
			entry = entry.next;
		}

		boolean b = key instanceof Comparable;
		Arrays.sort(arr, 0, i, (o1, o2) -> {
			int v = Integer.compare(hasher.hashCode(o1.key), hasher.hashCode(o2.key));
			if (v != 0) return v;
			if (b) v = ((Comparable<?>) o1.key).compareTo(Helpers.cast(o2.key));
			if (v != 0) return v;
			return Integer.compare(System.identityHashCode(o1.key), System.identityHashCode(o2.key));
		});

		ListEntry listEntry = new ListEntry(b);
		listEntry.key = arr;
		listEntry.len = i;
		listEntry.slot = slot;
		entries[slot] = listEntry;
		return true;
	}
	@SuppressWarnings("unchecked")
	private void untreeify(AbstractEntry<K, V> entry, int newMask, AbstractEntry<?, ?>[] newEntries) {
		AbstractEntry<K, V>[] arr = (AbstractEntry<K, V>[]) entry.key;
		for (int k = ((ListEntry) entry).len - 1; k >= 0; k--) {
			entry = arr[k];
			int newKey = hasher.hashCode(entry.key) & newMask;
			AbstractEntry<K, V> old = (AbstractEntry<K, V>) newEntries[newKey];
			newEntries[newKey] = entry;
			entry.next = old;
		}
	}

	protected boolean acceptTreeNode() { return true; }

	@SuppressWarnings("unchecked")
	@Contract("_, true -> !null")
	private AbstractEntry<K, V> getFirst(K key, boolean create) {
		if (key == UNDEFINED) throw new FastFailException("IntMap.UNDEFINED cannot be key");

		int i = hasher.hashCode(key)&mask;
		if (entries == null) {
			if (!create) return null;
			entries = new AbstractEntry<?, ?>[mask+1];
		}
		AbstractEntry<K, V> entry;
		if ((entry = (AbstractEntry<K, V>) entries[i]) == null) {
			if (!create) return null;
			entries[i] = entry = useEntry();
		}
		return entry;
	}

	protected AbstractEntry<K, V> useEntry() {
		AbstractEntry<K, V> entry = new Entry<>();
		entry.key = Helpers.cast(UNDEFINED);
		return entry;
	}
}