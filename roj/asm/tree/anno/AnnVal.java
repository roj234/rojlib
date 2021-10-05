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

package roj.asm.tree.anno;

import roj.asm.cst.*;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public abstract class AnnVal {
    AnnVal(char type) {
        this.type = (byte) type;
    }

    public final byte type;

    public static AnnVal parse(ConstantPool pool, ByteReader r) {
        char type = AnnotationType.verify(r.readUByte());

        switch (type) {
            case AnnotationType.BOOLEAN:
            case AnnotationType.BYTE:
            case AnnotationType.SHORT:
            case AnnotationType.CHAR:
            case AnnotationType.INT:
            case AnnotationType.DOUBLE:
            case AnnotationType.FLOAT:
            case AnnotationType.LONG:
            case AnnotationType.STRING:
            case AnnotationType.CLASS:
                return parsePrimitive(pool, r, type);
            case AnnotationType.ENUM:
                return new roj.asm.tree.anno.AnnValEnum(((CstUTF) pool.get(r)).getString(), ((CstUTF) pool.get(r)).getString());
            case AnnotationType.ANNOTATION:
                return new AnnValAnnotation(Annotation.deserialize(pool, r));
            case AnnotationType.ARRAY:
                int len = r.readUnsignedShort();
                List<AnnVal> annos = new ArrayList<>();
                while (len > 0) {
                    annos.add(parse(pool, r));
                    len--;
                }
                return new AnnValArray(annos);
        }
        return null;
    }

    public static AnnVal parsePrimitive(ConstantPool pool, ByteReader r, char type) {
        Constant c = pool.get(r);
        switch (type) {
            case AnnotationType.BOOLEAN:
            case AnnotationType.BYTE:
            case AnnotationType.SHORT:
            case AnnotationType.CHAR:
            case AnnotationType.INT:
                return new AnnValInt(type, ((CstInt) c).value);
            case AnnotationType.DOUBLE:
                return new AnnValDouble(((CstDouble) c).value);
            case AnnotationType.FLOAT:
                return new AnnValFloat(((CstFloat) c).value);
            case AnnotationType.LONG:
                return new AnnValLong(((CstLong) c).value);
            case AnnotationType.STRING:
                return new AnnValString(((CstUTF) c).getString());
            case AnnotationType.CLASS:
                return new AnnValClass(((CstUTF) c).getString());
        }
        return null;
    }

    public final void toByteArray(ConstantWriter pool, ByteWriter w) {
        _toByteArray(pool, w.writeByte(type));
    }

    abstract void _toByteArray(ConstantWriter pool, ByteWriter w);

    public abstract String toString();
}