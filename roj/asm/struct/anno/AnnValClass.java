/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AnnotationValueClass.java
 */
package roj.asm.struct.anno;

import roj.asm.util.ConstantWriter;
import roj.asm.util.type.ParamHelper;
import roj.asm.util.type.Type;
import roj.text.StringPool;
import roj.util.ByteWriter;

public final class AnnValClass extends AnnVal {
    public AnnValClass(String className) {
        super(AnnotationType.CLASS);
        this.value = ParamHelper.parseField(className);
    }

    public Type value;

    @Override
    public void _toByteArray(StringPool pool, ByteWriter w) {
        pool.writeString(w, ParamHelper.getField(value));
    }

    public void _toByteArray(ConstantWriter pool, ByteWriter w) {
        w.writeShort(pool.getUtfId(ParamHelper.getField(value)));
    }

    public String toString() {
        return value.toString() + ".class";
    }
}