/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AnnotationValueArray.java
 */
package roj.asm.struct.anno;

import roj.asm.util.ConstantWriter;
import roj.text.StringPool;
import roj.util.ByteWriter;

import java.util.List;

public final class AnnValArray extends roj.asm.struct.anno.AnnVal {
    public AnnValArray(List<roj.asm.struct.anno.AnnVal> value) {
        super(AnnotationType.ARRAY);
        this.value = value;
    }

    public List<roj.asm.struct.anno.AnnVal> value;

    public void _toByteArray(ConstantWriter pool, ByteWriter w) {
        w.writeShort((short) value.size());
        for (roj.asm.struct.anno.AnnVal val : value) {
            val.toByteArray(pool, w);
        }
    }

    @Override
    public void _toByteArray(StringPool pool, ByteWriter w) {
        w.writeVarInt(value.size(), false);
        for (roj.asm.struct.anno.AnnVal val : value) {
            val.toByteArray(pool, w);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        for (AnnVal val : value) {
            sb.append(val).append(", ");
        }
        if (value.size() > 0)
            sb.delete(sb.length() - 2, sb.length());
        sb.append('}');

        return sb.toString();
    }
}