package roj.asm.tree.insn;

import roj.asm.tree.attr.AttrCode;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;

import java.util.Map;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2021/5/24 23:21
 */
public class JumpInsnNode extends InsnNode {
	public JumpInsnNode(InsnNode target) {
		super(GOTO);
		this.target = target;
	}

	public JumpInsnNode(byte code, InsnNode target) {
		super(code);
		this.target = target;
	}

	@Override
	protected boolean validate() {
		switch (code) {
			case IFEQ: case IFNE: case IFLT:
			case IFGE: case IFGT: case IFLE:
			case IF_icmpeq: case IF_icmpne:
			case IF_icmplt: case IF_icmpge: case IF_icmpgt: case IF_icmple:
			case IF_acmpeq: case IF_acmpne:
			case IFNULL: case IFNONNULL:
			case GOTO:
			case GOTO_W: return true;
		}
		return false;
	}

	@Override
	public final int nodeType() {
		return T_GOTO_IF;
	}

	public InsnNode target;
	Label label;

	@Override
	public void preSerialize(Map<InsnNode, Label> labels) {
		label = AttrCode.monitorNode(labels, target = validate(target));
	}

	public void serialize(CodeWriter cw) {
		cw.jump(code, label);
	}

	@Override
	public int nodeSize(int prevBci) {
		return code == GOTO_W ? 5 : 3;
	}

	public final String toString() {
		return super.toString() + " => #" + (int)validate(target).bci;
	}
}