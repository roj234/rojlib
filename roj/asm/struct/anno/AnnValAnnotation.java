/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AnnotationValueAnnotation.java
 */
package roj.asm.struct.anno;

import roj.asm.util.ConstantWriter;
import roj.text.StringPool;
import roj.util.ByteWriter;

public final class AnnValAnnotation extends AnnVal {
    public AnnValAnnotation(Annotation value) {
        super(AnnotationType.ANNOTATION);
        this.value = value;
    }

    public Annotation value;

    public void _toByteArray(ConstantWriter pool, ByteWriter w) {
        value.toByteArray(pool, w);
    }

    @Override
    public void _toByteArray(StringPool pool, ByteWriter w) {
        value.serialize(pool, w);
    }

    public String toString() {
        return "Annotation: \n" + value + '\n';
    }
}