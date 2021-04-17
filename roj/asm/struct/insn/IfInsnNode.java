/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: IfInsnNode.java
 */
package roj.asm.struct.insn;

import roj.util.ByteWriter;

import static roj.asm.Opcodes.*;

public final class IfInsnNode extends GotoInsnNode {
    public IfInsnNode(byte code, InsnNode node) {
        super(code, node);
    }

    @Override
    protected boolean validate() {
        switch (code) {
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case IF_icmpeq:
            case IF_icmpne:
            case IF_icmplt:
            case IF_icmpge:
            case IF_icmpgt:
            case IF_icmple:
            case IF_acmpeq:
            case IF_acmpne:
            case IFNULL:
            case IFNONNULL:
                return true;
        }
        return false;
    }

    public void toByteArray(ByteWriter w) {
        w.writeByte(code).writeShort(delta);
    }
}