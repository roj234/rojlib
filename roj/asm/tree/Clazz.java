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
import roj.asm.cst.CstNameAndType;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.*;
import roj.asm.type.Signature;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.asm.util.ConstantPool;
import roj.asm.util.FlagList;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;

/**
 * Class LOD 3
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class Clazz implements IClass {
    public Clazz() {
        this.interfaces = new ArrayList<>();
        this.methods = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.attributes = new AttributeList();
    }

    public Clazz(int version, int acc, String name, String parent) {
        this();
        this.version = version;
        this.accesses = AccessFlag.of((short) acc);
        this.name = name;
        this.parent = parent;
    }

    public Clazz(ConstantData data) {
        this.version = data.version;
        this.accesses = data.accesses;
        this.name = data.name;
        this.parent = data.parent;
        this.interfaces = new ArrayList<>(data.interfaces.size());
        for (CstClass clz : data.interfaces) {
            this.interfaces.add(clz.getValue().getString());
        }

        this.methods = new ArrayList<>(data.methods.size());
        for (MethodSimple method : data.methods) {
            this.methods.add(new Method(data, method));
        }

        this.fields = new ArrayList<>(data.fields.size());
        for (FieldSimple field : data.fields) {
            this.fields.add(new Field(data, field));
        }

        this.attributes = new AttributeList(data.attributes.size());

        ConstantPool pool = data.cp;
        ByteReader r = new ByteReader();

        AttributeList attrs = data.attributes;
        for (int i = 0; i < attrs.size(); i++) {
            Attribute attr = attrs.get(i);
            if(attr.getClass() == AttrUnknown.class) {
                r.refresh(attr.getRawData());

                String name = attr.name;

                handleAttribute(pool, r, name, r.length());

                if (!r.isFinished()) {
                    System.err.println("[Warning] Attribute " + name + " has " + (r.length() - r.index) + " bytes not " + "read correctly!");
                }
            } else {
                attributes.add(attr);
            }
        }
    }

    public int version;

    public FlagList accesses;

    public String name, parent;

    public List<String> interfaces;
    public List<Method> methods;
    public List<Field> fields;
    public AttributeList attributes;

    public Signature signature;

    public String toString() {
        StringBuilder sb = new StringBuilder(1000);

        if (getVisibleAnnotations() != null)
            sb.append(getVisibleAnnotations());
        if (getInvisibleAnnotations() != null)
            sb.append(getInvisibleAnnotations());

        for (PrimitiveIterator.OfInt itr = this.accesses.iterator(); itr.hasNext(); ) {
            sb.append(AccessFlag.byIdClass(itr.nextInt())).append(' ');
        }
        sb.append("class ").append(signature == null ? this.name.substring(this.name.lastIndexOf('/') + 1) : signature);
        if (!"java/lang/Object".equals(this.parent) && this.parent != null)
            sb.append(" extends ").append(this.parent);
        if (this.interfaces.size() > 0) {
            sb.append(" implements ");
            for (String i : this.interfaces) {
                sb.append(i.substring(i.lastIndexOf('/') + 1)).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
        }
        sb.append("\n\n");
        if (!this.fields.isEmpty()) {
            for (Field i : this.fields) {
                sb.append(i).append(";\n\n");
            }
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("\n\n");
        if (!this.methods.isEmpty()) {
            for (Method i : this.methods) {
                sb.append(i).append("\n\n");
            }
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("\n\n");
        if (!this.attributes.isEmpty()) {
            for (Attribute i : this.attributes) {
                switch (i.name) {
                    case AttrAnnotation.INVISIBLE:
                    case AttrAnnotation.VISIBLE:
                        continue;
                }
                sb.append("   ").append(i).append('\n');
            }
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    public ByteList getBytes() {
        return getBytes(new ByteList(1024), new ByteList());
    }

    public ByteList getBytes(ByteList poolBuffer, ByteList mainBuffer) {
        poolBuffer.clear();
        mainBuffer.clear();

        ConstantPool pool = new ConstantPool();

        ByteWriter w = new ByteWriter(poolBuffer)
                .writeShort(accesses.flag)
                .writeShort(pool.getClassId(name))
                .writeShort(parent == null ? 0 : pool.getClassId(parent))

                .writeShort((short) interfaces.size());

        for (int i = 0; i < interfaces.size(); i++) {
            w.writeShort(pool.getClassId(interfaces.get(i)));
        }

        w.writeShort((short) fields.size());
        for (int i = 0, l = fields.size(); i < l; i++) {
            fields.get(i).toByteArray(pool, w);
        }

        w.writeShort((short) methods.size());
        for (int i = 0, l = methods.size(); i < l; i++) {
            methods.get(i).toByteArray(pool, w);
        }

        if (signature != null)
            attributes.add(new AttrUTF(AttrUTF.SIGNATURE, signature.toGeneric()));

        w.writeShort(attributes.size());
        for (int i = 0, l = attributes.size(); i < l; i++) {
            attributes.get(i).toByteArray(pool, w);
        }

        mainBuffer.ensureCapacity(poolBuffer.pos() + 10);

        // major, minor
        ByteWriter _gl = new ByteWriter(mainBuffer).writeInt(0xcafebabe).writeShort(version).writeShort(version >>> 16);
        pool.write(_gl);
        _gl.writeBytes(w);

        return mainBuffer;
    }

    public void initAttributes(ConstantPool pool, ByteReader r) {
        int len = r.readUnsignedShort();
        for (int i = 0; i < len; i++) {
            String name = ((CstUTF) pool.get(r)).getString();
            final int length = r.readInt();

            final int end = r.index + length;

            handleAttribute(pool, r, name, length);

            if (r.index != end) {
                System.err.println("[Warning] Attribute " + name + " has " + (end - r.index) + " bytes not read correctly!");
                r.index = end;
            }
        }
    }

    @SuppressWarnings("fallthrough")
    public void handleAttribute(ConstantPool pool, ByteReader r, String name, int length) {
        Attribute attr;
        switch (name) {
            case "RuntimeVisibleTypeAnnotations":
            case "RuntimeInvisibleTypeAnnotations":
                attr = new AttrTypeAnnotation(name, r, pool, this);
                break;
            // 内部类标记
            case "InnerClasses":
                attr = new AttrInnerClasses(r, pool);
                break;
            case "Module":
                attr = new AttrModule(r, pool);
                break;
            case "ModulePackages":
                attr = new AttrModulePackages(r, pool);
                break;
            case "ModuleMainClass":
                attr = new AttrUTFRef(name, r, pool);
                break;
            case "NestHost":
                attr = new AttrUTF(name, r, pool);
                break;
            case "NestMembers":
                attr = new AttrStringList(name, r, pool, 1);
                break;
            // 类注解
            case "RuntimeInvisibleAnnotations":
            case "RuntimeVisibleAnnotations":
                attr = new AttrAnnotation(name, r, pool);
                break;
            // 原文件名
            case "SourceFile":
                attr = new AttrSourceFile(((CstUTF) pool.get(r)).getString());
                break;
            // 变类方法 (模拟弱类型)
            case "BootstrapMethods":
                attr = new AttrBootstrapMethods(r, pool);
                break;
            // A class must have an EnclosingMethod attribute if and only if it represents a local class or an anonymous class (JLS §14.3, JLS §15.9.5).
            // if and only if => 在且仅在 内部类中含有
            case "EnclosingMethod":
                attr = new AttrEnclosingMethod((CstClass) pool.get(r), (CstNameAndType) pool.get(r));
                break;
            case "Signature":
                signature = Signature.parse(((CstUTF) pool.get(r)).getString());

                return;
            case "Deprecated":
                if(length != 0)
                    throw new IllegalArgumentException("Deprecated.length must be zero");
            case "SourceDebugExtension":
            default:
                attr = new AttrUnknown(name, r.readBytesDelegated(length));
        }
        attributes.add(attr);
    }

    public AttrInnerClasses getInnerClasses() {
        return (AttrInnerClasses) attributes.getByName(AttrInnerClasses.NAME);
    }

    public AttrAnnotation getVisibleAnnotations() {
        return (AttrAnnotation) attributes.getByName(AttrAnnotation.VISIBLE);
    }

    public AttrAnnotation getInvisibleAnnotations() {
        return (AttrAnnotation) attributes.getByName(AttrAnnotation.INVISIBLE);
    }

    public AttrSourceFile getSource() {
        return (AttrSourceFile) attributes.getByName(AttrSourceFile.NAME);
    }

    @Override
    public String className() {
        return name;
    }

    @Override
    public List<String> interfaces() {
        return interfaces;
    }

    @Override
    public String parentName() {
        return parent;
    }

    @Override
    public FlagList accessFlag() {
        return accesses;
    }

    @Override
    public List<? extends MoFNode> methods() {
        return methods;
    }

    @Override
    public List<? extends MoFNode> fields() {
        return fields;
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
    public byte type() {
        return 0;
    }
}