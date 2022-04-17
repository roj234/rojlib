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

import roj.asm.Parser;
import roj.asm.SharedBuf;
import roj.asm.cst.CstClass;
import roj.asm.cst.CstNameAndType;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.*;
import roj.asm.type.Signature;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.asm.util.ConstantPool;
import roj.collect.SimpleList;
import roj.util.ByteList;
import roj.util.ByteReader;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Class LOD 3
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class Clazz implements IClass {
    public Clazz() {
        this.interfaces = new SimpleList<>();
        this.methods = new SimpleList<>();
        this.fields = new SimpleList<>();
        this.accesses = AccessFlag.PUBLIC | AccessFlag.SUPER_OR_SYNC;
    }

    public Clazz(int version, int acc, String name, String parent) {
        this();
        this.version = version;
        this.accesses = (char) acc;
        this.name = name;
        this.parent = parent;
    }

    public Clazz(ConstantData data) {
        this.version = data.version;
        this.accesses = data.accesses;
        this.name = data.name;
        this.parent = data.parent;

        this.interfaces = data.interfaces();

        this.methods = new SimpleList<>(data.methods.size());
        List<? extends MethodNode> cstMethods = data.methods;
        for (int i = 0; i < cstMethods.size(); i++) {
            MoFNode node = cstMethods.get(i);
            if (node instanceof Method) {
                methods.add((Method) node);
            } else {
                methods.add(new Method(data, (MethodSimple) node));
            }
        }

        fields = new SimpleList<>(data.fields.size());
        List<? extends MoFNode> cstFields = data.fields;
        for (int i = 0; i < cstFields.size(); i++) {
            MoFNode node = cstFields.get(i);
            if (node instanceof Field) {
                fields.add((Field) node);
            } else {
                fields.add(new Field(data, (FieldSimple) node));
            }
        }

        if (data.attributes == null) return;

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

                if (r.hasRemaining()) {
                    System.err.println("[Warning] Attribute " + name + " has " + (r.length() - r.rIndex) + " bytes not " + "read correctly!");
                }
            } else {
                attributes.add(attr);
            }
        }
    }

    public int version;

    public char accesses;

    public String name, parent;

    public List<String> interfaces;
    public List<Method> methods;
    public List<Field> fields;
    private AttributeList attributes;

    public Signature signature;

    public String toString() {
        StringBuilder sb = new StringBuilder(1000);

        if (getVisibleAnnotations() != null)
            sb.append(getVisibleAnnotations());
        if (getInvisibleAnnotations() != null)
            sb.append(getInvisibleAnnotations());

        AccessFlag.toString(accesses, AccessFlag.TS_CLASS, sb);
        sb.append(signature == null ? this.name.substring(this.name.lastIndexOf('/') + 1) : signature);
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
        if (attributes != null) {
            for (int j = 0; j < attributes.size(); j++) {
                Attribute i = attributes.get(j);
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
        return new ByteList(Parser.toByteArray(this));
    }

    public ByteList getBytes(ByteList w) {
        ConstantPool cw = SharedBuf.alloc().constWriter();

        w.putShort(accesses)
         .putShort(cw.getClassId(name))
         .putShort(parent == null ? 0 : cw.getClassId(parent))
         .putShort(interfaces.size());

        for (int i = 0; i < interfaces.size(); i++) {
            w.putShort(cw.getClassId(interfaces.get(i)));
        }

        w.putShort(fields.size());
        for (int i = 0, l = fields.size(); i < l; i++) {
            fields.get(i).toByteArray(cw, w);
        }

        w.putShort(methods.size());
        for (int i = 0, l = methods.size(); i < l; i++) {
            methods.get(i).toByteArray(cw, w);
        }

        if (attributes == null && signature == null) {
            w.putShort(0);
        } else {
            if (signature != null)
                attributes().add(new AttrUTF(AttrUTF.SIGNATURE, signature.toGeneric()));

            w.putShort(attributes.size());
            for (int i = 0, l = attributes.size(); i < l; i++) {
                attributes.get(i).toByteArray(cw, w);
            }
        }

        int pos = w.wIndex();
        byte[] tmp = w.list;
        int cpl = cw.byteLength() + 10;
        if (tmp.length < pos + cpl) {
            tmp = new byte[pos + cpl];
        }
        System.arraycopy(w.list, 0, tmp, cpl, pos);
        w.list = tmp;

        w.wIndex(0);
        cw.write(w.putInt(0xCAFEBABE).putShort(version).putShort(version >> 16));
        assert w.wIndex() == cpl;
        w.wIndex(pos + cpl);

        cw.clear();

        return w;
    }

    public void initAttributes(ConstantPool pool, ByteReader r) {
        int len = r.readUnsignedShort();
        if (len == 0) return;
        attributes = new AttributeList(len);
        for (int i = 0; i < len; i++) {
            String name = ((CstUTF) pool.get(r)).getString();
            final int length = r.readInt();

            final int end = r.rIndex + length;

            handleAttribute(pool, r, name, length);

            if (r.rIndex != end) {
                System.err.println("[Warning] Attribute " + name + " has " + (end - r.rIndex) + " bytes not read correctly!");
                r.rIndex = end;
            }
        }
    }

    @SuppressWarnings("fallthrough")
    private void handleAttribute(ConstantPool pool, ByteReader r, String name, int length) {
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
                attr = new AttrUnknown(name, r.slice(length));
        }
        attributes.add(attr);
    }

    public AttrInnerClasses getInnerClasses() {
        return attributes == null ? null : (AttrInnerClasses) attributes.getByName(AttrInnerClasses.NAME);
    }

    public AttrAnnotation getVisibleAnnotations() {
        return attributes == null ? null : (AttrAnnotation) attributes.getByName(AttrAnnotation.VISIBLE);
    }

    public AttrAnnotation getInvisibleAnnotations() {
        return attributes == null ? null : (AttrAnnotation) attributes.getByName(AttrAnnotation.INVISIBLE);
    }

    public AttrSourceFile getSource() {
        return attributes == null ? null : (AttrSourceFile) attributes.getByName(AttrSourceFile.NAME);
    }

    @Override
    public Attribute attrByName(String name) {
        return attributes == null ? null : (Attribute) attributes.getByName(name);
    }

    @Override
    public AttributeList attributes() {
        return attributes == null ? attributes = new AttributeList() : attributes;
    }

    @Nullable
    @Override
    public AttributeList attributesNullable() {
        return attributes;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<String> interfaces() {
        return interfaces;
    }

    @Override
    public String parent() {
        return parent;
    }

    @Override
    public char accessFlag() {
        return accesses;
    }

    @Override
    public void accessFlag(int flag) {
        this.accesses = (char) flag;
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
    public int type() {
        return Parser.CTYPE_LOD_3;
    }
}