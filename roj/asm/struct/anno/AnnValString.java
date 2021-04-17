/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AnnotationValueString.java
 */
package roj.asm.struct.anno;

import roj.asm.util.ConstantWriter;
import roj.text.StringPool;
import roj.util.ByteWriter;

public final class AnnValString extends AnnVal {
    public AnnValString(String value) {
        super(AnnotationType.STRING);
        this.value = value;
    }

    public String value;

    @Override
    public void _toByteArray(StringPool pool, ByteWriter w) {
        pool.writeString(w, value);
    }

    public void _toByteArray(ConstantWriter pool, ByteWriter w) {
        w.writeShort(pool.getUtfId(value));
    }

    public String toString() {
        return '"' + value + '"';
    }
}