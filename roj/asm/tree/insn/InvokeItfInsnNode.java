package roj.asm.tree.insn;

import roj.asm.Opcodes;
import roj.asm.cst.CstRefItf;
import roj.asm.util.InsnList;
import roj.asm.visitor.CodeWriter;

/**
 * Invoke interface method
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class InvokeItfInsnNode extends InvokeInsnNode {
	public InvokeItfInsnNode() {
		super(Opcodes.INVOKEINTERFACE);
	}

	public InvokeItfInsnNode(String str) {
		super(Opcodes.INVOKEINTERFACE, str);
	}

	public InvokeItfInsnNode(String owner, String name, String types) {
		super(Opcodes.INVOKEINTERFACE, owner, name, types);
	}

	public InvokeItfInsnNode(CstRefItf ref, short flag) {
		super(Opcodes.INVOKEINTERFACE, ref);
	}

	@Override
	protected boolean validate() {
		return code == Opcodes.INVOKEINTERFACE;
	}

	@Override
	public void verify(InsnList list, int index, int mainVer) throws IllegalArgumentException {
		// Only class file version 52.0 or above, can supports (CstRefItf).
		if (mainVer < 52) throw new IllegalArgumentException("Interface supported since version 53");
		super.verify(list, index, mainVer);
	}

	public void serialize(CodeWriter cw) {
		cw.invoke_interface(owner, name, rawDesc());
	}
}