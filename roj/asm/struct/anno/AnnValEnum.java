/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AnnotationValueEnum.java
 */
package roj.asm.struct.anno;

import roj.asm.util.ConstantWriter;
import roj.asm.util.type.ParamHelper;
import roj.asm.util.type.Type;
import roj.text.StringPool;
import roj.util.ByteWriter;

public final class AnnValEnum extends AnnVal {
    public AnnValEnum(String type, String value) {
        super(AnnotationType.ENUM);
        this.clazz = ParamHelper.parseField(type);
        this.value = value;
    }

    public Type clazz;
    public String value;

    public void _toByteArray(ConstantWriter pool, ByteWriter w) {
        w.writeShort(pool.getUtfId(ParamHelper.getField(this.clazz)));
        w.writeShort(pool.getUtfId(value));
    }

    @Override
    public void _toByteArray(StringPool pool, ByteWriter w) {
        pool.writeString(w, ParamHelper.getField(this.clazz));
        pool.writeString(w, value);
    }

    public String toString() {
        return String.valueOf(clazz) + '.' + value;
    }
}