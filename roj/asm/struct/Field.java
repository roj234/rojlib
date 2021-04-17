/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: Field.java
 */
package roj.asm.struct;

import roj.asm.constant.CstUTF;
import roj.asm.struct.attr.*;
import roj.asm.struct.simple.FieldSimple;
import roj.asm.struct.simple.IConstantSerializable;
import roj.asm.util.*;
import roj.asm.util.type.ParamHelper;
import roj.asm.util.type.Signature;
import roj.asm.util.type.SignatureHelper;
import roj.asm.util.type.Type;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.util.PrimitiveIterator;

public final class Field implements IConstantSerializable {
    public Field(FlagList accesses, String name, String type) {
        this.accesses = accesses;
        this.name = name;
        this.type = ParamHelper.parseField(type);
    }

    public Field(FlagList accesses, String name, Type type) {
        this.accesses = accesses;
        this.name = name;
        this.type = type;
    }

    public Field(ConstantData data, FieldSimple field) {
        this(field.accesses, field.name.getString(), field.type.getString());

        ConstantPool pool = data.constants;
        ByteReader r = new ByteReader();

        for (Attribute attr : field.attributes) {
            r.refresh(attr.getRawData());

            String name = attr.name;

            handleAttribute(pool, r, name, r.length());

            if (!r.isFinished()) {
                System.err.println("[Warning] Attribute " + name + " has " + (r.length() - r.index) + " bytes not read correctly!");
            }
        }
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

    private void handleAttribute(ConstantPool pool, ByteReader r, String name, int length) {
        Attribute attr;
        switch (name) {
            case "RuntimeVisibleTypeAnnotations":
            case "RuntimeInvisibleTypeAnnotations":
                attr = new AttrTypeAnnotation(name, r, pool, this);
                break;
            // 字段泛型签名
            case "Signature":
                signature = SignatureHelper.parse(((CstUTF) pool.get(r)).getString());
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
                attr = new AttrUnknown(name, r.readBytesDelegated(length));
        }
        attributes.add(attr);
    }

    public String name;
    public Type type;
    public FlagList accesses;
    public AttributeList attributes = new AttributeList();
    public Signature signature;

    public void toByteArray(ConstantWriter pool, ByteWriter w) {
        w.writeShort(accesses.flag).writeShort(pool.getUtfId(name)).writeShort(pool.getUtfId(ParamHelper.getField(type)));

        if (signature != null)
            attributes.add(new AttrUTF(AttrUTF.SIGNATURE, signature.toGeneric()));
        w.writeShort((short) attributes.size());
        for (Attribute attribute : attributes) {
            attribute.toByteArray(pool, w);
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String rawDesc() {
        return ParamHelper.getField(type);
    }

    public AttrAnnotation getAnnotations() {
        return (AttrAnnotation) attributes.getByName(AttrAnnotation.VISIBLE);
    }

    public AttrAnnotation getInvisibleAnnotations() {
        return (AttrAnnotation) attributes.getByName(AttrAnnotation.INVISIBLE);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (getAnnotations() != null)
            sb.append("    ").append(getAnnotations()).append('\n');
        if (getInvisibleAnnotations() != null)
            sb.append("    ").append(getInvisibleAnnotations()).append('\n');

        sb.append("    ");
        for (PrimitiveIterator.OfInt itr = accesses.iterator(); itr.hasNext(); ) {
            sb.append(AccessFlag.byIdField(itr.nextInt())).append(' ');
        }
        sb.append(signature != null ? signature : type).append(' ').append(name);

        AttrConstantValue constant = (AttrConstantValue) attributes.getByName("ConstantValue");
        if (constant != null) {
            sb.append(" = ").append(constant.c);
        }
        sb.append('\n');

        if (!attributes.isEmpty()) {
            for (Attribute attr : attributes) {
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