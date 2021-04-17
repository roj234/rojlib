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
import roj.collect.SimpleList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/1/1 23:12
 */
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
    protected void toByteArray1(ConstantPool pool, ByteWriter w) {
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