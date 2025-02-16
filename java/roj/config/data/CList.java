package roj.config.data;

import org.jetbrains.annotations.NotNull;
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
	protected List<CEntry> list;

	public CList() { this(SimpleList.hugeCapacity(0)); }
	public CList(int size) { list = new SimpleList<>(size); }
	@SuppressWarnings("unchecked")
	public CList(List<? extends CEntry> list) { this.list = (List<CEntry>) list; }

	public Type getType() { return Type.LIST; }

	public final CList asList() { return this; }

	public void accept(CVisitor ser) {
		List<CEntry> l = list;
		ser.valueList(l.size());
		for (int i = 0; i < l.size(); i++) l.get(i).accept(ser);
		ser.pop();
	}

	public List<CEntry> raw() { return list; }
	public Object unwrap() {
		List<Object> caster = Arrays.asList(new Object[list.size()]);
		for (int i = 0; i < list.size(); i++) caster.set(i, list.get(i).unwrap());
		return caster;
	}

	public final boolean isEmpty() { return list.isEmpty(); }
	public final int size() { return list.size(); }

	@NotNull
	public Iterator<CEntry> iterator() { return list.iterator(); }
	@Override
	public void forEach(Consumer<? super CEntry> action) { list.forEach(action); }
	@Override
	public Spliterator<CEntry> spliterator() { return list.spliterator(); }

	public final CList add(CEntry entry) {
		list.add(entry == null ? CNull.NULL : entry);
		return this;
	}
	public final void add(String s) { list.add(CEntry.valueOf(s)); }
	public final void add(boolean b) { list.add(CEntry.valueOf(b)); }
	public final void add(int s) { list.add(CEntry.valueOf(s)); }
	public final void add(long s) { list.add(CEntry.valueOf(s)); }
	public final void add(double s) { list.add(CEntry.valueOf(s)); }

	public void set(int i, CEntry val) {list.set(i, val);}
	@NotNull
	public CEntry get(int i) { return list.get(i); }
	public final boolean getBool(int i) {
		CEntry entry = list.get(i);
		return entry.mayCastTo(Type.BOOL) && entry.asBool();
	}
	public final String getString(int i) {
		CEntry entry = list.get(i);
		return entry.mayCastTo(Type.STRING) ? entry.asString() : "";
	}
	public final int getInteger(int i) {
		CEntry entry = list.get(i);
		return entry.mayCastTo(Type.INTEGER) ? entry.asInt() : 0;
	}
	public final long getLong(int i) {
		CEntry entry = list.get(i);
		return entry.mayCastTo(Type.LONG) ? entry.asLong() : 0;
	}
	public final double getFloat(int i) {
		CEntry entry = list.get(i);
		return entry.mayCastTo(Type.Float4) ? entry.asFloat() : 0;
	}
	public final double getDouble(int i) {
		CEntry entry = list.get(i);
		return entry.mayCastTo(Type.DOUBLE) ? entry.asDouble() : 0;
	}
	public CList getList(int i) { return list.get(i).asList(); }
	public CMap getMap(int i) { return list.get(i).asMap(); }

	public final MyHashSet<String> toStringSet() {
		MyHashSet<String> stringSet = new MyHashSet<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			stringSet.add(list.get(i).asString());
		}
		return stringSet;
	}
	public final SimpleList<String> toStringList() {
		SimpleList<String> stringList = new SimpleList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			stringList.add(list.get(i).asString());
		}
		return stringList;
	}
	public final String[] toStringArray() {
		String[] array = new String[list.size()];
		for (int i = 0; i < list.size(); i++) {
			array[i] = list.get(i).asString();
		}
		return array;
	}
	public byte[] toByteArray() {
		byte[] array = new byte[list.size()];
		for (int i = 0; i < list.size(); i++) {
			CEntry entry = list.get(i);
			array[i] = (byte) (entry.mayCastTo(Type.Int1) ? entry.asInt() : 0);
		}
		return array;
	}
	public int[] toIntArray() {
		int[] array = new int[list.size()];
		for (int i = 0; i < list.size(); i++) {
			array[i] = list.get(i).asInt();
		}
		return array;
	}
	public long[] toLongArray() {
		long[] array = new long[list.size()];
		for (int i = 0; i < list.size(); i++) {
			array[i] = list.get(i).asLong();
		}
		return array;
	}

	public boolean isCommentSupported() {return false;}
	public String getComment(int key) {return null;}
	public void putComment(int key, String val) {throw new UnsupportedOperationException();}
	public CList withComments() { return new CCommList(list); }
	public void clearComments() {}

	@Override
	public CharList toTOML(CharList sb, int depth, CharSequence chain) {
		if (list.isEmpty()) {
			return sb.append("[]");
		} else if (depth != 3) {
			for (int i = 0; i < list.size(); i++) {
				sb.append("[[");
				if (!TOMLParser.literalSafe(chain)) {
					Tokenizer.addSlashes(sb, chain);
				} else {
					sb.append(chain);
				}
				list.get(i).toTOML(sb.append("]]\n"), 2, chain).append("\n");
			}
			sb.setLength(sb.length()-1);
			return sb;
		} else {
			if (!TOMLParser.literalSafe(chain)) {
				Tokenizer.addSlashes(sb, chain);
			} else {
				sb.append(chain);
			}
			sb.append(" = [");
			for (int j = 0; j < list.size(); j++) {
				CEntry entry = list.get(j);
				entry.toTOML(sb, 3, chain).append(", ");
			}
			sb.delete(sb.length() - 2, sb.length());
			return sb.append(']');
		}
	}

	public int hashCode() { return list.hashCode(); }
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof CList that)) return false;
		return list.equals(that.list);
	}
}