package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.asm.visitor.XInsnList;
import roj.collect.IntMap;
import roj.collect.SimpleList;
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

	public LocalVariableTable(String name, XInsnList owner, ConstantPool cp, DynByteBuf r, int maxIdx) {
		generic = name.equals("LocalVariableTypeTable");
		int len = r.readUnsignedShort();
		List<Item> list = new SimpleList<>(len);

		while (len-- > 0) {
			int start = r.readUnsignedShort();
			int end = start + r.readUnsignedShort();

			name = ((CstUTF) cp.get(r)).str();
			String desc = ((CstUTF) cp.get(r)).str();

			Item item = new Item(name, generic ? Signature.parseGeneric(desc) : TypeHelper.parseField(desc));
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
			 .putShort(c.cpw.getUtfId(TypeHelper.getField(v.type)))
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

	public String toString() { return toString(null); }
	public String toString(LocalVariableTable table) {
		List<Object> a = SimpleList.asModifiableList("名称","类型","槽","从","至",IntMap.UNDEFINED);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			Item v = list.get(i);
			v = table != null ? table.getSimilar(v) : v;
			a.add(v.name);
			a.add(v.type);
			a.add(v.slot);
			a.add(v.start.getValue());
			a.add(v.end==null?"<函数结束>":v.end.getValue());
			a.add(IntMap.UNDEFINED);
		}
		return TextUtil.prettyTable(sb, "    ", a.toArray(), "  ", "  ").toString();
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