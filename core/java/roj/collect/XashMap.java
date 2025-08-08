package roj.collect;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.reflect.Bypass;
import roj.ci.annotation.Public;
import roj.reflect.Unaligned;

import java.util.*;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2024/1/26 06:09
 */
public final class XashMap<K, V> extends AbstractSet<V> {
	public static <K, V> Builder<K, V> builder(Class<K> kType, Class<V> vType, String field_key, String field_next) { return builder(kType, vType, field_key, field_next, Hasher.defaul()); }
	public static <K, V> Builder<K, V> builder(Class<K> kType, Class<V> vType, String field_key, String field_next, Hasher<K> hasher) {
		Bypass<ObjectNew> da = Bypass.builder(ObjectNew.class).weak().unchecked();
		try {
			da.construct(vType, "createValue", kType);
		} catch (IllegalArgumentException e) {
			da.construct(vType, "createValueNoarg");
		}

		ObjectNew creator = da.build();
		long key = Unaligned.fieldOffset(vType, field_key);
		long next = Unaligned.fieldOffset(vType, field_next);
		return new Builder<>(vType, next, key, hasher, creator);
	}
	public static <K, V> Builder<K, V> noCreation(Class<V> vType, String field_key) {return noCreation(vType, field_key, "_next");}
	public static <K, V> Builder<K, V> noCreation(Class<V> vType, String field_key, String field_next) {return noCreation(vType, field_key, field_next, Hasher.defaul());}
	public static <K, V> Builder<K, V> noCreation(Class<V> vType, String field_key, String field_next, Hasher<K> hasher) {
		long key = Unaligned.fieldOffset(vType, field_key);
		long next = Unaligned.fieldOffset(vType, field_next);
		return new Builder<>(vType, next, key, hasher, null);
	}

	@Public
	private interface ObjectNew {
		default Object createValue(Object key) { return createValueNoarg(); }
		Object createValueNoarg();
	}
	public static final class Builder<K, V> {
		final Class<V> type;
		final long next_offset, key_offset;
		final Hasher<K> hasher;
		final ObjectNew creater;

		Builder(Class<V> type, long nextOffset, long keyOffset, Hasher<K> hasher, ObjectNew creater) {
			this.type = type;
			next_offset = nextOffset;
			key_offset = keyOffset;
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
		final K GET_KEY(@NotNull Object obj) { return (K) U.getReference(obj, key_offset); }
		final void SET_KEY(@NotNull V obj, K key) { U.putReference(obj, key_offset, key); }
		final Object GET_NEXT(@NotNull Object obj) { return U.getReference(obj, next_offset); }
		final void SET_NEXT(@NotNull Object obj, Object next) { U.putReference(obj, next_offset, next); }

		@SuppressWarnings("unchecked")
		final V createValue(K k) {
			if (creater == null) throw new UnsupportedOperationException("creation unavailable");
			V v = (V) creater.createValue(k);
			SET_KEY(v, k);
			return v;
		}
	}

	private Object[] entries;
	int size, mask = 1;
	private final Builder<K, V> builder;

	static final float LOAD_FACTOR = 1f;

	XashMap(Builder<K, V> builder, int size) { this.builder = builder; ensureCapacity(size); }

	public void ensureCapacity(int size) {
		if (size < mask+1) return;
		mask = MathUtils.getMin2PowerOf(size)-1;
		if (entries != null) resize(mask+1);
	}

	@NotNull
	public Iterator<V> iterator() { return size == 0 ? Collections.emptyIterator() : new ValueItr(); }
	public AbstractIterator<V> valItr() { return new ValueItr(); }

	public int size() { return size; }

	@Override
	public final boolean contains(Object o) { return get(builder.GET_KEY(builder.checkCast(o))) != null; }
	public boolean containsKey(Object o) { return get(o) != null; }
	public V get(Object k) { return getOrDefault(k, null); }
	@SuppressWarnings("unchecked")
	public V getOrDefault(Object k, V def) {
		if (entries != null) {
			Object obj = entries[builder.hashCode((K) k)&mask];
			while (obj != null) {
				if (builder.equals((K) k, builder.GET_KEY(obj))) return (V) obj;
				obj = builder.GET_NEXT(obj);
			}
		}
		return def;
	}

	public boolean add(@NotNull V value) { return null == put1(builder.GET_KEY(builder.checkCast(value)), value, false, false); }
	public boolean set(@NotNull V value) { return null == put1(builder.GET_KEY(builder.checkCast(value)), value, true, false); }

	public V put(K key, @NotNull V val) {
		if (val == null) throw new NullPointerException("val");
		return put1(key, val, true, false);
	}
	public V putIfAbsent(K key, @NotNull V val) {
		if (val == null) throw new NullPointerException("val");
		return put1(key, val, false, false);
	}
	public V computeIfAbsent(K key) { return put1(key, null, false, true); }
	@NotNull public V intern(@NotNull V node) {return put1(builder.GET_KEY(builder.checkCast(node)), node, false, true);}

	@SuppressWarnings("unchecked")
	@Contract("_,_,_,true -> !null")
	private V put1(K k, V v, boolean replace, boolean add) {
		int i = builder.hashCode(k)&mask;

		if (v != null && builder.GET_NEXT(builder.checkCast(v)) == v)
			throw new UnsupportedOperationException("entry "+v+" 被锁定，无法加入map");

		if (entries == null) entries = new Object[mask+1];
		Object obj = entries[i];
		if (obj != null) {
			Object prev = null;
			while (obj != null) {
				if (builder.equals(k, builder.GET_KEY(obj))) {
					if (replace) {
						if (v == null) v = builder.createValue(k);

						builder.SET_NEXT(v, builder.GET_NEXT(obj));
						builder.SET_NEXT(obj, null);

						if (prev == null) entries[i] = v;
						else builder.SET_NEXT(prev, v);

						return add ? v : null;
					}
					return (V) obj;
				}

				prev = obj;
				obj = builder.GET_NEXT(obj);
			}
		}


		if (v == null) v = builder.createValue(k);

		builder.SET_NEXT(v, entries[i]);
		entries[i] = v;

		if (++size > mask * LOAD_FACTOR) resize((mask+1)<<1);
		return add ? v : null;
	}

	@Override
	public boolean remove(Object o) { return removeKey(builder.GET_KEY(builder.checkCast(o))) != null; }
	@SuppressWarnings("unchecked")
	public V removeKey(K key) {
		if (entries == null) return null;

		int i = builder.hashCode(key)&mask;
		Object entry = entries[i], prev = null;
		if (entry == null) return null;

		while (!builder.equals(key, builder.GET_KEY(entry))) {
			prev = entry;

			entry = builder.GET_NEXT(entry);
			if (entry == null) return null;
		}

		size--;

		Object next = builder.GET_NEXT(entry);
		if (prev != null) builder.SET_NEXT(prev, next);
		else entries[i] = next;

		builder.SET_NEXT(entry, null);
		return (V) entry;
	}

	boolean removeInternal(V v, int hash) {
		if (entries == null) return false;

		int i = hash&mask;
		Object entry = entries[i], prev = null;
		if (entry == null) return false;

		while (entry != v) {
			prev = entry;

			entry = builder.GET_NEXT(entry);
			if (entry == null) return false;
		}

		size--;

		Object next = builder.GET_NEXT(entry);
		if (prev != null) builder.SET_NEXT(prev, next);
		else entries[i] = next;

		builder.SET_NEXT(entry, null);
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
				int newKey = builder.hashCode(builder.GET_KEY(entry))&len;

				Object next = builder.GET_NEXT(entry);

				Object old = newEntries[newKey];
				builder.SET_NEXT(entry, old);
				newEntries[newKey] = entry;

				entry = next;
			}
		}

		this.entries = newEntries;
		this.mask = len;
	}

	final class ValueItr extends AbstractIterator<V> {
		private Object[] localEntries;
		private Object entry;
		private int i;

		public ValueItr() { reset(); }

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
					entry = builder.GET_NEXT(entry);
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
					entry = builder.GET_NEXT(entry);
				}

				throw new ConcurrentModificationException();
			}

			Object next = builder.GET_NEXT(entry);
			builder.SET_NEXT(entry, null);

			if (prev != null) builder.SET_NEXT(prev, next);
			else entries[i-1] = next;

			size--;
		}

		private void checkConcMod() {
			if (localEntries != entries) throw new ConcurrentModificationException();
		}
	}

	final K _valueGetKey(V next) {return builder.GET_KEY(next);}
}