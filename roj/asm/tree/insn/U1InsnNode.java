package roj.asm.tree.insn;

import roj.asm.OpcodeUtil;
import roj.asm.visitor.CodeWriter;

import static roj.asm.Opcodes.BIPUSH;
import static roj.asm.Opcodes.NEWARRAY;

/**
 * bipush / newarray
 *
 * @author Roj234
 * @since 2021/5/24 23:21
 */
public final class U1InsnNode extends InsnNode implements IIndexInsnNode {
	public U1InsnNode(byte code, int index) {
		super(code);
		this.index = (byte) index;
	}

	/*@Override
	protected boolean validate() {
		return code==Opcodes.BIPUSH||code==Opcodes.NEWARRAY;
	}*/

	/**
	 * for new array
	 * T_BOOLEAN 4
	 * T_CHAR	 5
	 * T_FLOAT   6
	 * T_DOUBLE	 7
	 * T_BYTE	 8
	 * T_SHORT	 9
	 * T_INT	 10
	 * T_LONG	 11
	 */
	public byte index;

	@Override
	public int nodeType() {
		int c = code & 0xFF;
		return (c >= 0x15 && c <= 0x19) || (c >= 0x36 && c <= 0x3a) ? T_LOAD_STORE : T_OTHER;
	}

	public int getIndex() {
		return index;
	}

	@Override
	public void setIndex(int index) {
		if (index < -128 || index > 255) throw new IndexOutOfBoundsException("U1InsnNode supports [-128,255]");
		this.index = (byte) index;
	}

	public void serialize(CodeWriter cw) {
		if (code == BIPUSH) {
			cw.smallNum(BIPUSH, index);
		} else if (code == NEWARRAY) {
			cw.newArray(index);
		} else {
			cw.var(code, index);
		}
	}

	@Override
	public int nodeSize(int prevBci) {
		return 2;
	}

	public String toString() {
		return OpcodeUtil.toString0(code) + " " + index;
	}
}