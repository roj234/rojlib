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
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.*;
import roj.asm.type.ParamHelper;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.asm.util.ConstantPool;
import roj.util.ByteList;
import roj.util.ByteReader;

import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class Field implements FieldNode {
    public Field(int accesses, String name, String type) {
        this.accesses = (char) accesses;
        this.name = name;
        this.type = ParamHelper.parseField(type);
    }

    public Field(int accesses, String name, Type type) {
        this.accesses = (char) accesses;
        this.name = name;
        this.type = type;
    }

    public Field(ConstantData data, FieldSimple field) {
        this(field.accesses, field.name.getString(), field.type.getString());

        ConstantPool pool = data.cp;
        ByteReader r = new ByteReader();

        AttributeList al = field.attributes();
        if (al == null || al.isEmpty()) return;
        attributes = new AttributeList(al.size());
        for (int i = 0; i < al.size(); i++) {
            Attribute attr = al.get(i);
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

    public Field(java.lang.reflect.Field f) {
        this.name = f.getName();
        this.accesses = (char) f.getModifiers();
        this.type = ParamHelper.parseField(ParamHelper.class2asm(f.getType()));
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

    private void handleAttribute(ConstantPool pool, ByteReader r, String name, int length) {
        Attribute attr;
        switch (name) {
            case "RuntimeVisibleTypeAnnotations":
            case "RuntimeInvisibleTypeAnnotations":
                attr = new AttrTypeAnnotation(name, r, pool, this);
                break;
            // 字段泛型签名
            case "Signature":
                signature = Signature.parse(((CstUTF) pool.get(r)).getString());
                return;
            // 字段注解
            case "RuntimeVisibleAnnotations":
            case "RuntimeInvisibleAnnotations":
                attr = new AttrAnnotation(name, r, pool);
                break;
            // static final型‘常量’的默认值
            case "ConstantValue":
                attr = new AttrConstantValue(pool.get(r));
                break;
            // 由编译器生成
            case "Synthetic":
                // 弃用
            case "Deprecated":
            default:
                attr = new AttrUnknown(name, r.slice(length));
        }
        attributes.add(attr);
    }

    public String name;
    public Type type;
    public char accesses;

    private AttributeList attributes;
    public Signature signature;

    public void toByteArray(ConstantPool pool, ByteList w) {
        w.putShort(accesses).putShort(pool.getUtfId(name)).putShort(pool.getUtfId(ParamHelper.getField(type)));

        if (signature != null) attributes().add(new AttrUTF(AttrUTF.SIGNATURE, signature.toGeneric()));

        AttributeList attr = attributes;
        if (attr == null) {
            w.putShort(0);
            return;
        }

        w.putShort((short) attr.size());
        for (int i = 0; i < attr.size(); i++) {
            attr.get(i).toByteArray(pool, w);
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void name(String name) {
        this.name = name;
    }

    @Override
    public String rawDesc() {
        return ParamHelper.getField(type);
    }

    @Override
    public void rawDesc(String rawDesc) {
        type = ParamHelper.parseField(rawDesc);
    }

    @Override
    public int type() {
        return Parser.FTYPE_FULL;
    }

    @Override
    public void accessFlag(int flag) {
        this.accesses = (char) flag;
    }

    @Override
    public char accessFlag() {
        return accesses;
    }

    @Override
    public Type fieldType() {
        return type;
    }

    @Override
    public void fieldType(Type type) {
        this.type = type;
    }

    FieldSimple i_downgrade(ConstantPool cw) {
        FieldSimple f = new FieldSimple(accesses, cw.getUtf(name), cw.getUtf(rawDesc()));
        if (attributes == null) {
            if (signature == null) return f;
            attributes = new AttributeList();
        }

        AttributeList fAttr = f.attributes();
        fAttr.ensureCapacity(attributes.size() + (signature == null ? 0 : 1));
        ByteList w = SharedBuf.i_get();
        for (int i = 0; i < attributes.size(); i++) {
            fAttr.add(AttrUnknown.downgrade(cw, w, this.attributes.get(i)));
        }
        if (signature != null) {
            w.clear();
            w.putShort(cw.getUtfId(signature.toGeneric()));
            fAttr.add(new AttrUnknown(AttrUTF.SIGNATURE, new ByteList(w.toByteArray())));
        }
        return f;
    }

    @Override
    public Attribute attrByName(String name) {
        return attributes == null ? null : (Attribute) attributes.getByName(name);
    }

    public AttributeList attributes() {
        return attributes == null ? attributes = new AttributeList() : attributes;
    }

    @Nullable
    @Override
    public AttributeList attributesNullable() {
        return attributes;
    }

    public AttrAnnotation getAnnotations() {
        return attributes == null ? null : (AttrAnnotation) attributes.getByName(AttrAnnotation.VISIBLE);
    }

    public AttrAnnotation getInvisibleAnnotations() {
        return attributes == null ? null : (AttrAnnotation) attributes.getByName(AttrAnnotation.INVISIBLE);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (getAnnotations() != null)
            sb.append("    ").append(getAnnotations()).append('\n');
        if (getInvisibleAnnotations() != null)
            sb.append("    ").append(getInvisibleAnnotations()).append('\n');

        sb.append("    ");
        AccessFlag.toString(accesses, AccessFlag.TS_FIELD, sb)
                  .append(signature != null ? signature : type).append(' ').append(name);

        AttrConstantValue constant = (AttrConstantValue) attributes.getByName("ConstantValue");
        if (constant != null) {
            sb.append(" = ").append(constant.c);
        }
        sb.append('\n');

        if (attributes != null && !attributes.isEmpty()) {
            for (int i = 0; i < attributes.size(); i++) {
                Attribute attr = attributes.get(i);
                switch (attr.name) {
                    case AttrAnnotation.VISIBLE:
                    case AttrAnnotation.INVISIBLE:
                    case "ConstantValue":
                        continue;
                }
                sb.append("      ").append(attr.toString()).append('\n');
            }
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}