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
import roj.collect.SimpleList;
import roj.concurrent.OperationDone;
import roj.text.TextUtil;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.Idx;

import javax.annotation.Nonnull;
import java.io.UTFDataFormatException;
import java.util.List;
import java.util.function.Consumer;

import static roj.asm.cst.CstType.*;

/**
 * @author Roj234
 * @version 1.3
 * @since 2021/5/29 17:16
 */
public class ConstantPool {
    static final boolean LAZY_INIT_REF_MAP = true;

    private Constant[] cst;
    private SimpleList<Constant> constants;
    private MyHashSet<Constant>  refMap;
    int index;

    public ConstantPool() {
        this.constants = new SimpleList<>(64);
        this.refMap = new MyHashSet<>(64);
        this.index = 1;
    }

    public ConstantPool(int len) {
        this.cst = new Constant[(this.index = len) - 1];
        this.constants = new SimpleList<>();
        this.constants.setRawArray(cst);
        this.constants.i_setSize(len - 1);
        this.refMap = new MyHashSet<>(cst.length);
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
                String error;
                try {
                    error = getReferTo(c).toString();
                } catch (Throwable e1) {
                    error = e1.toString();
                }
                throw new IllegalArgumentException(c + " is referencing an invalid constant " + error);
            }
        }

        if (!LAZY_INIT_REF_MAP)
            initRefMap();
        this.cst = null;
    }

    private Constant getReferTo(Constant c) {
        switch (c.type()) {
            case METHOD_TYPE:
            case STRING:
            case CLASS: {
                CstRefUTF cz = (CstRefUTF) c;
                return cst[cz.getValueIndex() - 1];
            }
            case NAME_AND_TYPE: {
                CstNameAndType cz = (CstNameAndType) c;
                Constant cst = this.cst[cz.getNameIndex() - 1];
                if (!(cst instanceof CstUTF)) {
                    return cst;
                }
                return this.cst[cz.getTypeIndex() - 1];
            }
            case FIELD:
            case METHOD:
            case INTERFACE: {
                CstRefField cz = (CstRefField) c;
                Constant cst = this.cst[cz.getClassIndex() - 1];
                if (!(cst instanceof CstClass)) {
                    return cst;
                }
                return this.cst[cz.getDescIndex() - 1];
            }
            case INVOKE_DYNAMIC: {
                CstDynamic cz = (CstDynamic) c;
                return cst[cz.getDescIndex() - 1];
            }
            case METHOD_HANDLE: {
                CstMethodHandle cz = (CstMethodHandle) c;
                return cst[cz.getRefIndex() - 1];
            }
        }
        return null;
    }

    static Constant readConstant(ByteReader r) {
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
        return constants;
    }

    @Nonnull
    public Constant array(int i) {
        // noinspection all
        return i == 0 ? null : constants.get(i - 1);
    }

    @Nonnull
    public Constant get(ByteReader r) {
        int id = r.readUnsignedShort();
        // noinspection all
        return id == 0 ? null : constants.get(id - 1);
    }

    public String getName(ByteReader r) {
        int id = r.readUnsignedShort() - 1;

        return id < 0 ? null : ((CstClass) constants.get(id)).getValue().getString();
    }

    // threadlocal needed?
    private final CstUTF fp0 = new CstUTF();
    private final CstClass fp1 = new CstClass();
    private final CstRefField fp2 = new CstRefField();
    private final CstRefMethod fp3 = new CstRefMethod();
    private final CstRefItf fp4 = new CstRefItf();
    private final CstNameAndType fp5 = new CstNameAndType();

    private void initRefMap() {
        if (!refMap.isEmpty() || !(constants.getRawArray() instanceof Constant[])) return;
        Constant[] cst = (Constant[]) constants.getRawArray();
        for (int i = 1; i < cst.length; i++) {
            Constant c = cst[i];
            if (c == CstTop.TOP) continue;
            if(c != (c = refMap.intern(c))) {
                cst[i].setIndex(c.getIndex());
            }
        }
    }

    public void setUTFValue(CstUTF utf, String value) {
        initRefMap();
        this.refMap.remove(utf);
        utf.setString(value);
        this.refMap.add(utf);
    }

    private void addConstant(Constant c) {
        c.setIndex(index++);
        refMap.add(c);
        constants.add(c);

        switch (c.type()) {
            case LONG:
            case DOUBLE:
                index++;
        }

        if (listener != null)
            listener.accept(c);
    }

    public CstUTF getUtf(String msg) {
        initRefMap();
        fp0.setString(msg);
        CstUTF utf = (CstUTF) refMap.find(fp0);

        if (utf == fp0) {
            utf = new CstUTF(msg);
            addConstant(utf);
        } else if (!utf.getString().equals(msg)) {
            throw new IllegalStateException("Unfit utf id!!! G: '" + utf.getString() + "' E: '" + msg + '\'');
        }

        return utf;
    }

    public int getUtfId(String msg) {
        return getUtf(msg).getIndex();
    }

    public CstNameAndType getDesc(String name, String type) {
        CstUTF uName = getUtf(name);
        CstUTF uType = getUtf(type);

        fp5.setName(uName);
        fp5.setType(uType);
        CstNameAndType nat = (CstNameAndType) refMap.find(fp5);
        if (nat == fp5) {
            nat = new CstNameAndType();
            nat.setName(uName);
            nat.setType(uType);
            addConstant(nat);
        }

        return nat;
    }

    public int getDescId(String name, String desc) {
        return getDesc(name, desc).getIndex();
    }

    public CstClass getClazz(String className) {
        CstUTF name = getUtf(className);

        fp1.setValue(name);

        CstClass found = (CstClass) refMap.find(fp1);

        if (found == fp1) {
            found = new CstClass();
            found.setValue(name);
            addConstant(found);
        }

        return found;
    }

    public int getClassId(String className) {
        return getClazz(className).getIndex();
    }

    public CstRefMethod getMethodRef(String className, String name, String desc) {
        CstClass clazz = getClazz(className);
        CstNameAndType nat = getDesc(name, desc);

        fp3.setClazz(clazz);
        fp3.desc(nat);

        CstRefMethod found = (CstRefMethod) refMap.find(fp3);

        if (fp3 == found) {
            found = new CstRefMethod();
            found.setClazz(clazz);
            found.desc(nat);
            addConstant(found);
        }

        return found;
    }

    public int getMethodRefId(String className, String name, String desc) {
        return getMethodRef(className, name, desc).getIndex();
    }

    public CstRefField getFieldRef(String className, String name, String desc) {
        CstClass clazz = getClazz(className);
        CstNameAndType nat = getDesc(name, desc);

        fp2.setClazz(clazz);
        fp2.desc(nat);

        CstRefField found = (CstRefField) refMap.find(fp2);

        if (fp2 == found) {
            found = new CstRefField();
            found.setClazz(clazz);
            found.desc(nat);
            addConstant(found);
        }

        return found;
    }

    public int getFieldRefId(String className, String name, String desc) {
        return getFieldRef(className, name, desc).getIndex();
    }

    public CstRefItf getItfRef(String className, String name, String desc) {
        CstClass clazz = getClazz(className);
        CstNameAndType nat = getDesc(name, desc);

        fp4.setClazz(clazz);
        fp4.desc(nat);

        CstRefItf found = (CstRefItf) refMap.find(fp4);

        if (fp4 == found) {
            found = new CstRefItf();
            found.setClazz(clazz);
            found.desc(nat);
            addConstant(found);
        }

        return found;
    }

    public int getItfRefId(String className, String name, String desc) {
        return getItfRef(className, name, desc).getIndex();
    }

    private CstRef getRefByType(String className, String name, String desc, byte type) {
        switch (type) {
            case FIELD:
                return getFieldRef(className, name, desc);
            case METHOD:
                return getMethodRef(className, name, desc);
            case INTERFACE:
                return getItfRef(className, name, desc);
        }
        throw new IllegalStateException("Illegal type " + type);
    }

    public CstMethodHandle getMethodHandle(String className, String name, String desc, byte kind, byte type) {
        CstRef ref = getRefByType(className, name, desc, type);

        CstMethodHandle handle = new CstMethodHandle(kind, -1);
        handle.setRef(ref);

        CstMethodHandle found = (CstMethodHandle) refMap.find(handle);

        if (found == handle) {
            addConstant(handle);
        }
        return found;
    }

    public int getMethodHandleId(String className, String name, String desc, byte kind, byte type) {
        return getMethodHandle(className, name, desc, kind, type).getIndex();
    }

    public CstDynamic getInvokeDyn(boolean isMethod, int bootstrapTableIndex, String name, String desc) {
        CstNameAndType nat = getDesc(name, desc);

        CstDynamic handle = new CstDynamic(isMethod, bootstrapTableIndex, -1);
        handle.setDesc(nat);

        CstDynamic found = (CstDynamic) refMap.find(handle);

        if (found == handle) {
            addConstant(handle);
        }
        return found;
    }

    public int getInvokeDynId(int bootstrapTableIndex, String name, String desc) {
        return getInvokeDyn(true, bootstrapTableIndex, name, desc).getIndex();
    }

    public int getDynId(int bootstrapTableIndex, String name, String desc) {
        return getInvokeDyn(false, bootstrapTableIndex, name, desc).getIndex();
    }

    public CstPackage getPackage(String className) {
        CstUTF name = getUtf(className);

        CstPackage handle = new CstPackage();
        handle.setValue(name);

        CstPackage found = (CstPackage) refMap.find(handle);

        if (found == handle) {
            found = new CstPackage();
            found.setValue(name);
            addConstant(found);
        }

        return found;
    }

    public int getPackageId(String className) {
        return getPackage(className).getIndex();
    }

    public CstModule getModule(String className) {
        CstUTF name = getUtf(className);

        CstModule handle = new CstModule();
        handle.setValue(name);

        CstModule found = (CstModule) refMap.find(handle);

        if (found == handle) {
            found = new CstModule();
            found.setValue(name);
            addConstant(found);
        }

        return found;
    }

    public int getModuleId(String className) {
        return getModule(className).getIndex();
    }

    public int getIntId(int i) {
        initRefMap();
        CstInt handle = new CstInt(i);

        CstInt found = (CstInt) refMap.find(handle);
        if (found == handle) {
            addConstant(found);
        }
        return found.getIndex();
    }

    public int getDoubleId(double i) {
        initRefMap();
        CstDouble handle = new CstDouble(i);

        CstDouble found = (CstDouble) refMap.find(handle);
        if (found == handle) {
            addConstant(found);
        }
        return found.getIndex();
    }

    public int getFloatId(float i) {
        initRefMap();
        CstFloat handle = new CstFloat(i);

        CstFloat found = (CstFloat) refMap.find(handle);
        if (found == handle) {
            addConstant(found);
        }
        return found.getIndex();
    }

    public int getLongId(long i) {
        initRefMap();
        CstLong handle = new CstLong(i);

        CstLong found = (CstLong) refMap.find(handle);
        if (found == handle) {
            addConstant(found);
        }
        return found.getIndex();
    }

    @SuppressWarnings("unchecked")
    public <T extends Constant> T reset(T c) {
        initRefMap();
        if (c == null)
            throw new NullPointerException("Check null before reset()!");
        switch (c.type()) {
            case DYNAMIC:
            case INVOKE_DYNAMIC: {
                CstDynamic dyn = (CstDynamic) c;
                dyn.setDesc(reset(dyn.getDesc()));
            }
            break;
            case STRING:
            case CLASS:
            case METHOD_TYPE: {
                CstRefUTF ref = (CstRefUTF) c;
                ref.setValue(reset(ref.getValue()));
            }
            break;
            case METHOD_HANDLE: {
                CstMethodHandle ref = ((CstMethodHandle) c);
                ref.setRef(reset(ref.getRef()));
            }
            break;
            case METHOD:
            case INTERFACE:
            case FIELD: {
                CstRef ref = (CstRef) c;
                ref.setClazz(reset(ref.getClazz()));
                ref.desc(reset(ref.desc()));
            }
            break;
            case NAME_AND_TYPE: {
                CstNameAndType nat = (CstNameAndType) c;
                nat.setName(reset(nat.getName()));
                nat.setType(reset(nat.getType()));
            }
            break;
            case INT:
            case DOUBLE:
            case FLOAT:
            case LONG:
            case UTF:
                // No need to do anything, just append it
                break;
            default:
                throw new IllegalArgumentException("Unsupported type: " + c.type());
        }

        if (!refMap.contains(c)) {
            addConstant(c);
            return c;
        } else {
            return (T) refMap.find(c);
        }
    }

    public void init(ConstantPool pool) {
        this.constants = pool.constants;
        this.cst = pool.cst;
        this.refMap = pool.refMap;
        this.index = pool.index;

        if (listener != null)
            listener.accept(null);
    }

    public List<Constant> getConstants() {
        return constants;
    }

    public void write(ByteWriter w) {
        w.writeShort(index);
        List<Constant> csts = this.constants;
        for (int i = 0; i < csts.size(); i++) {
            csts.get(i).write(w);
        }
    }

    @Override
    public String toString() {
        return "ConstantPool{" + "constants[" + index + "]=" + TextUtil.prettyPrint(constants) + '}';
    }

    public void clear() {
        this.constants.clear();
        this.refMap.clear();
        this.index = 1;
        if (listener != null)
            listener.accept(null);
    }

    Consumer<Constant> listener;
    public void setAddListener(Consumer<Constant> listener) {
        this.listener = listener;
    }
}