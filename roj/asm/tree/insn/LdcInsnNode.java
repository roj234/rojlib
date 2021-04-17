package roj.asm.tree.insn;

import roj.asm.Opcodes;
import roj.asm.cst.Constant;
import roj.asm.visitor.CodeWriter;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class LdcInsnNode extends InsnNode {
	public LdcInsnNode(Constant c) {
		super(Opcodes.LDC);
		if (c.type() == Constant.DOUBLE || c.type() == Constant.LONG) code = Opcodes.LDC2_W;
		this.c = c;
	}

	public Constant c;

	@Override
	public int nodeType() { return T_LDC; }

	@Override
	public void serialize(CodeWriter cw) { cw.ldc(c); }

	@Override
	public int nodeSize(int prevBci) { return code == Opcodes.LDC ? 2 : 3; }

	public String toString() { return super.toString() + ' ' + c; }
}