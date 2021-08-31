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

package roj.asm.util;

import roj.asm.cst.Constant;
import roj.asm.cst.CstClass;
import roj.asm.cst.CstType;
import roj.asm.cst.CstUTF;
import roj.collect.CharMap;
import roj.util.ByteReader;

import java.io.UTFDataFormatException;

import static roj.asm.cst.CstType.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public class ConstantNamePool {
    CharMap<Constant> map;
    int index = 1, len;

    public ConstantNamePool(int length) {
        this.map = new CharMap<>();
        this.len = length;
    }

    public int index() {
        return index;
    }

    void addConstant(Constant c) {
        c.setIndex(index);
        map.put((char) index++, c);
    }

    public void readNames(ByteReader r) {
        int len = this.len;
        while (index < len) {
            Constant c = null;

            int b = r.readUnsignedByte();
            if (CstType.toString(b) == null)
                throw new IllegalArgumentException("Illegal constant type " + b);
            switch (b) {
                case UTF:
                    try {
                        c = new CstUTF(r.readUTF());
                    } catch (UTFDataFormatException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                case INT:
                case INVOKE_DYNAMIC:
                case METHOD:
                case FIELD:
                case FLOAT:
                case INTERFACE:
                case NAME_AND_TYPE:
                    r.index += 4;
                    break;

                case LONG:
                case DOUBLE:
                    index++;
                    r.index += 8;
                    break;

                case METHOD_TYPE:
                case STRING:
                case MODULE: // may
                case PACKAGE: // may
                    r.index += 2;
                    break;

                case CLASS:
                    c = new CstClass(r.readUnsignedShort());
                    break;

                case METHOD_HANDLE:
                    r.index += 3;
                    break;
            }

            if (c != null)
                addConstant(c);
            else
                index++;
        }
    }

    public Constant get(ByteReader r) {
        return map.get(r.readChar());
    }

    public String getName(ByteReader r) {
        int id = r.readUnsignedShort();

        return id == 0 ? null :
                ((CstUTF) map.get((char) ((CstClass) map.get((char) id)).getValueIndex())).getString();
    }
}