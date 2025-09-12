package roj.collect;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.collect.TrieEntry.Itr;
import roj.collect.TrieEntry.KeyItr;
import roj.util.OperationDone;
import roj.config.node.IntValue;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.nio.file.FileVisitResult;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author Roj234
 * @since 2021/2/21 10:39
 */
public class TrieTree<V> extends AbstractMap<CharSequence, V> {
	static final int COMPRESS_START_DEPTH = 1;

	public static class Entry<V> extends TrieEntry {
		V value;

		@SuppressWarnings("unchecked")
		public Entry(char c) {
			super(c);
			this.value = (V) UNDEFINED;
		}
		// only external use
		public Entry(char c, V value) {
			super(c);
			this.value = value;
		}


		Entry(char c, Entry<V> entry) {
			super(c);
			this.mask = entry.mask;
			this.size = entry.size;
			this.entries = entry.entries;
			this.value = entry.value;
		}

		@Override
		public boolean isLeaf() { return value != UNDEFINED; }

		@SuppressWarnings("unchecked")
		public int copyFrom(TrieEntry x) {
			Entry<?> node = (Entry<?>) x;
			int v = 0;
			if (node.value != UNDEFINED && value == UNDEFINED) {
				this.value = (V) node.value;
				v = 1;
			}

			for (TrieEntry entry : node) {
				TrieEntry sub = getChild(entry.firstChar);
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

		CharSequence text() { return val; }
		public void append(CharList sb) { sb.append(val); }
		public int length() { return val.length(); }
		public String toString() { return "\""+val+'"'; }
	}

	Entry<V> root = new Entry<>((char) 0);
	int size = 0;

	public TrieTree() {}
	public TrieTree(Map<CharSequence, V> map) {putAll(map);}

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
						prev.putChild(new Entry<>(entry.firstChar));
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
					(entry = (Entry<V>) prev.getChild(entry.firstChar)).putChild(child);

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
	public int size() { return size; }
	@Override
	public V put(CharSequence key, V e) { return put(key, 0, key.length(), e); }
	public V put(CharSequence key, int len, V e) { return put(key, 0, len, e); }
	public V put(CharSequence key, int off, int len, V e) {
		Entry<V> entry = entryForPut(key, off, len);
		V v = entry.value;
		entry.value = e;

		return v;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void putAll(@NotNull Map<? extends CharSequence, ? extends V> m) {
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
	public V remove(CharSequence s, int len) { return remove(s, 0, len, UNDEFINED); }
	public V remove(CharSequence s, int i, int len) { return remove(s, i, len, UNDEFINED); }
	@SuppressWarnings("unchecked")
	public V remove(CharSequence s, int i, int len, Object except) {
		if (len - i < 0) throw new IllegalArgumentException("Δlength < 0");

		ArrayList<Entry<V>> list = new ArrayList<>();

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
		if (!Objects.equals(entry.value, except) && except != UNDEFINED) return null;

		var old = entry.value;
		entry.value = (V) UNDEFINED;
		size--;

		var sb = new CharList();
		for (i = list.size-2; i >= 1; i--) {
			var self = list.get(i);
			var next = list.get(i+1);
			if (self.size == 1 && !self.isLeaf()) {
				var owner = list.get(i-1);
				owner.removeChild(self);

				sb.clear();
				self.append(sb);
				next.append(sb);

				PEntry<V> replaceEntry = new PEntry<>(sb.toString(), next);
				list.set(i, replaceEntry);
				owner.putChild(replaceEntry);
			}
		}
		sb._free();

		return old;
	}

	@Override
	public boolean containsKey(Object i) {
		final CharSequence s = (CharSequence) i;
		return containsKey(s, 0, s.length());
	}
	public boolean containsKey(CharSequence s, int off, int len) { return getEntry(s, off, len) != null; }

	public Map.Entry<IntValue, V> longestMatches(CharSequence s) { return longestMatches(s, 0, s.length()); }
	public Map.Entry<IntValue, V> longestMatches(CharSequence s, int i, int len) {
		HashMap.Entry<IntValue, V> entry = new HashMap.Entry<>(new IntValue(), null);
		match(s,i,len,entry);
		return entry.getKey().value < 0 ? null : entry;
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
	public int match(CharSequence s, int i, int len, HashMap.Entry<IntValue, V> feed) {
		feed.key.value = -1;

		int d = 0;

		//root.initFastMatcher();
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
				feed.key.value = d;
				feed.value = entry.value;
			}
		}
		return d;
	}

	@SuppressWarnings("unchecked")
	public int longestWithCallback(CharSequence s, int i, int len, IntValue cont, BiFunction<IntValue, V, Boolean> callback) {
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
				cont.value = d;
				if (callback.apply(cont, entry.value)) break;
			}
		}
		return d;
	}

	public List<V> valueMatches(CharSequence s, int limit) { return valueMatches(s, 0, s.length(), limit); }
	@SuppressWarnings("unchecked")
	public List<V> valueMatches(CharSequence s, int i, int len, int limit) {
		KeyItr itr = matches(s, i, len);
		if (itr == null) return Collections.emptyList();

		ArrayList<V> values = new ArrayList<>();
		while (itr.hasNext()) {
			if (values.size() >= limit) break;

			itr.next();
			values.add(((Entry<V>) itr.ent).value);
		}
		return values;
	}

	public List<Map.Entry<String, V>> entryMatches(CharSequence s, int limit) { return entryMatches(s, 0, s.length(), limit); }
	@SuppressWarnings("unchecked")
	public List<Map.Entry<String, V>> entryMatches(CharSequence s, int i, int len, int limit) {
		KeyItr itr = matches(s, i, len);
		if (itr == null) return Collections.emptyList();

		ArrayList<Map.Entry<String, V>> entries = new ArrayList<>();
		while (itr.hasNext()) {
			if (entries.size() >= limit) {
				break;
			}
			entries.add(new AbstractMap.SimpleImmutableEntry<>(itr.next().toString(), ((Entry<V>) itr.ent).value));
		}
		return entries;
	}

	public KeyItr matchesIterator(CharSequence s) { return matchesIterator(s, 0, s.length()); }
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
	public final boolean startsWith_R(CharSequence s) { return startsWith_R(s, 0, s.length()); }
	public boolean startsWith_R(CharSequence s, int i, int len) { return null != getEntry1(s, i, len, FIRST_NON_NULL); }

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

	/**
	 * @see #forEach(BiConsumer)
	 */
	@NotNull
	@Override
	@Deprecated
	public Set<Map.Entry<CharSequence, V>> entrySet() {
		return new EntrySet<>(this);
	}

	public void forEachSince(CharSequence s, BiFunction<? super CharSequence, ? super V, FileVisitResult> consumer) {
		forEachSince(s, 0, s.length(), consumer);
	}
	public void forEachSince(CharSequence s, int i, int len, BiFunction<? super CharSequence, ? super V, FileVisitResult> consumer) {
		CharList base = new CharList();
		Entry<V> entry = matches(s, i, len, base);
		if (entry == null) return;
		try {
			recursionEntry(entry, consumer, base);
		} catch (OperationDone ignored) {}
	}

	@Override
	public void forEach(BiConsumer<? super CharSequence, ? super V> consumer) {
		recursionEntry(root, (key, value) -> {
			consumer.accept(key, value);
			return FileVisitResult.CONTINUE;
		}, new CharList());
	}

	@SuppressWarnings("unchecked")
	private static <V> boolean recursionEntry(Entry<V> parent, BiFunction<? super CharSequence, ? super V, FileVisitResult> consumer, CharList sb) {
		if (parent.value != UNDEFINED) {
			var result = consumer.apply(sb.toString(), parent.value);
			if (result == FileVisitResult.SKIP_SUBTREE) return false;
			if (result == FileVisitResult.TERMINATE) throw OperationDone.INSTANCE;
			if (result == FileVisitResult.SKIP_SIBLINGS) return true;
		}

		int length = sb.length();
		for (TrieEntry entry : parent) {
			entry.append(sb);
			if (recursionEntry((Entry<V>) entry, consumer, sb)) break;
			sb.setLength(length);
		}

		return false;
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

		@NotNull
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