/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: U2InsnNode.java
 */
package roj.asm.struct.insn;

import roj.asm.Opcodes;
import roj.util.ByteWriter;

public class U2InsnNode extends InsnNode implements IIndexInsnNode {
    public U2InsnNode(byte code, int index) {
        super(code);
        this.index = index;
    }

    public int index;

    public int getIndex() {
        return index;
    }

    public void toByteArray(ByteWriter w) {
        super.toByteArray(w);
        w.writeShort((short) index);
    }

    public String toString() {
        return Opcodes.toString0(code, index);
    }
}