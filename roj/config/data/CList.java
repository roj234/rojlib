package roj.config.data;

import roj.collect.IntList;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.config.NBTParser;
import roj.config.TOMLParser;
import roj.config.VinaryParser;
import roj.config.Wrapping;
import roj.config.serial.CVisitor;
import roj.config.word.ITokenizer;
import roj.text.CharList;
import roj.util.DynByteBuf;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class CList extends CEntry implements Iterable<CEntry> {
	protected List<CEntry> list;

	public CList() {
		this(new SimpleList<>(0,2));
	}

	public CList(int size) {
		this.list = new SimpleList<>(size);
	}

	@SuppressWarnings("unchecked")
	public CList(List<? extends CEntry> list) {
		this.list = (List<CEntry>) list;
	}

	@Deprecated
	public static CList of(Object... arr) {
		CList list1 = new CList(arr.length);
		for (Object o : arr) list1.add(Wrapping.wrap(o));
		return list1;
	}

	public final boolean isEmpty() {
		return list.isEmpty();
	}

	public final int size() {
		return list.size();
	}

	@Nonnull
	public Iterator<CEntry> iterator() {
		return list.iterator();
	}

	@Override
	public void forEach(Consumer<? super CEntry> action) {
		list.forEach(action);
	}

	@Override
	public Spliterator<CEntry> spliterator() {
		return list.spliterator();
	}

	public final CList add(CEntry entry) {
		list.add(entry == null ? CNull.NULL : entry);
		return this;
	}

	public final void add(String s) {
		list.add(CString.valueOf(s));
	}
	public final void add(int s) {
		list.add(CInteger.valueOf(s));
	}
	public final void add(double s) {
		list.add(CDouble.valueOf(s));
	}
	public final void add(long s) {
		list.add(CLong.valueOf(s));
	}
	public final void add(boolean b) {
		list.add(CBoolean.valueOf(b));
	}

	public final void set(int index, CEntry entry) {
		list.set(index, entry == null ? CNull.NULL : entry);
	}

	@Nonnull
	public CEntry get(int i) {
		return list.get(i);
	}
	public final boolean getBool(int i) {
		CEntry entry = list.get(i);
		return Type.BOOL.isSimilar(entry.getType()) && entry.asBool();
	}
	public final String getString(int i) {
		CEntry entry = list.get(i);
		return Type.STRING.isSimilar(entry.getType()) ? entry.asString() : "";
	}
	public final int getInteger(int i) {
		CEntry entry = list.get(i);
		return entry.getType().isNumber() ? entry.asInteger() : 0;
	}
	public final long getLong(int i) {
		CEntry entry = list.get(i);
		return entry.getType().isNumber() ? entry.asLong() : 0;
	}
	public final double getDouble(int i) {
		CEntry entry = list.get(i);
		return entry.getType().isNumber() ? entry.asDouble() : 0;
	}

	public CEntry remove(int i) {
		return list.remove(i);
	}

	@Nonnull
	@Override
	public Type getType() {
		return Type.LIST;
	}

	public final MyHashSet<String> asStringSet() {
		MyHashSet<String> stringSet = new MyHashSet<>(list.size());
		for (CEntry entry : list) {
			try {
				String val = entry.asString();
				stringSet.add(val);
			} catch (ClassCastException ignored) {
			}
		}
		return stringSet;
	}

	public final SimpleList<String> asStringList() {
		SimpleList<String> stringList = new SimpleList<>(list.size());
		for (CEntry entry : list) {
			try {
				String val = entry.asString();
				stringList.add(val);
			} catch (ClassCastException ignored) {
			}
		}
		return stringList;
	}

	public final int[] asIntList() {
		IntList numberList = new IntList(list.size());
		for (CEntry entry : list) {
			try {
				int val = entry.asInteger();
				numberList.add(val);
			} catch (ClassCastException ignored) {
			}
		}
		return numberList.toArray();
	}

	public final void addAll(CList list) {
		this.list.addAll(list.list);
	}

	public final List<CEntry> raw() {
		return list;
	}

	public final void clear() {
		list.clear();
	}

	@Nonnull
	@Override
	public final CList asList() {
		return this;
	}

	@Override
	public final CharList toJSON(CharList sb, int depth) { throw new NoSuchMethodError(); }

	@Override
	public byte getNBTType() {
		return NBTParser.LIST;
	}

	@Override
	public final CharList toINI(CharList sb, int depth) {
		if (depth != 1) {
			throw new IllegalArgumentException("Can not serialize LIST to INI at depth " + depth);
		}
		for (int i = 0; i < list.size(); i++) {
			list.get(i).toINI(sb, 1).append('\n');
		}
		return sb;
	}

	@Override
	public CharList toTOML(CharList sb, int depth, CharSequence chain) {
		if (list.isEmpty()) {
			return sb.append("[]");
		} else if (depth != 3) {
			for (int i = 0; i < list.size(); i++) {
				sb.append("[[");
				if (!TOMLParser.literalSafe(chain)) {
					ITokenizer.addSlashes(chain, sb);
				} else {
					sb.append(chain);
				}
				list.get(i).toTOML(sb.append("]]\n"), 2, chain).append("\n");
			}
			sb.setLength(sb.length()-1);
			return sb;
		} else {
			if (!TOMLParser.literalSafe(chain)) {
				ITokenizer.addSlashes(chain, sb);
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

	@Override
	public Object unwrap() {
		List<Object> caster = Arrays.asList(new Object[list.size()]);
		for (int i = 0; i < list.size(); i++) {
			caster.set(i, list.get(i).unwrap());
		}
		return caster;
	}

	@Override
	@SuppressWarnings("fallthrough")
	protected void toBinary(DynByteBuf w, VinaryParser struct) {
		int iType = -1;

		List<CEntry> list = this.list;
		for (int i = 0; i < list.size(); i++) {
			int type = list.get(i).getType().ordinal();
			if (type == 0) {
				iType = -1;
				break;
			}

			if (iType == -1) {
				iType = type;
			} else if (iType != type) {
				iType = -1;
				break;
			}
		}

		if (iType == 1 && struct != null) iType = -1;

		w.put((byte) (((1 + iType) << 4))).putVUInt(list.size());

		Type type = iType >= 0 ? Type.VALUES[iType] : null;
		if (type == null) {
			// list, map, or mixed different types
			for (int i = 0; i < list.size(); i++) {
				list.get(i).toBinary(w, struct);
			}
			return;
		} else if (type == Type.NULL) {
			return;
		}

		int bv = 0, bvi = 8;
		for (int i = 0; i < list.size(); i++) {
			CEntry el = list.get(i);
			switch (type) {
				case BOOL:
					bv |= (el.asInteger() << --bvi);
					if (bvi == 0) {
						w.put((byte) bv);
						bv = 0;
						bvi = 8;
					}
					break;
				// noMap
				case INTEGER: w.putInt(el.asInteger()); break;
				case LONG: w.putLong(el.asLong()); break;
				case DOUBLE: w.putDouble(el.asDouble()); break;
				case STRING: w.putVStr(el.asString()); break;
				case Int1: w.put((byte) el.asInteger()); break;
				case Int2: w.putShort(el.asInteger()); break;
				case Float4: w.putFloat((float) el.asDouble()); break;
			}
		}

		if (bvi != 8) w.put((byte) bv);
	}

	@Override
	public void toB_encode(DynByteBuf w) {
		w.put((byte) 'l');
		for (int i = 0; i < list.size(); i++) {
			list.get(i).toB_encode(w);
		}
		w.put((byte) 'e');
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		CList that = (CList) o;

		return Objects.equals(list, that.list);
	}

	@Override
	public int hashCode() {
		return list != null ? list.hashCode() : 0;
	}

	@Override
	public void forEachChild(CVisitor ser) {
		List<CEntry> l = list;
		ser.valueList(l.size());
		for (int i = 0; i < l.size(); i++) l.get(i).forEachChild(ser);
		ser.pop();
	}
}
