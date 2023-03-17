package roj.asm.frame.node;

import roj.asm.frame.MethodPoet;
import roj.asm.tree.insn.IIndexInsnNode;
import roj.asm.tree.insn.InsnNode;
import roj.asm.visitor.CodeWriter;

import static roj.asm.Opcodes.ALOAD;
import static roj.asm.Opcodes.ILOAD;
import static roj.asm.frame.VarType.*;

/**
 * @author Roj234
 * @since 2022/2/5 12:22
 */
public final class VarSLNode extends InsnNode implements IIndexInsnNode {
	public final MethodPoet.Variable v;

	public VarSLNode(MethodPoet.Variable v, boolean store) {
		super((byte) 0);
		this.v = v;
		switch (v.curType.type) {
			case TOP:
				throw new IllegalStateException();
			case INT:
			case LONG:
			case FLOAT:
			case DOUBLE:
				code = (byte) (ILOAD - 1 + v.curType.type);
				break;
			default:
				code = ALOAD;
		}
		if (store) code += 33;
	}

	@Override
	public int nodeType() {
		return T_LOAD_STORE;
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
		int vid = v.slot;
		if (vid <= 3) {
			cw.one((byte) loadSore(code, vid));
		} else {
			cw.var(code, vid);
		}
	}

	@Override
	public int nodeSize(int i) {
		int vid = v.slot;
		if (vid < 256) {
			if (vid <= 3) {
				return 1;
			} else {
				return 2;
			}
		} else if (vid < 65535) {
			return 4;
		} else {
			throw new IllegalArgumentException();
		}
	}

	private static int loadSore(byte base, int id) {
		return ((base <= 25 ? ((base - 0x15) * 4 + 0x1a) : ((base - 0x36) * 4 + 0x3b)) + id);
	}

	public boolean isLoad() {
		return code <= 25;
	}
}
