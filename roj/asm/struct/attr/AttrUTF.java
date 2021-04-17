/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AttrBootstrapMethods.java
 */
package roj.asm.struct.attr;

import roj.asm.constant.CstUTF;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.util.ByteReader;
import roj.util.ByteWriter;

public final class AttrUTF extends Attribute {
    public static final String NEST_HOST = "NestHost";
    public static final String SIGNATURE = "Signature";

    public AttrUTF(String name) {
        super(name);
    }

    public AttrUTF(String name, ByteReader r, ConstantPool pool) {
        super(name);
        this.value = ((CstUTF)pool.get(r)).getString();
    }

    public String value;

    public AttrUTF(String name, String value) {
        super(name);
        this.value = value;
    }

    @Override
    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        w.writeShort(pool.getUtfId(value));
    }

    public String toString() {
        return name + ": " + value;
    }
}