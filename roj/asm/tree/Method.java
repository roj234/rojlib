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

import roj.asm.SharedBuf;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.*;
import roj.asm.type.*;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.asm.util.ConstantPool;
import roj.asm.util.FlagList;
import roj.collect.SimpleList;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import javax.annotation.Nullable;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.PrimitiveIterator.OfInt;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class Method implements MethodNode, MoFNode {
    public Method(int accesses, IClass owner, String name, String desc) {
        this(AccessFlag.of((short) accesses), owner.className(), name, desc);
    }

    public Method(FlagList accesses, IClass owner, String name, String desc) {
        this(accesses, owner.className(), name, desc);
    }

    public Method(FlagList accesses, String owner, String name, String desc) {
        this.accesses = accesses;
        this.owner = owner;
        this.name = name;
        this.rawDesc = desc;
        this.attributes = new AttributeList();
    }


    public Method(ConstantData data, MethodSimple method) {
        this.accesses = method.accesses.copy();
        this.owner = data.name;
        this.name = method.name.getString();
        this.rawDesc = method.type.getString();
        this.attributes = new AttributeList(method.attributes.size());

        ConstantPool pool = data.cp;
        ByteReader r = new ByteReader();

        AttributeList al = method.attributes;
        for (int i = 0; i < al.size(); i++) {
            Attribute attr = al.get(i);
            if(attr.getClass() == AttrUnknown.class) {
                r.refresh(attr.getRawData());

                String name = attr.name;

                attr = handleAttribute(pool, r, name, r.length());

                if (!r.isFinished()) {
                    throw new IllegalStateException("[M.W.A] " + name + " has " + (r.length() - r.index) + " bytes left: " + attr);
                }
            } else {
                attributes.add(attr);
            }
        }

        if (code == null && !accesses.hasAny(AccessFlag.ABSTRACT | AccessFlag.NATIVE)) {
            throw new IllegalArgumentException("Non-abstract method " + data.name + '.' + name + ':' + rawDesc + " did not contain Code attribute.");
        }
    }

    public Method(IClass owner, Method method) {
        this.accesses = method.accesses.copy();
        this.owner = owner.className();
        this.name = method.name;
        this.rawDesc = method.rawDesc();
        this.attributes = new AttributeList(method.attributes);
    }

    public void initAttributes(ConstantPool pool, ByteReader r) {
        int len = r.readUnsignedShort();
        for (int i = 0; i < len; i++) {
            String name = ((CstUTF) pool.get(r)).getString();
            int length = r.readInt();
            int end = r.index + length;

            Attribute attr = handleAttribute(pool, r, name, length);

            if (r.index != end) {
                new IllegalStateException(
                "[M.I.A] " + name + " has " + (end - r.index) + " bytes left(total: " + length + "): \n"
                + attr + "\nAt " + owner + "." + this.name + "\n" + r.getBytes().subList(r.index,
                end - r.index)).printStackTrace();
                r.index = end;
            }
        }
    }

    private Attribute handleAttribute(ConstantPool pool, ByteReader r, String name, int length) {
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
                return null;
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
                return code;
            // 由编译器生成
            case "Synthetic":
                // 弃用
            case "Deprecated":
            default:
                attr = new AttrUnknown(name, r.readBytesDelegated(length));
        }
        attributes.add(attr);
        return attr;
    }

    public String owner, name;

    private String rawDesc;
    private List<Type> params;
    private Type       returnType;

    public FlagList accesses;
    public AttributeList attributes;

    @Nullable
    public Signature signature;
    public AttrCode code;

    private void aOn(Attribute a) {
        if (a != null)
            attributes.add(a);
    }

    MethodSimple i_downgrade(ConstantPool cw) {
        if (params != null) {
            params.add(returnType);
            rawDesc = ParamHelper.getMethod(params);
            params.remove(params.size() - 1);
        }
        MethodSimple m = new MethodSimple(accesses, cw.getUtf(name), cw.getUtf(rawDesc));
        m.owner = owner;
        if (params != null) {
            m.params = params;
        }
        m.attributes.ensureCapacity(attributes.size() +
                                            (code == null ? 0 : 1) +
                                            (signature == null ? 0 : 1));
        ByteWriter w = new ByteWriter(SharedBuf.i_get());
        for (int i = 0; i < attributes.size(); i++) {
            m.attributes.add(AttrUnknown.downgrade(cw, w, attributes.get(i)));
        }
        if (signature != null) {
            w.list.clear();
            w.writeShort(cw.getUtfId(signature.toGeneric()));
            m.attributes.add(new AttrUnknown(AttrUTF.SIGNATURE, new ByteList(w.toByteArray())));
        }
        if (code != null) {
            m.attributes.add(AttrUnknown.downgrade(cw, w, code));
        }
        return m;
    }

    @Override
    public AttributeList attributes() {
        return attributes;
    }

    public void toByteArray(ConstantPool pool, ByteWriter w) {
        w.writeShort(accesses.flag).writeShort(pool.getUtfId(name));

        if (params != null) {
            params.add(returnType);
            rawDesc = ParamHelper.getMethod(params);
            params.remove(params.size() - 1);
        }
        w.writeShort(pool.getUtfId(rawDesc));

        aOn(code);
        if (signature != null)
            attributes.add(new AttrUTF(AttrUTF.SIGNATURE, signature.toGeneric()));

        w.writeShort(attributes.size());
        for (int i = 0; i < attributes.size(); i++) {
            attributes.get(i).toByteArray(pool, w);
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
            initPar();

            sb.append(returnType).append(' ').append(name).append('(');

            if (params.size() > 0) {
                AttrMethodParameters acc = getParameterAccesses();
                if (acc != null && code != null) {
                    int i = 0;
                    final List<LocalVariable> list = code.getLVT().list;
                    for (int j = 0; j < params.size(); j++) {
                        Type p = params.get(j);
                        String name = list.get(i++).name;

                        FlagList ls = acc.flags.get(name);
                        if (ls != null) {
                            for (OfInt itr = ls.iterator(); itr.hasNext(); ) {
                                sb.append(AccessFlag.byIdParameter(itr.nextInt())).append(' ');
                            }
                        }
                        sb.append(p).append(' ').append(name).append(", ");
                    }
                } else {
                    for (int i = 0; i < params.size(); i++) {
                        Type p = params.get(i);
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
    public String ownerClass() {
        return owner;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Type> parameters() {
        initPar();
        return params;
    }

    @Override
    public Type getReturnType() {
        initPar();
        return returnType;
    }

    public void setReturnType(Type returnType) {
        initPar();
        this.returnType = returnType;
    }

    private void initPar() {
        if (params == null) {
            if (rawDesc == null) { // fallback
                params = new SimpleList<>();
                returnType = Type.std(NativeType.VOID);
                return;
            }
            params = ParamHelper.parseMethod(rawDesc);
            returnType = params.remove(params.size() - 1);
        }
    }

    public String rawDesc() {
        if (this.rawDesc == null) {
            if (params != null) {
                params.add(returnType);
                String v = ParamHelper.getMethod(params);
                params.remove(params.size() - 1);
                return v;
            }
        }
        return this.rawDesc;
    }

    public void rawDesc(String param) {
        this.rawDesc = param;
        if (params != null) {
            params.clear();
            ParamHelper.parseMethod(param, params);
            returnType = params.remove(params.size() - 1);
        }
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
}