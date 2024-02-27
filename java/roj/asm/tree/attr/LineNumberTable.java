package roj.asm.tree.attr;

import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.asm.visitor.ReadonlyLabel;
import roj.asm.visitor.XInsnList;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.text.TextUtil;
import roj.util.ArrayUtil;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/30 19:27
 */
public final class LineNumberTable extends Attribute implements CodeAttribute {
	public SimpleList<Item> list;

	public LineNumberTable() { list = new SimpleList<>(); }

	public LineNumberTable(XInsnList owner, DynByteBuf r) {
		int len = r.readUnsignedShort();
		list = new SimpleList<>(len);
		while (len-- > 0) {
			list.add(new Item(owner._monitor(r.readUnsignedShort()), r.readUnsignedShort()));
		}
	}

	@Override
	public boolean isEmpty() { return list.isEmpty(); }
	@Override
	public String name() { return "LineNumberTable"; }

	public int searchLine(int bci) {
		int i = ArrayUtil.binarySearchEx(list, value -> Integer.compare(value.pos.getValue(), bci));
		return i < 0 ? -1 : list.get(i).line;
	}

	public void add(Label pos, int i) {list.add(new Item(pos, i));}
	public int lastBci() { return list.isEmpty() ? -1 : list.getLast().pos.getValue(); }

	public String toString() {
		List<Object> a = SimpleList.asModifiableList("BCI","行号",IntMap.UNDEFINED);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			Item item = list.get(i);
			a.add(item.pos.getValue());
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
		public ReadonlyLabel pos;
		char line;

		public Item(Label pos, int i) {
			this.pos = pos;
			this.line = (char) i;
		}

		public int getLine() { return line; }
		public void setLine(int line) { this.line = (char) line; }
	}
}