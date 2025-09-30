package roj.collect;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.ci.annotation.Public;
import roj.math.MathUtils;
import roj.reflect.Bypass;
import roj.reflect.Unsafe;

import java.util.*;

import static roj.collect.IntMap.REFERENCE_LOAD_FACTOR;
import static roj.reflect.Unsafe.U;

/**
 * 使用Object Instance自身作为Entry从而节省内存的HashMap
 * @author Roj234
 * @since 2024/1/26 06:09
 */
public final class XashMap<K, V> extends AbstractSet<V> {
	public static <K, V> Builder<K, V> builder(Class<K> kType, Class<V> vType, String field_key, String field_next) { return builder(kType, vType, field_key, field_next, Hasher.defaul()); }
	public static <K, V> Builder<K, V> builder(Class<K> kType, Class<V> vType, String field_key, String field_next, Hasher<K> hasher) {
		Bypass<ObjectNew> da = Bypass.builder(ObjectNew.class).unchecked();
		try {
			da.construct(vType, "createValue", kType);
		} catch (IllegalArgumentException e) {
			da.construct(vType, "createValueNoarg");
		}

		ObjectNew creator = da.build();
		long key = Unsafe.fieldOffset(vType, field_key);
		long next = Unsafe.fieldOffset(vType, field_next);
		return new Builder<>(vType, next, key, hasher, creator);
	}
	public static <K, V> Builder<K, V> noCreation(Class<V> vType, String field_key) {return noCreation(vType, field_key, "_next");}
	public static <K, V> Builder<K, V> noCreation(Class<V> vType, String field_key, String field_next) {return noCreation(vType, field_key, field_next, Hasher.defaul());}
	public static <K, V> Builder<K, V> noCreation(Class<V> vType, String field_key, String field_next, Hasher<K> hasher) {
		long key = Unsafe.fieldOffset(vType, field_key);
		long next = Unsafe.fieldOffset(vType, field_next);
		return new Builder<>(vType, next, key, hasher, null);
	}

	@Public
	private interface ObjectNew {
		default Object createValue(Object key) { return createValueNoarg(); }
		Object createValueNoarg();
	}
	public static final class Builder<K, V> {
		final Class<V> type;
		final long NEXT, KEY;
		final Hasher<K> hasher;
		final ObjectNew creater;

		Builder(Class<V> type, long nextOffset, long keyOffset, Hasher<K> hasher, ObjectNew creater) {
			this.type = type;
			NEXT = nextOffset;
			KEY = keyOffset;
			this.hasher = hasher;
			this.creater = creater;
		}

		public XashMap<K, V> create() { return new XashMap<>(this, 16); }
		public XashMap<K, V> createSized(int capacity) { return new XashMap<>(this, capacity); }
		public XashMap<K, V> createValued(Collection<V> values) {
			XashMap<K, V> set = createSized(values.size());
			for (V value : values) set.add(value);
			return set;
		}

		final int hashCode(K k) { return hasher.hashCode(k); }
		final boolean equals(K from_argument, Object stored_in) { return hasher.equals(from_argument, stored_in); }
		@SuppressWarnings("unchecked")
		final V checkCast(Object s) {
			if (!type.isAssignableFrom(s.getClass())) throw new ClassCastException(s.getClass()+" cannot cast to "+type);
			return (V) s;
		}

		@SuppressWarnings("unchecked")
		final K GetKey(@NotNull Object obj) { return (K) U.getReference(obj, KEY); }
		final void SetKey(@NotNull V obj, K key) { U.putReference(obj, KEY, key); }
		final Object GetNext(@NotNull Object obj) { return U.getReference(obj, NEXT); }
		final void SetNext(@NotNull Object obj, Object next) { U.putReference(obj, NEXT, next); }

		@SuppressWarnings("unchecked")
		final V createValue(K k) {
			if (creater == null) throw new UnsupportedOperationException("creation unavailable");
			V v = (V) creater.createValue(k);
			SetKey(v, k);
			return v;
		}
	}

	private Object[] entries;
	int size, mask;
	private final Builder<K, V> builder;

	XashMap(Builder<K, V> builder, int size) { this.builder = builder; ensureCapacity(size); }

	public void ensureCapacity(int size) {
		if (size < mask+1) return;
		int length = MathUtils.nextPowerOfTwo(size);
		if (entries != null) resize(length);
		else mask = length-1;
	}

	@NotNull
	public Iterator<V> iterator() { return new Itr(); }

	public int size() { return size; }

	@Override
	public final boolean contains(Object o) { return get(builder.GetKey(builder.checkCast(o))) != null; }
	public boolean containsKey(Object o) { return get(o) != null; }
	public V get(Object k) { return getOrDefault(k, null); }
	@SuppressWarnings("unchecked")
	public V getOrDefault(Object k, V def) {
		if (entries != null) {
			Object obj = entries[builder.hashCode((K) k)&mask];
			while (obj != null) {
				if (builder.equals((K) k, builder.GetKey(obj))) return (V) obj;
				obj = builder.GetNext(obj);
			}
		}
		return def;
	}

	public boolean add(@NotNull V value) { return null == put1(builder.GetKey(builder.checkCast(value)), value, false, false); }
	public boolean set(@NotNull V value) { return null == put1(builder.GetKey(builder.checkCast(value)), value, true, false); }

	public V put(K key, @NotNull V val) {
		if (val == null) throw new NullPointerException("val");
		return put1(key, val, true, false);
	}
	public V putIfAbsent(K key, @NotNull V val) {
		if (val == null) throw new NullPointerException("val");
		return put1(key, val, false, false);
	}
	public V computeIfAbsent(K key) { return put1(key, null, false, true); }
	@NotNull public V intern(@NotNull V node) {return put1(builder.GetKey(builder.checkCast(node)), node, false, true);}

	@SuppressWarnings("unchecked")
	@Contract("_,_,_,true -> !null")
	private V put1(K k, V v, boolean replace, boolean add) {
		int i = builder.hashCode(k)&mask;

		if (v != null && builder.GetNext(builder.checkCast(v)) == v)
			throw new UnsupportedOperationException("entry "+v+" 被锁定，无法加入map");

		if (entries == null) entries = new Object[mask+1];
		Object obj = entries[i];
		if (obj != null) {
			Object prev = null;
			while (obj != null) {
				if (builder.equals(k, builder.GetKey(obj))) {
					if (replace) {
						if (v == null) v = builder.createValue(k);

						builder.SetNext(v, builder.GetNext(obj));
						builder.SetNext(obj, null);

						if (prev == null) entries[i] = v;
						else builder.SetNext(prev, v);

						return add ? v : null;
					}
					return (V) obj;
				}

				prev = obj;
				obj = builder.GetNext(obj);
			}
		}


		if (v == null) v = builder.createValue(k);

		builder.SetNext(v, entries[i]);
		entries[i] = v;

		if (++size > mask * REFERENCE_LOAD_FACTOR) resize((mask+1)<<1);
		return add ? v : null;
	}

	@Override
	public boolean remove(Object o) { return removeKey(builder.GetKey(builder.checkCast(o))) != null; }
	@SuppressWarnings("unchecked")
	public V removeKey(K key) {
		if (entries == null) return null;

		int i = builder.hashCode(key)&mask;
		Object entry = entries[i], prev = null;
		if (entry == null) return null;

		while (!builder.equals(key, builder.GetKey(entry))) {
			prev = entry;

			entry = builder.GetNext(entry);
			if (entry == null) return null;
		}

		size--;

		Object next = builder.GetNext(entry);
		if (prev != null) builder.SetNext(prev, next);
		else entries[i] = next;

		builder.SetNext(entry, null);
		return (V) entry;
	}

	boolean removeInternal(V v, int hash) {
		if (entries == null) return false;

		int i = hash&mask;
		Object entry = entries[i], prev = null;
		if (entry == null) return false;

		while (entry != v) {
			prev = entry;

			entry = builder.GetNext(entry);
			if (entry == null) return false;
		}

		size--;

		Object next = builder.GetNext(entry);
		if (prev != null) builder.SetNext(prev, next);
		else entries[i] = next;

		builder.SetNext(entry, null);
		return true;
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		if (entries != null) Arrays.fill(entries, null);
	}

	private void resize(int len) {
		Object[] newEntries = new Object[len];
		len--;

		int i = 0, j = entries.length;
		for (; i < j; i++) {
			Object entry = entries[i];
			while (entry != null) {
				int newKey = builder.hashCode(builder.GetKey(entry))&len;

				Object next = builder.GetNext(entry);

				Object old = newEntries[newKey];
				builder.SetNext(entry, old);
				newEntries[newKey] = entry;

				entry = next;
			}
		}

		this.entries = newEntries;
		this.mask = len;
	}

	final class Itr extends AbstractIterator<V> {
		private Object[] localEntries;
		private Object entry;
		private int i;

		public Itr() { reset(); }

		@Override
		public void reset() {
			localEntries = entries;
			i = 0;
			entry = null;
			stage = localEntries == null ? ENDED : INITIAL;
		}

		@Override
		@SuppressWarnings("unchecked")
		public boolean computeNext() {
			checkConcMod();
			while (true) {
				if (entry != null) {
					result = (V) entry;
					entry = builder.GetNext(entry);
					return true;
				} else {
					Object[] ent = localEntries;
					if (i >= ent.length) return false;

					entry = ent[i++];
				}
			}
		}

		@Override
		protected void remove(V obj) {
			checkConcMod();
			Object entry = entries[i-1], prev = null;

			chk: {
				while (entry != null) {
					if (entry == result) break chk;

					prev = entry;
					entry = builder.GetNext(entry);
				}

				throw new ConcurrentModificationException();
			}

			Object next = builder.GetNext(entry);
			builder.SetNext(entry, null);

			if (prev != null) builder.SetNext(prev, next);
			else entries[i-1] = next;

			size--;
		}

		private void checkConcMod() {
			if (localEntries != entries) throw new ConcurrentModificationException();
		}
	}

	final K _valueGetKey(V next) {return builder.GetKey(next);}
}