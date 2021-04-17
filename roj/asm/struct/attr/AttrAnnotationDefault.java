/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AttrAnnotationDefault.java
 */
package roj.asm.struct.attr;

import roj.asm.struct.anno.AnnVal;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.util.ByteReader;
import roj.util.ByteWriter;

public final class AttrAnnotationDefault extends Attribute {
    public static final String NAME = "AnnotationDefault";

    public AttrAnnotationDefault(ByteReader r, ConstantPool constantPool) {
        super(NAME);
        def = AnnVal.parse(constantPool, r);
    }

    public AnnVal def;

    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        def.toByteArray(pool, w);
    }

    public String toString() {
        return "AnnotationDefault: " + def;
    }
}