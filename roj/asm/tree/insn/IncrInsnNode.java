package roj.asm.tree.insn;

import roj.asm.OpcodeUtil;
import roj.asm.Opcodes;
import roj.asm.visitor.CodeWriter;

/**
 * @author Roj234
 * @since 2021/5/24 23:21
 */
public final class IncrInsnNode extends InsnNode implements IIndexInsnNode {
	public IncrInsnNode(int variableId, int amount) {
		super(Opcodes.IINC);
		this.variableId = (char) variableId;
		this.amount = (short) amount;
	}

	@Override
	protected boolean validate() {
		return code == Opcodes.IINC;
	}

	public char variableId;
	public short amount;

	@Override
	public int nodeType() {
		return T_IINC;
	}

	@Override
	public int getIndex() {
		return variableId;
	}

	@Override
	public void setIndex(int index) {
		if (index < 0 || index > 65535) throw new IllegalArgumentException("IncrInsnNode supports [0,65535]");

		if (variableId > 255) {
			if (index < 255) throw new IllegalArgumentException("Check [wide] node and manually set index");
		} else if (index > 255) throw new IllegalArgumentException("Check [wide] node and manually set index");
		this.variableId = (char) index;
	}

	@Override
	public void serialize(CodeWriter cw) {
		cw.increase(variableId, amount);
	}

	@Override
	public int nodeSize(int prevBci) {
		return variableId > 255 || amount != (byte) amount ? 6 : 3;
	}

	public String toString() {
		return OpcodeUtil.toString0(code) + " #" + (int) variableId + " += " + amount;
	}
}