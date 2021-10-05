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

import roj.asm.cst.*;
import roj.collect.IntIterator;
import roj.collect.SimpleList;
import roj.concurrent.OperationDone;
import roj.text.TextUtil;
import roj.util.ByteReader;
import roj.util.Idx;

import javax.annotation.Nonnull;
import java.io.UTFDataFormatException;
import java.util.List;

import static roj.asm.cst.CstType.*;

/**
 * 常量池
 *
 * @author Roj234
 * @version 2.0
 * @since 2021/5/29 17:16
 */
public final class ConstantPool {
    Constant[] cst;
    final SimpleList<Constant> cstList;
    int index;

    public ConstantPool(int len) {
        this.cst = new Constant[(this.index = len) - 1];
        this.cstList = new SimpleList<>();
        this.cstList.setRawArray(cst);
        this.cstList.i_setSize(len - 1);
    }

    public void read(ByteReader r) {
        Constant[] cst = this.cst;
        Idx idx = new Idx(cst.length);

        int i = 0;
        while (i < cst.length) {
            Constant c = readConstant(r);
            cst[i++] = c;
            c.setIndex(i);
            switch (c.type()) {
                case LONG:
                case DOUBLE:
                    idx.add(i - 1);
                    idx.add(i);
                    cst[i++] = CstTop.TOP;
                    break;
                case UTF:
                case INT:
                case FLOAT:
                    idx.add(i - 1);
                    break;
            }
        }

        for (IntIterator it = idx.remains(); it.hasNext(); ) {
            Constant c = cst[it.nextInt()];
            try {
                switch (c.type()) {
                    case MODULE:
                    case PACKAGE:
                    case METHOD_TYPE:
                    case STRING:
                    case CLASS: {
                        CstRefUTF cz = (CstRefUTF) c;
                        cz.setValue((CstUTF) cst[cz.getValueIndex() - 1]);
                    }
                    break;
                    case NAME_AND_TYPE: {
                        CstNameAndType cz = (CstNameAndType) c;
                        cz.setName((CstUTF) cst[cz.getNameIndex() - 1]);
                        cz.setType((CstUTF) cst[cz.getTypeIndex() - 1]);
                    }
                    break;
                    case FIELD:
                    case METHOD:
                    case INTERFACE: {
                        CstRef cz = (CstRef) c;
                        cz.setClazz((CstClass) cst[cz.getClassIndex() - 1]);
                        cz.desc((CstNameAndType) cst[cz.getDescIndex() - 1]);
                    }
                    break;
                    case INVOKE_DYNAMIC: {
                        CstDynamic cz = (CstDynamic) c;
                        cz.setDesc((CstNameAndType) cst[cz.getDescIndex() - 1]);
                    }
                    break;
                    case METHOD_HANDLE: {
                        CstMethodHandle cz = (CstMethodHandle) c;
                        cz.setRef((CstRef) cst[cz.getRefIndex() - 1]);
                    }
                    break;
                }
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(c + " is referencing an invalid constant " + getReferTo(c), e);
            }
        }
    }

    private Constant getReferTo(Constant c) {
        switch (c.type()) {
            case METHOD_TYPE:
            case STRING:
            case CLASS: {
                CstRefUTF cz = (CstRefUTF) c;
                return cst[cz.getValueIndex()];
            }
            case NAME_AND_TYPE: {
                CstNameAndType cz = (CstNameAndType) c;
                Constant cst = this.cst[cz.getNameIndex()];
                if (!(cst instanceof CstUTF)) {
                    return cst;
                }
                return this.cst[cz.getTypeIndex()];
            }
            case FIELD:
            case METHOD:
            case INTERFACE: {
                CstRefField cz = (CstRefField) c;
                Constant cst = this.cst[cz.getClassIndex()];
                if (!(cst instanceof CstClass)) {
                    return cst;
                }
                return this.cst[cz.getDescIndex()];
            }
            case INVOKE_DYNAMIC: {
                CstDynamic cz = (CstDynamic) c;
                return cst[cz.getDescIndex()];
            }
            case METHOD_HANDLE: {
                CstMethodHandle cz = (CstMethodHandle) c;
                return cst[cz.getRefIndex()];
            }
        }
        return null;
    }

    private static Constant readConstant(ByteReader r) {
        short b = r.readUByte();
        if (CstType.toString(b) == null)
            throw new IllegalArgumentException("Illegal constant type " + b);
        switch (b) {
            case UTF:
                try {
                    return new CstUTF(r.readUTF());
                } catch (UTFDataFormatException e) {
                    throw new RuntimeException(e);
                }
            case INT:
                return new CstInt(r.readInt());
            case FLOAT:
                return new CstFloat(r.readFloat());
            case LONG:
                return new CstLong(r.readLong());
            case DOUBLE:
                return new CstDouble(r.readDouble());

            case METHOD_TYPE:
                return new CstMethodType(r.readUnsignedShort());
            case MODULE:
                return new CstModule(r.readUnsignedShort());
            case PACKAGE:
                return new CstPackage(r.readUnsignedShort());
            case CLASS:
                return new CstClass(r.readUnsignedShort());
            case STRING:
                return new CstString(r.readUnsignedShort());
            case NAME_AND_TYPE:
                return new CstNameAndType(r.readUnsignedShort(), r.readUnsignedShort());

            case FIELD:
                return new CstRefField(r.readUnsignedShort(), r.readUnsignedShort());
            case METHOD:
                return new CstRefMethod(r.readUnsignedShort(), r.readUnsignedShort());
            case INTERFACE:
                return new CstRefItf(r.readUnsignedShort(), r.readUnsignedShort());
            case DYNAMIC:
            case INVOKE_DYNAMIC:
                return new CstDynamic(b == INVOKE_DYNAMIC, r.readUnsignedShort(), r.readUnsignedShort());

            case METHOD_HANDLE:
                return new CstMethodHandle(r.readByte(), r.readUnsignedShort());
        }
        throw OperationDone.NEVER;
    }

    public List<Constant> array() {
        return cstList;
    }

    // fucking inspection
    @Nonnull
    public Constant array(int i) {
        return i == 0 ? null : cstList.get(i - 1);
    }

    // fucking inspection
    @Nonnull
    public Constant get(ByteReader r) {
        int id = r.readUnsignedShort();
        return id == 0 ? null : cstList.get(id - 1);
    }

    public String getName(ByteReader r) {
        int id = r.readUnsignedShort() - 1;

        return id < 0 ? null : ((CstClass) cstList.get(id)).getValue().getString();
    }

    @Override
    public String toString() {
        return "ConstantPool[" + (index + 1) + "]= " + TextUtil.prettyPrint(array());
    }
}