package roj.collect;

import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static roj.collect.IntMap.UNDEFINED;

public class UnsortedMultiKeyMap<K, T, V> {
	private static String[] generateRecipes(int i) {
		String[] item = new String[i];
		for (int j = 0; j < i; j++) {
			item[j] = Integer.toString(j);
		}
		return item;
	}

	public interface Keys<K, T> extends Comparator<T> {
		/**
		 * 建议保证返回的list中使用频率大的靠前
		 * @param holder 装t的容器，空列表
		 * @return holder, 非强制
		 */
		List<T> getKeys(K key, List<T> holder);

		T getPrimaryKey(K k);
	}

	public static final class REntry<T, V> implements Iterable<REntry<T, V>> {
		T k;
		V v;

		REntry<T, V> next;

		REntry<?, ?>[] children;
		int size;
		short refCnt;

		static final REntry<?, ?>[] EMPTY = new REntry<?, ?>[0];
		static final int INITIAL_CAPACITY = 1;
		static final float LOAD_FACTOR = 1f;

		@SuppressWarnings("unchecked")
		REntry(T k) {
			this.k = k;
			this.v = (V) UNDEFINED;
			this.children = EMPTY;
		}

		REntry(REntry<T, V> entry) {
			if (entry.children.length > 0) children = new REntry<?, ?>[entry.children.length];
			else children = EMPTY;
			size = entry.size;
			k = entry.k;
			v = entry.v;
		}

		@SuppressWarnings("unchecked")
		void resize() {
			REntry<?, ?>[] arr = children;
			REntry<?, ?>[] arr1 = new REntry<?, ?>[arr.length << 1];

			REntry<T, V> entry, next;

			int mask1 = arr1.length - 1;

			int i = 0, j = arr.length;
			for (; i < j; i++) {
				entry = (REntry<T, V>) arr[i];
				while (entry != null) {
					next = entry.next;

					int newKey = entry.k.hashCode() & mask1;

					REntry<T, V> old = (REntry<T, V>) arr1[newKey];

					arr1[newKey] = entry;
					entry.next = old;
					entry = next;
				}
			}

			children = arr1;
		}

		@SuppressWarnings("unchecked")
		public REntry<T, V> getChild(T key) {
			REntry<?, ?>[] ch = children;
			if (ch.length == 0) return null;
			int hash = key.hashCode() & (ch.length - 1);

			REntry<T, V> entry = (REntry<T, V>) ch[hash];
			while (entry != null) {
				if (key.equals(entry.k)) return entry;
				entry = entry.next;
			}

			return null;
		}

		@SuppressWarnings("unchecked")
		public boolean putChild(REntry<T, V> child) {
			REntry<?, ?>[] ch = children;
			if (ch.length == 0) {
				children = ch = new REntry<?, ?>[INITIAL_CAPACITY];
			} else if (size > ((ch.length == INITIAL_CAPACITY && INITIAL_CAPACITY < 8) ? (INITIAL_CAPACITY + 1) : ch.length * LOAD_FACTOR)) {
				resize();
				ch = children;
			}

			REntry<T, V> entry;

			int hashId = child.k.hashCode() & (ch.length - 1);

			if ((entry = (REntry<T, V>) ch[hashId]) == null) {
				ch[hashId] = child;
			} else {
				T k = child.k;
				while (true) {
					if (k.equals(entry.k)) return false;
					if (entry.next == null) break;
					entry = entry.next;
				}
				entry.next = child;
			}
			child.refCnt++;
			size++;

			return true;
		}

		@SuppressWarnings("unchecked")
		public boolean removeChild(T key) {
			REntry<?, ?>[] ch = children;
			if (ch.length == 0) return false;

			REntry<T, V> entry, prev = null, toRemove = null;
			int hashId = key.hashCode() & (ch.length - 1);

			entry = (REntry<T, V>) ch[hashId];
			while (entry != null) {
				if (key.equals(entry.k)) {
					toRemove = entry;
					break;
				}
				prev = entry;
				entry = entry.next;
			}

			if (toRemove == null) {
				return false;
			}

			if (prev != null) {
				prev.next = toRemove.next;
			} else {
				ch[hashId] = toRemove.next;
			}

			toRemove.refCnt--;
			size--;

			return true;
		}

		public String toString() {
			return "{" + k + "=" + v + "}";
		}

		public void clear() {
			if (size == 0) return;
			size = 0;
			refCnt = 0;

			Arrays.fill(children, null);
		}

		int recursionSum() {
			int i = v != UNDEFINED ? 1 : 0;
			if (size > 0) {
				for (REntry<?, ?> value : children) {
					while (value != null) {
						i += value.recursionSum();
						value = value.next;
					}
				}
			}
			return i;
		}

		@Nonnull
		@Override
		@SuppressWarnings("unchecked")
		public Iterator<REntry<T, V>> iterator() {
			if (children.length == 0) return Collections.emptyIterator();
			return new AbstractIterator<REntry<T, V>>() {
				REntry<T, V> entry;
				int i = 0;

				@Override
				public boolean computeNext() {
					while (true) {
						if (entry != null) {
							result = entry;
							entry = entry.next;
							return true;
						} else if (i < children.length) {
							entry = (REntry<T, V>) children[i++];
						} else {
							return false;
						}
					}
				}
			};
		}

		public int copyFrom(REntry<T, V> node) {
			int v = 0;
			if (node.v != UNDEFINED && this.v == UNDEFINED) {
				this.v = node.v;
				v = 1;
			}

			for (REntry<T, V> entry : node) {
				REntry<T, V> sub = getChild(entry.k);
				if (sub == null) putChild(sub = new REntry<>(this));
				v += sub.copyFrom(entry);
			}
			return v;
		}

		// getOrCreateEntryMulti会复用一些entry
		// 以此避免entry数量的阶乘级增加: 现在是阶加
		// 但是，若有未完全包含的新key组，则需要拆分一个entry
		// 目前没有合并机制
		REntry<T, V> uncollapse(REntry<T, V> parent) {
			if (refCnt == 1) return this;
			if (refCnt == 0) throw new IllegalStateException();
			parent.removeChild(k);

			REntry<T, V> copy = new REntry<>(k);

			copy.children = children.clone();
			copy.size = size;
			copy.v = v;

			parent.putChild(copy);
			return copy;
		}

		// 同上，如果不只一个parent需要包含
		REntry<T, V> uncollapse(List<REntry<T, V>> parent) {
			if (refCnt == parent.size()) return this;
			if (refCnt < parent.size()) throw new IllegalStateException();
			REntry<T, V> copy = new REntry<>(k);

			copy.children = children.clone();
			copy.size = size;
			copy.v = v;

			for (int i = 0; i < parent.size(); i++) {
				REntry<T, V> entry = parent.get(i);
				entry.removeChild(k);
				entry.putChild(copy);
			}
			return copy;
		}
	}

	static final class Finder<T, V> {
		final SimpleList<T>[] tHolder;
		final SimpleList<REntry<?,?>[]> nodes, nodes1;
		final IntList routes, routes1;
		final MyHashSet<REntry<?,?>> traversed;

		Finder(int cap) {
			nodes = new SimpleList<>(16);
			nodes.capacityType = 2;
			nodes1 = new SimpleList<>(16);
			nodes1.capacityType = 2;
			routes = new IntList(16);
			routes1 = new IntList(16);
			tHolder = Helpers.cast(new SimpleList<?>[cap]);
			while (cap-- > 0) tHolder[cap] = new SimpleList<>(8);

			traversed = new MyHashSet<>(128);
		}
	}

	public static <K, T, V> UnsortedMultiKeyMap<K, T, V> create(Keys<K, T> keys, int maxLen) {
		return new UnsortedMultiKeyMap<>(keys, maxLen);
	}

	public final Keys<K, T> comparator;
	public final int maxLen;

	@SuppressWarnings("rawtypes")
	static final AtomicReferenceFieldUpdater<UnsortedMultiKeyMap, Object> BufUpdater = AtomicReferenceFieldUpdater.newUpdater(UnsortedMultiKeyMap.class, Object.class, "buffer");
	private volatile Object buffer;

	REntry<T, V> root = new REntry<>((T) null);
	int size = 0;

	public UnsortedMultiKeyMap(Keys<K, T> comparator, int len) {
		this.comparator = comparator;
		this.maxLen = len;
	}

	public REntry<T, V> getEntry(List<K> s, int i, int len) {
		if (len < 0) throw new IllegalArgumentException("delta length < 0");

		REntry<T, V> entry = root;
		for (; i < len; i++) {
			entry = entry.getChild(comparator.getPrimaryKey(s.get(i)));
			if (entry == null) {
				return null;
			}
		}

		return entry.v != UNDEFINED ? entry : null;
	}

	public REntry<T, V> getRoot() {
		return root;
	}

	public int size() {
		return size;
	}
	public boolean isEmpty() {
		return size == 0;
	}

	public V put(List<K> keys, V e) {
		REntry<T, V> entry = GOC(new AbstractList<T>() {
			@Override
			public T get(int i) {
				return comparator.getPrimaryKey(keys.get(i));
			}

			@Override
			public int size() {
				return keys.size();
			}
		});

		if (entry.v == UNDEFINED) {
			size++;
			entry.v = null;
		}

		V v = entry.v;
		entry.v = e;
		return v;
	}
	public V put1(List<T> keys, V e) {
		REntry<T, V> entry = GOC(keys);
		if (entry.v == UNDEFINED) {
			size++;
			entry.v = null;
		}

		V v = entry.v;
		entry.v = e;
		return v;
	}

	public V computeIfAbsent1(List<T> keys, Function<List<T>, V> fn) {
		REntry<T, V> entry = GOC(keys);
		if (entry.v == UNDEFINED) {
			size++;
			entry.v = fn.apply(keys);
		}

		return entry.v;
	}

	// 通过这个方法添加的恐怕很难删除
	// 组合压缩，keys可以是base也可以是base[]按顺序连接，fn是到value的映射
	public V computeIfAbsentMulti(Class<T> base, List<Object> keys, Function<List<Object>, V> fn) {
		SimpleList<REntry<T, V>> curr = GOC_Multi(base, keys);

		V state = curr.get(0).v;
		for (int i = 1; i < curr.size; i++) {
			if (state != curr.get(i).v) throw new IllegalStateException("key∩keySet not empty, and key∪(key∩keySet) != key\n" +
				"新增的key覆盖了一部分值, 而且更糟糕的是，它们还不一样");
		}

		if (state == UNDEFINED) {
			size++;
			V v = fn.apply(keys);
			for (int i = 0; i < curr.size; i++) {
				curr.get(i).v = v;
			}
			return v;
		} else {
			return state;
		}
	}

	// get or create
	protected final REntry<T, V> GOC(List<T> keys) {
		try {
			keys.sort(comparator);
		} catch (UnsupportedOperationException e) {
			keys = Helpers.cast(Arrays.asList(keys.toArray()));
			keys.sort(comparator);
		}

		REntry<T, V> entry = root;
		REntry<T, V> prev;
		for (int i = 0; i < keys.size(); i++) {
			prev = entry;
			T t = keys.get(i);
			entry = entry.getChild(t);
			if (entry == null) {
				prev.putChild(entry = new REntry<>(t));
			} else {
				entry = entry.uncollapse(prev);
			}
		}
		return entry;
	}

	@SuppressWarnings("unchecked")
	protected final SimpleList<REntry<T, V>> GOC_Multi(Class<T> base, List<Object> keys) {
		SimpleList<REntry<T, V>> curr = new SimpleList<>(1);
		MyHashMap<REntry<T, V>, List<REntry<T, V>>> map = new MyHashMap<>(1);
		curr.add(root);

		for (int i = 0; i < keys.size(); i++) {
			Object o = keys.get(i);
			// T
			if (base.isAssignableFrom(o.getClass())) {
				REntry<T, V> miss = null;
				for (int j = 0; j < curr.size; j++) {
					REntry<T, V> entry = curr.get(j).getChild((T) o);
					if (entry == null) {
						if (miss == null) miss = new REntry<>((T) o);
						curr.get(j).putChild(miss);
					} else {
						map.computeIfAbsent(entry, Helpers.fnArrayList()).add(curr.get(j));
					}
				}

				curr.clear();
				if (miss != null) curr.add(miss);
				// T的数组
			} else if (o.getClass().isArray()) {
				T[] key = (T[]) o;
				curr.ensureCapacity(key.length);

				// 当一个map用
				REntry<T, V> misses = new REntry<>((T) null);

				for (int j = 0; j < curr.size(); j++) {
					REntry<T, V> prev = curr.get(j);
					for (T k : key) {
						REntry<T, V> child = prev.getChild(k);
						if (child == null) {
							if (misses.getChild(k) == null) {
								REntry<T, V> c1 = new REntry<>(k);
								misses.putChild(c1);
								c1.refCnt = 0;
							}
							curr.get(j).putChild(misses.getChild(k));
						} else {
							map.computeIfAbsent(child, Helpers.fnArrayList()).add(curr.get(j));
						}
					}
				}

				curr.clear();
				if (misses.size > 0) curr.addAll(misses);
			}

			// 不需要全部uncollapse, 只要拆成两份
			for (Map.Entry<REntry<T, V>, List<REntry<T, V>>> entry : map.entrySet()) {
				curr.add(entry.getKey().uncollapse(entry.getValue()));
			}
			map.clear();
		}
		return curr;
	}

	public void addTree(UnsortedMultiKeyMap<? extends List<K>, ? extends T, ? extends V> m) {
		size += root.copyFrom(Helpers.cast(m.root));
	}

	public V remove(List<K> s) {
		return remove(s, 0, s.size());
	}
	public V remove(List<K> s, int i, int len) {
		if (len < 0) throw new IllegalArgumentException("delta length < 0");

		SimpleList<REntry<T, V>> list = new SimpleList<>(Math.min(len, maxLen));

		REntry<T, V> entry = root;
		for (; i < len; i++) {
			entry = entry.getChild(comparator.getPrimaryKey(s.get(i)));
			if (entry == null) return null;

			list.add(entry);
		}

		if (entry.v == UNDEFINED) return null;

		size--;

		if (!list.isEmpty()) {
			i = list.size;

			while (--i >= 0) {
				REntry<T, V> prev = list.get(i);

				if (prev.recursionSum() > 0) {
					prev.removeChild(entry.k);
					break;
				}
				entry = prev;
			}
			list.list = null;
		}

		return entry.v;
	}

	public boolean containsKey(List<K> s) {
		return getEntry(s, 0, s.size()) != null;
	}

	public List<V> getMulti(List<K> s, int limit) {
		return getMulti(s, limit, new SimpleList<>(), false);
	}
	public List<V> getMulti(List<K> s, int limit, List<V> dest) {
		return getMulti(s, limit, dest, false);
	}
	@SuppressWarnings("unchecked")
	public <X extends Collection<V>> X getMulti(List<K> keys, int limit, X dest, boolean partial) {
		// empty
		if (root.children.length == 0) return dest;

		// concurrent
		Finder<T, V> f = (Finder<T, V>) BufUpdater.getAndSet(this, null);
		if (f == null) f = new Finder<>(maxLen);

		// ore-dictionary
		List<T>[] allKeys = f.tHolder;
		for (int i = 0; i < keys.size(); i++) {
			allKeys[i].clear();
			allKeys[i] = comparator.getKeys(keys.get(i), allKeys[i]);
		}

		MyHashSet<REntry<?,?>> traversed = f.traversed;
		SimpleList<REntry<?,?>[]> nodes = f.nodes, nodes1 = f.nodes1;
		IntList routes = f.routes, routes1 = f.routes1;

		nodes.add(root.children);
		routes.add(0);

		int remain = keys.size();
		if (remain == 1) partial = true;
		boolean hasNext = false;

		loop:
		while (true) {
			for (int i = 0; i < keys.size(); i++) {
				List<T> subKeys = allKeys[i];

				int[] subRoutes = routes.getRawArray();

				for (int j = nodes.size()-1; j >= 0; j--) {
					int route = subRoutes[j];
					if ((route & (1 << i)) != 0) continue;
					route |= (1 << i);

					REntry<?,?>[] children = nodes.get(j);
					int mask = children.length-1;

					for (int k = 0; k < subKeys.size(); k++) {
						T t = subKeys.get(k);

						for (REntry<T, V> re = (REntry<T, V>) children[t.hashCode() & mask]; re != null; re = re.next) {
							if (!t.equals(re.k)) continue;

							// 比较关键的一个筛选
							// 假设输入为9*铁,表为9*铁
							// 若重复则代表上一与当前来源均为相同的重复序列
							// 如[铁铜金铁铜金]会筛选掉来源3-4-5的[铁铜金]保留来源[0-1-2]
							// 因为012≡345所以该筛选不会影响结果(只是去重)
							// 不过实际上在Level0 铁-铁就会被筛选掉，进而防止后面的阶乘倍增
							if (!traversed.add(re)) continue;

							if (re.v != UNDEFINED && partial) {
								dest.add(re.v);
								if (dest.size() >= limit) break loop;
							}

							if (re.children.length == 0) break;

							// 路线未经过第l项
							routes1.add(route);
							nodes1.add(re.children);

							hasNext = true;
							break;
						}

					}
				}

			}

			if (!hasNext) break;

			remain--;
			// 最后的Level时要添加结果(EXACT_RESULT)
			if (remain == 1) partial = true;
			else if (remain == 0) break;

			IntList T = routes;
			routes = routes1;
			routes1 = T;

			SimpleList<REntry<?,?>[]> t = nodes1;
			nodes1 = nodes;
			nodes = t;

			nodes1.clear();
			routes1.clear();

			// 能否clear取决于树上是否存在长度不同的链接,像这样
			// a ----> b ____> c
			// d ---------/
			// 目前不会出现
			// 新增dc只会创建新的c节点,这也符合直觉

			// optional, clear耗时线性
			if (traversed.size*2 > traversed.mask) traversed.clear();

			hasNext = false;
		}

		nodes.clear();
		routes.clear();
		nodes1.clear();
		routes1.clear();
		traversed.clear();

		BufUpdater.compareAndSet(this, null, f);

		return dest;
	}

	public V get(List<K> s) {
		REntry<T, V> vs = getEntry(s, 0, s.size());
		return vs == null || vs.v == UNDEFINED ? null : vs.v;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append('{');
		forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
		if (!isEmpty()) {
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.append('}').toString();
	}

	public void clear() {
		size = 0;
		root.clear();
	}

	public void forEach(BiConsumer<? super List<T>, ? super V> consumer) {
		recursionEntry(root, consumer, new SimpleList<>());
	}
	private static <K, V> void recursionEntry(REntry<K, V> parent, BiConsumer<? super List<K>, ? super V> consumer, SimpleList<K> list) {
		for (REntry<K, V> entry : parent) {
			list.add(entry.k);
			if (entry.v != UNDEFINED) {
				consumer.accept(list, entry.v);
			}
			recursionEntry(entry, consumer, list);
			list.size--;
		}
	}
}