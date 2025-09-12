package roj.asm.attr;

import roj.asm.insn.CodeWriter;
import roj.asm.insn.InsnList;
import roj.asm.insn.Label;
import roj.collect.ArrayList;
import roj.collect.IntMap;
import roj.text.TextUtil;
import roj.util.ArrayUtil;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/30 19:27
 */
public final class LineNumberTable extends Attribute implements CodeAttribute {
	public ArrayList<Item> list;

	public LineNumberTable() { list = new ArrayList<>(); }

	public LineNumberTable(InsnList owner, DynByteBuf r) {
		int len = r.readUnsignedShort();
		list = new ArrayList<>(len);
		while (len-- > 0) {
			list.add(new Item(owner._monitor(r.readUnsignedShort()), r.readUnsignedShort()));
		}
	}

	@Override
	public boolean writeIgnore() { return list.isEmpty(); }
	@Override
	public String name() { return "LineNumberTable"; }

	public int searchLine(int bci) {
		int i = ArrayUtil.binarySearchEx(list, value -> Integer.compare(value.pos.getValue(), bci));
		return i < 0 ? -1 : list.get(i).line;
	}

	public void add(Label pos, int i) {list.add(new Item(pos, i));}
	public int lastBci() { return list.isEmpty() ? -1 : list.getLast().pos.getValue(); }

	public String toString() {
		List<Object> a = ArrayList.asModifiableList("BCI","行号",IntMap.UNDEFINED);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			Item item = list.get(i);
			a.add(item.pos);
			a.add((int)item.line);
			a.add(IntMap.UNDEFINED);
		}
		return TextUtil.prettyTable(sb, "    ", a.toArray(), "  ", "  ").toString();
	}

	@Override
	public void toByteArray(CodeWriter c) {
		DynByteBuf w = c.bw.putShort(list.size());
		if (list.isEmpty()) return;

		sort();
		for (int i = 0; i < list.size(); i++) {
			Item item = list.get(i);
			w.putShort(item.pos.getValue()).putShort(item.line);
		}
	}

	public void sort() { list.sort((o1, o2) -> Integer.compare(o1.pos.getValue(), o2.pos.getValue())); }

	public static final class Item {
		public Label pos;
		char line;

		public Item(Label pos, int i) {
			this.pos = pos;
			this.line = (char) i;
		}

		public int getLine() { return line; }
		public void setLine(int line) { this.line = (char) line; }
	}
}