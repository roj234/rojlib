/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AttrBootstrapMethods.java
 */
package roj.asm.struct.attr;

import roj.asm.constant.Constant;
import roj.asm.constant.CstRefUTF;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.util.ByteReader;
import roj.util.ByteWriter;

public final class AttrUTFRef extends Attribute {
    public AttrUTFRef(String name) {
        super(name);
    }

    public AttrUTFRef(String name, ByteReader r, ConstantPool pool) {
        super(name);
        Constant cst = pool.get(r);
        this.cst = ((CstRefUTF)cst);
    }

    public CstRefUTF cst;

    @Override
    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        w.writeShort(pool.reset(cst).getIndex());
    }

    public String toString() {
        return name + ": " + cst.getValue().getString();
    }
}