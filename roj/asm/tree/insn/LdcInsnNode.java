package roj.asm.tree.insn;

import roj.asm.Opcodes;
import roj.asm.cst.Constant;
import roj.asm.util.InsnList;
import roj.asm.visitor.CodeWriter;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class LdcInsnNode extends InsnNode {
	public LdcInsnNode(byte code, Constant c) {
		super(code);
		this.c = c;
	}

	public LdcInsnNode(Constant c) {
		super(Opcodes.LDC);
		if (c.type() == Constant.DOUBLE || c.type() == Constant.LONG) code = Opcodes.LDC2_W;
		this.c = c;
	}

	@Override
	public int nodeType() {
		return T_LDC;
	}

	@Override
	public void verify(InsnList list, int index, int mainVer) throws IllegalArgumentException {
		/**
		 * The constant pool entry referenced by that index must be of type:
		 *
		 * Int, Float, or String if main < 49
		 *
		 * Int, Float, String, or Class if main in 49, 50
		 *
		 * Int, Float, String, Class, MethodType, or MethodHandle if main >= 51
		 */
		switch (c.type()) {
			case Constant.INT:
			case Constant.FLOAT:
			case Constant.STRING:
			case Constant.LONG:
			case Constant.DOUBLE:
				break;
			case Constant.CLASS:
				if (mainVer < 49) throw new IllegalArgumentException("Constant " + c + " is not loadable at version " + mainVer);
				break;
			case Constant.METHOD_TYPE:
			case Constant.METHOD_HANDLE:
				if (mainVer < 51) throw new IllegalArgumentException("Constant " + c + " is not loadable at version " + mainVer);
				break;
			case Constant.DYNAMIC:
				if (mainVer < 55) throw new IllegalArgumentException("Constant " + c + " is not loadable at version " + mainVer);
				break;
		}
	}

	@Override
	protected boolean validate() {
		switch (code) {
			case Opcodes.LDC:
			case Opcodes.LDC_W:
			case Opcodes.LDC2_W: return true;
			default: return false;
		}
	}

	public Constant c;

	@Override
	public void serialize(CodeWriter cw) {
		cw.ldc(code, c);
	}

	@Override
	public int nodeSize(int prevBci) {
		return code == Opcodes.LDC ? 2 : 3;
	}

	public String toString() {
		return super.toString() + ' ' + c;
	}
}