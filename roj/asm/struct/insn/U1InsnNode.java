/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: U1InsnNode.java
 */
package roj.asm.struct.insn;

import roj.asm.Opcodes;
import roj.util.ByteWriter;

// bipush / newarray
public class U1InsnNode extends InsnNode implements IIndexInsnNode {
    public U1InsnNode(byte code, short index) {
        super(code);
        this.index = index;
    }

    /**
     * for new array
     * T_BOOLEAN4
     * T_CHAR	5
     * T_FLOAT	6
     * T_DOUBLE	7
     * T_BYTE	8
     * T_SHORT	9
     * T_INT	10
     * T_LONG	11
     */

    public short index;

    public int getIndex() {
        return index;
    }

    public void toByteArray(ByteWriter w) {
        super.toByteArray(w);
        w.writeByte((byte) index);
    }

    public String toString() {
        return "#" + ((int)bci) + ' ' + Opcodes.toString0(code, index);
    }
}