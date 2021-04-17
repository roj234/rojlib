/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantInt.java
 */
package roj.asm.constant;

import roj.util.ByteWriter;

public final class CstInt extends Constant {
    public int value;

    public CstInt(int value) {
        super(CstType.INT);
        this.value = value;
    }

    @Override
    protected final void write0(ByteWriter writer) {
        writer.writeInt(value);
    }

    public final String toString() {
        return super.toString() + " : " + value;
    }

    public final int hashCode() {
        return value;
    }

    public final boolean equals(Object o) {
        if (o == this)
            return true;
        return o instanceof CstInt && ((CstInt) o).value == this.value;
    }
}