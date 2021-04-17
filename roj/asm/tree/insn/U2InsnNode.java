package roj.asm.tree.insn;

import roj.asm.OpcodeUtil;
import roj.asm.visitor.CodeWriter;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2021/5/24 23:21
 */
public final class U2InsnNode extends InsnNode implements IIndexInsnNode {
	public U2InsnNode(byte code, int index) {
		super(code);
		this.index = (char) index;
	}

	@Override
	protected boolean validate() {
		switch (code) {
			case ISTORE: case LSTORE: case FSTORE: case DSTORE: case ASTORE:
			case ILOAD: case LLOAD: case FLOAD: case DLOAD: case ALOAD:
			case RET: case SIPUSH:
				return true;
		}
		return false;
	}

	public char index;

	@Override
	public int nodeType() {
		int c = code & 0xFF;
		return (c >= 0x15 && c <= 0x19) || (c >= 0x36 && c <= 0x3a) ? T_LOAD_STORE : T_OTHER;
	}

	public int getIndex() {
		return code==RET||code==SIPUSH?(short)index:index;
	}

	@Override
	public void setIndex(int index) {
		if (index < -32768 || index > 65535) throw new IndexOutOfBoundsException("U2InsnNode supports [-32768,65535]");
		this.index = (char) index;
	}

	public void serialize(CodeWriter cw) {
		if (code == SIPUSH) {
			cw.smallNum(SIPUSH, index);
		} else {
			cw.var(code, index);
		}
	}

	@Override
	public int nodeSize(int prevBci) {
		return code == SIPUSH || (index & 0xFF00) != 0 ? 3 : 2;
	}

	public String toString() {
		return OpcodeUtil.toString0(code) + " " + getIndex();
	}
}