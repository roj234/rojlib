package roj.collect;

import org.intellij.lang.annotations.MagicConstant;
import roj.reflect.ReflectionUtils;
import roj.util.ArrayCache;
import roj.util.NativeArray;
import roj.util.TimSortForEveryone;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;

import static roj.reflect.Unaligned.U;

/**
 * 比删除的UnsortedMultiKeyMap更快、更好、更方便 (除了现在key只有string外...)
 *
 * @since 2024/1/5 06:53
 */
public class MatchMapString<V> {
	private final CharMap<PosList> map = new CharMap<>();
	private int size;

	private static final IntFunction<PosList> CIA_FUNC = (x) -> new PosList();
	static final class PosList {
		private static final Entry<?>[] Empty = new Entry<?>[0];

		// sorted by [pos,entry]
		//BitArray ba = new BitArray(8, 256);
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
				int cmp = entries[o].key.compareTo(entries[o2].key);
				if (cmp != 0) return cmp;

				return Integer.compare(U.getChar(refLeft, offLeft), U.getChar(offRight));
			}, NativeArray.primitiveArray(pos), NativeArray.objectArray(entries));

			if (size > 255) {
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

		private static final Comparator<Entry<?>> KEYCMP = (o1, o2) -> o1.key.compareTo(o2.key);
		int indexOf(Entry<?> entry, int start) {
			if (indexOf != null) return indexOf.getOrDefault(entry, -1);
			return Arrays.binarySearch(entries, start, size, entry, KEYCMP);
		}
	}

	public static final class Entry<V> extends MatchMap.AbstractEntry<V> {
		public String key;

		@Override
		public String toString() { return "Entry{"+key+"="+value+'}'; }
	}

	public int size() { return size; }
	public boolean isEmpty() { return size != 0; }

	public V put(String key, V value) {
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

	public void add(String key, V value) {
		Entry<V> entry;
		if (key.length() > 65536) throw new IllegalArgumentException("Key length cannot > 65536");

		entry = new Entry<>();
		entry.key = key;
		entry.value = value;
		for (int i = 0; i < key.length(); i++) {
			map.computeIfAbsentC(key.charAt(i), CIA_FUNC).add(entry, i);
		}

		size++;
	}

	public V get(String key) {
		Entry<V> entry = getEntry(key);
		return entry == null ? null : entry.value;
	}

	@SuppressWarnings("unchecked")
	public final Entry<V> getEntry(String key) {
		PosList list = null;
		int minSize = 0;
		for (int i = 0; i < key.length(); i++) {
			PosList pl = map.get(key.charAt(i));
			if (pl == null) return null;

			if (minSize == 0 || pl.size < minSize) {
				list = pl;
				minSize = pl.size;
				if (minSize < 50) break;
			}
		}

		for (int i = 0; i < list.size; i++) {
			Entry<?> entry1 = list.entries[i];
			if (key.equals(entry1.key)) return (Entry<V>) entry1;
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	public V remove(String key) {
		Entry<?> entry = null;
		int pos = 0;

		outer:
		for (int i = 0; i < key.length(); i++) {
			PosList list = map.get(key.charAt(i));
			if (list == null) break;

			for (int j = 0; j < list.size; j++) {
				Entry<?> entry1 = list.entries[j];
				String key1 = entry1.key;
				if (i < key1.length() && key1.charAt(i) == key.charAt(i) && list.pos[j] == pos) {

					System.arraycopy(list.entries, j+1, list.entries, j, list.size-j-1);
					System.arraycopy(list.pos, j+1, list.pos, j, list.size-j-1);
					list.entries[--list.size] = null;

					assert entry == null || entry == entry1;
					entry = entry1;
					pos++;
					continue outer;
				}
			}

			break;
		}

		if (entry == null) return null;

		assert pos == entry.key.length();
		return (V) entry.value;
	}

	public String prepareUnorderedQuery(String key) {
		char[] out = ArrayCache.getCharArray(key.length(), false);
		int[] size = ArrayCache.getIntArray(key.length(), 0);
		for (int i = 0; i < key.length(); i++) {
			PosList prev = map.get(key.charAt(i));
			if (prev == null) return null;
			out[i] = key.charAt(i);
			size[i] = prev.size;
		}
		TimSortForEveryone.sort(0, key.length(), (refLeft, offLeft, offRight) -> Integer.compare(U.getInt(refLeft, offLeft), U.getInt(offRight)),
			NativeArray.primitiveArray(size), NativeArray.primitiveArray(out));

		String s = new String(out, 0, key.length());
		ArrayCache.putArray(size);
		ArrayCache.putArray(out);
		return s;
	}

	private static final long VOL_OFFSET = ReflectionUtils.fieldOffset(MatchMapString.class, "vol0");
	private Object[] vol0;
	private Object[] getVolArray() {
		Object[] vol0 = (Object[]) U.getAndSetObject(this, VOL_OFFSET, null);
		if (vol0 == null) vol0 = new Object[] { new PosList(), new PosList(), new SimpleList<>(), new SimpleList<>(), new int[2] };
		return vol0;
	}
	private void setVolArray(Object[] vol0) {
		((PosList) vol0[0]).clear();
		((PosList) vol0[1]).clear();
		((SimpleList<?>) vol0[2]).clear();
		((SimpleList<?>) vol0[3]).clear();
		U.compareAndSwapObject(this, VOL_OFFSET, null, vol0);
	}

	public static final byte MATCH_SHORTER = 1, MATCH_LONGER = 2, MATCH_CONTINUOUS = 4;

	/**
	 * @see #matchUnordered(String, int, List)
	 */
	public List<Entry<V>> matchUnordered(String key, int flag) { return matchUnordered(key, flag, new SimpleList<>()); }

	private static MyBitSet myAddBitset(int pos, int[] flag, MyBitSet extraFlag) {
		if (pos < 16) {
			if ((flag[0] & (1 << pos)) != 0) return extraFlag;
			flag[0] |= 1 << pos;
			flag[1] = 1;
		} else {
			if (extraFlag == null) extraFlag = new MyBitSet();
			if (extraFlag.add(pos)) flag[1] = 1;
		}
		return extraFlag;
	}
	/**
	 * 该方法的匹配模式:
	 * 返回之前通过添加的字符串（S）中，包含key中每个字符出现的次数的
	 * 参考：添加过abcde
	 * [0] abc acd cde均可获取 (也即key中只能出现abcde，且最多一个，且需要按顺序)
	 * [MATCH_SHORTER] adeg ((名称存在争议) 符合上述条件，且以S中最后一个字符结尾的子序列也可匹配)
	 */
	@SuppressWarnings("unchecked")
	public List<Entry<V>> matchUnordered(final String key, final int flag, final List<Entry<V>> out) {
		if (key.length() == 0) throw new IllegalArgumentException();

		PosList prev = map.get(key.charAt(0));
		if (prev == null) return out;

		Object[] vol0 = getVolArray();
		PosList next = (PosList) vol0[0];

		List<MyBitSet>
			prevBits = (List<MyBitSet>) vol0[2],
			nextBits = (List<MyBitSet>) vol0[3];

		int[] tmp00 = (int[]) vol0[4];

		int i = 1;
		block:
		if (key.length() > 1) {
			{
				PosList list = map.get(key.charAt(i));
				if (list == null) break block;

				if (list.size > prev.size) {
					PosList tmp = prev;
					prev = list;
					list = tmp;
				}

				int prevK = 0;
				for (int j = 0; j < list.size; j++) {
					Entry<?> entry = list.entries[j];

					int length = entry.key.length();
					if (length != key.length()) {
						if (length < key.length()) {
							if ((flag&MATCH_SHORTER) == 0) continue;
						} else {
							if ((flag&MATCH_LONGER) == 0) continue;
						}
					}

					int k = prev.indexOf(entry, prevK);
					if (k < 0) continue;
					prevK = k+1;

					if (next.indexOf(entry, 0) >= 0) continue;

					tmp00[0] = 0;
					MyBitSet bitSet = myAddBitset(list.pos[j], tmp00, null);
					bitSet = myAddBitset(prev.pos[k], tmp00, bitSet);

					prevBits.add(bitSet);
					next.add(entry, tmp00[0]);
				}
			}

			if (next.size == 0 || ++i == key.length()) break block;

			prev = next;
			next = (PosList) vol0[1];

			while (true) {
				PosList list = map.get(key.charAt(i));
				if (list == null) break;

				Entry<?>[] keys = list.entries;
				char[] vals = list.pos;
				int prevK = 0;

				for (int j = 0; j < prev.size; j++) {
					Entry<?> entry = prev.entries[j];

					fail: {
						int k = list.indexOf(entry, prevK);
						if (k < 0) break fail;
						prevK = k;

						MyBitSet bitSet = prevBits.get(j);
						tmp00[0] = prev.pos[j];
						tmp00[1] = 0;

						while (true) {
							bitSet = myAddBitset(vals[k], tmp00, bitSet);
							if (tmp00[1] != 0) break;

							if (++k == list.size || keys[k] != entry) {
								k = prevK;
								do {
									if (k == 0 || keys[--k] != entry) break fail;
									bitSet = myAddBitset(vals[k], tmp00, bitSet);
								} while (tmp00[1] == 0);

								break;
							}
						}

						next.add(entry, tmp00[0]);
						nextBits.add(bitSet);
						continue;
					}

					if ((flag&MATCH_SHORTER) != 0) out.add((Entry<V>) entry);
				}

				if (next.size == 0 || ++i == key.length()) break;

				PosList temp = next;
				next = prev;
				next.clear();
				prev = temp;

				List<MyBitSet> tmp = nextBits;
				nextBits = prevBits;
				nextBits.clear();
				prevBits = tmp;
			}
		} else {
			for (i = 0; i < prev.size; i++) {
				Entry<?> entry = prev.entries[i];
				if (((flag & MATCH_LONGER) != 0 || entry.key.length() == 1) && !out.contains(entry))
					out.add((Entry<V>) entry);
			}

			setVolArray(vol0);
			return out;
		}

		for (i = 0; i < next.size; i++) {
			Entry<?> entry = next.entries[i];
			if ((flag & MATCH_LONGER) == 0) {
				int bits = Integer.bitCount(next.pos[i]);
				MyBitSet bitSet = prevBits.get(i);
				if (bitSet != null) bits += bitSet.size();
				if (bits < entry.key.length()) continue;
			}

			out.add((Entry<V>) entry);
		}

		setVolArray(vol0);
		return out;
	}

	/**
	 * @param flag 该方法的匹配模式:
	 * <pre>
	 * [0] 返回Map内容（下称S）中，包含key的子序列 <b>（不一定连续）</b>
	 *     比如添加了abc，则ac可获取,bc也可获取（结尾相同即可）
	 * [MATCH_CONTINUOUS] 返回S中，<b>连续</b>包含key的子序列
	 *     如同 LIKE %key%
	 *     比如添加了abcde，则ac,bd不能获取，cde,bcde...可获取
	 * [MATCH_SHORTER] 返回，匹配到S结尾，但未匹配到key结尾，的成功匹配
	 *    key=abc可以在该模式下匹配S=ab
	 * [MATCH_LONGER] 返回，未匹配到S结尾的成功匹配
	 *    key=ab可以在该模式下匹配S=abc
	 * 上述模式可组合使用
	 */
	@SuppressWarnings("unchecked")
	public List<Entry<V>> matchOrdered(String key, @MagicConstant(flags = {MATCH_CONTINUOUS, MATCH_SHORTER, MATCH_LONGER}) final int flag, List<Entry<V>> out) {
		if (key.length() == 0) throw new IllegalArgumentException();

		PosList prev = map.get(key.charAt(0));
		if (prev == null) return out;

		Object[] vol0 = getVolArray();
		PosList next = (PosList) vol0[0];

		int i = 1;
		block:
		if (key.length() > 1) {
			{
				PosList list = map.get(key.charAt(i));
				if (list == null) break block;

				Entry<?>[] keys = prev.entries;
				char[] vals = prev.pos;
				int prevK = 0;

				fail:
				for (int j = 0; j < list.size; j++) {
					Entry<?> entry = list.entries[j];

					int length = entry.key.length();
					if (length < key.length()) {
						if ((flag&MATCH_SHORTER) == 0) continue;
					}

					int pos = list.pos[j];

					int k = prev.indexOf(entry, prevK);
					if (k < 0) continue;
					prevK = k+1;

					if (vals[k] <= pos) {
						do {
							if (k+1 == prev.size || keys[k+1] != entry) break;
						} while (vals[++k] <= pos);
					} else {
						do {
							if (k == 0 || keys[k-1] != entry) continue fail;
						} while (vals[--k] > pos);
					}

					if ((flag&MATCH_CONTINUOUS) != 0 && pos != vals[k]+1) continue;

					if (next.indexOf(entry, 0) >= 0) continue;

					if ((flag&MATCH_SHORTER) != 0 && pos == entry.key.length()-1) {
						out.add((Entry<V>) entry);
					} else if (length+1 - pos >= key.length()) {
						next.add(entry, pos);
					}
				}
			}

			if (next.size == 0 || ++i == key.length()) break block;

			prev = next;
			next = (PosList) vol0[1];

			while (true) {
				PosList list = map.get(key.charAt(i));
				if (list == null) break;

				Entry<?>[] keys = list.entries;
				char[] vals = list.pos;
				int prevK = 0;

				fail:
				for (int j = 0; j < prev.size; j++) {
					Entry<?> entry = prev.entries[j];
					int prevPos = prev.pos[j];

					int k = list.indexOf(entry, prevK);
					if (k < 0) continue;
					prevK = k+1;

					if (vals[k] <= prevPos) {
						do {
							if (++k == list.size || keys[k] != entry) continue fail;
						} while (vals[k] <= prevPos);
					} else {
						do {
							if (k == 0 || keys[k-1] != entry) break;
						} while (vals[--k] > prevPos);
					}

					int pos = vals[k];
					if ((flag&MATCH_CONTINUOUS) != 0 && pos != prevPos+1) continue;

					if ((flag&MATCH_SHORTER) != 0 && pos == entry.key.length()-1) {
						out.add((Entry<V>) entry);
					} else if (entry.key.length() - pos >= key.length() - i) {
						next.add(entry, pos);
					}
				}

				if (next.size == 0 || ++i == key.length()) break;

				PosList temp = next;
				next = prev;
				next.clear();
				prev = temp;
			}
		} else next = prev;

		for (i = 0; i < next.size; i++) {
			Entry<?> entry = next.entries[i];
			if ((flag&MATCH_LONGER) != 0 || next.pos[i] == entry.key.length()-1) {
				out.add((Entry<V>) entry);
			}
		}

		setVolArray(vol0);
		return out;
	}

	public void compat() {
		for (PosList value : map.values()) {
			value.compact();
		}
	}

	public void clear() { size = 0; map.clear(); }
}