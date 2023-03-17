package roj.asm.tree.attr;

import roj.asm.tree.insn.InsnNode;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

import java.util.List;
import java.util.Map;

import static roj.asm.tree.insn.InsnNode.validate;

/**
 * @author Roj234
 * @since 2021/4/30 19:27
 */
public final class AttrLineNumber extends Attribute implements CodeAttributeSpec {
	public List<LineNumber> list;

	public AttrLineNumber() {
		super("LineNumberTable");
		this.list = new SimpleList<>();
	}

	public AttrLineNumber(DynByteBuf r, IntMap<InsnNode> pc) {
		super("LineNumberTable");

		int count = r.readUnsignedShort();
		List<LineNumber> list = this.list = new SimpleList<>(count);
		while (count-- > 0) {
			int i = r.readUnsignedShort();
			InsnNode node = pc.get(i);
			if (node == null) {
				System.err.println("AttrLineNumber.java:36: No code at " + i);
				r.rIndex += 2;
			}
			else list.add(new LineNumber(node, r.readUnsignedShort()));
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("LineNumberTable:   Node <=======> Line\n");
		for (int i = 0; i < list.size(); i++) {
			LineNumber ln = list.get(i);
			sb.append("                  ").append(ln.node).append(" = ").append((int)ln.line).append('\n');
		}
		return sb.toString();
	}

	@Override
	public boolean isEmpty() {
		return list.isEmpty();
	}

	@Override
	public void toByteArray(CodeWriter c) {
		DynByteBuf w = c.bw;
		w.putShort(list.size());
		if (list.isEmpty()) return;

		list.sort((o1, o2) -> Integer.compare(o1.bci(), o2.bci()));
		for (int i = 0; i < list.size(); i++) {
			LineNumber ln = list.get(i);
			w.putShort(ln.bci()).putShort(ln.line);
		}
	}

	@Override
	public void preToByteArray(Map<InsnNode, Label> concerned) {
		for (int i = 0; i < list.size(); i++) {
			LineNumber ln = list.get(i);
			AttrCode.monitorNode(concerned, ln.node = validate(ln.node));
		}
	}

	public static final class LineNumber {
		public InsnNode node;
		public Label alternative;
		char line;

		public int bci() {
			return alternative == null ? node.bci : alternative.getValue();
		}

		public LineNumber(InsnNode node, int i) {
			this.node = node;
			this.line = (char) i;
		}

		public int getLine() {
			return line;
		}

		public void setLine(int line) {
			this.line = (char) line;
		}
	}
}