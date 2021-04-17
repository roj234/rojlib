/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AnnotationValueDouble.java
 */
package roj.asm.struct.anno;

import roj.asm.util.ConstantWriter;
import roj.text.StringPool;
import roj.util.ByteWriter;

public final class AnnValDouble extends AnnVal {
    public AnnValDouble(double value) {
        super(AnnotationType.DOUBLE);
        this.value = value;
    }

    public double value;

    @Override
    public void _toByteArray(StringPool pool, ByteWriter w) {
        w.writeDouble(value);
    }

    public void _toByteArray(ConstantWriter pool, ByteWriter w) {
        w.writeShort(pool.getDoubleId(value));
    }

    public String toString() {
        return String.valueOf(value);
    }
}