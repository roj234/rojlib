/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: IncrInsnNode.java
 */
package roj.asm.struct.insn;

import roj.asm.Opcodes;
import roj.util.ByteWriter;

public class IncrInsnNode extends InsnNode implements IIndexInsnNode {
    public IncrInsnNode(int variableId, int amount) {
        super(Opcodes.IINC);
        this.variableId = variableId;
        this.amount = amount;
    }

    @Override
    protected boolean validate() {
        return code == Opcodes.IINC;
    }

    public int variableId, amount;

    @Override
    public int getIndex() {
        return variableId;
    }

    @Override
    public void toByteArray(ByteWriter w) {
        super.toByteArray(w);
        if (variableId != (byte) variableId || amount != (byte) amount) {
            w.writeShort(variableId).writeShort(amount);
        } else {
            w.writeByte((byte) variableId).writeByte((byte) amount);
        }
    }

    public String toString() {
        return "#" + bci + ' ' + Opcodes.toString0(code, variableId, amount);
    }
}