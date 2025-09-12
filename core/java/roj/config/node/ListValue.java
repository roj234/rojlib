package roj.config.node;

import org.jetbrains.annotations.NotNull;
import roj.collect.ArrayList;
import roj.collect.HashSet;
import roj.collect.IntMap;
import roj.config.TomlParser;
import roj.config.ValueEmitter;
import roj.text.CharList;
import roj.text.Tokenizer;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class ListValue extends ConfigValue implements Iterable<ConfigValue> {
	protected List<ConfigValue> elements;

	public ListValue() { this(ArrayList.hugeCapacity(0)); }
	public ListValue(int initialCapacity) { elements = new ArrayList<>(initialCapacity); }
	@SuppressWarnings("unchecked")
	public ListValue(List<? extends ConfigValue> elements) { this.elements = (List<ConfigValue>) elements; }

	public Type getType() { return Type.LIST; }

	public final ListValue asList() { return this; }

	public void accept(ValueEmitter visitor) {
		var entries = elements;
		visitor.emitList(entries.size());
		for (int i = 0; i < entries.size(); i++) entries.get(i).accept(visitor);
		visitor.pop();
	}

	public List<ConfigValue> raw() { return elements; }
	public Object unwrap() {
		List<Object> rawdata = Arrays.asList(new Object[elements.size()]);
		for (int i = 0; i < elements.size(); i++) rawdata.set(i, elements.get(i).unwrap());
		return rawdata;
	}

	public final boolean isEmpty() { return elements.isEmpty(); }
	public final int size() { return elements.size(); }

	@NotNull
	public Iterator<ConfigValue> iterator() { return elements.iterator(); }
	@Override
	public void forEach(Consumer<? super ConfigValue> action) { elements.forEach(action); }
	@Override
	public Spliterator<ConfigValue> spliterator() { return elements.spliterator(); }

	public final ListValue add(ConfigValue entry) { elements.add(entry == null ? NullValue.NULL : entry); return this; }
	public final void add(String s) { elements.add(ConfigValue.valueOf(s)); }
	public final void add(boolean b) { elements.add(ConfigValue.valueOf(b)); }
	public final void add(int s) { elements.add(ConfigValue.valueOf(s)); }
	public final void add(long s) { elements.add(ConfigValue.valueOf(s)); }
	public final void add(double s) { elements.add(ConfigValue.valueOf(s)); }

	public void set(int i, ConfigValue val) { elements.set(i, val);}
	@NotNull
	public ConfigValue get(int i) { return elements.get(i); }
	public final boolean getBool(int i) {
		ConfigValue entry = elements.get(i);
		return entry.mayCastTo(Type.BOOL) && entry.asBool();
	}
	public final String getString(int i) {
		ConfigValue entry = elements.get(i);
		return entry.mayCastTo(Type.STRING) ? entry.asString() : "";
	}
	public final int getInteger(int i) {
		ConfigValue entry = elements.get(i);
		return entry.mayCastTo(Type.INTEGER) ? entry.asInt() : 0;
	}
	public final long getLong(int i) {
		ConfigValue entry = elements.get(i);
		return entry.mayCastTo(Type.LONG) ? entry.asLong() : 0;
	}
	public final double getFloat(int i) {
		ConfigValue entry = elements.get(i);
		return entry.mayCastTo(Type.Float4) ? entry.asFloat() : 0;
	}
	public final double getDouble(int i) {
		ConfigValue entry = elements.get(i);
		return entry.mayCastTo(Type.DOUBLE) ? entry.asDouble() : 0;
	}
	public ListValue getList(int i) { return elements.get(i).asList(); }
	public MapValue getMap(int i) { return elements.get(i).asMap(); }

	public final HashSet<String> toStringSet() {
		HashSet<String> stringSet = new HashSet<>(elements.size());
		for (int i = 0; i < elements.size(); i++) {
			stringSet.add(elements.get(i).asString());
		}
		return stringSet;
	}
	public final ArrayList<String> toStringList() {
		ArrayList<String> stringList = new ArrayList<>(elements.size());
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
			ConfigValue entry = elements.get(i);
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
	public ListValue toCommentable() {return new Commentable(elements);}
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
				if (!TomlParser.literalSafe(chain)) {
					Tokenizer.escape(sb, chain);
				} else {
					sb.append(chain);
				}
				elements.get(i).toTOML(sb.append("]]\n"), 2, chain).append("\n");
			}
			sb.setLength(sb.length()-1);
			return sb;
		} else {
			if (!TomlParser.literalSafe(chain)) {
				Tokenizer.escape(sb, chain);
			} else {
				sb.append(chain);
			}
			sb.append(" = [");
			for (int j = 0; j < elements.size(); j++) {
				ConfigValue entry = elements.get(j);
				entry.toTOML(sb, 3, chain).append(", ");
			}
			sb.delete(sb.length() - 2, sb.length());
			return sb.append(']');
		}
	}

	public int hashCode() { return elements.hashCode(); }
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ListValue that)) return false;
		return elements.equals(that.elements);
	}

	public static class Commentable extends ListValue {
		public IntMap<String> comments = new IntMap<>();

		public Commentable() {}
		public Commentable(List<ConfigValue> elements) {super(elements);}

		@Override
		public void accept(ValueEmitter visitor) {
			var entries = elements;
			visitor.emitList(entries.size());
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
		public final ListValue toCommentable() {return this;}
		public void clearComments() {comments.clear();}
	}
}