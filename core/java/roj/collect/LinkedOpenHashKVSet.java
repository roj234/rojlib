package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.util.Helpers;

import java.util.*;

/**
 * This impl is **MUCH SLOWER** than {@link XashMap}, however, it consumes less memory, preserves order, and not needed to create <code>_next</code> field.
 * * (-20%/get, -70%/churn)
 * @author Roj234
 * @since 2026/01/24 00:22
 */
public class LinkedOpenHashKVSet<K, V> extends AbstractSet<V> implements FindSet<V> {
	public static final LinkedOpenHashKVSet<Object, Object> EMPTY_SET = new LinkedOpenHashKVSet<>(0, Collections.emptyList());
	public static <K, V> LinkedOpenHashKVSet<K, V> emptySet() {return Helpers.cast(EMPTY_SET);}

	private static final float LOAD_FACTOR = 0.75f;

	public static int mix(int x) {
		int h = x * 0x9e3779b9;
		return h ^ h >>> 16;
	}

	// TODO use Lambda
	protected int hashCode(K key) {return mix(key.hashCode());}
	protected boolean equals(K key, V value) {return key.equals(getKey(value));}
	@SuppressWarnings("unchecked")
	protected K getKey(V value) {return (K) value;}

	private List<V> items;

	private int[] table;
	private int mask;
	private int deletedSize;

	public LinkedOpenHashKVSet() {this(16, new ArrayList<>());}
	public LinkedOpenHashKVSet(int capacity, List<V> items) {
		int n = MathUtils.nextPowerOfTwo(capacity);
		this.mask = n - 1;
		this.items = items;
	}
	@SuppressWarnings("CopyConstructorMissesField")
	protected LinkedOpenHashKVSet(LinkedOpenHashKVSet<K, V> copy) {
		if (copy.deletedSize > 0) {
			ArrayList<V> oldItems = (ArrayList<V>) copy.items;
			ArrayList<V> newItems = new ArrayList<>(copy.size());

			int size = oldItems.size();
			Object[] oldArray = oldItems.getInternalArray();
			Object[] newArray = newItems.getInternalArray();
			int j = 0;
			for (int i = 0; i < size; i++) {
				Object item = oldArray[i];
				if (item != null) {
					newArray[j++] = item;
				}
			}
			newItems._setSize(j);

			items = newItems;
		} else {
			items = new ArrayList<>(copy.items);
			if (copy.table != null)
				this.table = copy.table.clone();
		}
		this.mask = copy.mask;
	}

	public List<V> items() {return items;}

	@Override
	public final int size() {return items.size() - deletedSize;}

	@Override
	public @NotNull Iterator<V> iterator() {
		return new AbstractIterator<>() {
			int pos;

			@Override
			protected boolean computeNext() {
				while (pos < items.size()) {
					result = items.get(pos++);
					if (result != null) return true;
				}

				return false;
			}

			@Override
			protected void remove(V obj) {LinkedOpenHashKVSet.this.remove(obj);}
		};
	}

	@Override
	@SuppressWarnings("unchecked")
	public final boolean contains(Object o) {return getItem(getKey((V) o)) != null;}

	public final V get(K key) {return getItem(key);}

	@Override
	public final V find(V key) {
		V el = getItem(getKey(key));
		return el != null ? el : key;
	}

	@Override
	public final boolean add(V key) {return key == intern(key);}

	@Override
	public final V intern(V key) {
		int slot = addItem(key);
		if (slot >= 0) return items.get(slot);

		items.add(key);
		table[slot & 0x7FFFFFFF] = items.size();
		return key;
	}

	public final boolean setItem(V key, int index) {
		int slot = addItem(key);
		if (slot >= 0) return false;

		assert items.get(index) == key;
		table[slot & 0x7FFFFFFF] = index;
		return true;
	}

	public final void directAdd(V key) {
		if (deletedSize > 0) collapse((ArrayList<V>) items);
		items.add(key);
		table = null;
	}

	public void setItems(List<V> items) {
		this.items = items;
		deletedSize = 0;
		rehash();
	}
	public List<V> getItems() {return items;}

	@SuppressWarnings("unchecked")
	public int indexOf(Object key) {
		if (table != null) {
			int i = hashCode((K) key) & mask;
			int idx;
			while ((idx = table[i]) != 0) {
				V val = items.get(idx - 1);
				if (equals((K) key, val)) return idx - 1;
				i = (i + 1) & mask;
			}
		}

		return -1;
	}

	private V getItem(K key) {
		if (table != null) {
			int i = hashCode(key) & mask;
			int idx;
			while ((idx = table[i]) != 0) {
				V val = items.get(idx - 1);
				if (equals(key, val)) return val;
				i = (i + 1) & mask;
			}
		}
		return null;
	}

	private int addItem(V val) {
		if (table == null
			|| items.size() >= (table.length >= 8 ? (int)(table.length * LOAD_FACTOR) : mask)
			|| (deletedSize > 16 && deletedSize > size())
		) rehash();

		K key = getKey(val);
		int h = hashCode(key);
		int i = h & mask;

		int idx;
		while ((idx = table[i]) != 0) {
			V el = items.get(idx - 1);
			if (equals(key, el)) return idx - 1;
			i = (i + 1) & mask;
		}

		return i | 0x80000000;
	}

	@Override
	@SuppressWarnings("unchecked")
	public final boolean remove(Object val) {
		var key = getKey((V) val);
		return removeKey(key) != null;
	}
	public V removeKey(K key) {
		if (table == null) rehash();
		int[] tab = table;

		int i = hashCode(key) & mask;
		int idx;

		while ((idx = tab[i]) != 0) {
			if (equals(key, items.get(idx - 1))) {
				deletedSize++;
				backshiftKey(i);
				return items.set(idx - 1, null);
			}
			i = (i + 1) & mask;
		}

		return null;
	}

	private void backshiftKey(int cur) {
		var tab = table;

		while(true) {
			int prev = cur;
			cur = cur + 1 & mask;

			int idx;
			while(true) {
				if ((idx = tab[cur]) == 0) {
					tab[prev] = 0;
					return;
				}

				int curSlot = hashCode(getKey(items.get(idx - 1))) & mask;
				if (cur <= prev) {
					if (cur < curSlot && curSlot <= prev) break;
				} else {
					if (cur < curSlot || curSlot <= prev) break;
				}
				cur = cur + 1 & mask;
			}

			tab[prev] = idx;
		}
	}

	public void clear() {
		if (items.isEmpty()) return;
		items.clear();
		if (table != null) Arrays.fill(table, 0);
	}

	private void rehash() {
		if (deletedSize > 0) collapse((ArrayList<V>) items);

		int newCap = table == null ? mask+1 : MathUtils.nextPowerOfTwo(size() * 4 / 3 + 2);

		int[] table = new int[newCap];
		mask = newCap - 1;
		if (mask + 1 < size()) throw new AssertionError();

		for (int index = 0; index < items.size(); index++) {
			V val = items.get(index);
			K key = getKey(val);

			int i = hashCode(key) & mask;
			while (table[i] != 0) {
				i = (i + 1) & mask;
			}
			table[i] = index + 1;
		}

		this.table = table;
	}

	private void collapse(ArrayList<V> items) {
		int writeCursor = 0;
		int size = items.size();
		Object[] array = items.getInternalArray();

		for (int i = 0; i < size; i++) {
			Object item = array[i];
			if (item != null) array[writeCursor++] = item;
		}
		items.removeRange(writeCursor, items.size());
		deletedSize = 0;
	}
}
