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
import roj.collect.MyHashSet;
import roj.concurrent.OperationDone;
import roj.text.TextUtil;
import roj.util.ByteReader;
import roj.util.Idx;

import java.io.UTFDataFormatException;
import java.util.Arrays;
import java.util.List;

import static roj.asm.cst.CstType.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public class ConstantPool {
    Constant[] cst;
    int index = 1;

    MyHashSet<Constant> uniquer;
    CstUTF empty;

    public ConstantPool(int len) {
        this.cst = new Constant[len];
        this.uniquer = new MyHashSet<>(len);
    }

    public int index() {
        return index;
    }

    void addConstant(Constant c) {
        c.setIndex(index);
        cst[index++] = c;
        switch (c.type()) {
            case LONG:
            case DOUBLE:
                cst[index++] = CstTop.TOP;
        }
    }

    private boolean fixNPE(Constant c) {
        if (empty == null) {
            this.cst = Arrays.copyOf(this.cst, this.cst.length + 1);
            addConstant(empty = new CstUTF(""));
        }
        if (c instanceof CstRefUTF) {
            CstRefUTF ref = ((CstRefUTF) c);
            ref.setValue(empty);
        } else {
            return false;
        }
        return true;
    }

    @SuppressWarnings("fallthrough")
    public void valid() {
        if (uniquer == null)
            throw new IllegalStateException("Already validated.");

        MyHashSet<Constant> uniquer = new MyHashSet<>(cst.length);

        Constant[] cst = this.cst;
        Idx idx = new Idx(cst.length);
        idx.add(0); // remove 0

        IntIterator itr = idx.remains();
        for (int pass = 0; pass < 3; pass++) {
            while (itr.hasNext()) {
                int i = itr.nextInt();
                Constant c = cst[i];

                try {
                    boolean thisLv = validate(c, pass);
                    if (thisLv) {
                        cst[i] = uniquer.intern(c);
                        idx.add(i);
                    }
                } catch (ClassCastException e) {
                    Constant err = getReferTo(c, pass);
                    throw new IllegalArgumentException("Constant " + c + " is referencing to invalid index " + err.getIndex() + " ( " + err + " )", e);
                } catch (NullPointerException e) {
                    if (fixNPE(c)) {
                        System.err.println("NPE at " + i + ", probably a coding error.");
                        validate(c, pass);
                        idx.add(i);
                    } else {
                        throw e;
                    }
                }
            }
            itr.reset();
        }

        uniquer.clear();
        this.uniquer = null;
    }

    private Constant getReferTo(Constant c, int level) {
        switch (level) {
            case 1: {
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
                }
            }
            break;
            case 2: {
                switch (c.type()) {
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
                }
            }
            break;
            case 3: {
                if (c.type() == CstType.METHOD_HANDLE) {
                    CstMethodHandle cz = (CstMethodHandle) c;
                    return cst[cz.getRefIndex()];
                }
            }
            break;
        }
        return new CstUTF("Impossible error at " + level + " of " + c);
    }

    private boolean validate(Constant c, int level) throws ClassCastException {
        switch (level) {
            case 0: {
                switch (c.type()) {
                    case MODULE:
                    case PACKAGE:
                    case METHOD_TYPE:
                    case STRING:
                    case CLASS: {
                        CstRefUTF cz = (CstRefUTF) c;
                        cz.setValue((CstUTF) cst[cz.getValueIndex()]);
                    }
                    return true;
                    case NAME_AND_TYPE: {
                        CstNameAndType cz = (CstNameAndType) c;
                        cz.setName((CstUTF) cst[cz.getNameIndex()]);
                        cz.setType((CstUTF) cst[cz.getTypeIndex()]);
                    }
                    return true;
                    case UTF:
                    case DOUBLE:
                    case INT:
                    case LONG:
                    case FLOAT:
                    case _TOP_:
                        return true;
                }
            }
            break;
            case 1: {
                switch (c.type()) {
                    case FIELD:
                    case METHOD:
                    case INTERFACE: {
                        CstRef cz = (CstRef) c;
                        cz.setClazz((CstClass) cst[cz.getClassIndex()]);
                        cz.desc((CstNameAndType) cst[cz.getDescIndex()]);
                    }
                    return true;
                    case INVOKE_DYNAMIC: {
                        CstDynamic cz = (CstDynamic) c;
                        cz.setDesc((CstNameAndType) cst[cz.getDescIndex()]);
                    }
                    return true;
                }
            }
            break;
            case 2: {
                if (c.type() == CstType.METHOD_HANDLE) {
                    CstMethodHandle cz = (CstMethodHandle) c;
                    cz.setRef((CstRef) cst[cz.getRefIndex()]);
                    return true;
                }
            }
            break;
        }
        return false;
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

    public void readNames(ByteReader r) {
        if (uniquer == null)
            throw new IllegalStateException("Already validated.");

        int len = cst.length;
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

    @SuppressWarnings("unchecked")
    public <T extends Constant> T autoCast(int index) {
        return (T) cst[index];
    }

    public Constant[] array() {
        return cst;
    }

    public Constant array(int i) {
        return cst[i];
    }

    public void read(ByteReader r) {
        if (uniquer == null)
            throw new IllegalStateException("Already validated.");

        int len = cst.length;
        while (index < len) {
            Constant c = readConstant(r);

            switch (c.type()) {
                case UTF:
                case DOUBLE:
                case INT:
                case LONG:
                case FLOAT:
                    c = uniquer.intern(c);
            }

            addConstant(c);
        }
    }

    public Constant get(ByteReader r) {
        return cst[r.readUnsignedShort()];
    }

    public String getName(ByteReader r) {
        int id = r.readUnsignedShort();

        return id == 0 ? null : (uniquer == null ?
                ((CstClass) (cst[id])).getValue() :
                ((CstUTF) cst[((CstClass) cst[id]).getValueIndex()])).getString();
    }

    @Override
    public String toString() {
        return "ConstantPool[" + index + "]= " + TextUtil.prettyPrint(Arrays.asList(cst));
    }

    public void reload(ConstantWriter writer) {
        if(cst.length != writer.getIndex())
            cst = new Constant[writer.getIndex()];
        int i = 1;

        List<Constant> csts = writer.getConstants();
        for (int j = 0, max = csts.size(); j < max; j++) {
            Constant c = csts.get(j);
            cst[i++] = c;
            if(c.type() == DOUBLE || c.type() == LONG)
                cst[i++] = CstTop.TOP;
        }
    }
}