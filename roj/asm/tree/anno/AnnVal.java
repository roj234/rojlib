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
import roj.util.ByteList;
import roj.util.ByteReader;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public abstract class AnnVal {
    public static final char BYTE       = 'B';
    public static final char CHAR       = 'C';
    public static final char DOUBLE     = 'D';
    public static final char FLOAT      = 'F';
    public static final char INT        = 'I';
    public static final char LONG       = 'J';
    public static final char SHORT      = 'S';
    public static final char BOOLEAN    = 'Z';

    public static final char STRING     = 's';
    public static final char ENUM       = 'e';
    public static final char CLASS      = 'c';
    public static final char ANNOTATION = '@';
    public static final char ARRAY      = '[';

    public AnnVal() {}

    @Deprecated
    public static AnnVal parse(ConstantPool pool, ByteReader r) {
        ByteList l = r.bytes;
        int ri = l.rIndex;
        l.rIndex = r.rIndex;
        try {
            return parse(pool, l);
        } finally {
            r.rIndex = l.rIndex;
            l.rIndex = ri;
        }
    }

    public static AnnVal parse(ConstantPool pool, ByteList r) {
        int type = r.readUnsignedByte();

        switch (type) {
            case BOOLEAN:
            case BYTE:
            case SHORT:
            case CHAR:
            case INT:
            case DOUBLE:
            case FLOAT:
            case LONG:
            case STRING:
            case CLASS:
                Constant c = pool.get(r);
                switch (type) {
                    case DOUBLE:
                        return new AnnValDouble(((CstDouble) c).value);
                    case FLOAT:
                        return new AnnValFloat(((CstFloat) c).value);
                    case LONG:
                        return new AnnValLong(((CstLong) c).value);
                    case STRING:
                        return new AnnValString(((CstUTF) c).getString());
                    case CLASS:
                        return new AnnValClass(((CstUTF) c).getString());
                    default:
                        return new AnnValInt((char) type, ((CstInt) c).value);
                }
            case ENUM:
                return new AnnValEnum(((CstUTF) pool.get(r)).getString(), ((CstUTF) pool.get(r)).getString());
            case ANNOTATION:
                return new AnnValAnnotation(Annotation.deserialize(pool, r));
            case ARRAY:
                int len = r.readUnsignedShort();
                List<AnnVal> annos = new ArrayList<>(len);
                while (len-- > 0) {
                    annos.add(parse(pool, r));
                }
                return new AnnValArray(annos);
        }
        throw new IllegalArgumentException("Unknown annotation value type '" + (char)type + "'");
    }

    public abstract byte type();

    public abstract void toByteArray(ConstantPool pool, ByteList w);

    public abstract String toString();
}