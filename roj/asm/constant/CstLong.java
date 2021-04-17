/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantLong.java
 */
package roj.asm.constant;

import roj.util.ByteWriter;

public final class CstLong extends Constant {
    public long value;

    public CstLong(long value) {
        super(CstType.LONG);
        this.value = value;
    }

    @Override
    protected final void write0(ByteWriter writer) {
        writer.writeLong(value);
    }

    public final String toString() {
        return super.toString() + " : " + value;
    }

    public final int hashCode() {
        return (int) value;
    }

    public final boolean equals(Object o) {
        if (o == this)
            return true;
        return o instanceof CstLong && ((CstLong) o).value == this.value;
    }
}