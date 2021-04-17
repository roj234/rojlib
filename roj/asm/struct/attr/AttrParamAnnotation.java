/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AttrParamAnnotation.java
 */
package roj.asm.struct.attr;

import roj.asm.struct.anno.Annotation;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.List;

public final class AttrParamAnnotation extends Attribute {
    public static final String VISIBLE = "RuntimeVisibleParameterAnnotations",
            INVISIBLE = "RuntimeInvisibleParameterAnnotations";

    public AttrParamAnnotation(String name) {
        super(name);
    }

    public AttrParamAnnotation(String name, ByteReader r, ConstantPool constantPool) {
        super(name);
        annotations = parse(constantPool, r);
    }

    public List<List<Annotation>> annotations;

    @Override
    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        w.writeByte((byte) annotations.size());
        for (List<Annotation> list : annotations) {
            w.writeShort(list.size());
            for (Annotation anno : list) {
                anno.toByteArray(pool, w);
            }
        }
    }

    public static List<List<Annotation>> parse(ConstantPool constantPool, ByteReader r) {
        int paramLen = r.readUByte();
        List<List<Annotation>> annotationss = new ArrayList<>(paramLen);
        for (; paramLen > 0; paramLen--) {
            annotationss.add(AttrAnnotation.parse(constantPool, r));
        }
        return annotationss;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(" Parameters' Annotation: \n");
        int i = 0;
        for (List<Annotation> list : annotations) {
            sb.append("            Par.No.").append(i).append(": \n");
            for (Annotation anno : list) {
                sb.append("               ").append(anno).append('\n');
            }
            i++;
        }
        return sb.toString();
    }
}