package roj.asm.frame.node;

import roj.asm.Opcodes;
import roj.asm.frame.MethodPoet;
import roj.asm.tree.insn.IIndexInsnNode;
import roj.asm.tree.insn.InsnNode;
import roj.asm.visitor.CodeWriter;

/**
 * @author Roj234
 * @since 2022/2/5 12:22
 */
public class VarIncrNode extends InsnNode implements IIndexInsnNode {
	public final MethodPoet.Variable v;
	final short amount;

	public VarIncrNode(MethodPoet.Variable v, int amount) {
		super(Opcodes.IINC);
		this.v = v;
		this.amount = (short) amount;
	}

	@Override
	public int nodeType() {
		return T_IINC;
	}

	@Override
	public int getIndex() {
		return v.slot;
	}

	@Override
	public void setIndex(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void serialize(CodeWriter cw) {
		cw.increase(v.slot, amount);
	}

	@Override
	public int nodeSize(int i) {
		return v.slot > 255 || amount != (byte) amount ? 6 : 3;
	}
}
