/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: Method.java
 */
package roj.asm.struct;

import roj.asm.constant.CstUTF;
import roj.asm.struct.attr.*;
import roj.asm.struct.simple.IConstantSerializable;
import roj.asm.struct.simple.MethodSimple;
import roj.asm.util.*;
import roj.asm.util.type.*;
import roj.collect.SimpleList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import javax.annotation.Nullable;
import java.util.List;
import java.util.PrimitiveIterator;

public final class Method implements IMethodData, IConstantSerializable {
    public Method(int accesses, Clazz owner, String name, String desc) {
        this(AccessFlag.parse((short) accesses), owner.name, owner.parent, name, desc);
    }

    public Method(FlagList accesses, Clazz owner, String name, String desc) {
        this(accesses, owner.name, owner.parent, name, desc);
    }



    public Method(int accesses, ConstantData owner, String name, String desc) {
        this(AccessFlag.parse((short) accesses), owner.name, owner.parent, name, desc);
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

        ConstantPool pool = data.constants;
        ByteReader r = new ByteReader();

        for (Attribute attr : method.attributes) {
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
                signature = SignatureHelper.parse(((CstUTF) pool.get(r)).getString());
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

        initParam();

        parameters.add(returnType);
        w.writeShort(pool.getUtfId(ParamHelper.getMethod(parameters)));
        parameters.remove(parameters.size() - 1);

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
            initParam();

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
        initParam();
        return parameters;
    }

    public Type getReturnType() {
        initParam();
        return returnType;
    }

    public void setReturnType(Type returnType) {
        initParam();
        this.returnType = returnType;
    }

    public void resetParam(boolean side) {
        if (side) {
            this.parameters.add(returnType);
            this.desc = ParamHelper.getMethod(this.parameters);
            this.parameters.remove(this.parameters.size() - 1);
        } else {
            this.parameters = null;
        }
    }

    private void initParam() {
        if (parameters == null) {
            if (desc == null) {
                this.parameters = new SimpleList<>();
                this.returnType = Type.VOID;
                return;
            }
            this.parameters = ParamHelper.parseMethod(desc);
            this.returnType = parameters.get(parameters.size() - 1);
            parameters.remove(parameters.size() - 1);
        }
    }

    @Override
    public FlagList access() {
        return accesses;
    }

    public String rawDesc() {
        return this.desc;
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