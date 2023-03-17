package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.tree.insn.InsnNode;
import roj.asm.type.LocalVariable;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.text.TextUtil;
import roj.util.DynByteBuf;

import java.util.List;
import java.util.Map;

import static roj.asm.tree.attr.AttrCode.monitorNode;
import static roj.asm.tree.insn.InsnNode.validate;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class AttrLocalVars extends Attribute implements CodeAttributeSpec {
	public List<LocalVariable> list;

	public AttrLocalVars(String name) {
		super(name);
		this.list = new SimpleList<>();
	}

	public AttrLocalVars(String name, ConstantPool pool, DynByteBuf r, IntMap<InsnNode> pc, int maxIdx) {
		super(name);

		boolean generic = name.equals("LocalVariableTypeTable");
		int len = r.readUnsignedShort();
		List<LocalVariable> list = new SimpleList<>(len);

		while (len-- > 0) {
			int start = r.readUnsignedShort();
			int end = start + r.readUnsignedShort();

			InsnNode startNode = pc.get(start);
			if (startNode == null) {
				throw new NullPointerException("No code at " + start + ", len=" + maxIdx + ", pc="+pc);
			}

			InsnNode endNode;
			if (end >= maxIdx) endNode = null;
			else {
				endNode = pc.get(end);
				if (endNode == null) {
					throw new NullPointerException("No code at " + end + ", len=" + maxIdx + ", pc="+pc);
				}
			}

			//The given local variable must have a value at indexes into the code array in the interval [start_pc, start_pc + length)

			name = ((CstUTF) pool.get(r)).str();
			String desc = ((CstUTF) pool.get(r)).str();
			list.add(new LocalVariable(r.readUnsignedShort(), name, generic ? Signature.parseGeneric(desc) : TypeHelper.parseField(desc), startNode, endNode));
		}
		this.list = list;
	}

	@Override
	public boolean isEmpty() {
		return list.isEmpty();
	}

	@Override
	public void toByteArray(CodeWriter c) {
		DynByteBuf w = c.bw.putShort(list.size());
		for (int i = 0; i < list.size(); i++) {
			LocalVariable v = list.get(i);
			int s = v.start.bci;
			int e = (v.end == null ? c.getBci() : v.end.bci)-s;
			w.putShort(s).putShort(e)
			 .putShort(c.cpw.getUtfId(v.name))
			 .putShort(c.cpw.getUtfId(v.type.genericType() != 0 ? v.type.toDesc() : TypeHelper.getField((Type) v.type)))
			 .putShort(v.slot);
		}
	}

	@Override
	public void preToByteArray(Map<InsnNode, Label> concerned) {
		for (int i = 0; i < list.size(); i++) {
			LocalVariable item = list.get(i);
			monitorNode(concerned, item.start = validate(item.start));
			if (item.end != null) monitorNode(concerned, item.end = validate(item.end));
		}
	}

	public String toString() {
		return toString(null);
	}
	public String toString(AttrLocalVars table) {
		List<Object> a = SimpleList.asModifiableList("Slot","Type","Name","Start","End",IntMap.UNDEFINED);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			LocalVariable v = list.get(i);
			v = table != null ? table.getSimilar(v) : v;
			a.add(v.slot);
			a.add(v.type);
			a.add(v.name);
			a.add((int)v.start.bci);
			a.add(v.end==null?"-1":(int)v.end.bci);
			a.add(IntMap.UNDEFINED);
		}
		return TextUtil.prettyTable(sb, " ", " ", "    ", a.toArray()).toString();
	}
	private LocalVariable getSimilar(LocalVariable lv) {
		for (int i = 0; i < list.size(); i++) {
			LocalVariable v = list.get(i);
			if (v.equals(lv)) return v;
		}
		return lv;
	}
}