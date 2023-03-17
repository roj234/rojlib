package roj.asm.tree.insn;

import roj.asm.Opcodes;
import roj.asm.visitor.CodeWriter;

/**
 * @author Roj234
 * @since 2021/5/24 23:21
 */
public final class JsrInsnNode extends InsnNode implements IIndexInsnNode {
	public JsrInsnNode(byte code, int index) {
		super(code);
		this.index = index;
	}

	@Override
	protected boolean validate() {
		return code==Opcodes.JSR||code==Opcodes.JSR_W;
	}

	public int index;

	public int getIndex() {
		return index;
	}

	@Override
	public void setIndex(int index) {
		this.index = index;
	}

	public void serialize(CodeWriter cw) {
		cw.jsr(index);
	}

	@Override
	public int nodeSize(int prevBci) {
		return (short)index != index ? 5 : 3;
	}

	public String toString() {
		return "JSR => " + index;
	}
}