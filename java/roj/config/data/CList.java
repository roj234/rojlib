package roj.config.data;

import org.jetbrains.annotations.NotNull;
import roj.collect.IntMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.config.TOMLParser;
import roj.config.Tokenizer;
import roj.config.serial.CVisitor;
import roj.text.CharList;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class CList extends CEntry implements Iterable<CEntry> {
	protected List<CEntry> elements;

	public CList() { this(SimpleList.hugeCapacity(0)); }
	public CList(int initialCapacity) { elements = new SimpleList<>(initialCapacity); }
	@SuppressWarnings("unchecked")
	public CList(List<? extends CEntry> elements) { this.elements = (List<CEntry>) elements; }

	public Type getType() { return Type.LIST; }

	public final CList asList() { return this; }

	public void accept(CVisitor visitor) {
		var entries = elements;
		visitor.valueList(entries.size());
		for (int i = 0; i < entries.size(); i++) entries.get(i).accept(visitor);
		visitor.pop();
	}

	public List<CEntry> raw() { return elements; }
	public Object unwrap() {
		List<Object> rawdata = Arrays.asList(new Object[elements.size()]);
		for (int i = 0; i < elements.size(); i++) rawdata.set(i, elements.get(i).unwrap());
		return rawdata;
	}

	public final boolean isEmpty() { return elements.isEmpty(); }
	public final int size() { return elements.size(); }

	@NotNull
	public Iterator<CEntry> iterator() { return elements.iterator(); }
	@Override
	public void forEach(Consumer<? super CEntry> action) { elements.forEach(action); }
	@Override
	public Spliterator<CEntry> spliterator() { return elements.spliterator(); }

	public final CList add(CEntry entry) { elements.add(entry == null ? CNull.NULL : entry); return this; }
	public final void add(String s) { elements.add(CEntry.valueOf(s)); }
	public final void add(boolean b) { elements.add(CEntry.valueOf(b)); }
	public final void add(int s) { elements.add(CEntry.valueOf(s)); }
	public final void add(long s) { elements.add(CEntry.valueOf(s)); }
	public final void add(double s) { elements.add(CEntry.valueOf(s)); }

	public void set(int i, CEntry val) { elements.set(i, val);}
	@NotNull
	public CEntry get(int i) { return elements.get(i); }
	public final boolean getBool(int i) {
		CEntry entry = elements.get(i);
		return entry.mayCastTo(Type.BOOL) && entry.asBool();
	}
	public final String getString(int i) {
		CEntry entry = elements.get(i);
		return entry.mayCastTo(Type.STRING) ? entry.asString() : "";
	}
	public final int getInteger(int i) {
		CEntry entry = elements.get(i);
		return entry.mayCastTo(Type.INTEGER) ? entry.asInt() : 0;
	}
	public final long getLong(int i) {
		CEntry entry = elements.get(i);
		return entry.mayCastTo(Type.LONG) ? entry.asLong() : 0;
	}
	public final double getFloat(int i) {
		CEntry entry = elements.get(i);
		return entry.mayCastTo(Type.Float4) ? entry.asFloat() : 0;
	}
	public final double getDouble(int i) {
		CEntry entry = elements.get(i);
		return entry.mayCastTo(Type.DOUBLE) ? entry.asDouble() : 0;
	}
	public CList getList(int i) { return elements.get(i).asList(); }
	public CMap getMap(int i) { return elements.get(i).asMap(); }

	public final MyHashSet<String> toStringSet() {
		MyHashSet<String> stringSet = new MyHashSet<>(elements.size());
		for (int i = 0; i < elements.size(); i++) {
			stringSet.add(elements.get(i).asString());
		}
		return stringSet;
	}
	public final SimpleList<String> toStringList() {
		SimpleList<String> stringList = new SimpleList<>(elements.size());
		for (int i = 0; i < elements.size(); i++) {
			stringList.add(elements.get(i).asString());
		}
		return stringList;
	}
	public final String[] toStringArray() {
		String[] array = new String[elements.size()];
		for (int i = 0; i < elements.size(); i++) {
			array[i] = elements.get(i).asString();
		}
		return array;
	}
	public byte[] toByteArray() {
		byte[] array = new byte[elements.size()];
		for (int i = 0; i < elements.size(); i++) {
			CEntry entry = elements.get(i);
			array[i] = (byte) (entry.mayCastTo(Type.Int1) ? entry.asInt() : 0);
		}
		return array;
	}
	public int[] toIntArray() {
		int[] array = new int[elements.size()];
		for (int i = 0; i < elements.size(); i++) {
			array[i] = elements.get(i).asInt();
		}
		return array;
	}
	public long[] toLongArray() {
		long[] array = new long[elements.size()];
		for (int i = 0; i < elements.size(); i++) {
			array[i] = elements.get(i).asLong();
		}
		return array;
	}


	public final boolean isCommentable() {return false;}
	public CList toCommentable() {return new Commentable(elements);}
	public String getComment(int key) {return null;}
	public void setComment(int key, String val) {throw new UnsupportedOperationException();}
	public void clearComments() {}

	@Override
	public CharList toTOML(CharList sb, int depth, CharSequence chain) {
		if (elements.isEmpty()) {
			return sb.append("[]");
		} else if (depth != 3) {
			for (int i = 0; i < elements.size(); i++) {
				sb.append("[[");
				if (!TOMLParser.literalSafe(chain)) {
					Tokenizer.escape(sb, chain);
				} else {
					sb.append(chain);
				}
				elements.get(i).toTOML(sb.append("]]\n"), 2, chain).append("\n");
			}
			sb.setLength(sb.length()-1);
			return sb;
		} else {
			if (!TOMLParser.literalSafe(chain)) {
				Tokenizer.escape(sb, chain);
			} else {
				sb.append(chain);
			}
			sb.append(" = [");
			for (int j = 0; j < elements.size(); j++) {
				CEntry entry = elements.get(j);
				entry.toTOML(sb, 3, chain).append(", ");
			}
			sb.delete(sb.length() - 2, sb.length());
			return sb.append(']');
		}
	}

	public int hashCode() { return elements.hashCode(); }
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof CList that)) return false;
		return elements.equals(that.elements);
	}

	public static class Commentable extends CList {
		public IntMap<String> comments = new IntMap<>();

		public Commentable() {}
		public Commentable(List<CEntry> elements) {super(elements);}

		@Override
		public void accept(CVisitor visitor) {
			var entries = elements;
			visitor.valueList(entries.size());
			for (int i = 0; i < entries.size(); i++) {
				var comment = comments.get(i);
				if (comment != null) visitor.comment(comment);
				entries.get(i).accept(visitor);
			}
			visitor.pop();
		}

		public final boolean isCommentSupported() {return true;}
		public String getComment(int key) {return comments.get(key);}
		public void setComment(int key, String val) {comments.put(key, val);}
		public final CList toCommentable() {return this;}
		public void clearComments() {comments.clear();}
	}
}