package roj.collect;

import roj.math.MathUtils;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author Roj234
 * @since 2021/6/15 21:33
 */
public class MyHashSet<K> extends AbstractSet<K> implements FindSet<K> {
	protected static final class Entry {
		public Object k, next;

		Entry(Object k) {
			this.k = k;
		}

		@Override
		public String toString() {
			return "{" + k + '}';
		}
	}

	protected boolean hasNull;
	protected Object[] entries;
	protected int size, mask = 1;

	static final float LOAD_FACTOR = 1f;

	public MyHashSet() {
		this(16);
	}

	@SafeVarargs
	@SuppressWarnings("varargs")
	public MyHashSet(K... arr) {
		ensureCapacity(arr.length);
		this.addAll(arr);
	}

	public MyHashSet(Iterable<? extends K> list) {
		for (K k : list) {
			add(k);
		}
	}

	public MyHashSet(int size) {
		ensureCapacity(size);
	}

	public MyHashSet(Collection<? extends K> list) {
		ensureCapacity(list.size());
		this.addAll(list);
	}

	public void ensureCapacity(int size) {
		if (size < mask+1) return;
		mask = MathUtils.getMin2PowerOf(size)-1;
		if (entries != null) resize(mask+1);
	}

	@Nonnull
	public Iterator<K> iterator() {
		return isEmpty() ? Collections.emptyIterator() : new SetItr();
	}

	public AbstractIterator<K> setItr() {
		return new SetItr();
	}

	public int size() {
		return size;
	}

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
				int newKey = hashFor((K) entry.k)&len;

				obj = entry.next;

				Object old = newEntries[newKey];
				newEntries[newKey] = entry;
				entry.next = old;
			}
			if (obj == null) continue;
			int newKey = hashFor((K) obj)&len;
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
			resize( (mask+1)<<1);
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
		for (K k : otherSet) {
			if (!this.add(k)) {
				otherSet.remove(k);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public boolean remove(Object o) {
		if (o == null) {
			if (hasNull) {
				hasNull = false;
				size--;
				return true;
			}
			return false;
		}
		if (entries == null) return false;

		K id = (K) o;

		int i = hashFor(id)&mask;
		Entry prev = null;
		Object obj = entries[i];

		chk: {
			while (obj instanceof Entry) {
				Entry curr = (Entry) obj;
				if (eq(id, curr.k)) break chk;
				prev = curr;
				obj = prev.next;
			}

			if (obj == null || !eq(id, obj)) return false;
		}

		this.size--;

		Object next = obj instanceof Entry ? ((Entry) obj).next : null;
		if (prev != null) {
			prev.next = next;
		} else {
			this.entries[i] = next;
		}

		return true;
	}

	protected boolean eq(K input, Object entry) { return input.equals(entry); }

	public boolean contains(Object o) {
		if (o == null) return hasNull;
		return find1(o) != UNDEFINED;
	}

	@SuppressWarnings("unchecked")
	public Object find1(Object id) {
		if (entries == null) return UNDEFINED;
		if (id == null) return null;

		Object obj = entries[hashFor((K) id)&mask];
		while (obj instanceof Entry) {
			Entry prev = (Entry) obj;
			if (eq((K) id, prev.k)) return prev.k;
			obj = prev.next;
		}
		if (obj != null && eq((K) id, obj)) return obj;
		return UNDEFINED;
	}

	protected Object add1(K id) {
		int i = hashFor(id)&mask;
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

		while (obj instanceof Entry) {
			Entry prev = (Entry) obj;
			if (eq(id, prev.k)) return prev.k;
			if (prev.next == null) { // after resize()
				prev.next = id;
				return UNDEFINED;
			}
			obj = prev.next;
		}
		if (eq(id, obj)) return obj;

		Entry unused = new Entry(id);
		unused.next = entries[i];
		entries[i] = unused;
		return UNDEFINED;
	}

	protected int hashFor(K id) {
		int v = id.hashCode() * -1640531527;
		return (v ^ (v >>> 16));
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
			while (obj instanceof Entry) {
				Entry prev = (Entry) obj;
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
					while (ent instanceof Entry) {
						Entry curr = (Entry) ent;
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