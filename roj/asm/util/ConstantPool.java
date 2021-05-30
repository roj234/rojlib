/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantPool.java
 */
package roj.asm.util;

import roj.asm.cst.*;
import roj.collect.FindSet;
import roj.collect.MyHashSet;
import roj.concurrent.OperationDone;
import roj.util.ByteReader;
import roj.util.Idx;

import java.io.UTFDataFormatException;
import java.util.Arrays;
import java.util.List;
import java.util.PrimitiveIterator;

import static roj.asm.cst.CstType.*;

public class ConstantPool {
    Constant[] cst;
    int index = 1;

    boolean validated = false;
    CstUTF empty;

    ConstantPool() {}

    public ConstantPool(int len) {
        this.cst = new Constant[len];
    }

    void addConstant(Constant c) {
        c.setIndex(index);
        cst[index++] = c;
        switch (c.type()) {
            case LONG:
            case DOUBLE:
                cst[index++] = CstDoLHolder.HOLDER;
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

    public void valid() {
        if (validated)
            throw new IllegalStateException("ConstantPool is marked unmodifiable.");
        int len = cst.length;

        FindSet<Constant> constantSet = new MyHashSet<>();

        final Constant[] cst = this.cst;
        Idx idx = new Idx(cst.length);
        idx.add(0); // remove 0

        for (int i = 0; i < 3; i++) {
            PrimitiveIterator.OfInt itr = idx.remains();
            while (itr.hasNext()) {
                int index = itr.nextInt();
                Constant c = cst[index];
                if (c == CstDoLHolder.HOLDER/* null */) { // and there will be not any null class
                    idx.add(index);
                    continue;
                }

                try {
                    boolean flag = validate(c, i);
                    if (flag) {
                        cst[index] = constantSet.find(c);
                        idx.add(index);
                    }
                } catch (ClassCastException e) {
                    Constant refers = getReferTo(c, i);
                    throw new IllegalArgumentException("Constant " + c + " is referencing to invalid index " + refers.getIndex() + " ( " + refers + " )", e);
                } catch (NullPointerException e) {
                    if (fixNPE(c)) {
                        System.err.println("NPE found at id" + index + ", probably error occurred.");
                        validate(c, i);
                        idx.add(index);
                    } else {
                        throw e;
                    }
                }
            }
        }

        validated = true;
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
                    case UTF:
                    case DOUBLE:
                    case INT:
                    case LONG:
                    case FLOAT:
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
        throw new UnsupportedOperationException("It can't happen!");
    }

    private boolean validate(Constant c, int level) throws ClassCastException {
        //if (c == null)
        //    return true;
        switch (level) {
            case 0: {
                switch (c.type()) {
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
        if (byId(b) == null)
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
        if (validated)
            throw new IllegalStateException("ConstantPool is marked unmodifiable.");

        int len = cst.length;
        while (index < len) {
            Constant c = null;

            int b = r.readUnsignedByte();
            if (byId(b) == null)
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
        if (validated)
            throw new IllegalStateException("ConstantPool is marked unmodifiable.");
        int len = cst.length;
        while (index < len) {
            addConstant(readConstant(r));
        }
    }

    public Constant get(ByteReader r) {
        return cst[r.readUnsignedShort()];
    }

    public String getName(ByteReader r) {
        int id = r.readUnsignedShort();

        return id == 0 ? null : (validated ?
                ((CstClass) (cst[id])).getValue() :
                ((CstUTF) cst[((CstClass) cst[id]).getValueIndex()])).getString();
    }

    @Override
    public String toString() {
        return "ConstantPool{" + "cp=" + Arrays.toString(cst) + ", cpi=" + index + '}';
    }

    public void reload(ConstantWriter writer) {
        cst = new Constant[writer.getIndex()];
        int i = 1;

        List<Constant> csts = writer.getConstants();
        for (int j = 0, max = csts.size(); j < max; j++) {
            Constant c = csts.get(j);
            cst[i++] = c;
            if(c.type() == DOUBLE || c.type() == LONG)
                cst[i++] = CstDoLHolder.HOLDER;
        }
    }
}