package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;

import java.util.AbstractSet;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.PrimitiveIterator.OfInt;

import static roj.collect.AbstractIterator.*;
import static roj.collect.IntMap.MAX_NOT_USING;

/**
 * @author Roj234
 * @since 2023/4/3 10:22
 */
public class IntSet extends AbstractSet<Integer> {
	static final class Entry {
		int k, set;

		Entry(int k) {
			this.k = k;
		}

		Entry next;
	}

	private Entry[] entries;
	private int size = 0, mask = 1;

	private boolean resizeRequested;

	public IntSet() { this(16); }
	public IntSet(int size) { ensureCapacity(size); }

	public IntSet(int... arr) {
		ensureCapacity(arr.length);
		addAll(arr);
	}
	public IntSet(IntSet list) { addAll(list); }

	public void ensureCapacity(int size) {
		if (size <= mask+1) return;
		mask = MathUtils.getMin2PowerOf(size)-1;
		resize();
	}

	@NotNull
	public IntIterator iterator() {
		return new Itr(this);
	}

	public int[] toIntArray() {
		int[] result = new int[size];

		int i = 0;
		for (PrimitiveIterator.OfInt itr = this.iterator(); itr.hasNext(); ) {
			int v = itr.nextInt();
			result[i++] = v;
		}

		return result;
	}

	public int size() { return size; }

	public void resize() {
		if (entries == null) return;

		Entry[] newEntries = new Entry[mask+1];
		Entry entry, next;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			entry = entries[i];
			entries[i] = null;
			while (entry != null) {
				next = entry.next;

				int newKey = indexFor(entry.k);
				Entry old = newEntries[newKey];

				// dualEntry
				if ((entry.k&31) != 0) {
					if (entry.set != 0) {
						int newKey2 = indexFor(entry.set);
						if (newKey2 != newKey) {
							Entry entry1 = getCachedEntry(entry.set);
							entry.set = 0;

							Entry old1 = newEntries[newKey2];
							newEntries[newKey2] = entry1;
							entry1.next = old1;
						}
					}
				}

				newEntries[newKey] = entry;
				entry.next = old;
				entry = next;
			}
		}

		this.entries = newEntries;
	}

	public final boolean contains(Object o) { return contains((int) o); }
	public final boolean contains(int key) {
		int mKey = key & ~31;

		Entry e = getEntryFirst(key);
		while (e != null) {
			// bitset entry
			if (e.k == mKey) return (e.set&(1<<(key&31))) != 0;

			// key.isDual() && entry.isDual()
			if (key != mKey && (e.k & 31) != 0) {
				if (e.k == key || e.set == key) return true;
			}
			e = e.next;
		}
		return false;
	}

	public final boolean add(Integer v) { return add((int) v); }
	public final boolean add(int key) {
		if (resizeRequested) {
			resizeRequested = false;
			mask = ((mask+1) << 1) - 1;
			resize();
		}

		int mKey = key & ~31;

		if (entries == null) entries = new Entry[mask+1];

		int i = indexFor(key);
		Entry entry = entries[i];
		if (entry == null) {
			entry = entries[i] = getCachedEntry(key);

			if (key == mKey) {
				entry.set = 1;
				merge(key, entry);
			}

			size++;
			return true;
		}

		int depth = 0;
		Entry pendEntry = null, dualEntry = null;
		while (true) {
			// bitset entry
			if (entry.k == mKey) {
				int set = entry.set;
				if (set != (set |= 1<<(key&31) )) {
					entry.set = set;
					size++;
					return true;
				}
				return false;
			}

			// key.isDual() && entry.isDual()
			if (key != mKey && (entry.k & 31) != 0) {
				// 'dual' entry hold 2 ints, not bitset
				// better for random data
				if (entry.k == key || entry.set == key) return false;

				if (dualEntry == null) {
					if ((entry.k & ~31) == mKey) dualEntry = entry;
					// int#2
					else if (entry.set == 0) pendEntry = entry;
					else if ((entry.set & ~31) == mKey) {
						int t = entry.set;
						entry.set = entry.k;
						entry.k = t;

						dualEntry = entry;
					}
				}
			}

			if (entry.next == null) break;
			entry = entry.next;
			depth++;
		}

		if (depth > 3) resizeRequested = true;

		size++;

		// convert to bitset
		if (dualEntry != null) {
			int int1 = dualEntry.k;
			int int2 = dualEntry.set;

			dualEntry.k = mKey;
			dualEntry.set = (1<<(key&31)) | (1<<(int1&31));

			entry = merge(key, dualEntry);
			if (int2 != 0) {
				if ((int2 & ~31) == mKey) {
					dualEntry.set |= 1<<(int2&31);
				} else {
					entry.next = getCachedEntry(int2);
				}
			}
			return true;
		} else if (pendEntry != null) {
			pendEntry.set = key;
			return true;
		}

		entry = (entry.next = getCachedEntry(key));

		// force bitset
		if (key == mKey) {
			entry.set = 1;
			merge(key, entry);
		}
		return true;
	}

	@NotNull
	private Entry merge(int key, Entry dualEntry) {
		int mKey = dualEntry.k;

		Entry entry = entries[indexFor(key)];
		Entry prev = null;
		while (true) {
			if ((entry.k & 31) != 0) {
				if ((entry.set & ~31) == mKey) {
					dualEntry.set |= (1<<(entry.set&31));
					entry.set = 0;
				}

				if ((entry.k & ~31) == mKey) {
					dualEntry.set |= (1<<(entry.k&31));

					if (entry.set != 0) {
						entry.k = entry.set;
						entry.set = 0;
					} else {
						Entry next = entry.next;
						if (prev != null) prev.next = next;
						else entries[indexFor(key)] = next;

						putRemovedEntry(entry);
						entry = next != null ? next : prev;
						if (entry == null) throw new AssertionError();
					}
				}
			}

			if (entry.next == null) break;
			prev = entry;
			entry = entry.next;
		}
		return entry;
	}

	public final boolean remove(Object o) { return remove((int) o); }
	public final boolean remove(int key) {
		int mKey = key & ~31;

		Entry prev = null;
		Entry entry = getEntryFirst(key);
		while (entry != null) {
			// bitset entry
			if (entry.k == mKey) {
				int set = entry.set;
				if (set == (set &= ~(1<<(key&31)))) return false;

				size--;
				if (set == 0) {
					if (prev != null) prev.next = entry.next;
					else entries[indexFor(key)] = entry.next;

					putRemovedEntry(entry);
				} else {
					entry.set = set;
				}

				return true;
			}

			// key.isDual() && entry.isDual()
			if (key != mKey && (entry.k & 31) != 0) {
				if (entry.k == key) {
					size--;

					if (entry.set == 0) {
						if (prev != null) prev.next = entry.next;
						else entries[indexFor(key)] = entry.next;

						putRemovedEntry(entry);
					} else {
						entry.k = entry.set;
						entry.set = 0;
					}
					return true;
				}

				if (entry.set == key) {
					size--;
					entry.set = 0;
					return true;
				}
			}

			prev = entry;
			entry = entry.next;
		}
		return false;
	}

	public void addAll(IntSet other) {
		Entry[] ov = other.entries;
		if (ov == null) return;
		ensureCapacity(other.size());

		for (Entry entry : ov) {
			while (entry != null) {
				add(entry.k);
				entry = entry.next;
			}
		}
	}

	public boolean containsAll(int... array) {
		for (int o : array) {
			if (!contains(o)) return false;
		}
		return true;
	}

	public boolean addAll(int... array) {
		boolean a = false;
		for (int k : array) {
			a |= add(k);
		}
		return a;
	}

	public boolean removeAll(int... array) {
		boolean k = false;
		for (int o : array) {
			k |= remove(o);
		}
		return k;
	}

	public boolean intersection(IntSet other) {
		boolean m = false;
		for (OfInt itr = iterator(); itr.hasNext(); ) {
			int i = itr.nextInt();
			if (!other.contains(i)) {
				itr.remove();
				m = true;
			}
		}
		return m;
	}

	private int indexFor(int id) {
		id >>= 5;
		return (id ^ (id >>> 16)) & mask;
	}

	private Entry notUsing = null;
	private Entry getCachedEntry(int key) {
		Entry entry = notUsing;
		if (entry != null) {
			notUsing = entry.next;

			entry.k = key;
			entry.set = 0;
			entry.next = null;
			return entry;
		}

		return new Entry(key);
	}
	private void putRemovedEntry(Entry entry) {
		if (notUsing != null && notUsing.k > MAX_NOT_USING) return;

		entry.next = notUsing;
		entry.k = notUsing == null ? 1 : notUsing.k+1;
		notUsing = entry;
	}

	private Entry getEntryFirst(int key) {
		return entries == null ? null : entries[indexFor(key)];
	}

	public void clear() {
		if (size == 0) return;
		size = 0;

		for (int i = 0; i < entries.length; i++) {
			if (entries[i] != null) {
				putRemovedEntry(entries[i]);
				entries[i] = null;
			}
		}
	}

	private static final class Itr implements IntIterator {
		private byte stage;
		private int i, set, value;
		private Entry entry;

		private final IntSet map;
		private final Entry[] arr;

		Itr(IntSet map) {
			this.map = map;
			arr = map.entries;
			if (arr == null) stage = ENDED;
		}

		private boolean computeNext() {
			while (true) {
				if (set != 0) {
					while (true) {
						boolean hasNext = (set & 1) == 0;
						value++;
						set >>>= 1;
						if (!hasNext) return true;
					}
				}

				find: {
					if (entry != null) {
						if ((entry.k&31) != 0 && value == entry.k) {
							if (entry.set != 0) {
								value = entry.set;
								return true;
							}
						}

						if (entry.next != null) {
							entry = entry.next;
							break find;
						}
					}

					do {
						if (i == arr.length) return false;
						entry = arr[i++];
					} while (entry == null);
				}

				// dual entry
				if ((entry.k&31) != 0) {
					value = entry.k;
					return true;
				} else {
					set = entry.set;
					value = entry.k-1;
				}
			}
		}

		@Override
		public final boolean hasNext() {
			check();
			return stage != ENDED;
		}

		@Override
		public int nextInt() {
			check();
			if (stage == ENDED) throw new NoSuchElementException();
			stage = GOTTEN;
			return value;
		}

		@Override
		public final void remove() {
			if (stage != GOTTEN) throw new IllegalStateException();

			int v = value;
			check();
			map.remove(v);
		}

		private void check() {
			if (stage <= 1) {
				stage = computeNext() ? CHECKED : ENDED;
			}
		}
	}
}