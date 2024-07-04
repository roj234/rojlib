package roj.collect;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2024/5/15 0015 14:33
 */
public class CollectionX {
	private static class CollectionView<To, From> extends AbstractCollection<To> {
		private final Collection<From> from;
		private final Function<From, To> toto;

		public CollectionView(Collection<From> from, Function<From, To> toto) {
			this.from = from;
			this.toto = toto;
		}

		@Override
		public int size() {return from.size();}

		@NotNull
		@Override
		public Iterator<To> iterator() {
			Iterator<From> itr = from.iterator();
			return new AbstractIterator<>() {
				@Override
				protected boolean computeNext() {
					if (itr.hasNext()) {
						result = toto.apply(itr.next());
						return true;
					}
					return false;
				}

				@Override
				protected void remove(To obj) {
					super.remove(obj);
				}
			};
		}

		@NotNull
		@Override
		@SuppressWarnings("unchecked")
		public Object[] toArray() {
			Object[] array = from.toArray();
			for (int i = 0; i < array.length; i++) {
				array[i] = toto.apply((From) array[i]);
			}
			return array;
		}

		@Override
		public void clear() {from.clear();}
	}

	public static <From, To> Collection<To> mapToView(Collection<From> from, Function<From, To> mapper) {return new CollectionView<>(from, mapper);}
	@SuppressWarnings("unchecked")
	public static <From, To> Collection<To> mapToView(Collection<From> from, Function<From, To> mapper, Function<To, From> unmapper) {
		return new CollectionView<>(from, mapper) {
			@Override
			public boolean contains(Object o) {return from.contains(unmapper.apply((To) o));}
			@Override
			public boolean add(To p) {return from.add(unmapper.apply(p));}
			@Override
			public boolean remove(Object o) {return from.remove(unmapper.apply((To) o));}
		};
	}

	public static <T> Map<T, T> toMap(Collection<T> name) {return toMap(name, Function.identity());}
	@SuppressWarnings("unchecked")
	public static <From, To> Map<From, To> toMap(Collection<From> from, Function<From, To> mapper) {
		return new AbstractMap<>() {
			@NotNull
			@Override
			public Set<Map.Entry<From, To>> entrySet() {
				return new AbstractSet<>() {
					@Override
					public Iterator<Map.Entry<From, To>> iterator() {
						Iterator<From> itr = from.iterator();
						return new AbstractIterator<>() {
							@Override
							protected boolean computeNext() {
								if (itr.hasNext()) {
									From next = itr.next();
									result = new SimpleImmutableEntry<>(next, mapper.apply(next));
									return true;
								}
								return false;
							}
						};
					}

					@Override
					public int size() {return from.size();}
				};
			}

			@Override
			public int size() {return from.size();}
			@Override
			public To get(Object key) {return containsKey(key)?mapper.apply((From) key):null;}
			@Override
			public boolean containsKey(Object key) {return from.contains((From) key);}
		};
	}

	public static <K, V> Map<K, V> toMap(XHashSet<K, V> from) {
		return new AbstractMap<>() {
			@NotNull
			@Override
			public Set<Map.Entry<K, V>> entrySet() {
				return new AbstractSet<>() {
					@Override
					public Iterator<Map.Entry<K, V>> iterator() {
						Iterator<V> itr = from.iterator();
						return new AbstractIterator<>() {
							@Override
							protected boolean computeNext() {
								if (itr.hasNext()) {
									V next = itr.next();
									result = new SimpleImmutableEntry<>(from._valueGetKey(next), next);
									return true;
								}
								return false;
							}
						};
					}

					@Override
					public int size() {return from.size();}
				};
			}

			@Override
			public int size() { return from.size; }
			@Override
			public boolean containsKey(Object key) { return from.containsKey(key); }
			@Override
			public boolean containsValue(Object value) { return from.contains(value); }
			@Override
			public V get(Object key) { return from.get(key); }
			@Override
			public V put(K key, V value) { return from.put(key, value); }
			@Override
			public V remove(Object key) { return from.removeKey(key); }
			@Override
			public void clear() { from.clear(); }
			@Override
			public V putIfAbsent(K key, V value) { return from.putIfAbsent(key, value); }
		};
	}
}