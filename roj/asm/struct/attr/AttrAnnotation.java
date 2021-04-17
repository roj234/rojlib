/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AttrAnnotation.java
 */
package roj.asm.struct.attr;

import roj.asm.struct.anno.Annotation;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.collect.SimpleList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.List;

public final class AttrAnnotation extends Attribute {
    public static final String VISIBLE = "RuntimeVisibleAnnotations",
            INVISIBLE = "RuntimeInvisibleAnnotations";

    public AttrAnnotation(String name) {
        super(name);
    }

    public AttrAnnotation(boolean visible, ByteReader r, ConstantPool pool) {
        super(visible ? VISIBLE : INVISIBLE);
        annotations = parse(pool, r);
    }

    public AttrAnnotation(String name, ByteReader r, ConstantPool pool) {
        super(name);
        annotations = parse(pool, r);
    }

    public List<Annotation> annotations;

    @Override
    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        w.writeShort(annotations.size());
        for (Annotation annotation : annotations) {
            annotation.toByteArray(pool, w);
        }
    }

    public static List<Annotation> parse(ConstantPool pool, ByteReader r) {
        int annoLen = r.readUnsignedShort();
        List<Annotation> annos = new SimpleList<>(annoLen);
        for (int i = 0; i < annoLen; i++) {
            annos.add(Annotation.deserialize(pool, r));
        }
        return annos;
    }

    public String toString() {
        if (annotations.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        for (Annotation anno : annotations) {
            sb.append(anno).append('\n');
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
    }
}