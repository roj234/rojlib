/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AttrConstantValue.java
 */
package roj.asm.struct.attr;

import roj.asm.cst.Constant;
import roj.asm.util.ConstantWriter;
import roj.util.ByteWriter;

public final class AttrConstantValue extends Attribute {
    public AttrConstantValue(Constant c) {
        super("ConstantValue");
        this.c = c;
    }

    public Constant c;

    @Override
    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        w.writeShort(pool.reset(c).getIndex());
    }

    public String toString() {
        return "ConstantValue: " + c;
    }
}