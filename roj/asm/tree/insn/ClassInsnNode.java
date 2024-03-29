package roj.asm.tree.insn;

import roj.asm.OpcodeUtil;
import roj.asm.Opcodes;
import roj.asm.cst.CstClass;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.CodeWriter;

/**
 * new / checkcast / instanceof / anewarray
 *
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class ClassInsnNode extends InsnNode {
	public ClassInsnNode(byte code, String owner) {
		super(code);
		this.owner = owner;
	}

	public ClassInsnNode(byte code, CstClass clazz) {
		super(code);
		this.owner = clazz.name().str();
	}

	protected boolean validate() { return OpcodeUtil.category(code) == OpcodeUtil.CATE_CLASS; }
	public int nodeType() { return T_CLASS; }

	public String owner;

	@Override
	public void serialize(CodeWriter cw) {
		if (code == Opcodes.NEW && owner.startsWith("[")) {
			throw new IllegalArgumentException("NEW cannot be used to create an array.");
		}
		cw.clazz(code, owner);
	}

	@Override
	public int nodeSize(int prevBci) { return 3; }

	public String toString() {
		StringBuilder sb = new StringBuilder().append(OpcodeUtil.toString0(code)).append(" ").append(owner.endsWith(";") ? TypeHelper.parseField(owner) : owner);
		if (code == Opcodes.ANEWARRAY) sb.append("[]");
		return sb.toString();
	}
}