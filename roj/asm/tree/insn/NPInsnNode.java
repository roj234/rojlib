package roj.asm.tree.insn;

import roj.asm.visitor.CodeWriter;

/**
 * @author Roj234
 * @since 2021/1/2 15:21
 */
public final class NPInsnNode extends InsnNode {
	public NPInsnNode(byte code) {
		super(code);
	}

	public static NPInsnNode of(byte code) {
		return new NPInsnNode(code);
	}

	public static NPInsnNode of(int code) {
		return new NPInsnNode((byte) code);
	}

	@Override
	public int nodeType() {
		int c = code & 0xFF;
		return (c >= 0x1a && c <= 0x2d) || (c >= 0x3c && c <= 0x4e) ? T_LOAD_STORE : T_OTHER;
	}

	@Override
	public void serialize(CodeWriter cw) {
		cw.one(code);
	}

	@Override
	public int nodeSize(int prevBci) {
		return 1;
	}
}