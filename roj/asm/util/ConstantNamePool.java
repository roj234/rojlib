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
import roj.asm.cst.CstUTF;
import roj.collect.CharMap;
import roj.util.ByteReader;

import java.io.UTFDataFormatException;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class ConstantNamePool {
    public final CharMap<Constant> map;
    private int len;
    private int begin;

    public ConstantNamePool(int length) {
        this.map = new CharMap<>();
        this.len = length;
    }

    public void internalReset(int len) {
        this.map.clear();
        this.len = len;
    }

    public void skip(ByteReader r) {
        this.begin = r.rIndex;
        int i = 1;
        int len = this.len;
        while (i < len) {
            int b = r.readUByte();
            if (Constant.toString(b) == null)
                throw new IllegalArgumentException("Illegal constant type " + b);
            switch (b) {
                case Constant.UTF:
                    r.rIndex = r.readUnsignedShort() + r.rIndex;
                    break;
                case Constant.INT:
                case Constant.INVOKE_DYNAMIC:
                case Constant.METHOD:
                case Constant.FIELD:
                case Constant.FLOAT:
                case Constant.INTERFACE:
                case Constant.NAME_AND_TYPE:
                    r.rIndex += 4;
                    break;

                case Constant.LONG:
                case Constant.DOUBLE:
                    i++;
                    r.rIndex += 8;
                    break;

                case Constant.METHOD_TYPE:
                case Constant.STRING:
                case Constant.MODULE:
                case Constant.PACKAGE:
                case Constant.CLASS:
                    r.rIndex += 2;
                    break;

                case Constant.METHOD_HANDLE:
                    r.rIndex += 3;
                    break;
            }
            i++;
        }
    }

    public void init(ByteReader r) {
        r.rIndex = begin;
        int i = 1;
        while (i < len) {
            switch (r.readUByte()) {
                case Constant.UTF:
                    if (map.containsKey((char) i)) {
                        try {
                            map.put((char) i, new CstUTF(r.readUTF()));
                        } catch (UTFDataFormatException e) {
                            throw new RuntimeException(e);
                        }
                    } else
                        r.rIndex = r.readUnsignedShort() + r.rIndex;
                    break;
                case Constant.INT:
                case Constant.INVOKE_DYNAMIC:
                case Constant.METHOD:
                case Constant.FIELD:
                case Constant.FLOAT:
                case Constant.INTERFACE:
                case Constant.NAME_AND_TYPE:
                    r.rIndex += 4;
                    break;

                case Constant.LONG:
                case Constant.DOUBLE:
                    i++;
                    r.rIndex += 8;
                    break;

                case Constant.METHOD_TYPE:
                case Constant.STRING:
                case Constant.MODULE:
                case Constant.PACKAGE:
                    r.rIndex += 2;
                    break;

                case Constant.CLASS:
                    if (map.containsKey((char) i)) {
                        int idx = r.readUnsignedShort();
                        if (!map.containsKey((char) idx))
                            map.put((char) idx, null);
                        map.put((char) i, new CstClass(idx));
                    } else
                        r.rIndex += 2;
                    break;

                case Constant.METHOD_HANDLE:
                    r.rIndex += 3;
                    break;
            }

            i++;
        }
        r.rIndex = begin;
        i = 1;
        while (i < len) {
            switch (r.readUByte()) {
                case Constant.UTF:
                    if (map.containsKey((char) i) && map.get((char) i) == null) {
                        try {
                            map.put((char) i, new CstUTF(r.readUTF()));
                        } catch (UTFDataFormatException e) {
                            throw new RuntimeException(e);
                        }
                    } else
                        r.rIndex = r.readUnsignedShort() + r.rIndex;
                    break;
                case Constant.INT:
                case Constant.INVOKE_DYNAMIC:
                case Constant.METHOD:
                case Constant.FIELD:
                case Constant.FLOAT:
                case Constant.INTERFACE:
                case Constant.NAME_AND_TYPE:
                    r.rIndex += 4;
                    break;
                case Constant.LONG:
                case Constant.DOUBLE:
                    i++;
                    r.rIndex += 8;
                    break;
                case Constant.METHOD_TYPE:
                case Constant.STRING:
                case Constant.MODULE:
                case Constant.PACKAGE:
                case Constant.CLASS:
                    r.rIndex += 2;
                    break;
                case Constant.METHOD_HANDLE:
                    r.rIndex += 3;
                    break;
            }
            i++;
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