/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantFloat.java
 */
package roj.asm.cst;

import roj.util.ByteWriter;

public final class CstFloat extends Constant {
    public float value;

    public CstFloat(float value) {
        this.value = value;
    }

    @Override
    protected final void write0(ByteWriter writer) {
        writer.writeFloat(value);
    }

    public final String toString() {
        return super.toString() + " : " + value;
    }

    @Override
    public byte type() {
        return CstType.FLOAT;
    }

    public final int hashCode() {
        return Float.floatToIntBits(value);
    }

    public final boolean equals(Object o) {
        if (o == this)
            return true;
        return o instanceof CstFloat && ((CstFloat) o).value == this.value;
    }
}