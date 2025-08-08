package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.collect.TrieEntry.KeyItr;
import roj.config.data.CInt;
import roj.text.CharList;
import roj.text.TextUtil;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static roj.collect.TrieTree.COMPRESS_START_DEPTH;

/**
 * @author Roj234
 * @since 2021/4/30 19:27
 */
public final class TrieTreeSet extends AbstractSet<CharSequence> {
	static class Entry extends TrieEntry {
		boolean isEnd;

		Entry(char c) {
			super(c);
		}

		Entry(char c, Entry entry) {
			super(c);
			this.entries = entry.entries;
			this.mask = entry.mask;
			this.size = entry.size;
			this.isEnd = entry.isEnd;
		}

		public int copyFrom(TrieEntry x) {
			Entry node = (Entry) x;
			int v = 0;
			if (node.isEnd && !isEnd) {
				this.isEnd = true;
				v = 1;
			}

			for (TrieEntry entry : node) {
				TrieEntry sub = getChild(entry.firstChar);
				if (sub == null) putChild(sub = entry.clone());
				v += sub.copyFrom(entry);
			}
			return v;
		}

		@Override
		public boolean isLeaf() { return isEnd; }
	}

	static final class PEntry extends Entry {
		CharSequence val;

		PEntry(CharSequence val) {
			super(val.charAt(0));
			this.val = val;
		}

		PEntry(CharSequence val, Entry entry) {
			super(val.charAt(0), entry);
			this.val = val;
		}

		CharSequence text() { return val; }
		@Override
		public void append(CharList sb) { sb.append(val); }
		@Override
		public int length() { return val.length(); }
		@Override
		public String toString() { return "PE{"+val+'}'; }
	}

	/**
	 * Ref for ""
	 */
	Entry root = new Entry((char) 0);
	int size = 0;

	public TrieTreeSet() {
	}

	public TrieTreeSet(String... args) {
		for (String s : args)
			add(s);
	}

	public TrieTreeSet(Collection<? extends CharSequence> args) {
		this.addAll(args);
	}

	public Entry getRoot() {
		return root;
	}

	Entry entryForPut(CharSequence s, int i, int len) {
		if (len - i < 0) throw new IllegalArgumentException("Δlength < 0");

		Entry entry = root;
		Entry prev;
		for (; i < len; i++) {
			char c = s.charAt(i);
			prev = entry;
			entry = (Entry) entry.getChild(c);
			if (entry == null) {
				if (len - i == 1 || i < COMPRESS_START_DEPTH) {
					prev.putChild(entry = new Entry(c));
				} else {
					prev.putChild(entry = new PEntry(s.subSequence(i, len)));
					break;
				}
			} else if (entry.text() != null) {
				final CharSequence text = entry.text();

				int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
				if (lastMatch == text.length()) {
					i += lastMatch - 1;
				} else {
					if (lastMatch == 1) {
						prev.putChild(new Entry(entry.firstChar));
					} else {
						((PEntry) entry).val = text.subSequence(0, lastMatch);
					}

					Entry child;
					if (text.length() - 1 == lastMatch) {
						child = new Entry(text.charAt(lastMatch), entry);
					} else {
						child = new PEntry(text.subSequence(lastMatch, text.length()), entry);
					}

					entry.isEnd = false;
					entry.clear();

					(entry = (Entry) prev.getChild(entry.firstChar)).putChild(child);

					lastMatch += i;
					if (len == lastMatch + 1) {
						child = new Entry(s.charAt(len - 1));
					} else {
						if (len == lastMatch) {
							break;
						} else {
							child = new PEntry(s.subSequence(lastMatch, len));
						}
					}

					entry.putChild(child);
					entry = child;

					break;
				}
			}
		}
		if (!entry.isEnd) {
			size++;
			entry.isEnd = true;
		}
		return entry;
	}

	Entry getEntry(CharSequence s, int i, int len) {
		if (len - i < 0) throw new IllegalArgumentException("Δlength < 0");

		Entry entry = root;
		for (; i < len; i++) {
			entry = (Entry) entry.getChild(s.charAt(i));
			if (entry == null) return null;
			final CharSequence text = entry.text();
			if (text != null) {
				int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
				if (lastMatch != text.length()) return null;
				i += text.length() - 1;
			}
		}
		return entry.isEnd ? entry : null;
	}

	public void addTrieTree(TrieTreeSet m) {
		size += root.copyFrom(m.root);
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public boolean add(CharSequence key) {
		return add(key, 0, key.length());
	}

	public boolean add(CharSequence key, int len) {
		return add(key, 0, len);
	}

	public boolean add(CharSequence key, int off, int len) {
		int size = this.size;
		Entry entry = entryForPut(key, off, len);
		return size != this.size;
	}

	@Override
	public boolean addAll(@NotNull Collection<? extends CharSequence> m) {
		if (m instanceof TrieTreeSet) {
			addTrieTree((TrieTreeSet) m);
			return true;
		}
		for (CharSequence entry : m) {
			this.add(entry);
		}
		return true;
	}

	@Override
	public boolean remove(Object o) {
		CharSequence s = (CharSequence) o;
		return remove(s, 0, s.length());
	}

	public boolean remove(CharSequence s, int len) {
		return remove(s, 0, len);
	}

	public boolean remove(CharSequence s, int i, int len) {
		if (len - i < 0) throw new IllegalArgumentException("Δlength < 0");

		ArrayList<Entry> list = new ArrayList<>();

		Entry entry = root;
		for (; i < len; i++) {
			list.add(entry);

			entry = (Entry) entry.getChild(s.charAt(i));
			if (entry == null) return false;

			final CharSequence text = entry.text();
			if (text != null) {
				int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
				if (lastMatch != text.length()) return false;
				i += text.length() - 1;
			}
		}
		if (!entry.isEnd) return false;

		entry.isEnd = false;
		size--;

		i = list.size - 1;

		Entry prev = entry;
		while (i >= 0) {
			Entry curr = list.get(i);

			if (curr.size > 1 || curr.isLeaf()) {
				curr.removeChild(prev);

				if (curr.size == 1 && !curr.isEnd && i >= COMPRESS_START_DEPTH) {
					CharList sb = new CharList(16);

					do {
						curr.append(sb);

						TrieEntry[] entries = curr.entries;
						for (TrieEntry trieEntry : entries) {
							if (trieEntry != null) {
								curr = (Entry) trieEntry;
								break;
							}
						}
					} while (curr.size == 1 && !curr.isEnd);
					curr.append(sb);
					// sb = "bdefghi"

					// sb.length 必定大于 1
					// 因为至少包含 curr 与 curr.next
					list.get(i - 1).putChild(new PEntry(sb.toString(), curr));
				}
				return true;
			}
			prev = curr;
			i--;
		}

		root.removeChild(entry);
		return true;
	}

	@Override
	public boolean contains(Object i) {
		if (!(i instanceof CharSequence)) throw new ClassCastException();
		final CharSequence s = (CharSequence) i;
		return getEntry(s, 0, s.length()) != null;
	}

	public boolean contains(CharSequence i, int len) {
		return getEntry(i, 0, len) != null;
	}
	public boolean contains(CharSequence i, int off, int len) {
		return getEntry(i, off, len) != null;
	}

	public int longestMatches(CharSequence s) { return longestMatches(s, 0, s.length()); }
	public int longestMatches(CharSequence s, int len) { return longestMatches(s, 0, len); }
	public int longestMatches(CharSequence s, int i, int len) {
		int d = 0;

		Entry entry = root, next;
		for (; i < len; i++) {
			next = (Entry) entry.getChild(s.charAt(i));
			if (next == null) break;

			CharSequence text = next.text();
			if (text != null) {
				int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
				d += lastMatch;

				if (lastMatch < text.length()) break;
				i += text.length() - 1;
			} else {
				d++;
			}

			entry = next;
		}

		return entry.isEnd ? d : -1;
	}

	public List<String> keyMatches(CharSequence s, int limit) { return keyMatches(s, 0, s.length(), limit); }
	public List<String> keyMatches(CharSequence s, int len, int limit) { return keyMatches(s, 0, len, limit); }
	public List<String> keyMatches(CharSequence s, int i, int len, int limit) {
		CharList sb = new CharList();

		Entry entry = root, next;
		for (; i < len; i++) {
			next = (Entry) entry.getChild(s.charAt(i));
			if (next == null) {
				return Collections.emptyList();
			}
			final CharSequence text = next.text();
			if (text != null) {
				int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
				if (lastMatch != text.length()) {
					if (lastMatch < len - i) // 字符串没匹配上
					{return Collections.emptyList();}

					entry = next;
					sb.append(text);
					break;
				}
				i += text.length() - 1;
				sb.append(text);
			} else {
				sb.append(next.firstChar);
			}
			entry = next;
		}

		List<String> values = new java.util.ArrayList<>();
		KeyItr itr = new KeyItr(entry, sb);
		while (itr.hasNext()) {
			if (values.size() >= limit) {
				break;
			}
			values.add(itr.next().toString());
		}
		return values;
	}

	/**
	 * internal: nodes.forEach(if (s.startsWith(node)))
	 * s: abcdef
	 * node: abcdef / abcde / abcd... true
	 * node: abcdefg  ... false
	 */
	public boolean strStartsWithThis(CharSequence s) {
		return strStartsWithThis(s, 0, s.length());
	}
	public boolean strStartsWithThis(CharSequence s, int len) {
		return strStartsWithThis(s, 0, len);
	}
	public boolean strStartsWithThis(CharSequence s, int i, int len) {
		Entry entry = root;

		for (; i < len;) {
			entry = (Entry) entry.getChild(s.charAt(i));
			if (entry == null) return false;
			var text = entry.text();
			if (text != null) {
				int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
				// 不符合规定: entry.isEnd && lastMatch == len - i;
				if (lastMatch != text.length()) return false;
			}
			i += entry.length();

			if (entry.isEnd) return true;
		}

		return entry.isEnd;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("TrieTreeSet").append('{');
		if (!isEmpty()) {
			forEach((key) -> sb.append(key).append(','));
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
	 * 懒得弄
	 *
	 * @return null
	 */
	@NotNull
	@Override
	public Iterator<CharSequence> iterator() {
		return new KeyItr(root);
	}

	@NotNull
	@Override
	public Object[] toArray() {
		Object[] arr = new Object[size];
		reuseLambda(arr);
		return arr;
	}

	private void reuseLambda(Object[] arr) {
		CInt i = new CInt(0);
		forEach((cs) -> arr[i.value++] = cs);
	}

	@NotNull
	@Override
	@SuppressWarnings("unchecked")
	public <T> T[] toArray(@NotNull T[] arr) {
		if (arr.length < size) {
			Class<?> newType = arr.getClass();
			arr = (newType == Object[].class) ? (T[]) new Object[size] : (T[]) Array.newInstance(newType.getComponentType(), size);
		}
		reuseLambda(arr);
		return arr;
	}

	@Override
	public void forEach(Consumer<? super CharSequence> consumer) {
		recursionEntry(root, consumer, new CharList());
	}

	private static void recursionEntry(Entry parent, Consumer<? super CharSequence> consumer, CharList sb) {
		if (parent.isEnd) {
			consumer.accept(sb.toString());
		}
		for (TrieEntry entry : parent) {
			entry.append(sb);
			recursionEntry((Entry) entry, consumer, sb);
			sb.setLength(sb.length() - entry.length());
		}
	}

	@Override
	public boolean removeIf(Predicate<? super CharSequence> filter) {
		boolean mod = false;
		KeyItr itr = new KeyItr(root);
		while (itr.hasNext()) {
			CharSequence k = itr.next();
			if (filter.test(k)) {
				remove(k);
				mod = true;
			}
		}
		return mod;
	}
}