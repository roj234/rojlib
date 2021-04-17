/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AnnotationValueFloat.java
 */
package roj.asm.struct.anno;

import roj.asm.util.ConstantWriter;
import roj.text.StringPool;
import roj.util.ByteWriter;

public final class AnnValFloat extends AnnVal {
    public AnnValFloat(float value) {
        super(AnnotationType.FLOAT);
        this.value = value;
    }

    public float value;

    @Override
    public void _toByteArray(StringPool pool, ByteWriter w) {

    }

    public void _toByteArray(ConstantWriter pool, ByteWriter w) {
        w.writeShort(pool.getFloatId(value));
    }

    public String toString() {
        return String.valueOf(value);
    }
}