package roj.collect;

import roj.collect.TrieEntry.Itr;
import roj.collect.TrieEntry.KeyItr;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static roj.collect.IntMap.UNDEFINED;

/**
 * Enhance: 改成byte[]存储数据（用VIVIC），当然如果用Java9就没有必要了
 * @author Roj234
 * @since 2021/2/21 10:39
 */
public class TrieTree<V> extends AbstractMap<CharSequence, V> {
	static final int COMPRESS_START_DEPTH = 1, MIN_REMOVE_ARRAY_SIZE = 5;

	public static class Entry<V> extends TrieEntry {
		V value;

		@SuppressWarnings("unchecked")
		Entry(char c) {
			super(c);
			this.value = (V) UNDEFINED;
		}

		Entry(char c, Entry<V> entry) {
			super(c);
			this.length = entry.length;
			this.size = entry.size;
			this.entries = entry.entries;
			this.value = entry.value;
		}

		@Override
		boolean isValid() {
			return value != UNDEFINED;
		}

		@SuppressWarnings("unchecked")
		public int copyFrom(TrieEntry x) {
			Entry<?> node = (Entry<?>) x;
			int v = 0;
			if (node.value != UNDEFINED && value == UNDEFINED) {
				this.value = (V) node.value;
				v = 1;
			}

			for (TrieEntry entry : node) {
				TrieEntry sub = getChild(entry.c);
				if (sub == null) putChild(sub = entry.clone());
				v += sub.copyFrom(entry);
			}
			return v;
		}

		public V getValue() {
			if (value == UNDEFINED) throw new UnsupportedOperationException();
			return value;
		}

		public V setValue(V value) {
			if (value == UNDEFINED || this.value == UNDEFINED) throw new UnsupportedOperationException();
			V ov = this.value;
			this.value = value;
			return ov;
		}
	}

	static final class PEntry<V> extends Entry<V> {
		CharSequence val;

		PEntry(CharSequence val) {
			super(val.charAt(0));
			this.val = val;
		}

		PEntry(CharSequence val, Entry<V> entry) {
			super(val.charAt(0), entry);
			this.val = val;
		}

		CharSequence text() {
			return val;
		}

		@Override
		void append(CharList sb) {
			sb.append(val);
		}

		@Override
		int length() {
			return val.length();
		}

		@Override
		public String toString() {
			return "PE{" + val + '}';
		}
	}

	Entry<V> root = new Entry<>((char) 0);
	int size = 0;

	public TrieTree() {
	}

	public TrieTree(Map<CharSequence, V> map) {
		putAll(map);
	}

	public void addTrieTree(TrieTree<? extends V> m) {
		size += root.copyFrom(Helpers.cast(m.root));
	}

	@SuppressWarnings("unchecked")
	Entry<V> entryForPut(CharSequence s, int i, int len) {
		if (len - i < 0) throw new IllegalArgumentException("Δlength < 0");

		Entry<V> entry = root;
		Entry<V> prev;
		for (; i < len; i++) {
			char c = s.charAt(i);
			prev = entry;
			entry = (Entry<V>) entry.getChild(c);
			if (entry == null) {
				// 前COMPRESS_START_DEPTH个字符，或者只剩一个字符不压缩
				if (len - i == 1 || i < COMPRESS_START_DEPTH) {
					prev.putChild(entry = new Entry<>(c));
				} else {
					prev.putChild(entry = new PEntry<>(s.subSequence(i, len)));
					break;
				}
			} else if (entry.text() != null) {
				final CharSequence text = entry.text();

				int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
				if (lastMatch == text.length()) {
					// 全部match
					i += lastMatch - 1;
				} else {
					// 拆分P1: 前半部分[0, lastMatch)
					if (lastMatch == 1) {
						prev.putChild(new Entry<>(entry.c));
					} else {
						((PEntry<V>) entry).val = text.subSequence(0, lastMatch);
					}

					// 拆分P2: 后半部分[lastMatch, text.length)
					Entry<V> child;
					if (text.length() - 1 == lastMatch) {
						child = new Entry<>(text.charAt(lastMatch), entry);
					} else {
						child = new PEntry<>(text.subSequence(lastMatch, text.length()), entry);
					}

					// entry的数据已复制到了child
					entry.clear();
					entry.value = (V) UNDEFINED;

					// 目的：避免之前修改entry的值
					(entry = (Entry<V>) prev.getChild(entry.c)).putChild(child);

					// 插入新的entry
					lastMatch += i;
					if (len == lastMatch + 1) {
						// 情况1
						// 原先 abcde 插入 abcdf
						// 一个字符
						child = new Entry<>(s.charAt(len - 1));
					} else {
						if (len == lastMatch) {
							// 情况2
							// 原先 abcde 插入 abcd
							// 拆分的P1就是child
							break;
						} else {
							// 情况2
							// 原先 abcde 插入 abcdef
							child = new PEntry<>(s.subSequence(lastMatch, len));
						}
					}

					entry.putChild(child);
					entry = child;

					break;
				}
			}
		}
		if (entry.value == UNDEFINED) {
			size++;
			entry.value = null;
		}
		return entry;
	}

	private Entry<V> getEntry(CharSequence s, int i, int len) {
		return getEntry1(s, i, len, EXACT);
	}

	private static final int EXACT = 0, FIRST_NON_NULL = 1, LAST_NON_NULL = 2;
	@Nullable
	@SuppressWarnings("unchecked")
	Entry<V> getEntry1(CharSequence s, int i, int len, int kind) {
		if (len - i < 0) throw new IllegalArgumentException("Δlength < 0");

		Entry<V> entry = root, prev;
		while (i < len) {
			prev = entry;
			entry = (Entry<V>) entry.getChild(s.charAt(i));
			if (entry == null) return kind == LAST_NON_NULL && prev != root ? prev : null;

			CharSequence text = entry.text();
			if (text != null) {
				int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
				if (lastMatch != text.length()) return kind == LAST_NON_NULL && prev != root ? prev : null;
				i += text.length();
			} else i++;

			if (kind == FIRST_NON_NULL && entry.value != UNDEFINED) return entry;
		}
		return entry.value != UNDEFINED ? entry : null;
	}

	public Entry<V> getRoot() {
		return root;
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public V put(CharSequence key, V e) {
		return put(key, 0, key.length(), e);
	}

	public V put(CharSequence key, int len, V e) {
		return put(key, 0, len, e);
	}

	public V put(CharSequence key, int off, int len, V e) {
		Entry<V> entry = entryForPut(key, off, len);
		V v = entry.value;
		entry.value = e;

		return v;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void putAll(@Nonnull Map<? extends CharSequence, ? extends V> m) {
		if (m instanceof TrieTree) {
			addTrieTree((TrieTree<? extends V>) m);
			return;
		}
		for (Map.Entry<? extends CharSequence, ? extends V> entry : m.entrySet()) {
			this.put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public V remove(Object k) {
		CharSequence s = (CharSequence) k;
		return remove(s, 0, s.length(), UNDEFINED);
	}

	public V remove(CharSequence s, int len) {
		return remove(s, 0, len, UNDEFINED);
	}

	public V remove(CharSequence s, int i, int len) {
		return remove(s, i, len, UNDEFINED);
	}

	@SuppressWarnings("unchecked")
	V remove(CharSequence s, int i, int len, Object tc) {
		if (len - i < 0) throw new IllegalArgumentException("Δlength < 0");

		SimpleList<Entry<V>> list = new SimpleList<>();

		Entry<V> entry = root;
		while (i < len) {
			list.add(entry);

			entry = (Entry<V>) entry.getChild(s.charAt(i));
			if (entry == null) return null;

			CharSequence text = entry.text();
			if (text != null) {
				int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
				if (lastMatch != text.length()) return null;
				i += text.length();
			} else i++;
		}
		if (entry.value == UNDEFINED) return null;
		if (!Objects.equals(entry.value, tc) && tc != UNDEFINED) return null;

		size--;

		i = list.size - 1;

		Entry<V> prev = entry;
		while (i >= 0) {
			Entry<V> curr = list.get(i);

			// 清除单线连接:
			// root <== a <== b <== cd <== efg
			if (curr.size > 1 || curr.isValid()) {
				curr.removeChild(prev);

				// 压缩剩余的entry
				// root <== a <== b (curr) <== def <== ghi
				if (curr.size == 1 && curr.value == UNDEFINED && i >= COMPRESS_START_DEPTH) {
					CharList sb = IOUtil.ddLayeredCharBuf();

					do {
						curr.append(sb);

						TrieEntry[] entries = curr.entries;
						for (TrieEntry trieEntry : entries) {
							if (trieEntry != null) {
								curr = (Entry<V>) trieEntry;
								break;
							}
						}
					} while (curr.size == 1);
					curr.append(sb);
					// sb = "bdefghi"

					// sb.length 必定大于 1
					// 因为至少包含 curr 与 curr.next
					list.get(i - 1).putChild(new PEntry<>(sb.toString(), curr));
					sb._free();
				}
				return entry.value;
			}
			prev = curr;
			i--;
		}
		throw new AssertionError("Entry list chain size");
	}

	@Override
	public boolean containsKey(Object i) {
		final CharSequence s = (CharSequence) i;
		return containsKey(s, 0, s.length());
	}
	public boolean containsKey(CharSequence s, int off, int len) {
		return getEntry(s, off, len) != null;
	}

	public Map.Entry<MutableInt, V> longestMatches(CharSequence s) {
		return longestMatches(s, 0, s.length());
	}
	public Map.Entry<MutableInt, V> longestMatches(CharSequence s, int i, int len) {
		MyHashMap.Entry<MutableInt, V> entry = new MyHashMap.Entry<>(new MutableInt(), null);
		longestIn(s,i,len,entry);
		return entry.getKey().getValue() < 0 ? null : entry;
	}
	/**
	 * 返回：s能连续匹配在map中的最长长度
	 * map=["abcd"=A, "abcedf"=B]
	 * s="abc", 返回 3, feed: -1=UNDEFINED
	 * s="abcd" 返回 4, feed: 4=A
	 * s="abcde" 返回 5, feed: 4=A
	 * s="abcdef" 返回 6, feed: 6=B
	 */
	@SuppressWarnings("unchecked")
	public int longestIn(CharSequence s, int i, int len, MyHashMap.Entry<MutableInt, V> feed) {
		feed.k.setValue(-1);

		int d = 0;

		Entry<V> entry = root;
		while (i < len) {
			entry = (Entry<V>) entry.getChild(s.charAt(i));
			if (entry == null) break;

			CharSequence text = entry.text();
			if (text != null) {
				int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
				d += lastMatch;

				if (lastMatch < text.length()) break;
				i += text.length();
			} else {
				d++;
				i++;
			}

			if (entry.value != UNDEFINED) {
				feed.k.setValue(d);
				feed.v = entry.value;
			}
		}
		return d;
	}

	@SuppressWarnings("unchecked")
	public int longestWithCallback(CharSequence s, int i, int len, MutableInt cont, BiFunction<MutableInt, V, Boolean> callback) {
		int d = 0;

		Entry<V> entry = root;
		while (i < len) {
			entry = (Entry<V>) entry.getChild(s.charAt(i));
			if (entry == null) break;

			CharSequence text = entry.text();
			if (text != null) {
				int lastMatch = TextUtil.lastMatches(text, 0, s, i, len-i);
				d += lastMatch;

				if (lastMatch < text.length()) break;
				i += text.length();
			} else {
				d++;
				i++;
			}

			if (entry.value != UNDEFINED) {
				cont.setValue(d);
				if (callback.apply(cont, entry.value)) break;
			}
		}
		return d;
	}

	public List<V> valueMatches(CharSequence s, int limit) {
		return valueMatches(s, 0, s.length(), limit);
	}
	@SuppressWarnings("unchecked")
	public List<V> valueMatches(CharSequence s, int i, int len, int limit) {
		KeyItr itr = matches(s, i, len);
		if (itr == null) return Collections.emptyList();

		SimpleList<V> values = new SimpleList<>();
		while (itr.hasNext()) {
			if (values.size() >= limit) break;

			itr.next();
			values.add(((Entry<V>) itr.ent).value);
		}
		return values;
	}

	public List<Map.Entry<String, V>> entryMatches(CharSequence s, int limit) {
		return entryMatches(s, 0, s.length(), limit);
	}
	@SuppressWarnings("unchecked")
	public List<Map.Entry<String, V>> entryMatches(CharSequence s, int i, int len, int limit) {
		KeyItr itr = matches(s, i, len);
		if (itr == null) return Collections.emptyList();

		SimpleList<Map.Entry<String, V>> entries = new SimpleList<>();
		while (itr.hasNext()) {
			if (entries.size() >= limit) {
				break;
			}
			entries.add(new AbstractMap.SimpleImmutableEntry<>(itr.next().toString(), ((Entry<V>) itr.ent).value));
		}
		return entries;
	}

	public KeyItr matchesIterator(CharSequence s) {
		return matchesIterator(s, 0, s.length());
	}

	/**
	 * <pre>
	 *   TrieEntry.KeyItr itr = map.matchesIterator(name);
	 *   while (itr.hasNext()) {
	 *       CharSequence key = itr.next();
	 *       TrieEntry val = itr.entry();
	 *   }</pre>
	 */
	public KeyItr matchesIterator(CharSequence s, int i, int len) {
		Entry<V> entry = matches(s, i, len, IOUtil.getSharedCharBuf());
		return entry == null ? null : new KeyItr(entry, new CharList(32));
	}

	private KeyItr matches(CharSequence s, int i, int len) {
		CharList prefix = IOUtil.getSharedCharBuf();
		Entry<V> entry = matches(s, i, len, prefix);
		return entry == null ? null : new KeyItr(entry, prefix);
	}
	@SuppressWarnings("unchecked")
	private Entry<V> matches(CharSequence s, int i, int len, CharList prefix) {
		Entry<V> entry = root;
		while (i < len) {
			entry = (Entry<V>) entry.getChild(s.charAt(i));
			if (entry == null) return null;

			CharSequence text = entry.text();
			if (text != null) {
				int lastMatches = TextUtil.lastMatches(text, 0, s, i, len - i);
				if (lastMatches != text.length()) {
					// 字符串没匹配上
					if (lastMatches < len - i) return null;

					prefix.append(text);
					break;
				}
				i += text.length();
			} else {
				i++;
			}

			entry.append(prefix);
		}

		return entry;
	}

	/**
	 * s.startsWith(any value in map)
	 */
	public final boolean startsWith_R(CharSequence s) {
		return startsWith_R(s, 0, s.length());
	}
	public boolean startsWith_R(CharSequence s, int i, int len) {
		return null != getEntry1(s, i, len, FIRST_NON_NULL);
	}

	@Override
	public final V get(Object id) {
		CharSequence s = (CharSequence) id;
		return get(s, 0, s.length());
	}
	public V get(CharSequence s, int off, int len) {
		Entry<V> entry = getEntry(s, off, len);
		return entry == null ? null : entry.value;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("TrieTree").append('{');
		if (!isEmpty()) {
			forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.append('}').toString();
	}

	@Override
	public void clear() {
		size = 0;
		root.clear();
	}

	public Iterator<Entry<V>> mapItr() {
		return new Itr<Entry<V>, Entry<V>>() {
			{setupDepthFirst(root);}

			@Override
			public boolean computeNext() {
				boolean v = _computeNextDepthFirst();
				if (v) result = ent;
				return v;
			}
		};
	}

	/**
	 * @see #forEach(BiConsumer)
	 */
	@Nonnull
	@Override
	@Deprecated
	public Set<Map.Entry<CharSequence, V>> entrySet() {
		return new EntrySet<>(this);
	}

	public void forEachSince(CharSequence s, BiConsumer<? super CharSequence, ? super V> consumer) {
		forEachSince(s, 0, s.length(), consumer);
	}
	public void forEachSince(CharSequence s, int i, int len, BiConsumer<? super CharSequence, ? super V> consumer) {
		CharList base = new CharList();
		Entry<V> entry = matches(s, i, len, base);
		if (entry == null) return;
		recursionEntry(root, consumer, base);
	}

	@Override
	public void forEach(BiConsumer<? super CharSequence, ? super V> consumer) {
		recursionEntry(root, consumer, new CharList());
	}

	@SuppressWarnings("unchecked")
	private static <V> void recursionEntry(Entry<V> parent, BiConsumer<? super CharSequence, ? super V> consumer, CharList sb) {
		if (parent.value != UNDEFINED) {
			consumer.accept(sb.toString(), parent.value);
		}
		for (TrieEntry entry : parent) {
			entry.append(sb);
			recursionEntry((Entry<V>) entry, consumer, sb);
			sb.setLength(sb.length() - entry.length());
		}
	}

	static class EntrySet<V> extends AbstractSet<Map.Entry<CharSequence, V>> {
		private final TrieTree<V> map;

		public EntrySet(TrieTree<V> map) {
			this.map = map;
		}

		public final int size() {
			return map.size();
		}

		public final void clear() {
			map.clear();
		}

		@Nonnull
		public final Iterator<Map.Entry<CharSequence, V>> iterator() {
			return new Itr<Map.Entry<CharSequence, V>, Entry<V>>() {
				{
					setupDepthFirst(map.root);
					key();
				}

				@Override
				public boolean computeNext() {
					boolean v = _computeNextDepthFirst();
					if (v) result = new SimpleImmutableEntry<>(seq.toString(), ent.getValue());
					return v;
				}
			};
		}
	}


	@Override
	public boolean remove(Object key, Object value) {
		int os = size;
		CharSequence cs = (CharSequence) key;
		remove(cs, 0, cs.length(), value);
		return os != size;
	}

	@Override
	public boolean replace(CharSequence key, V oldValue, V newValue) {
		Entry<V> entry = getEntry(key, 0, key.length());
		if (entry == null || entry.value == UNDEFINED) return false;
		if (Objects.equals(oldValue, entry.value)) {
			entry.value = newValue;
			return true;
		}
		return false;
	}

	@Override
	public V compute(CharSequence key, BiFunction<? super CharSequence, ? super V, ? extends V> remap) {
		Entry<V> entry = getEntry(key, 0, key.length());
		V newV = remap.apply(key, entry == null || entry.value == UNDEFINED ? null : entry.value);
		if (newV == null) {
			if (entry != null && entry.value != UNDEFINED) {
				remove(key, 0, key.length(), UNDEFINED);
			}
			return null;
		} else if (entry == null) {
			entry = entryForPut(key, 0, key.length());
		}

		if (entry.value == UNDEFINED) size++;
		entry.value = newV;

		return newV;
	}

	@Override
	public V computeIfAbsent(CharSequence key, Function<? super CharSequence, ? extends V> map) {
		Entry<V> entry = getEntry(key, 0, key.length());
		if (entry != null && entry.value != UNDEFINED) return entry.value;
		if (entry == null) {
			entry = entryForPut(key, 0, key.length());
		}
		if (entry.value == UNDEFINED) size++;
		return entry.value = map.apply(key);
	}

	@Override
	public V computeIfPresent(CharSequence key, BiFunction<? super CharSequence, ? super V, ? extends V> remap) {
		Entry<V> entry = getEntry(key, 0, key.length());
		if (entry == null || entry.value == UNDEFINED) return null;
		if (entry.value == null) return null; // default implement guarantee
		V newV = remap.apply(key, entry.value);
		if (newV == null) {
			remove(key, 0, key.length(), UNDEFINED);
			return null;
		}

		return entry.value = newV;
	}

	@Override
	public V getOrDefault(Object key, V defaultValue) {
		CharSequence cs = (CharSequence) key;
		Entry<V> entry = getEntry(cs, 0, cs.length());
		if (entry == null || entry.value == UNDEFINED) return defaultValue;
		return entry.value;
	}

	@Override
	public V putIfAbsent(CharSequence key, V value) {
		int os = size;
		Entry<V> entry = entryForPut(key, 0, key.length());
		if (os != size) {
			entry.value = value;
			return null;
		}
		return entry.value;
	}

	@Override
	public V replace(CharSequence key, V value) {
		Entry<V> entry = getEntry(key, 0, key.length());
		if (entry == null) return null;

		V v = entry.value;
		if (v == UNDEFINED) v = null;

		entry.value = value;
		return v;
	}
}