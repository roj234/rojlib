package roj.collect;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;

import java.util.*;
import java.util.function.Consumer;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author Roj234
 * @since 2021/6/15 21:33
 */
public final class MyHashSet<K> extends AbstractSet<K> implements FindSet<K> {
	protected static final class Entry {
		public Object k, next;
		Entry(Object k) {this.k = k;}
		@Override
		public String toString() {return "{" + k + '}';}
	}

	private boolean hasNull;
	private Object[] entries;
	int size, mask = 1;
	private final Hasher<K> hasher;

	static final float LOAD_FACTOR = 1f;

	public MyHashSet() { this(16); }
	public MyHashSet(int size) { hasher = Hasher.defaul(); ensureCapacity(size); }

	public MyHashSet(Hasher<K> hasher) { this(16, hasher); }
	public MyHashSet(int size, Hasher<K> hasher) { this.hasher = hasher; ensureCapacity(size); }

	@SafeVarargs
	@SuppressWarnings("varargs")
	public MyHashSet(K... arr) {
		hasher = Hasher.defaul();
		ensureCapacity(arr.length);
		this.addAll(arr);
	}
	public MyHashSet(Iterable<? extends K> list) {
		hasher = Hasher.defaul();
		for (K k : list) add(k);
	}
	public MyHashSet(Collection<? extends K> list) {
		hasher = Hasher.defaul();
		ensureCapacity(list.size());
		this.addAll(list);
	}

	public void ensureCapacity(int size) {
		if (size < mask+1) return;
		mask = MathUtils.getMin2PowerOf(size)-1;
		if (entries != null) resize(mask+1);
	}

	@NotNull
	public Iterator<K> iterator() {
		return isEmpty() ? Collections.emptyIterator() : new SetItr();
	}
	public AbstractIterator<K> setItr() { return new SetItr(); }

	public int size() { return size; }

	@Override
	@SuppressWarnings("unchecked")
	public K find(K k) {
		Object entry = find1(k);
		return entry == UNDEFINED ? k : (K) entry;
	}

	@SuppressWarnings("unchecked")
	public K intern(K k) {
		if (k == null) return null;

		if (size > mask * LOAD_FACTOR) {
			resize((mask+1) << 1);
		}

		Object entry = add1(k);
		if (entry == UNDEFINED) {
			size++;
			return k;
		}
		return (K) entry;
	}

	@SuppressWarnings("unchecked")
	private void resize(int len) {
		Object[] newEntries = new Object[len];
		len--;

		Entry entry;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			Object obj = entries[i];
			while (obj instanceof Entry) {
				entry = (Entry) obj;
				int newKey = hasher.hashCode((K) entry.k)&len;

				obj = entry.next;

				entry.next = newEntries[newKey];
				newEntries[newKey] = entry;
			}
			if (obj == null) continue;
			int newKey = hasher.hashCode((K) obj)&len;
			Object old = newEntries[newKey];
			if (old == null) {
				newEntries[newKey] = obj;
			} else if (old instanceof Entry) {
				entry = (Entry) old;
				if (entry.next != null) {
					Entry entry1 = new Entry(obj);
					entry1.next = entry;
					newEntries[newKey] = entry1;
				} else {
					entry.next = obj;
				}
			} else {
				Entry entry1 = new Entry(obj);
				entry1.next = old;
				newEntries[newKey] = entry1;
			}
		}

		this.entries = newEntries;
		this.mask = len;
	}

	public boolean add(K key) {
		if (key == null) {
			if (!hasNull) {
				hasNull = true;
				size++;
				return true;
			}
			return false;
		}

		if (size > mask * LOAD_FACTOR) {
			resize((mask+1)<<1);
		}

		Object entry = add1(key);
		if (UNDEFINED == entry) {
			size++;
			return true;
		}
		return false;
	}

	public boolean addAll(K[] collection) {
		boolean a = false;
		for (K k : collection) {
			a |= this.add(k);
		}
		return a;
	}

	public void deduplicate(Collection<K> otherSet) {
		for (var itr = otherSet.iterator(); itr.hasNext(); ) {
			if (!this.add(itr.next())) itr.remove();
		}
	}

	public boolean remove(Object o) {return remove1(o) != UNDEFINED;}
	@SuppressWarnings("unchecked")
	public K removeValue(Object o) {
		Object o1 = remove1(o);
		return o1 == UNDEFINED ? null : (K) o1;
	}
	@SuppressWarnings("unchecked")
	private Object remove1(Object o) {
		if (o == null) {
			if (hasNull) {
				hasNull = false;
				size--;
				return null;
			}
			return UNDEFINED;
		}
		if (entries == null) return UNDEFINED;

		K id = (K) o;

		int i = hasher.hashCode(id)&mask;
		Entry prev = null;
		Object obj = entries[i];

		chk: {
			while (obj instanceof Entry curr) {
				if (hasher.equals(id, curr.k)) break chk;
				prev = curr;
				obj = prev.next;
			}

			if (obj == null || !hasher.equals(id, obj)) return UNDEFINED;
		}

		size--;

		Object next = obj instanceof Entry ? ((Entry) obj).next : null;
		if (prev != null) prev.next = next;
		else entries[i] = next;

		return obj instanceof Entry entry ? entry.k : obj;
	}

	public boolean contains(Object o) {
		if (o == null) return hasNull;
		return find1(o) != UNDEFINED;
	}

	@SuppressWarnings("unchecked")
	@Contract("null -> null ; !null -> !null")
	public Object find1(Object id) {
		if (id == null) return null;
		if (entries == null) return UNDEFINED;

		Object obj = entries[hasher.hashCode((K) id)&mask];
		while (obj instanceof Entry prev) {
			if (hasher.equals((K) id, prev.k)) return prev.k;
			obj = prev.next;
		}
		if (obj != null && hasher.equals((K) id, obj)) return obj;
		return UNDEFINED;
	}

	private Object add1(K id) {
		int i = hasher.hashCode(id)&mask;
		if (entries == null) {
			entries = new Object[mask+1];
			entries[i] = id;
			return UNDEFINED;
		}
		Object obj = entries[i];
		if (obj == null) {
			entries[i] = id;
			return UNDEFINED;
		}

		while (obj instanceof Entry prev) {
			if (hasher.equals(id, prev.k)) return prev.k;
			if (prev.next == null) { // after resize()
				prev.next = id;
				return UNDEFINED;
			}
			obj = prev.next;
		}
		if (hasher.equals(id, obj)) return obj;

		Entry unused = new Entry(id);
		unused.next = entries[i];
		entries[i] = unused;
		return UNDEFINED;
	}

	public void clear() {
		if (size == 0) return;
		hasNull = false;
		size = 0;
		if (entries != null) Arrays.fill(entries, null);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void forEach(Consumer<? super K> action) {
		if (hasNull) action.accept(null);
		if (entries == null) return;
		for (Object obj : entries) {
			while (obj instanceof Entry prev) {
				action.accept((K) prev.k);
				obj = prev.next;
			}
			if (obj != null) action.accept((K) obj);
		}
	}

	final class SetItr extends AbstractIterator<K> {
		private Object[] prevList;
		private Object entry;
		private int i;

		public SetItr() { reset(); }

		@Override
		public void reset() {
			prevList = entries;
			i = 0;
			entry = null;
			stage = hasNull ? CHECKED : INITIAL;
		}

		@Override
		@SuppressWarnings("unchecked")
		public boolean computeNext() {
			checkConcMod();
			while (true) {
				if (entry != null) {
					if (entry instanceof Entry) {
						Entry entry = (Entry) this.entry;
						result = (K) entry.k;
						this.entry = entry.next;
					} else {
						result = (K) entry;
						entry = null;
					}
					return true;
				} else {
					Object[] ent = entries;
					if (ent != null && i < ent.length) {
						this.entry = ent[i++];
					} else {
						return false;
					}
				}
			}
		}

		@Override
		protected void remove(K obj) {
			if (obj == null) {
				hasNull = false;
			} else {
				checkConcMod();

				Entry prev = null;
				Object ent = entries[i-1];

				chk: {
					while (ent instanceof Entry curr) {
						if (curr.k == obj) break chk;
						prev = curr;
						ent = prev.next;
					}

					if (ent == null || obj != ent) {
						throw new ConcurrentModificationException();
					}
				}

				Object next = ent instanceof Entry ? ((Entry) ent).next : null;
				if (prev != null) prev.next = next;
				else entries[i-1] = next;
			}

			size--;
		}

		private void checkConcMod() {
			if (prevList != entries) throw new ConcurrentModificationException();
		}
	}
}