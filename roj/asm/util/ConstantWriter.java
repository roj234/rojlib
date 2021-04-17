/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantWriter.java
 */
package roj.asm.util;

import roj.asm.constant.*;
import roj.collect.FindSet;
import roj.collect.MyHashSet;
import roj.text.TextUtil;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static roj.asm.constant.CstType.*;

public class ConstantWriter {
    private final List<Constant> constants;
    private final FindSet<Constant> refMap;

    private final CstUTF fp0 = new CstUTF();
    private final CstClass fp1 = new CstClass();
    private final CstRefField fp2 = new CstRefField();
    private final CstRefMethod fp3 = new CstRefMethod();
    private final CstRefItf fp4 = new CstRefItf();
    private final CstNameAndType fp5 = new CstNameAndType();

    private int index = 1;
    private boolean dirty;

    public ConstantWriter() {
        this.constants = new ArrayList<>(80);
        this.refMap = new MyHashSet<>(80);

        dirty = true;
    }

    public ConstantWriter(ConstantPool pool) {
        final Constant[] cst = pool.array();

        this.constants = new ArrayList<>(cst.length);
        this.refMap = new MyHashSet<>(cst.length);

        for (Constant c : cst) {
            if (c == null)
                continue;
            refMap.add(c);
            this.constants.add(c);
        }

        this.index = pool.index;

        dirty = false;
    }

    void addConstant(Constant c) {
        refMap.add(c);
        constants.add(c);
        c.setIndex(index++);

        switch (c.type) {
            case LONG:
            case DOUBLE:
                index++;
        }
        dirty = true;
    }

    public CstUTF getUtf(String msg) {
        fp0.setString(msg);
        CstUTF utf = (CstUTF) refMap.find(fp0);

        if (utf == fp0) {
            utf = new CstUTF(msg);
            addConstant(utf);
        }
        if (!utf.getString().equals(msg)) {
            System.out.println("Unfit utf id!!! How can it be??? G: '" + utf.getString() + "' R: '" + msg + '\'');
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
        switch (c.type) {
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
                throw new IllegalArgumentException("Unsupported type: " + c.type);
        }

        Constant found = refMap.find(c);
        if (found == c) {
            addConstant(c);
            return c;
        } else {
            return (T) found;
        }
    }

    public List<Constant> getConstants() {
        return Collections.unmodifiableList(constants);
    }

    public void writeTo(ByteWriter writer) {
        final List<Constant> csts = this.constants;
        for (int i = 0; i < csts.size(); i++) {
            Constant cst = csts.get(i);
            cst.write(writer);
        }
    }

    //public void reIndex() {
    //reIndex(null, true);
    //}

    /*private void reIndex(ByteWriter writer, boolean set) {
        // as ConstantPool merged index, set index to the lowest one ?
        // fixed: not duplicate now

        int i = 1;
        for (ListIterator<Constant> iterator = constants.listIterator(); iterator.hasNext(); ) {
            Constant cst = iterator.next();
            if (cst.getIndex() != i) {
                System.err.println("Index mismatch! " + i + "  " + cst.getIndex());
                cst = copy(cst);
                cst.setIndex(i);
                iterator.set(cst);
            }
            if (!set)
                cst.write(writer);
            switch (cst.type) {
                case LONG:
                case DOUBLE:
                    i++;
            }
            i++;
        }
        index = i;
    }*/

    private static Constant copy(Constant c) {
        switch (c.type) {
            case METHOD_TYPE:
            case STRING:
            case CLASS: {
                CstRefUTF cz = (CstRefUTF) c;
                CstRefUTF result = (CstRefUTF) getClass(c);
                result.setValue(cz.getValue());
                return result;
            }
            case NAME_AND_TYPE: {
                CstNameAndType cz = (CstNameAndType) c;
                CstNameAndType result = new CstNameAndType(0, 0);
                result.setName(cz.getName());
                result.setType(cz.getType());
                return result;
            }
            case UTF: {
                return new CstUTF(((CstUTF) c).getString());
            }
            case DOUBLE:
            case INT:
            case LONG:
            case FLOAT: {
                return getClass(c);
            }

            case PACKAGE:
            case MODULE:
            case FIELD:
            case INTERFACE:
            case METHOD: {
                CstRef cz = (CstRef) c;
                CstRef result = (CstRef) getClass(c);
                result.setClazz(cz.getClazz());
                result.desc(cz.desc());
                return result;
            }

            case DYNAMIC:
            case INVOKE_DYNAMIC: {
                CstDynamic cz = (CstDynamic) c;
                CstDynamic dyn = new CstDynamic(c.type == INVOKE_DYNAMIC, cz.bootstrapTableIndex, 0);
                dyn.setDesc(cz.getDesc());
                return dyn;
            }

            case METHOD_HANDLE: {
                CstMethodHandle cz = (CstMethodHandle) c;
                CstMethodHandle result = new CstMethodHandle(cz.kind, 0);
                result.setRef(cz.getRef());
                return result;
            }
        }
        throw new IllegalArgumentException();
    }

    private static Constant getClass(Constant c) {
        switch (c.type) {
            case STRING:
                return new CstString(0);
            case METHOD_TYPE:
                return new CstMethodType(0);
            case CLASS:
                return new CstClass(0);
            case DOUBLE:
                return new CstDouble(((CstDouble) c).value);
            case INT:
                return new CstInt(((CstInt) c).value);
            case LONG:
                return new CstLong(((CstLong) c).value);
            case FLOAT:
                return new CstFloat(((CstFloat) c).value);
            case FIELD:
                return new CstRefField(0, 0);
            case METHOD:
                return new CstRefMethod(0, 0);
            case INTERFACE:
                return new CstRefItf(0, 0);
        }
        throw new RuntimeException();
    }

    @Override
    public String toString() {
        return "ConstantWriter{" + "constants=" + TextUtil.prettyPrint(constants) + ", index=" + index + '}';
    }

    public int getIndex() {
        return index;
    }

    public boolean dirty() {
        return dirty;
    }
}