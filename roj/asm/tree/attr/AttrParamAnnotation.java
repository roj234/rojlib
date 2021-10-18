/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package roj.asm.tree.attr;

import roj.asm.tree.anno.Annotation;
import roj.asm.util.ConstantPool;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/1/1 23:12
 */
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
    protected void toByteArray1(ConstantPool pool, ByteWriter w) {
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