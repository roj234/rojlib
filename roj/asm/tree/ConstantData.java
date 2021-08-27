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

package roj.asm.tree;

import roj.asm.cst.CstClass;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.simple.FieldSimple;
import roj.asm.tree.simple.MethodSimple;
import roj.asm.tree.simple.MoFNode;
import roj.asm.util.*;
import roj.collect.MyHashSet;
import roj.util.ByteList;
import roj.util.ByteWriter;
import roj.util.Helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PrimitiveIterator;

import static roj.asm.util.AccessFlag.*;

/**
 * Class LOD 2
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/30 19:59
 */
public class ConstantData implements IClass {
    public int version;

    public final FlagList accesses;

    public final CstClass nameCst, parentCst;

    public final String name, parent;

    public ConstantPool cp;
    public ConstantWriter writer;

    public final List<MethodSimple> methods = new ArrayList<>();
    public final List<FieldSimple> fields = new ArrayList<>();
    public final List<CstClass> interfaces = new ArrayList<>();
    public final AttributeList attributes = new AttributeList();

    public int exceptedBufferLength;

    static final boolean NOVERIFY = System.getProperty("roj.asm.cst.NoVerify") != null;

    public void verify() {
        if(NOVERIFY) return;

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
                case ANNOTATION:
                case SUPER_OR_SYNC:
                case SYNTHETIC:
                case STRICTFP:
                case VOLATILE_OR_BRIDGE:
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported access flag " + flag);
            }
        }

        int v = accesses.flag & (ANNOTATION | INTERFACE);
        if(v == ANNOTATION)
            throw new IllegalArgumentException("Not valid @interface " + accesses);

        if (permDesc > 1) {
            throw new IllegalArgumentException("ACCPermission too much " + accesses);
        }

        if (typeDesc > 1) {
            throw new IllegalArgumentException("ACCType too much " + accesses);
        }

        MyHashSet<String> descs = new MyHashSet<>();
        for (int j = 0; j < methods.size(); j++) {
            MoFNode method = methods.get(j);
            if (!descs.add(method.name() + '|' + method.rawDesc())) {
                throw new IllegalArgumentException("Duplicate method " + method.name() + method.rawDesc());
            }
        }
        descs.clear();

        for (int j = 0; j < fields.size(); j++) {
            MoFNode field = fields.get(j);
            if (!descs.add(field.name() + '|' + field.rawDesc())) {
                throw new IllegalArgumentException("Duplicate field " + field.name() + field.rawDesc());
            }
        }
    }

    public ConstantData(int version, ConstantPool cp, int exceptedBufferLength, int accesses, int nameIndex, int superNameIndex) {
        this.cp = cp;
        this.version = version;
        this.accesses = AccessFlag.of((short) accesses);
        this.exceptedBufferLength = exceptedBufferLength;
        this.writer = new ConstantWriter(cp);
        this.nameCst = ((CstClass) cp.array(nameIndex));
        this.name = nameCst.getValue().getString();
        if (superNameIndex == 0) {
            this.parentCst = null;
            this.parent = null;
        } else {
            this.parentCst = ((CstClass) cp.array(superNameIndex));
            this.parent = parentCst.getValue().getString();
        }
    }

    public Attribute attrByName(String name) {
        return (Attribute) attributes.getByName(name);
    }

    public void addAttribute(Attribute attr) {
        this.attributes.putByName(attr);
    }

    public ByteList getBytes() {
        return getBytes(new ByteList(exceptedBufferLength), new ByteList());
    }

    public ByteList getBytes(ByteList poolBuffer, ByteList mainBuffer) {
        poolBuffer.clear();
        mainBuffer.clear();

        ConstantWriter writer = this.writer;

        ByteWriter w = new ByteWriter(poolBuffer);

        w.writeShort(accesses.flag).writeShort(writer.reset(nameCst).getIndex()).writeShort(parentCst == null ? 0 : writer.reset(parentCst).getIndex()).writeShort(interfaces.size());
        for (int i = 0; i < interfaces.size(); i++) {
            CstClass it = interfaces.get(i);
            w.writeShort(writer.reset(it).getIndex());
        }

        w.writeShort(fields.size());
        for (int i = 0, l = fields.size(); i < l; i++) {
            ((MoFNode) fields.get(i)).toByteArray(writer, w);
        }

        w.writeShort(methods.size());
        for (int i = 0, l = methods.size(); i < l; i++) {
            ((MoFNode) methods.get(i)).toByteArray(writer, w);
        }

        w.writeShort(attributes.size());
        for (int i = 0, l = attributes.size(); i < l; i++) {
            attributes.get(i).toByteArray(writer, w);
        }

        mainBuffer.ensureCapacity(poolBuffer.pos() + 10);
        ByteWriter _gl = new ByteWriter(mainBuffer).writeInt(0xcafebabe).writeShort(version).writeShort(version >>> 16).writeShort(writer.getIndex());
        writer.write(_gl);
        _gl.writeBytes(w);

        return mainBuffer;
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

    public int getMethodByName(String key) {
        for (int i = 0; i < methods.size(); i++) {
            MoFNode ms = methods.get(i);
            if (ms.name().equals(key)) return i;
        }
        return -1;
    }

    public int getFieldByName(String key) {
        for (int i = 0; i < fields.size(); i++) {
            MoFNode fs = fields.get(i);
            if (fs.name().equals(key)) return i;
        }
        return -1;
    }

    @Override
    public String className() {
        return nameCst.getValue().getString();
    }

    @Override
    public void className(String n) {
        nameCst.getValue().setString(n);
    }

    @Override
    public String parentName() {
        return parentCst == null ? null : parentCst.getValue().getString();
    }

    @Override
    public void parentName(String n) {
        if(parentCst == null)
            throw new UnsupportedOperationException();
        parentCst.getValue().setString(n);
    }

    @Override
    public FlagList accessFlag() {
        return accesses;
    }

    @Override
    public List<String> interfaces() {
        ArrayList<Object> list = new ArrayList<>(interfaces);
        for (int i = 0; i < list.size(); i++) {
            list.set(i, ((CstClass)list.get(i)).getValue().getString());
        }
        return Helpers.cast(Collections.unmodifiableList(list));
    }

    @Override
    public List<? extends MoFNode> methods() {
        return methods;
    }

    @Override
    public List<? extends MoFNode> fields() {
        return fields;
    }

    @Override
    public AttributeList attributes() {
        return attributes;
    }

    @Override
    public byte type() {
        return 1;
    }
}