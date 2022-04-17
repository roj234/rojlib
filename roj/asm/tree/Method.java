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
import roj.asm.tree.attr.AttrMethodParameters.MethodParam;
import roj.asm.type.LocalVariable;
import roj.asm.type.ParamHelper;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.asm.util.ConstantPool;
import roj.collect.SimpleList;
import roj.util.ByteList;
import roj.util.ByteReader;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class Method implements MethodNode {
    public Method(int accesses, IClass owner, String name, String desc) {
        this.accesses = (char) accesses;
        this.owner = owner.name();
        this.name = name;
        this.rawDesc = desc;
        this.attributes = new AttributeList();
    }

    public Method(int accesses, String owner, String name, String desc) {
        this.accesses = (char) accesses;
        this.owner = owner;
        this.name = name;
        this.rawDesc = desc;
        this.attributes = new AttributeList();
    }


    public Method(ConstantData data, MethodSimple method) {
        this.accesses = method.accesses;
        this.owner = data.name;
        this.name = method.name.getString();
        this.rawDesc = method.type.getString();
        this.attributes = new AttributeList(method.attributes().size());

        ConstantPool pool = data.cp;
        ByteReader r = new ByteReader();

        AttributeList al = method.attributes();
        for (int i = 0; i < al.size(); i++) {
            Attribute attr = al.get(i);
            if(attr.getClass() == AttrUnknown.class) {
                r.refresh(attr.getRawData());

                String name = attr.name;

                attr = handleAttribute(pool, r, name, r.length());

                if (r.hasRemaining()) {
                    throw new IllegalStateException("[M.W.A] " + name + " has " + (r.length() - r.rIndex) + " bytes left: " + attr);
                }
            } else {
                attributes.add(attr);
            }
        }

        if (code == null && 0 == (accesses & (AccessFlag.ABSTRACT | AccessFlag.NATIVE))) {
            throw new IllegalArgumentException("Non-abstract method " + data.name + '.' + name + ':' + rawDesc + " did not contain Code attribute.");
        }
    }

    public Method(java.lang.reflect.Method m) {
        name = m.getName();
        accesses = (char) m.getModifiers();
        rawDesc = ParamHelper.class2asm(m.getParameterTypes(), m.getReturnType());
        owner = m.getDeclaringClass().getName().replace('.', '/');
    }

    public void initAttributes(ConstantPool pool, ByteReader r) {
        int len = r.readUnsignedShort();
        if (len == 0) return;
        attributes = new AttributeList(len);
        for (int i = 0; i < len; i++) {
            String name = ((CstUTF) pool.get(r)).getString();
            int length = r.readInt();
            int end = r.rIndex + length;

            Attribute attr = handleAttribute(pool, r, name, length);

            if (r.rIndex != end) {
                new IllegalStateException(
                "[M.I.A] " + name + " has " + (end - r.rIndex) + " bytes left(total: " + length + "): \n"
                + attr + "\nAt " + owner + "." + this.name + "\n" + r.bytes().slice(r.rIndex,
                                                                                    end - r.rIndex)).printStackTrace();
                r.rIndex = end;
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
                attr = new AttrUnknown(name, r.slice(length));
        }
        attributes.add(attr);
        return attr;
    }

    public String owner;

    public String name;
    public char accesses;
    private String rawDesc;
    private List<Type> params;
    private Type returnType;

    private AttributeList attributes;

    @Nullable
    public Signature signature;
    public AttrCode code;

    MethodSimple i_downgrade(ConstantPool cw) {
        if (params != null) {
            params.add(returnType);
            rawDesc = ParamHelper.getMethod(params, rawDesc);
            params.remove(params.size() - 1);
        }
        MethodSimple m = new MethodSimple(accesses, cw.getUtf(name), cw.getUtf(rawDesc));
        m.owner = owner;
        if (params != null) m.params = params;

        if (attributes == null && code == null && signature == null) return m;

        AttributeList otherAttr = m.attributes();
        AttributeList myAttr = attributes();
        otherAttr.ensureCapacity(myAttr.size() + (code == null ? 0 : 1) + (signature == null ? 0 : 1));
        ByteList w = SharedBuf.i_get();
        for (int i = 0; i < myAttr.size(); i++) {
            otherAttr.add(AttrUnknown.downgrade(cw, w, myAttr.get(i)));
        }
        if (signature != null) {
            w.clear();
            w.putShort(cw.getUtfId(signature.toGeneric()));
            otherAttr.add(new AttrUnknown(AttrUTF.SIGNATURE, new ByteList(w.toByteArray())));
        }
        if (code != null) {
            otherAttr.add(AttrUnknown.downgrade(cw, w, code));
        }
        return m;
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

    public void toByteArray(ConstantPool pool, ByteList w) {
        if (params != null) {
            params.add(returnType);
            rawDesc = ParamHelper.getMethod(params, rawDesc);
            params.remove(params.size() - 1);
        }
        w.putShort(accesses).putShort(pool.getUtfId(name)).putShort(pool.getUtfId(rawDesc));

        if (code != null) attributes().add(code);
        if (signature != null) attributes().add(new AttrUTF(AttrUTF.SIGNATURE, signature.toGeneric()));

        AttributeList attr = attributes;
        if (attr == null) {
            w.putShort(0);
            return;
        }

        w.putShort(attr.size());
        for (int i = 0; i < attr.size(); i++) {
            attr.get(i).toByteArray(pool, w);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (getAnnotations() != null)
            sb.append("    ").append(getAnnotations()).append('\n');
        if (getInvisibleAnnotations() != null)
            sb.append("    ").append(getInvisibleAnnotations()).append('\n');

        sb.append("    ");
        AccessFlag.toString(accesses, AccessFlag.TS_METHODS, sb);
        if (signature != null) {
            sb.append(signature.toString().replaceFirst(" ", " " + name));
        } else {
            initPar();

            sb.append(returnType).append(' ').append(name).append('(');

            if (params.size() > 0) {
                AttrMethodParameters acc = getParameterAccesses();
                if (acc != null && code != null) {
                    final List<LocalVariable> list = code.getLVT().list;
                    for (int j = 0; j < params.size(); j++) {
                        Type p = params.get(j);
                        String name = list.get(j).name;

                        MethodParam e = acc.flags.size() <= j ? null : acc.flags.get(j);
                        if (e != null) {
                            AccessFlag.toString(e.flag, AccessFlag.TS_PARAM, sb);
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
            List<String> classes = throwsEx.classes;
            for (int i = 0; i < classes.size(); i++) {
                String clz = classes.get(i);
                sb.append(clz.substring(clz.lastIndexOf('/') + 1)).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
        }
        sb.append("\n      Code: \n").append(code);
        if (attributes != null) {
            sb.append("\n      Attributes: \n");
            for (int i = 0; i < attributes.size(); i++) {
                Attribute attr = attributes.get(i);
                switch (attr.name) {
                    case AttrAnnotation.INVISIBLE:
                    case AttrAnnotation.VISIBLE:
                    case "Exceptions":
                        continue;
                }
                sb.append("         ").append(attr.toString()).append('\n');
            }
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
        if (returnType == null) {
            if (rawDesc == null) initPar();
            else return returnType = ParamHelper.parseReturn(rawDesc);
        }
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
                returnType = Type.std(Type.VOID);
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

    @Override
    public void name(String name) {
        this.name = name;
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
        return Parser.MTYPE_FULL;
    }

    @Override
    public void accessFlag(int flag) {
        this.accesses = (char) flag;
    }

    @Override
    public char accessFlag() {
        return accesses;
    }

    public AttrAnnotation getAnnotations() {
        return attributes == null ? null : (AttrAnnotation) attributes.getByName(AttrAnnotation.VISIBLE);
    }

    public AttrAnnotation getInvisibleAnnotations() {
        return attributes == null ? null : (AttrAnnotation) attributes.getByName(AttrAnnotation.INVISIBLE);
    }

    public AttrMethodParameters getParameterAccesses() {
        return attributes == null ? null : (AttrMethodParameters) attributes.getByName(AttrMethodParameters.NAME);
    }

    public AttrStringList getThrows() {
        return attributes == null ? null : (AttrStringList) attributes.getByName(AttrStringList.EXCEPTIONS);
    }
}