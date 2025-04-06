package roj.collect;

import org.intellij.lang.annotations.MagicConstant;
import roj.reflect.ReflectionUtils;
import roj.util.ArrayCache;
import roj.util.Helpers;
import roj.util.NativeArray;
import roj.util.TimSortForEveryone;

import java.util.*;
import java.util.function.Function;

import static roj.reflect.Unaligned.U;

/**
 * 比删除的UnsortedMultiKeyMap更快、更好、更方便
 *
 * @since 2024/1/26 05:23
 */
public class MatchMap<K extends Comparable<K>, V> {
	public static abstract class AbstractEntry<V> {
		public V value;
	}

	private final MyHashMap<K, PosList> map = new MyHashMap<>();
	private int size;

	private static final Function<Object, PosList> CIA_FUNC = (x) -> new PosList();
	static final class PosList {
		private static final Entry<?>[] Empty = new Entry<?>[0];

		char[] pos = ArrayCache.CHARS;
		Entry<?>[] entries = Empty;
		int size;

		ToIntMap<Entry<?>> indexOf;

		void add(Entry<?> k, int v) {
			if (size == pos.length) {
				int size1 = pos.length == 0 ? 16 : pos.length << 1;
				pos = Arrays.copyOf(pos, size1);
				entries = Arrays.copyOf(entries, size1);
			}
			entries[size] = k;
			pos[size] = (char) v;
			size++;
		}

		void clear() {
			for (int i = 0; i < size; i++)
				entries[i] = null;
			size = 0;
		}

		void compact() {
			if (pos.length != size) {
				pos = Arrays.copyOf(pos, size);
				entries = Arrays.copyOf(entries, size);
			}

			// 同时排序pos和entries两个数组
			TimSortForEveryone.sort(0, size, (refLeft, offLeft, offRight) -> {
				int o = U.getInt(refLeft, offLeft+2), o2 = U.getInt(null, offRight+2);
				int cmp = KEYCMP.compare(entries[o], entries[o2]);
				if (cmp != 0) return cmp;

				return Integer.compare(U.getChar(refLeft, offLeft), U.getChar(offRight));
			}, NativeArray.primitiveArray(pos), NativeArray.objectArray(entries));

			if (size > 255) {
				if (indexOf == null)
					indexOf = new ToIntMap<>(size);

				Entry<?> prev = null;
				for (int i = 0; i < entries.length; i++) {
					Entry<?> entry = entries[i];
					if (prev != entry) {
						Integer i1 = indexOf.putInt(entry, i);
						assert i1 == null;
						prev = entry;
					}
				}
			}
		}

		private static final Comparator<Entry<?>> KEYCMP = (o1, o2) -> {
			Comparable<?>[] k1 = o1.key, k2 = o2.key;
			int lim = Math.min(k1.length, k2.length);
			for (int k = 0; k < lim; k++) {
				int i = k1[k].compareTo(Helpers.cast(k2[k]));
				if (i != 0) return i;
			}
			return k1.length - k2.length;
		};
		int indexOf(Entry<?> entry, int start) {
			if (indexOf != null) return indexOf.getOrDefault(entry, -1);
			return Arrays.binarySearch(entries, start, size, entry, KEYCMP);
		}
	}

	public static final class Entry<V> extends AbstractEntry<V> {
		public Comparable<?>[] key;

		@Override
		public String toString() { return "Entry{"+Arrays.toString(key)+"="+value+'}'; }
	}

	public int size() { return size; }
	public boolean isEmpty() { return size != 0; }

	public V put(List<K> key, V value) {
		if (key.isEmpty()) throw new IllegalArgumentException("Key cannot be empty");
		Entry<V> entry = getEntry(key);
		if (entry != null) {
			V prev = entry.value;
			entry.value = value;
			return prev;
		}

		add(key, value);
		return null;
	}
	public V computeIfAbsent_ForMe(Comparable<?>[] list, Function<Comparable<?>[], V> objectListFunction) {
		Arrays.sort(list);
		Entry<V> entry = getEntry(Helpers.cast(Arrays.asList(list)));
		if (entry != null) return entry.value;

		V value = objectListFunction.apply(list);
		add(list, value);
		return value;
	}

	@Deprecated public void add(List<K> key, V value) {add(key.toArray(new Comparable<?>[key.size()]), value);}
	@SuppressWarnings("unchecked")
	public void add(Comparable<?>[] needle, V value) {
		Entry<V> entry;

		entry = new Entry<>();
		entry.key = needle;
		entry.value = value;
		for (int i = 0; i < needle.length; i++) {
			map.computeIfAbsent((K) needle[i], CIA_FUNC).add(entry, i);
		}

		size++;
	}

	public V get(List<K> key) {
		Entry<V> entry = getEntry(key);
		return entry == null ? null : entry.value;
	}

	@SuppressWarnings("unchecked")
	public Entry<V> getEntry(List<K> key) {
		PosList list = null;
		int minSize = 0;
		for (int i = 0; i < key.size(); i++) {
			PosList pl = map.get(key.get(i));
			if (pl == null) return null;

			if (minSize == 0 || pl.size < minSize) {
				list = pl;
				minSize = pl.size;
				if (minSize < 50) break;
			}
		}

		for (int i = 0; i < list.size; i++) {
			Entry<?> entry1 = list.entries[i];
			if (listEqualsArray(key, entry1.key)) return (Entry<V>) entry1;
		}

		return null;
	}

	private static boolean listEqualsArray(List<? extends Comparable<?>> needle, Comparable<?>[] key) {
		if (needle.size() != key.length) return false;
		for (int i = 0; i < key.length; i++) {
			if (!needle.get(i).equals(key[i])) return false;
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	public V remove(List<K> needle) {
		Entry<?> entry = null;
		int pos = 0;

		outer:
		for (int i = 0; i < needle.size(); i++) {
			PosList list = map.get(needle.get(i));
			if (list == null) break;

			for (int j = 0; j < list.size; j++) {
				Entry<?> entry1 = list.entries[j];
				Object[] key1 = entry1.key;
				if (i < key1.length && Objects.equals(key1[i], needle.get(i)) && list.pos[j] == pos) {

					System.arraycopy(list.entries, j+1, list.entries, j, list.size-j-1);
					System.arraycopy(list.pos, j+1, list.pos, j, list.size-j-1);
					list.entries[--list.size] = null;
					if (list.indexOf != null) list.indexOf.remove(key1[i]);

					assert entry == null || entry == entry1;
					entry = entry1;
					pos++;
					continue outer;
				}
			}

			break;
		}

		if (entry == null) return null;

		assert pos == entry.key.length;
		return (V) entry.value;
	}

	/**
	 * 添加或删除之后需要调用这个方法来保证获得正确的结果，因此，这个集合并不适合频繁修改的场景
	 */
	public void compact() {
		for (PosList value : map.values()) {
			value.compact();
		}
	}

	public void clear() { size = 0; map.clear(); }

	private static final long CACHE_OFFSET = ReflectionUtils.fieldOffset(MatchMap.class, "stateCache");
	private static class State {
		final PosList a = new PosList(), b = new PosList();
		final SimpleList<MyBitSet> aflag = new SimpleList<>(), bflag = new SimpleList<>();
	}
	private State stateCache;
	private State getStateCache() {
		var stateCache = (State) U.getAndSetObject(this, CACHE_OFFSET, null);
		return stateCache == null ? new State() : stateCache;
	}
	private void setStateCache(State state) {
		state.a.clear();
		state.b.clear();
		state.aflag.clear();
		state.bflag.clear();
		U.compareAndSwapObject(this, CACHE_OFFSET, null, state);
	}

	public static final byte MATCH_SHORTER = 1, MATCH_LONGER = 2, MATCH_CONTINUOUS = 4;

	/**
	 * @see #matchUnordered(List, int, Collection)
	 */
	public List<Entry<V>> matchUnordered(List<K> key, int flag) { return matchUnordered(key, flag, new SimpleList<>()); }
	/**
	 * 高性能批量查找无序子序列.
	 * 如下示例中，haystack均指该集合中的一个字符串，不过方法实际上是对集合的匹配，而且仅需needle长度(而不是集合大小)的对数时间
	 * 如果你查找的是有序子序列，请使用{@link #matchOrdered(List, int, Collection)}.
	 * 此方法的needle每项除了是K类型，还可以是Iterable&lt;K&gt;，但是可能导致性能一定程度下降.
	 * MatchShorter不适合无序子序列，因为这时它就退化为每个PosList的并集了
	 *
	 * @param needle 待匹配的元素列表，用于与存储的内容进行匹配操作。
	 * @param matchLonger 匹配模式的标志位
	 *     - 当 matchLonger 为 0 时：
	 *       规则：返回包含 needle 子序列（子序列不一定连续）的元素。
	 *       示例：假设存储了 "cba"，那么 "abc" 和 "bac" 都可以匹配成功，因为needle与haystack拥有类型和数量相同的字符。
	 *
	 *     - 当 matchLonger 为 MATCH_LONGER 时：
	 *       附加规则：*也(OR)*返回那些匹配完needle中每个字符, 但未匹配完haystack中每个字符的结果。
	 *       示例：needle 为 "ba"，haystack为 "abc"，在该模式下可以匹配成功，因为 needle 已经匹配完，但 haystack 还有剩余部分 "c"。
	 * @param out 用于存储匹配结果的列表，匹配成功的元素将被添加到这个列表中。
	 * @return 包含匹配结果的列表，和参数out相同
	 */
	@SuppressWarnings("unchecked")
	public <COL extends Collection<Entry<V>>> COL matchUnordered(final List<?> needle, @MagicConstant(intValues = {0, MATCH_LONGER}) final int matchLonger, final COL out) {
		int bestStart = 0;
		int bestSize = Integer.MAX_VALUE;
		for (int i = 0; i < needle.size(); i++) {
			var o = needle.get(i);
			var size = 0;
			Iterator<?> itr;
			if (o instanceof Iterable<?> it) {
				itr = it.iterator();
				o = itr.next();
			} else {
				itr = Collections.emptyIterator();
			}

			while (true) {
				var list = map.get(o);
				if (list != null) size += list.size;

				if (!itr.hasNext()) break;
				o = itr.next();
			}

			if (size == 0) return out;
			if (size < bestSize) {
				bestSize = size;
				bestStart = i;
				if (bestSize == 1) break;
			}
		}

		var o = needle.get(bestStart);

		var tmp = getStateCache();
		var prev = tmp.a;
		SimpleList<MyBitSet> prevBits = tmp.aflag, nextBits = tmp.bflag;

		if (o instanceof Iterable<?> it) {
			var itr = it.iterator();
			o = itr.next();

			while (true) {
				var list = map.get(o);
				if (list != null) {
					for (int j = 0; j < list.size; j++) {
						Entry<?> entry = list.entries[j];

						int length = entry.key.length;
						if (length != needle.size()) {
							if (length < needle.size() || (matchLonger & MATCH_LONGER) == 0) continue;
						}

						if (prev.indexOf(entry, 0) >= 0) continue;

						var newPos = list.pos[j];
						prev.add(entry, 1 << newPos);
						if (newPos >= 16) {
							var largeBits = new MyBitSet();
							largeBits.add(newPos);

							prevBits.ensureCapacity(prev.size);
							prevBits._setSize(prev.size);
							prevBits.set(prev.size-1, largeBits);
						}
					}
				}

				if (!itr.hasNext()) break;
				o = itr.next();
			}
		} else {
			prev = map.get(o);
		}

		prevBits.ensureCapacity(prev.size);
		prevBits._setSize(prev.size);

		for (int i = 0; i < needle.size(); i++) {
			if (i == bestStart) continue;

			o = needle.get(i);
			Iterator<?> itr;
			if (o instanceof Iterable<?> it) {
				itr = it.iterator();
				o = itr.next();
			} else {
				itr = Collections.emptyIterator();
			}

			var next = prev == tmp.a ? tmp.b : tmp.a;
			next.clear();

            while (true) {
				var list = map.get(o);
				if (list != null) {
					// 我们可以假设所有list长度都大于prev了 （至少在不包含Iterable的情况下）
					int prevK = 0;
					outerLoop:
					for (int j = 0; j < prev.size && prevK < list.size; j++) {
						Entry<?> entry = prev.entries[j];

						int k = list.indexOf(entry, prevK);
						if (k < 0) continue;
						prevK = k+1;

						while (k > 0 && list.entries[k-1] == entry) k--;

						// if I could find any unused pos
						while (true) {
							var newPos = list.pos[k];
							if (newPos < 16) {
								char knownPos = prev.pos[j];
								if ((knownPos & (1 << newPos)) == 0) {
									prev.pos[j] = (char) (knownPos | (1 << newPos));
									break;
								}
							} else {
								var largeBits = prevBits.get(j);
								if (largeBits == null) prevBits.set(j, largeBits = new MyBitSet());
								if (largeBits.add(newPos)) break;
							}

							if (++k >= list.size || list.entries[k] != entry) continue outerLoop;
						}

						nextBits.add(prevBits.get(j));
						next.add(entry, prev.pos[j]);
					}
                }

                if (!itr.hasNext()) break;
                o = itr.next();
            }
			prev = next;
		}

		for (int i = 0; i < prev.size; i++) {
			Entry<?> entry = prev.entries[i];
			if ((matchLonger & MATCH_LONGER) == 0) {
				int bits = Integer.bitCount(prev.pos[i]);
				var largeBits = prevBits.get(i);
				if (largeBits != null) bits += largeBits.size();
				if (bits < entry.key.length) continue;
			}

			out.add((Entry<V>) entry);
		}

		setStateCache(tmp);
		return out;
	}

	/**
	 * 高性能批量查找有序子序列.
	 * 如下示例中，haystack均指该集合中的一个字符串，不过方法实际上是对集合的匹配，而且仅需needle长度(而不是集合大小)的对数时间.
	 * 此方法的needle每项除了是K类型，还可以是Iterable&lt;K&gt;
	 *
	 * @param needle 待匹配的元素列表，用于与存储的内容进行匹配操作。
	 * @param flag 匹配模式的标志位，支持以下几种模式，并且这些模式可以组合使用：
	 *     - 当 flag 为 0 时：
	 *       规则：返回包含 needle 子序列（子序列不一定连续）的元素。
	 *       示例：假设存储了 "abc"，那么 "ac" 和 "bc" 都可以匹配成功，因为needle与haystack的结尾相同，且 "ac" 和 "bc" 是 "abc" 的不连续（但是有序）子序列。
	 *
	 *     - 当 flag 包含 MATCH_CONTINUOUS 时：
	 *       附加规则：*仅(AND)*返回连续包含 needle 的子序列的元素，类似于 SQL 中的 LIKE %needle% 操作。
	 *       示例：haystack 为 "abcde"，那么 "ac" 和 "bd" 不能匹配成功，因为它们在 "abcde" 中不是连续出现的；而 "cde"、"bcde" 等可以匹配成功，因为它们是 "abcde" 中连续的子序列。
	 *     - 当 flag 包含 MATCH_SHORTER 时：
	 *       附加规则：*也(OR)*返回那些匹配到haystack结尾，但未匹配到needle结尾的结果。
	 *       示例：needle 为 "abc"，haystack为 "ab"，在该模式下可以匹配成功，因为匹配到了 haystack 的结尾 "b"，但未匹配到 needle 的结尾 "c"。
	 *     - 当 flag 包含 MATCH_LONGER 时：
	 *       附加规则：*也(OR)*返回那些未匹配到haystack结尾的结果。
	 *       示例：needle 为 "ab"，haystack为 "abc"，在该模式下可以匹配成功，因为 needle 已经匹配完，但 haystack 还有剩余部分 "c"。
	 * @param out 用于存储匹配结果的列表，匹配成功的元素将被添加到这个列表中。
	 * @return 包含匹配结果的列表，和参数out相同
	 */
	@SuppressWarnings("unchecked")
	public <COL extends Collection<Entry<V>>> COL matchOrdered(List<?> needle, @MagicConstant(flags = {MATCH_CONTINUOUS, MATCH_SHORTER, MATCH_LONGER}) final int flag, COL out) {
		if (out.size() < 2) return matchUnordered(needle, flag, out);

		var tmp = getStateCache();
		List<PosList> zero = Helpers.cast(tmp.aflag);

		Object o = needle.get(0);
		Iterator<?> itr;
		if (o instanceof Iterable<?> it) {
            for (itr = it.iterator(); itr.hasNext(); ) {
                var list = map.get(itr.next());
                if (list != null) zero.add(list);
            }
		} else {
			var list = map.get(o);
			if (list != null) zero.add(list);
		}

		if (zero.isEmpty()) {
			setStateCache(tmp);
			return out;
		}

		var next = tmp.a;
		int i = 1;

		while (true) {
			o = needle.get(i);
			if (o instanceof Iterable<?> it) {
				itr = it.iterator();
				o = itr.next();
			} else {
				itr = Collections.emptyIterator();
			}

			while (true) {
				PosList list = map.get(o);
				if (list != null) {
					Entry<?>[] nextVals = list.entries;
					char[] nextPoss = list.pos;
					int prevK = 0;

					// avoid combining first prevArray
					for (int index_ = 0; index_ < zero.size(); index_++) {
						var prev = zero.get(index_);

						fail:
						for (int j = 0; j < prev.size; j++) {
							Entry<?> entry = prev.entries[j];

							int k = list.indexOf(entry, prevK);
							if (k < 0) continue;
							prevK = k+1;

							int pos = prev.pos[j];
							if (nextPoss[k] <= pos) {
								do {
									if (++k == list.size || nextVals[k] != entry) continue fail;
								} while (nextPoss[k] <= pos);
							} else {
								while (k != 0 && nextVals[k-1] == entry && nextPoss[k-1] > pos) k--;
							}

							int nextPos = nextPoss[k];
							if ((flag&MATCH_CONTINUOUS) != 0 && nextPos != pos+1) continue;

							int existingPos = itr == Collections.emptyIterator() ? -1 : next.indexOf(entry, 0);
							if (existingPos >= 0) {
								if (next.pos[existingPos] > nextPos) {
									next.pos[existingPos] = (char) nextPos;
								}
							} else {
								if ((flag & MATCH_SHORTER) != 0) {
									if (nextPos == entry.key.length-1) {
										out.add((Entry<V>) entry);
									} else {
										next.add(entry, nextPos); // 不能使用下方的优化，因为不知道会不会提前匹配到结尾
									}
								} else if (entry.key.length - nextPos >= needle.size()-i) { // 还有机会匹配到结尾（还剩【needle.size() - i - 1】次匹配，还剩【length - nextPos - 1】个字符）
									next.add(entry, nextPos);
								}
							}
						}
					}
				}

				if (!itr.hasNext()) break;
				o = itr.next();
			}

			zero.clear();
			zero.add(next);
			if (next.size == 0 || ++i == needle.size()) break;

			next = next == tmp.a ? tmp.b : tmp.a;
			next.clear();
			i++;
		}

		for (i = 0; i < next.size; i++) {
			Entry<?> entry = next.entries[i];
			if ((flag&MATCH_LONGER) != 0 || next.pos[i] == entry.key.length-1) {
				out.add((Entry<V>) entry);
			}
		}

		setStateCache(tmp);
		return out;
	}
}