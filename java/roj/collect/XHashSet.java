package roj.collect;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.math.MathUtils;
import roj.reflect.Bypass;
import roj.reflect.Java22Workaround;
import roj.reflect.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.*;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2024/1/26 06:09
 */
public final class XHashSet<K, V> extends AbstractSet<V> {
	public static <K, V> Shape<K, V> shape(Class<K> kType, Class<V> vType, String field_key, String field_next) { return shape(kType, vType, field_key, field_next, Hasher.defaul()); }
	public static <K, V> Shape<K, V> shape(Class<K> kType, Class<V> vType, String field_key, String field_next, Hasher<K> hasher) {
		Bypass<ObjectNew> da = Bypass.builder(ObjectNew.class).weak().unchecked();
		try {
			da.construct(vType, "createValue", kType);
		} catch (IllegalArgumentException e) {
			da.construct(vType, "createValueNoarg");
		}
		try {
			ObjectNew creator = da.build();
			Field key = ReflectionUtils.getField(vType, field_key);
			Field next = ReflectionUtils.getField(vType, field_next);
			return new Shape<>(vType, U.objectFieldOffset(next), U.objectFieldOffset(key), hasher, creator);
		} catch (NoSuchFieldException e) {
			throw new IllegalArgumentException("无法找到字段", e);
		}
	}
	public static <K, V> Shape<K, V> noCreation(Class<V> vType, String field_key) {return noCreation(vType, field_key, "_next");}
	public static <K, V> Shape<K, V> noCreation(Class<V> vType, String field_key, String field_next) {return noCreation(vType, field_key, field_next, Hasher.defaul());}
	public static <K, V> Shape<K, V> noCreation(Class<V> vType, String field_key, String field_next, Hasher<K> hasher) {
		try {
			Field key = ReflectionUtils.getField(vType, field_key);
			Field next = ReflectionUtils.getField(vType, field_next);
			return new Shape<>(vType, U.objectFieldOffset(next), U.objectFieldOffset(key), hasher, null);
		} catch (NoSuchFieldException e) {
			throw new IllegalArgumentException("无法找到字段", e);
		}
	}

	@Java22Workaround
	private interface ObjectNew {
		default Object createValue(Object key) { return createValueNoarg(); }
		Object createValueNoarg();
	}
	public static final class Shape<K, V> {
		final Class<V> type;
		final long next_offset, key_offset;
		final Hasher<K> hasher;
		final ObjectNew creater;

		Shape(Class<V> type, long nextOffset, long keyOffset, Hasher<K> hasher, ObjectNew creater) {
			this.type = type;
			next_offset = nextOffset;
			key_offset = keyOffset;
			this.hasher = hasher;
			this.creater = creater;
		}

		public XHashSet<K, V> create() { return new XHashSet<>(this, 16); }
		public XHashSet<K, V> createSized(int capacity) { return new XHashSet<>(this, capacity); }
		public XHashSet<K, V> createValued(Collection<V> values) {
			XHashSet<K, V> set = createSized(values.size());
			for (V value : values) set.add(value);
			return set;
		}

		final int hashCode(@Nullable K k) { return hasher.hashCode(k); }
		final boolean equals(K from_argument, Object stored_in) { return hasher.equals(from_argument, stored_in); }
		@SuppressWarnings("unchecked")
		final V checkCast(Object s) {
			if (!type.isAssignableFrom(s.getClass())) throw new ClassCastException(s.getClass()+" cannot cast to "+type);
			return (V) s;
		}

		@SuppressWarnings("unchecked")
		final K GET_KEY(@NotNull Object obj) { return (K) U.getObject(obj, key_offset); }
		final void SET_KEY(@NotNull V obj, K key) { U.putObject(obj, key_offset, key); }
		final Object GET_NEXT(@NotNull Object obj) { return U.getObject(obj, next_offset); }
		final void SET_NEXT(@NotNull Object obj, Object next) { U.putObject(obj, next_offset, next); }

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
	private final Shape<K, V> shape;

	static final float LOAD_FACTOR = 1f;

	XHashSet(Shape<K, V> shape, int size) { this.shape = shape; ensureCapacity(size); }

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
	public final boolean contains(Object o) { return get(shape.GET_KEY(shape.checkCast(o))) != null; }
	public boolean containsKey(Object o) { return get(o) != null; }
	public V get(Object k) { return getOrDefault(k, null); }
	@SuppressWarnings("unchecked")
	public V getOrDefault(Object k, V def) {
		if (entries != null) {
			Object obj = entries[shape.hashCode((K) k)&mask];
			while (obj != null) {
				if (shape.equals((K) k, shape.GET_KEY(obj))) return (V) obj;
				obj = shape.GET_NEXT(obj);
			}
		}
		return def;
	}

	public boolean add(@NotNull V value) { return null == put1(shape.GET_KEY(shape.checkCast(value)), value, false, false); }
	public boolean set(@NotNull V value) { return null == put1(shape.GET_KEY(shape.checkCast(value)), value, true, false); }

	@Nullable public V put(K key, @NotNull V val) {
		if (val == null) throw new NullPointerException("val cannot be null");
		return put1(key, val, true, false);
	}
	@Nullable public V putIfAbsent(K key, @NotNull V val) {
		if (val == null) throw new NullPointerException("val cannot be null");
		return put1(key, val, false, false);
	}
	public V computeIfAbsent(K key) { return put1(key, null, false, true); }
	@NotNull public V intern(@NotNull V node) {return put1(shape.GET_KEY(shape.checkCast(node)), node, false, true);}

	@SuppressWarnings("unchecked")
	@Contract("_,_,_,true -> !null")
	private V put1(K k, V v, boolean replace, boolean add) {
		int i = shape.hashCode(k)&mask;

		if (v != null && shape.GET_NEXT(shape.checkCast(v)) == v)
			throw new UnsupportedOperationException("entry "+v+" 被锁定，无法加入map");

		if (entries == null) entries = new Object[mask+1];
		Object obj = entries[i];
		if (obj != null) {
			Object prev = null;
			while (obj != null) {
				if (shape.equals(k, shape.GET_KEY(obj))) {
					if (replace) {
						if (v == null) v = shape.createValue(k);

						shape.SET_NEXT(v, shape.GET_NEXT(obj));
						shape.SET_NEXT(obj, null);

						if (prev == null) entries[i] = v;
						else shape.SET_NEXT(prev, v);

						return add ? v : null;
					}
					return (V) obj;
				}

				prev = obj;
				obj = shape.GET_NEXT(obj);
			}
		}


		if (v == null) v = shape.createValue(k);

		shape.SET_NEXT(v, entries[i]);
		entries[i] = v;

		if (++size > mask * LOAD_FACTOR) resize((mask+1)<<1);
		return add ? v : null;
	}

	@Override
	public boolean remove(Object o) { return removeKey(shape.GET_KEY(shape.checkCast(o))) != null; }
	@SuppressWarnings("unchecked")
	public V removeKey(K key) {
		if (entries == null) return null;

		int i = shape.hashCode(key)&mask;
		Object entry = entries[i], prev = null;
		if (entry == null) return null;

		while (!shape.equals(key, shape.GET_KEY(entry))) {
			prev = entry;

			entry = shape.GET_NEXT(entry);
			if (entry == null) return null;
		}

		size--;

		Object next = shape.GET_NEXT(entry);
		if (prev != null) shape.SET_NEXT(prev, next);
		else entries[i] = next;

		shape.SET_NEXT(entry, null);
		return (V) entry;
	}

	boolean removeInternal(V v, int hash) {
		if (entries == null) return false;

		int i = hash&mask;
		Object entry = entries[i], prev = null;
		if (entry == null) return false;

		while (entry != v) {
			prev = entry;

			entry = shape.GET_NEXT(entry);
			if (entry == null) return false;
		}

		size--;

		Object next = shape.GET_NEXT(entry);
		if (prev != null) shape.SET_NEXT(prev, next);
		else entries[i] = next;

		shape.SET_NEXT(entry, null);
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
				int newKey = shape.hashCode(shape.GET_KEY(entry))&len;

				Object next = shape.GET_NEXT(entry);

				Object old = newEntries[newKey];
				shape.SET_NEXT(entry, old);
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
					entry = shape.GET_NEXT(entry);
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
					entry = shape.GET_NEXT(entry);
				}

				throw new ConcurrentModificationException();
			}

			Object next = shape.GET_NEXT(entry);
			shape.SET_NEXT(entry, null);

			if (prev != null) shape.SET_NEXT(prev, next);
			else entries[i-1] = next;

			size--;
		}

		private void checkConcMod() {
			if (localEntries != entries) throw new ConcurrentModificationException();
		}
	}

	final K _valueGetKey(V next) {return shape.GET_KEY(next);}
}