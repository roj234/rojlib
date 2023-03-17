package roj.asm.tree.insn;

import roj.asm.visitor.CodeWriter;

/**
 * @author solo6975
 * @since 2021/6/27 13:36
 */
public final class LabelInsnNode extends InsnNode {
	public LabelInsnNode() {
		super((byte) 0);
	}

	@Override
	public int nodeType() {
		return T_LABEL;
	}

	@Override
	public void serialize(CodeWriter cw) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int nodeSize(int prevBci) {
		return -1;
	}
}
