package roj.config.data;

import roj.collect.IntList;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.config.NBTParser;
import roj.config.TOMLParser;
import roj.config.Wrapping;
import roj.config.serial.CConsumer;
import roj.config.serial.Structs;
import roj.config.word.ITokenizer;
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

	public static CList of(Object... arr) {
		return Wrapping.wrap(arr).asList();
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
	public CEntry get(int index) {
		return list.get(index);
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
	public final StringBuilder toJSON(StringBuilder sb, int depth) {
		sb.append('[');
		if (!list.isEmpty()) {
			if (depth < 0) {
				for (int j = 0; j < list.size(); j++) {
					list.get(j).toJSON(sb, -1).append(',');
				}
				sb.delete(sb.length() - 1, sb.length());
			} else {
				sb.append('\n');
				for (int j = 0; j < list.size(); j++) {
					CEntry entry = list.get(j);
					for (int i = 0; i < depth + 4; i++) {
						sb.append(' ');
					}
					entry.toJSON(sb, depth + 4).append(",\n");
				}
				sb.delete(sb.length() - 2, sb.length() - 1);
				for (int i = 0; i < depth; i++) {
					sb.append(' ');
				}
			}
		}
		return sb.append(']');
	}

	@Override
	public byte getNBTType() {
		return NBTParser.LIST;
	}

	@Override
	public void toNBT(DynByteBuf w) {
		List<CEntry> list = this.list;
		if (list.isEmpty()) {
			w.put(NBTParser.END).writeInt(0);
			return;
		}

		byte prevType = list.get(0).getNBTType();
		w.put(prevType).writeInt(list.size());
		for (int i = 0; i < list.size(); i++) {
			CEntry entry = list.get(i);
			if (entry.getNBTType() != prevType) throw new IllegalStateException("Illegal NBTList: types are not same: " + list);
			entry.toNBT(w);
		}
	}

	@Override
	public final StringBuilder toINI(StringBuilder sb, int depth) {
		if (depth != 1) {
			throw new IllegalArgumentException("Can not serialize LIST to INI at depth " + depth);
		}
		for (int i = 0; i < list.size(); i++) {
			list.get(i).toINI(sb, 1).append('\n');
		}
		return sb;
	}

	@Override
	public StringBuilder toTOML(StringBuilder sb, int depth, CharSequence chain) {
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
			return sb.delete(sb.length() - 1, sb.length());
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
	public void toBinary(DynByteBuf w, Structs struct) {
		int iType = -1;
		List<CEntry> list = this.list;
		for (int i = 0; i < list.size(); i++) {
			int type1 = list.get(i).getType().ordinal();
			if (iType == -1) {
				iType = type1;
			} else if (iType != type1 || type1 == /*Type.LIST.ordinal()*/0) {
				iType = -1;
				break;
			}
		}
		w.put((byte) (((1 + iType) << 4)/* | Type.LIST.ordinal()*/)).putVarInt(list.size(), false);
		Type type = iType >= 0 ? Type.VALUES[iType] : null;
		if (type == null) {
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
				case INTEGER: w.putInt(el.asInteger()); break;
				case MAP:
					if (struct == null || !struct.tryCompress(el.asMap(), w)) {
						if (struct != null) w.put((byte) el.getType().ordinal());

						Map<String, CEntry> map = el.asMap().raw();
						w.putVarInt(map.size(), false);
						if (!map.isEmpty()) {
							for (Map.Entry<String, CEntry> entry : map.entrySet()) {
								entry.getValue().toBinary(w.putVarIntVIC(entry.getKey()), struct);
							}
						}
					}
					break;
				case LONG:
					w.putLong(el.asLong());
					break;
				case DOUBLE:
					w.putDouble(el.asDouble());
					break;
				case STRING:
					w.putVarIntVIC(el.asString());
					break;
				case Int1:
					w.put((byte) el.asInteger());
					break;
				case Int2:
					w.putShort(el.asInteger());
					break;
				case Float4:
					w.putFloat((float) el.asDouble());
					break;
			}
		}
		if (bvi != 8) {
			w.put((byte) bv);
		}
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
	public final void forEachChild(CConsumer ser) {
		ser.valueList();
		List<CEntry> l = this.list;
		for (int i = 0; i < l.size(); i++) {
			l.get(i).forEachChild(ser);
		}
		ser.pop();
	}
}
