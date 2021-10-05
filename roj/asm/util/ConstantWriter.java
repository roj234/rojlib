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
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.text.TextUtil;
import roj.util.ByteWriter;

import java.util.List;

import static roj.asm.cst.CstType.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public class ConstantWriter {
    private       SimpleList<Constant> constants;
    private final MyHashSet<Constant>  refMap;

    // threadlocal needed?
    private final CstUTF fp0 = new CstUTF();
    private final CstClass fp1 = new CstClass();
    private final CstRefField fp2 = new CstRefField();
    private final CstRefMethod fp3 = new CstRefMethod();
    private final CstRefItf fp4 = new CstRefItf();
    private final CstNameAndType fp5 = new CstNameAndType();

    private int index = 1;

    public ConstantWriter() {
        this.constants = new SimpleList<>(80);
        this.refMap = new MyHashSet<>(80);
    }

    public ConstantWriter(ConstantPool pool) {
        final Constant[] cst = pool.cst;

        this.constants = pool.cstList;
        this.refMap = new MyHashSet<>(cst.length);
        this.index = pool.index;

        for (int i = 1; i < cst.length; i++) {
            Constant c = cst[i];
            if (c == CstTop.TOP) continue;
            if(c != (c = refMap.intern(c))) {
                cst[i].setIndex(c.getIndex());
            }
        }
    }

    private void addConstant(Constant c) {
        refMap.add(c);
        constants.add(c);
        c.setIndex(index++);

        switch (c.type()) {
            case LONG:
            case DOUBLE:
                index++;
        }
    }

    public CstUTF getUtf(String msg) {
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
        CstInt handle = new CstInt(i);

        CstInt found = (CstInt) refMap.find(handle);
        if (found == handle) {
            addConstant(found);
        }
        return found.getIndex();
    }

    public int getDoubleId(double i) {
        CstDouble handle = new CstDouble(i);

        CstDouble found = (CstDouble) refMap.find(handle);
        if (found == handle) {
            addConstant(found);
        }
        return found.getIndex();
    }

    public int getFloatId(float i) {
        CstFloat handle = new CstFloat(i);

        CstFloat found = (CstFloat) refMap.find(handle);
        if (found == handle) {
            addConstant(found);
        }
        return found.getIndex();
    }

    public int getLongId(long i) {
        CstLong handle = new CstLong(i);

        CstLong found = (CstLong) refMap.find(handle);
        if (found == handle) {
            addConstant(found);
        }
        return found.getIndex();
    }

    @SuppressWarnings("unchecked")
    public <T extends Constant> T reset(T c) {
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
        if (this.constants.getRawArray().length <= pool.cstList.getRawArray().length)
            this.constants = pool.cstList;
        else {
            this.constants.clear();
            this.constants.addAll(pool.cst);
        }
        this.refMap.clear();
        this.refMap.ensureCapacity(this.index = pool.index);

        final Constant[] cst = pool.cst;
        for (int i = 1; i < cst.length; i++) {
            Constant c = cst[i];
            if (c == CstTop.TOP) continue;
            if(c != (c = refMap.intern(c))) {
                cst[i].setIndex(c.getIndex());
            }
        }
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
        return "ConstantWriter{" + "constants[" + index + "]=" + TextUtil.prettyPrint(constants) + '}';
    }

    public void clear() {
        this.constants.clear();
        this.refMap.clear();
        this.index = 1;
    }
}