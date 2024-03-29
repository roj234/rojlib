package roj.collect;

import roj.text.CharList;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.Iterator;

/**
 * @author Roj234
 * @since 2020/11/9 23:10
 */
public abstract class TrieEntry implements Iterable<TrieEntry>, Cloneable, MapLikeEntry<TrieEntry> {
	final char c;

	private TrieEntry next;
	@Override
	public TrieEntry nextEntry() {
		return next;
	}

	TrieEntry(char ch) {
		this.c = ch;
	}

	// region self management

	public final void putChild(TrieEntry te) {
		if (size > length * 1.3f) {
			length <<= 1;
			resize();
		}

		char key = te.c;
		if (entries == null) entries = new TrieEntry[length];
		TrieEntry prev = null, entry = entries[idx(key)];
		if (entry == null) {
			entries[idx(key)] = te;
			size++;
			return;
		}
		while (true) {
			if (entry.c == key) {
				if (prev == null) {
					entries[idx(key)] = te;
				} else {
					prev.next = te;
				}
				te.next = entry.next;
				return;
			}
			if (entry.next == null) break;
			prev = entry;
			entry = entry.next;
		}
		entry.next = te;
		size++;
	}

	public final boolean removeChild(TrieEntry te) {
		TrieEntry prevEntry = null;
		TrieEntry entry = first(te.c);
		while (entry != null) {
			if (entry.c == te.c) {
				break;
			}
			prevEntry = entry;
			entry = entry.next;
		}

		if (entry == null) return false;

		this.size--;

		if (prevEntry != null) {
			prevEntry.next = entry.next;
		} else {
			this.entries[idx(te.c)] = entry.next;
		}

		entry.next = null;

		return true;
	}

	public final TrieEntry getChild(char key) {
		TrieEntry entry = first(key);
		while (entry != null) {
			if (entry.c == key) return entry;
			entry = entry.next;
		}
		return null;
	}

	@Nonnull
	@Override
	public final Iterator<TrieEntry> iterator() {
		return new EntryItr<>(entries, null);
	}

	// endregion

	public abstract int copyFrom(TrieEntry node);

	abstract boolean isValid();

	CharSequence text() {
		return null;
	}

	void append(CharList sb) {
		sb.append(c);
	}

	int length() {
		return 1;
	}

	@Override
	public String toString() {
		return "CE{" + c + "}";
	}

	TrieEntry[] entries;
	int size;
	int length = 1;

	private void resize() {
		TrieEntry[] newEntries = new TrieEntry[length];
		TrieEntry entry;
		TrieEntry next;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			entry = entries[i];
			while (entry != null) {
				next = entry.next;
				int newKey = idx(entry.c);
				TrieEntry entry2 = newEntries[newKey];
				newEntries[newKey] = entry;
				entry.next = entry2;
				entry = next;
			}
		}

		this.entries = newEntries;
	}

	private int idx(int id) {
		return (id ^ (id >> 8)) & (length - 1);
	}

	private TrieEntry first(char k) {
		if (entries == null) return null;
		return entries[idx(k)];
	}

	public final void clear() {
		this.length = 1;
		this.entries = null;
		this.size = 0;
	}

	@Override
	public TrieEntry clone() {
		TrieEntry entry = null;
		try {
			entry = (TrieEntry) super.clone();
		} catch (CloneNotSupportedException ignored) {}
		entry.clear();
		return entry;
	}

	static final class Val extends AbstractIterator<TrieEntry> {
		TrieEntry map, entry;
		int i;

		public Val(TrieEntry map) {
			this.map = map;
			if (map.entries == null) stage = ENDED;
		}

		@Override
		public boolean computeNext() {
			TrieEntry[] entries = map.entries;
			while (true) {
				if (entry != null) {
					result = entry;
					entry = entry.next;
					return true;
				} else {
					if (i < entries.length) {
						this.entry = entries[i++];
					} else {
						return false;
					}
				}
			}
		}
	}

	static abstract class Itr<NEXT, ENTRY extends TrieEntry> extends AbstractIterator<NEXT> {
		SimpleList<ENTRY> a = new SimpleList<>(), b = new SimpleList<>();
		ENTRY ent;
		int i;
		protected CharList seq;

		public final void setupDepthFirst(ENTRY root) {
			a.add(root);
			b.add(Helpers.cast(root.iterator()));
		}

		public final void setupWidthFirst(ENTRY root) {
			a.add(root);
		}

		public final void key() {
			key(new CharList());
		}
		public final void key(CharList base) {
			seq = base;
			i = base.length();
		}

		// 深度优先
		@SuppressWarnings("unchecked")
		public final boolean _computeNextDepthFirst() {
			SimpleList<ENTRY> a = this.a;
			SimpleList<Iterator<ENTRY>> b = Helpers.cast(this.b);

			int i = a.size - 1;
			while (true) {
				ENTRY ent = a.get(i);
				if (this.ent != ent) {
					if (ent.isValid()) {
						this.ent = ent;
						updateKey_DFS();
						return true;
					}
				}

				Iterator<ENTRY> itr;
				while (!(itr = b.get(i)).hasNext()) {
					a.remove(i);
					b.remove(i--);
					if (i < 0) return false;
				}

				ENTRY t = itr.next();
				a.add(t);
				b.add((Iterator<ENTRY>) t.iterator());
				i++;
			}
		}

		// 广度优先
		@SuppressWarnings("unchecked")
		public final boolean _computeNextWidthFirst() {
			SimpleList<ENTRY> a = this.a;
			while (true) {
				if (a.size == 0) return false;

				while (i < a.size) {
					if ((ent = a.get(i++)).isValid()) return true;
				}
				i = 0;

				SimpleList<ENTRY> b = this.b;
				for (ENTRY entry : a) {
					if (entry.size > 0) {
						b.ensureCapacity(b.size + entry.size);
						for (TrieEntry subEntry : entry) {
							b.add((ENTRY) subEntry);
						}
					}
				}

				// Swap
				a.size = 0;
				this.a = this.b;
				this.b = a;
				a = this.a;
			}
		}

		private void updateKey_DFS() {
			if (seq == null) return;
			seq.setLength(i);
			// 1: skip root entry
			for (int i = 1; i < a.size; i++) {
				a.get(i).append(seq);
			}
		}
	}

	public static final class KeyItr extends Itr<CharSequence, TrieEntry> {
		KeyItr(TrieEntry root) {
			this(root, new CharList());
		}

		KeyItr(TrieEntry root, CharList base) {
			setupDepthFirst(root);
			result = seq = base;
			i = base.length();
		}

		@Override
		public boolean computeNext() {
			return _computeNextDepthFirst();
		}

		public TrieEntry entry() {
			return ent;
		}
	}
}
