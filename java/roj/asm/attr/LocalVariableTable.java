package roj.asm.attr;

import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.asm.insn.CodeWriter;
import roj.asm.insn.InsnList;
import roj.asm.insn.Label;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class LocalVariableTable extends Attribute implements CodeAttribute {
	private final boolean generic;
	public List<Item> list;

	public LocalVariableTable(String name) {
		generic = name.equals("LocalVariableTypeTable");
		list = new SimpleList<>();
	}

	public LocalVariableTable(String name, InsnList owner, ConstantPool cp, DynByteBuf r, int maxIdx) {
		generic = name.equals("LocalVariableTypeTable");
		int len = r.readUnsignedShort();
		List<Item> list = new SimpleList<>(len);

		while (len-- > 0) {
			int start = r.readUnsignedShort();
			int end = start + r.readUnsignedShort();

			name = ((CstUTF) cp.get(r)).str();
			String desc = ((CstUTF) cp.get(r)).str();

			Item item = new Item(name, generic ? Signature.parseGeneric(desc) : Type.fieldDesc(desc));
			list.add(item);

			item.slot = r.readUnsignedShort();
			item.start = owner._monitor(start);
			item.end = end >= maxIdx ? null : owner._monitor(end);
		}
		this.list = list;
	}

	@Override
	public boolean isEmpty() { return list.isEmpty(); }

	@Override
	public void toByteArray(CodeWriter c) {
		DynByteBuf w = c.bw.putShort(list.size());
		for (int i = 0; i < list.size(); i++) {
			Item v = list.get(i);
			int s = v.start.getValue();
			int e = (v.end == null ? c.bci() : v.end.getValue())-s;
			w.putShort(s).putShort(e)
			 .putShort(c.cpw.getUtfId(v.name))
			 .putShort(c.cpw.getUtfId((generic ? v.type : v.type.rawType()).toDesc()))
			 .putShort(v.slot);
		}
	}

	@Override
	public String name() { return generic?"LocalVariableTypeTable":"LocalVariableTable"; }

	public Item getItem(Label pos, int slot) {
		for (int i = 0; i < list.size(); i++) {
			Item item = list.get(i);
			if (item.slot == slot && pos.compareTo(item.start) >= 0 && (item.end == null || pos.compareTo(item.end) < 0)) return item;
		}
		return null;
	}

	public String toString() { return toString(IOUtil.getSharedCharBuf(), null, 0).toString(); }
	public CharList toString(CharList sb, LocalVariableTable table, int prefix) {
		CharList tmp = new CharList();
		List<Object> a = SimpleList.asModifiableList("名称","类型","槽","从","至",IntMap.UNDEFINED);
		for (int i = 0; i < list.size(); i++) {
			Item v = list.get(i);
			v = table != null ? table.getSimilar(v) : v;
			a.add(v.name);
			v.type.toString(tmp);
			a.add(tmp.toString());
			tmp.clear();
			a.add(v.slot);
			a.add(v.start.getValue());
			a.add(v.end==null?"<函数结束>":v.end.getValue());
			a.add(IntMap.UNDEFINED);
		}
		return TextUtil.prettyTable(sb, tmp.padEnd(' ', prefix).toStringAndFree(), a.toArray(), "  ", "  ");
	}
	private Item getSimilar(Item lv) {
		for (int i = 0; i < list.size(); i++) {
			Item v = list.get(i);
			if (v.equals(lv)) return v;
		}
		return lv;
	}

	public static class Item {
		public Item(String name, IType type) {
			this.name = name;
			this.type = type;
		}

		public String name;
		public IType type;
		public Label start, end;
		public int slot;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Item item = (Item) o;

			if (slot != item.slot) return false;
			if (!start.equals(item.start)) return false;
			return end != null ? end.equals(item.end) : item.end == null;
		}

		@Override
		public int hashCode() {
			int result = start.hashCode();
			result = 31 * result + (end != null ? end.hashCode() : 0);
			result = 31 * result + slot;
			return result;
		}

		@Override
		public String toString() {
			return "Item{" + "name='" + name + '\'' + ", type=" + type + ", start=" + start + ", end=" + end + ", slot=" + slot + '}';
		}
	}

}