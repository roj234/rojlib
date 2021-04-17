/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantData.java
 */
package roj.asm.struct;

import roj.asm.constant.CstClass;
import roj.asm.struct.attr.Attribute;
import roj.asm.struct.simple.FieldSimple;
import roj.asm.struct.simple.IConstantSerializable;
import roj.asm.struct.simple.MethodSimple;
import roj.asm.util.*;
import roj.util.ByteList;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PrimitiveIterator;

import static roj.asm.util.AccessFlag.*;

public class ConstantData {
    public int version;

    public final FlagList accesses;

    public final CstClass nameCst, parentCst;

    public final String name, parent;

    public final ConstantPool constants;
    public final ConstantWriter writer;

    public final List<MethodSimple> methods = new ArrayList<>();
    public final List<FieldSimple> fields = new ArrayList<>();
    public final List<CstClass> interfaces = new ArrayList<>();
    public final AttributeList attributes = new AttributeList();

    public int exceptedBufferLength;

    public void verify() {
        String tmp = nameCst.getValue().getString();
        if (tmp.contains(".") || tmp.contains("\\")) {
            throw new IllegalArgumentException("Illegal ClassName " + tmp);
        }
        if (parentCst == null) {
            if (!nameCst.getValue().getString().equals("java/lang/Object"))
                throw new IllegalArgumentException("No father found");
        } else {
            tmp = parentCst.getValue().getString();
            if (tmp.contains(".") || tmp.contains("\\")) {
                throw new IllegalArgumentException("Illegal SuperName " + tmp);
            }
        }

        int i = 0;
        for (CstClass itf : interfaces) {
            tmp = itf.getValue().getString();
            if (tmp.contains(".") || tmp.contains("\\")) {
                throw new IllegalArgumentException("Illegal Interface # " + i + ' ' + tmp);
            }
            i++;
        }

        int permDesc = 0;
        int typeDesc = 0;
        int fn = -1;

        for (PrimitiveIterator.OfInt itr = accesses.iterator(); itr.hasNext(); ) {
            int flag = itr.nextInt();
            switch (flag) {
                case PUBLIC:
                case PROTECTED:
                    permDesc++;
                    break;
                case INTERFACE:
                case ENUM:
                    typeDesc++;
                    break;
                case FINAL:
                    if (fn == 0) {
                        throw new IllegalArgumentException("Final and Abstract");
                    }
                    fn = 1;
                    break;
                case ABSTRACT:
                    if (fn == 1) {
                        throw new IllegalArgumentException("Final and Abstract");
                    }
                    fn = 0;
                    break;
                case SUPER_OR_SYNC:
                case SYNTHETIC:
                case STRICTFP:
                case VOLATILE_OR_BRIDGE:
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported access flag " + flag);
            }
        }

        if (permDesc > 1) {
            throw new IllegalArgumentException("ACCPermission too much " + accesses);
        }

        if (typeDesc > 1) {
            throw new IllegalArgumentException("ACCType too much " + accesses);
        }

        HashSet<String> descs = new HashSet<>();
        for (IConstantSerializable method : methods) {
            if (!descs.add(method.name() + '|' + method.rawDesc())) {
                throw new IllegalArgumentException("Duplicate method with same name and desc! " + method.name() + method.rawDesc());
            }
        }
        descs.clear();

        for (IConstantSerializable field : fields) {
            if (!descs.add(field.name() + '|' + field.rawDesc())) {
                throw new IllegalArgumentException("Duplicate field with same name and desc! " + field.name() + field.rawDesc());
            }
        }
    }

    public ConstantData(int version, ConstantPool constants, int exceptedBufferLength, int accesses, int nameIndex, int superNameIndex) {
        this.constants = constants;
        this.version = version;
        this.accesses = AccessFlag.parse((short) accesses);
        this.exceptedBufferLength = exceptedBufferLength;
        this.writer = new ConstantWriter(constants);
        this.nameCst = ((CstClass) constants.array(nameIndex));
        this.name = nameCst.getValue().getString();
        if (superNameIndex == 0) {
            this.parentCst = null;
            this.parent = null;
        } else {
            this.parentCst = ((CstClass) constants.array(superNameIndex));
            this.parent = parentCst.getValue().getString();
        }
    }

    public Attribute attrByName(String name) {
        return (Attribute) attributes.getByName(name);
    }

    public void addAttribute(Attribute attr) {
        this.attributes.add(attr);
    }

    public ByteList getBytes() {
        return getBytes(new ByteList(exceptedBufferLength), new ByteList());
    }

    public ByteList getBytes(ByteList poolBuffer, ByteList mainBuffer) {
        poolBuffer.clear();
        mainBuffer.clear();

        ConstantWriter writer = this.writer;

        boolean dirty = true;//writer.dirty();

        ByteWriter w;

        if (!dirty) {
            w = new ByteWriter(mainBuffer).writeInt(0xcafebabe).writeShort(version).writeShort(version >>> 16).writeShort(writer.getIndex());
            writer.writeTo(w);
        } else {
            w = new ByteWriter(poolBuffer);
        }

        w.writeShort(accesses.flag).writeShort(writer.reset(nameCst).getIndex()).writeShort(writer.reset(parentCst).getIndex()).writeShort(interfaces.size());
        for (int i = 0; i < interfaces.size(); i++) {
            CstClass it = interfaces.get(i);
            w.writeShort(writer.reset(it).getIndex());
        }

        w.writeShort(fields.size());
        for (int i = 0, l = fields.size(); i < l; i++) {
            ((IConstantSerializable) fields.get(i)).toByteArray(writer, w);
        }

        w.writeShort(methods.size());
        for (int i = 0, l = methods.size(); i < l; i++) {
            ((IConstantSerializable) methods.get(i)).toByteArray(writer, w);
        }

        w.writeShort(attributes.size());
        for (int i = 0, l = attributes.size(); i < l; i++) {
            attributes.get(i).toByteArray(writer, w);
        }

        if (dirty) {
            mainBuffer.ensureCapacity(poolBuffer.pos() + 10);
            ByteWriter _gl = new ByteWriter(mainBuffer).writeInt(0xcafebabe).writeShort(version).writeShort(version >>> 16).writeShort(writer.getIndex());
            writer.writeTo(_gl);
            _gl.writeBytes(w);

            return _gl.list;
        } else {
            return w.list;
        }
    }

    @Override
    public String toString() {
        return "ConstantData{" +
                "name='" + name + '\'' +
                ", extends='" + parent + '\'' +
                ", impls=" + interfaces +
                ", methods=" + methods +
                ", fields=" + fields +
                '}';
    }

    public IConstantSerializable getMethodByName(String key) {
        for (IConstantSerializable ms : methods) {
            if(ms.name().equals(key))
                return ms;
        }
        return null;
    }

    public IConstantSerializable getFieldByName(String key) {
        for (IConstantSerializable fs : fields) {
            if(fs.name().equals(key))
                return fs;
        }
        return null;
    }
}