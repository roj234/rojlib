package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author Roj234
 * @since 2026/01/24 00:22
 */
public class LinkedOpenHashSet<K, V> extends AbstractSet<V> implements FindSet<V> {
	private static final float LOAD_FACTOR = 0.75f;

	protected int hashCode(K key) {return key.hashCode();}
	protected boolean equals(K key, V value) {return key.equals(value);}
	@SuppressWarnings("unchecked")
	protected K getKey(V value) {return (K) value;}

	private List<V> items;

	private int[] table;
	private int mask;
	private int deletedSize;

	public LinkedOpenHashSet() {this(16, new ArrayList<>());}
	public LinkedOpenHashSet(int capacity, List<V> items) {
		int n = MathUtils.nextPowerOfTwo(capacity);
		this.mask = n - 1;
		this.items = items;
	}

	public List<V> items() {return items;}

	@Override
	public final int size() {return items.size() - deletedSize;}

	@Override
	public @NotNull Iterator<V> iterator() {
		if (deletedSize > 0) {
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
			};
		}
		return items.iterator();
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

	public final boolean addItem(V key, int index) {
		int slot = addItem(key);
		if (slot >= 0) return false;

		table[slot & 0x7FFFFFFF] = index;
		return true;
	}

	private V getItem(K key) {
		if (table != null) {
			int h = hashCode(key);
			int i = h & mask;
			int idx = table[i];
			while (idx != 0) {
				if (idx != -1) {
					V el = items.get(idx - 1);
					if (equals(key, el)) return el;
				}
				i = (i + 1) & mask;
				idx = table[i];
			}
		}
		return null;
	}

	private int addItem(V val) {
		if (table == null) table = new int[mask + 1];
		if (items.size() > table.length * LOAD_FACTOR) rehash();

		K key = getKey(val);
		int h = hashCode(key);
		int i = h & mask;
		int firstRemoved = -1;

		int idx = table[i];
		while (idx != 0) {
			if (idx != -1) {
				V el = items.get(idx - 1);
				if (equals(key, el)) return idx - 1;
			} else {
				if (firstRemoved == -1) firstRemoved = i;
			}

			i = (i + 1) & mask;
			idx = table[i];
		}

		if (firstRemoved != -1) {
			i = firstRemoved;
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
		int h = hashCode(key);
		int i = h & mask;

		int idx = table[i];
		while (idx != 0) {
			if (idx != -1 && equals(key, items.get(idx - 1))) {
				// 如果本身是最后一个
				table[i] = table[(i + 1) & mask] == 0 ? 0 : -1;
				V val = items.set(idx - 1, null);
				deletedSize++;
				return val;
			}
			i = (i + 1) & mask;
			idx = table[i];
		}
		return null;
	}

	public void clear() {
		if (items.isEmpty()) return;
		items.clear();
		Arrays.fill(table, 0);
	}

	private void rehash() {
		int[] oldTable = table;

		int newCap = oldTable.length << 1;
		table = new int[newCap];
		mask = newCap - 1;

		if (deletedSize > 0) {
			var oldItems = items;
			items = new ArrayList<>(size());
			for (int i = 0; i < oldItems.size(); i++) {
				V item = oldItems.get(i);
				if (item != null) items.add(item);
			}
			deletedSize = 0;
		}

		for (int index = 0; index < items.size(); index++) {
			V val = items.get(index);
			K key = getKey(val);

			int h = hashCode(key);
			int i = h & mask;

			int idx = table[i];
			while (idx != 0) {
				i = (i + 1) & mask;
				idx = table[i];
			}
			table[i] = index + 1;
		}
	}
}
