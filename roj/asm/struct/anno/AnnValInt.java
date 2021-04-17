/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AnnotationValueInt.java
 */
package roj.asm.struct.anno;

import roj.asm.util.ConstantWriter;
import roj.text.StringPool;
import roj.util.ByteWriter;

public final class AnnValInt extends AnnVal {
    public AnnValInt(char type, int value) {
        super(type);
        this.value = value;
    }

    public int value;

    @Override
    public void _toByteArray(StringPool pool, ByteWriter w) {
        w.writeVarInt(value);
    }

    public void _toByteArray(ConstantWriter pool, ByteWriter w) {
        w.writeShort(pool.getIntId(value));
    }

    public String toString() {
        return String.valueOf(value);
    }
}