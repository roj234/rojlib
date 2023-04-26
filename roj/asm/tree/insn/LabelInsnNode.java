package roj.asm.tree.insn;

import roj.asm.visitor.CodeWriter;

/**
 * @author solo6975
 * @since 2021/6/27 13:36
 */
public final class LabelInsnNode extends InsnNode {
	public LabelInsnNode() { super((byte) -1); }
	public void serialize(CodeWriter cw) {}
}
