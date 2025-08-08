package roj.collect;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2024/5/15 14:33
 */
public class CollectionX {
	@SuppressWarnings("unchecked")
	private static final class CollectionView<To, From> extends AbstractSet<To> {
		private final Collection<From> from;
		private final Function<From, To> mapper;
		private final Function<To, From> unmapper;

		public CollectionView(Collection<From> from, Function<From, To> mapper, Function<To, From> unmapper) {
			this.from = from;
			this.mapper = mapper;
			this.unmapper = unmapper;
		}

		public int size() {return from.size();}
		public boolean contains(Object o) {return unmapper == null ? super.contains(o) : from.contains(unmapper.apply((To) o));}
		public boolean add(To p) {return unmapper == null ? super.add(p) : from.add(unmapper.apply(p));}
		public boolean remove(Object o) {return unmapper == null ? super.remove(o) : from.remove(unmapper.apply((To) o));}

		@NotNull
		@Override
		public Iterator<To> iterator() {
			Iterator<From> itr = from.iterator();
			return new AbstractIterator<>() {
				@Override
				protected boolean computeNext() {
					if (!itr.hasNext()) return false;
					result = mapper.apply(itr.next());
					return true;
				}

				@Override
				protected void remove(To obj) {
					if (unmapper != null) from.remove(unmapper.apply(obj));
					else super.remove(obj);
				}
			};
		}

		@NotNull
		@Override
		@SuppressWarnings("unchecked")
		public Object[] toArray() {
			Object[] array = from.toArray();
			for (int i = 0; i < array.length; i++) {
				array[i] = mapper.apply((From) array[i]);
			}
			return array;
		}

		@Override
		public void clear() {from.clear();}
	}

	public static <From, To> Collection<To> mapToView(Collection<From> from, Function<From, To> mapper) {return new CollectionView<>(from, mapper, null);}
	public static <From, To> Collection<To> mapToView(Collection<From> from, Function<From, To> mapper, Function<To, From> unmapper) {return new CollectionView<>(from, mapper, unmapper);}

	public static <T> Map<T, T> toMap(Collection<T> name) {return toMap(name, Function.identity());}
	public static <From, To> Map<From, To> toMap(Collection<From> from, Function<From, To> mapper) {return new CMap<>(from, mapper);}
	@SuppressWarnings("unchecked")
	private static final class CMap<From, To> extends AbstractMap<From, To> implements _LibMap<_LibEntry> {
		private final Collection<From> from;
		private final Function<From, To> mapper;

		public CMap(Collection<From> from, Function<From, To> mapper) {
			this.from = from;
			this.mapper = mapper;
		}

		@NotNull
		public Set<Entry<From, To>> entrySet() {return _LibEntrySet.create(this);}
		public int size() {return from.size();}
		public To get(Object key) {return containsKey(key)?mapper.apply((From) key):null;}
		public boolean containsKey(Object key) {return from.contains((From) key);}

		@Override
		public _LibEntry[] __entries() {return null;}
		@Override
		public void __remove(_LibEntry entry) {}
		@Override
		public Iterator<?> __iterator() {
			Iterator<From> itr = from.iterator();
			return new AbstractIterator<>() {
				@Override
				protected boolean computeNext() {
					if (!itr.hasNext()) return false;
					From next = itr.next();
					result = new SimpleImmutableEntry<>(next, mapper.apply(next));
					return true;
				}
			};
		}
	}

	/**
	 * 很慢，因为只有iterator，甚至没缓存
	 */
	public static <From, To> Map<To, From> stupidMapFromView(Collection<From> collection, Function<From, To> extractor) {
		var entrySet = (Set<Map.Entry<To, From>>) mapToView(collection, (Function<From, Map.Entry<To, From>>) node -> new AbstractMap.SimpleImmutableEntry<>(extractor.apply(node), node));
		return new AbstractMap<>() {@NotNull @Override public Set<Entry<To, From>> entrySet() {return entrySet;}};
	}

	public static <K, V> Map<K, V> toMap(XashMap<K, V> from) {return new XMap<>(from);}
	private static final class XMap<K, V> extends AbstractMap<K, V> implements _LibMap<_LibEntry> {
		private final XashMap<K, V> from;

		public XMap(XashMap<K, V> from) {this.from = from;}

		@NotNull
		public Set<Entry<K, V>> entrySet() {return _LibEntrySet.create(this);}
		public int size() {return from.size;}
		public boolean containsKey(Object key) {return from.containsKey(key);}
		public boolean containsValue(Object value) {return from.contains(value);}
		public V get(Object key) { return from.get(key); }
		public V put(K key, V value) {return from.put(key, value);}
		@SuppressWarnings("unchecked")
		public V remove(Object key) {return from.removeKey((K) key);}
		public void clear() {from.clear();}
		public V putIfAbsent(K key, V value) {return from.putIfAbsent(key, value);}

		@Override
		public _LibEntry[] __entries() {return null;}
		@Override
		public void __remove(_LibEntry entry) {}
		@Override
		public Iterator<?> __iterator() {
			Iterator<V> itr = from.iterator();
			return new AbstractIterator<>() {
				@Override
				protected boolean computeNext() {
					if (!itr.hasNext()) return false;
					V next = itr.next();
					result = new SimpleImmutableEntry<>(from._valueGetKey(next), next);
					return true;
				}
			};
		}
	}

	public static <K, V> Function<K, V> lazyLfu(Function<K, V> mapper, int capacity) {return mapCache(new LFUCache<>(capacity), mapper);}
	public static <K, V> Function<K, V> lazyLru(Function<K, V> mapper, int capacity) {return mapCache(new LRUCache<>(capacity), mapper);}
	public static <K, V> Function<K, V> lazyCache(Function<K, V> mapper) {return mapCache(new ConcurrentHashMap<>(), mapper);}

	@NotNull
	private static <K, V> Function<K, V> mapCache(Map<K, V> map, Function<K, V> mapper) {return k -> map.computeIfAbsent(k, mapper);}
}