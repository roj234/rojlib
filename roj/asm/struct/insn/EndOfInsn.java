package roj.asm.struct.insn;

import roj.asm.Opcodes;
import roj.asm.util.InsnList;
import roj.util.ByteWriter;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: EndOfInsn.java
 */
public final class EndOfInsn extends InsnNode {
    public static final EndOfInsn INSTANCE = new EndOfInsn();

    private EndOfInsn() {
        super(Opcodes.NOP);
    }

    @Override
    public void onRemove(InsnList insnList, int pos) {}

    @Override
    public void toByteArray(ByteWriter w) {
        throw new InternalError("方法结束标识 should not be written");
    }

    public String toString() {
        return "[方法结束标识]";
    }
}
