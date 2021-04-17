/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantDouble.java
 */
package roj.asm.constant;

import roj.util.ByteWriter;

public final class CstDouble extends Constant {
    public double value;

    public CstDouble(double value) {
        super(CstType.DOUBLE);
        this.value = value;
    }

    @Override
    protected void write0(ByteWriter writer) {
        writer.writeDouble(value);
    }

    public String toString() {
        return super.toString() + " : " + value;
    }

    public int hashCode() {
        return (int) Double.doubleToLongBits(value);
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;
        return o instanceof CstDouble && ((CstDouble) o).value == this.value;
    }
}