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

import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.*;
import roj.asm.tree.simple.MethodSimple;
import roj.asm.tree.simple.MoFNode;
import roj.asm.type.*;
import roj.asm.util.*;
import roj.collect.SimpleList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import javax.annotation.Nullable;
import java.util.List;
import java.util.PrimitiveIterator;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class Method implements MethodNode, MoFNode {
    public Method(int accesses, Clazz owner, String name, String desc) {
        this(AccessFlag.of((short) accesses), owner.name, owner.parent, name, desc);
    }

    public Method(FlagList accesses, Clazz owner, String name, String desc) {
        this(accesses, owner.name, owner.parent, name, desc);
    }



    public Method(int accesses, ConstantData owner, String name, String desc) {
        this(AccessFlag.of((short) accesses), owner.name, owner.parent, name, desc);
    }

    public Method(FlagList accesses, ConstantData owner, String name, String desc) {
        this(accesses, owner.name, owner.parent, name, desc);
    }



    public Method(FlagList accesses, String owner, String parent, String name, String desc) {
        this.accesses = accesses;
        this.owner = owner;
        this.parent = parent;
        this.name = name;
        this.desc = desc;
        this.attributes = new AttributeList();
    }



    public Method(ConstantData data, MethodSimple method) {
        this.accesses = method.accesses.copy();
        this.owner = data.name;
        this.parent = data.parent;
        this.name = method.name.getString();
        this.desc = method.type.getString();
        this.attributes = new AttributeList(method.attributes.size());

        ConstantPool pool = data.cp;
        ByteReader r = new ByteReader();

        AttributeList al = method.attributes;
        for (int i = 0; i < al.size(); i++) {
            Attribute attr = al.get(i);
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
            // 方法注解
            case "RuntimeVisibleAnnotations":
            case "RuntimeInvisibleAnnotations":
                attr = new AttrAnnotation(name, r, pool);
                break;
            // 参数注解
            case "RuntimeVisibleParameterAnnotations":
            case "RuntimeInvisibleParameterAnnotations":
                attr = new AttrParamAnnotation(name, r, pool);
                break;
            // 泛型签名
            case "Signature":
                signature = Signature.parse(((CstUTF) pool.get(r)).getString());
                return;
            // 显示方法参数的标识符
            case "MethodParameters":
                attr = new AttrMethodParameters(r, pool);
                break;
            // 方法扔出的异常
            case "Exceptions":
                attr = new AttrStringList(name, r, pool, 0);
                // 好像放错了位置？
                break;
            case "AnnotationDefault":
                attr = new AttrAnnotationDefault(r, pool);
                break;
            // 代码
            case "Code":
                code = new AttrCode(this, r, pool);
                return;
            // 由编译器生成
            case "Synthetic":
                // 弃用
            case "Deprecated":
            default:
                attr = new AttrUnknown(name, r.readBytesDelegated(length));
        }
        attributes.add(attr);
    }

    public String owner, parent,
            name;
    private String desc;

    private List<Type> parameters;
    private Type returnType;

    public FlagList accesses;
    public AttributeList attributes;

    @Nullable
    public Signature signature;
    public AttrCode code;

    private void aOn(Attribute a) {
        if (a != null)
            attributes.add(a);
    }

    public void toByteArray(ConstantWriter pool, ByteWriter w) {
        w.writeShort(accesses.flag).writeShort(pool.getUtfId(name));

        if(parameters == null && desc != null) {
            w.writeShort(pool.getUtfId(desc));
        } else {
            par();

            parameters.add(returnType);
            w.writeShort(pool.getUtfId(ParamHelper.getMethod(parameters)));
            parameters.remove(parameters.size() - 1);
        }

        aOn(code);
        if (signature != null)
            attributes.add(new AttrUTF(AttrUTF.SIGNATURE, signature.toGeneric()));

        w.writeShort(attributes.size());
        for (Attribute attr : attributes) {
            attr.toByteArray(pool, w);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (getAnnotations() != null)
            sb.append("    ").append(getAnnotations()).append('\n');
        if (getInvisibleAnnotations() != null)
            sb.append("    ").append(getInvisibleAnnotations()).append('\n');

        sb.append("    ");
        for (PrimitiveIterator.OfInt itr = accesses.iterator(); itr.hasNext(); ) {
            sb.append(AccessFlag.byIdMethod(itr.nextInt())).append(' ');
        }
        if (signature != null) {
            sb.append(signature.toString().replaceFirst(" ", " " + name));
        } else {
            par();

            sb.append(returnType).append(' ').append(name).append('(');

            if (parameters.size() > 0) {
                AttrMethodParameters acc = getParameterAccesses();
                if (acc != null && code != null) {
                    int i = 0;
                    final List<LocalVariable> list = code.getLVT().list;
                    for (Type p : parameters) {
                        String name = list.get(i++).name;

                        FlagList ls = acc.flags.get(name);
                        if (ls != null) {
                            for (PrimitiveIterator.OfInt itr = ls.iterator(); itr.hasNext(); ) {
                                sb.append(AccessFlag.byIdParameter(itr.nextInt())).append(' ');
                            }
                        }
                        sb.append(p).append(' ').append(name).append(", ");
                    }
                } else {
                    for (Type p : parameters) {
                        sb.append(p).append(", ");
                    }
                }

                sb.delete(sb.length() - 2, sb.length());
            }

            sb.append(')');
        }

        AttrStringList throwsEx = getThrows();
        if (throwsEx != null) {
            sb.append(" throws ");
            for (String clz : throwsEx.classes) {
                sb.append(clz.substring(clz.lastIndexOf('/') + 1)).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
        }
        sb.append("\n      Code: \n").append(code);
        sb.append("\n      Attributes: \n");
        for (Attribute attr : attributes) {
            switch (attr.name) {
                case AttrAnnotation.INVISIBLE:
                case AttrAnnotation.VISIBLE:
                case "Exceptions":
                    continue;
            }
            sb.append("         ").append(attr.toString()).append('\n');
        }
        return sb.toString();
    }

    @Override
    public String parentClass() {
        return parent;
    }

    @Override
    public String ownerClass() {
        return owner;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Type> parameters() {
        par();
        return parameters;
    }

    @Override
    public Type getReturnType() {
        par();
        return returnType;
    }

    public void setReturnType(Type returnType) {
        par();
        this.returnType = returnType;
    }

    public void resetParam(boolean byList) {
        if (byList) {
            parameters.add(returnType);
            desc = ParamHelper.getMethod(parameters);
            parameters.remove(parameters.size() - 1);
        } else {
            if(parameters != null) {
                parameters.clear();
                ParamHelper.parseMethod(desc, parameters);
                returnType = parameters.remove(parameters.size() - 1);
            }
        }
    }

    private void par() {
        if (parameters == null) {
            if (desc == null) { // fallback
                parameters = new SimpleList<>();
                returnType = Type.std(NativeType.VOID);
                return;
            }
            parameters = ParamHelper.parseMethod(desc);
            returnType = parameters.remove(parameters.size() - 1);
        }
    }

    @Override
    public FlagList access() {
        return accesses;
    }

    public String rawDesc() {
        return this.desc;
    }

    @Override
    public int type() {
        return 2;
    }

    @Override
    public FlagList accessFlag() {
        return accesses;
    }

    public AttrAnnotation getAnnotations() {
        return (AttrAnnotation) attributes.getByName(AttrAnnotation.VISIBLE);
    }

    public AttrAnnotation getInvisibleAnnotations() {
        return (AttrAnnotation) attributes.getByName(AttrAnnotation.INVISIBLE);
    }

    public AttrParamAnnotation getParameterAnnotations() {
        return (AttrParamAnnotation) attributes.getByName(AttrParamAnnotation.VISIBLE);
    }

    public AttrParamAnnotation getInvisibleParameterAnnotations() {
        return (AttrParamAnnotation) attributes.getByName(AttrParamAnnotation.INVISIBLE);
    }

    public AttrMethodParameters getParameterAccesses() {
        return (AttrMethodParameters) attributes.getByName(AttrMethodParameters.NAME);
    }

    public AttrStringList getThrows() {
        return (AttrStringList) attributes.getByName(AttrStringList.EXCEPTIONS);
    }

    public AttrAnnotationDefault getAnnotationDefault() {
        return (AttrAnnotationDefault) attributes.getByName(AttrAnnotationDefault.NAME);
    }

    public void setDesc(String desc) {
        resetParam(false);
        this.desc = desc;
    }
}