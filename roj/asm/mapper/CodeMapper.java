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

package roj.asm.mapper;

import roj.asm.cst.*;
import roj.asm.mapper.struct.AttrCode_Simple;
import roj.asm.mapper.struct.AttrLVT_Simple;
import roj.asm.mapper.util.Context;
import roj.asm.mapper.util.FlDesc;
import roj.asm.mapper.util.MtDesc;
import roj.asm.struct.ConstantData;
import roj.asm.struct.attr.AttrInnerClasses;
import roj.asm.struct.attr.AttrUTF;
import roj.asm.struct.attr.AttrUnknown;
import roj.asm.struct.attr.Attribute;
import roj.asm.struct.simple.FieldSimple;
import roj.asm.struct.simple.MethodSimple;
import roj.asm.type.ParamHelper;
import roj.asm.type.Signature;
import roj.asm.type.SignatureHelper;
import roj.asm.type.Type;
import roj.asm.util.AttributeList;
import roj.asm.util.ConstantPool;
import roj.collect.IBitSet;
import roj.collect.LongBitSet;
import roj.collect.MyHashMap;
import roj.util.ByteList;
import roj.util.ByteReader;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 修改class名字
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class CodeMapper extends Mapping {
    public static final boolean
            DEBUG = Boolean.parseBoolean(System.getProperty("fmd.debugRename", "false")),
            REPLACE_DESC = Boolean.parseBoolean(System.getProperty("fmd.replaceDesc", "false"));

    public static final IBitSet HUMAN_READABLE_TOKENS = LongBitSet.preFilled("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_$");

    private Map<String, ?> paramNameMap = null;
    private byte paramNameType;

    public boolean rewrite;
    public IBitSet validVarChars = HUMAN_READABLE_TOKENS;

    private final UnaryOperator<String> NAME_REMAPPER = (old) -> {
        String now = Util.mapOwner(classMap, old, false);
        return now == null ? old : now;
    };

    public CodeMapper(CodeMapper o) {
        this.classMap = o.classMap;
        this.fieldMap = o.fieldMap;
        this.methodMap = o.methodMap;
        this.rewrite = o.rewrite;
        this.paramNameMap = o.paramNameMap;
        this.paramNameType = o.paramNameType;
        this.validVarChars = o.validVarChars;
    }

    public CodeMapper(Mapping remapper) {
        read(remapper);
    }

    public final CodeMapper read(Mapping remapper) {
        this.classMap = remapper.getClassMap();
        this.fieldMap = (MyHashMap<FlDesc, String>) remapper.getFieldMap();
        this.methodMap = (MyHashMap<MtDesc, String>) remapper.getMethodMap();
        return this;
    }

    public final void remap(boolean singleThread, Collection<Context> classes) {
        if(singleThread) {
            Context holder = null;

            try {
                for(Context entry : classes) {
                    holder = entry;
                    processOne(entry);
                }
            } catch (Throwable e) {
                final File file = new File("NR错误_" + (hashCode() ^ System.currentTimeMillis()) + ".class");
                try(FileOutputStream fos = new FileOutputStream(file)) {
                    holder.get().writeToStream(fos);
                } catch (Throwable ignored) {
                    try(FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(holder.getData().toString().getBytes());
                    } catch (Throwable ignored1) {}
                }
                e.printStackTrace();
                System.exit(-1);
            }
        } else {
            List<List<Context>> splitedContexts = new ArrayList<>();
            List<Context> tmp = new ArrayList<>();

            int splitThreshold = (classes.size() / Runtime.getRuntime().availableProcessors()) + 1;

            int i = 0;
            for (Context entry : classes) {
                if(i >= splitThreshold) {
                    splitedContexts.add(tmp);
                    tmp = new ArrayList<>(splitThreshold);
                    i = 0;
                }
                tmp.add(entry);
                i++;
            }
            splitedContexts.add(tmp);

            Util.waitfor("NRWorker", this::processOne, splitedContexts);
        }
    }

    public final void processOne(Context ctx) {
        ConstantData data = ctx.getData();
        mapAttribute(data.cp, data.attributes);
        mapParam(ctx, data);

        if(rewrite) {
            ctx.reset();
        } else {
            ctx.refresh();
        }
    }

    final void mapAttribute(ConstantPool pool, AttributeList list) {
        Attribute a = (Attribute) list.getByName("Signature");
        if(a != null) {
            if(a instanceof AttrUTF) {
                AttrUTF au = (AttrUTF) a;

                Signature generic = SignatureHelper.parse(au.value);

                generic.rename(NAME_REMAPPER);

                au.value = generic.toGeneric();
            } else {
                Signature generic = SignatureHelper.parse(((CstUTF) pool.array()[new ByteReader(a.getRawData()).readUnsignedShort()]).getString());

                generic.rename(NAME_REMAPPER);

                list.putByName(new AttrUTF(AttrUTF.SIGNATURE, generic.toGeneric()));
            }
        }

        a = (Attribute) list.getByName("InnerClasses");
        if(a != null) {
            AttrInnerClasses attr;
            if(a instanceof AttrInnerClasses)
                attr = (AttrInnerClasses) a;
            else
                list.putByName(attr = new AttrInnerClasses(new ByteReader(a.getRawData()), pool));

            List<AttrInnerClasses.InnerClass> classes = attr.classes;
            for (int j = 0; j < classes.size(); j++) {
                AttrInnerClasses.InnerClass clz = classes.get(j);
                if (clz.name != null && clz.parent != null) {
                    String name = Util.mapOwner(classMap, clz.parent + '$' + clz.name, false);
                    if (name != null) {
                        int i = name.indexOf('$');
                        clz.name = name.substring(i + 1);
                        clz.parent = name.substring(0, i);
                    }
                }
                if (clz.self != null) {
                    String name = Util.mapOwner(classMap, clz.self, false);
                    if (name != null)
                        clz.self = name;
                }
            }
        }
    }

    final void mapClassAndSuper(ConstantData data) {
        String name = Util.mapOwner(classMap, data.name, false);
        if(name != null)
            data.nameCst.setValue(data.writer.getUtf(name));

        name = Util.mapOwner(classMap, data.parent, false);
        if(name != null)
            data.parentCst.setValue(data.writer.getUtf(name));

        List<CstClass> itf = data.interfaces;
        for (int i = 0; i < itf.size(); i++) {
            CstClass clz = itf.get(i);
            name = Util.mapOwner(classMap, clz.getValue().getString(), false);
            if (name != null)
                clz.setValue(data.writer.getUtf(name));
        }
    }

    final void mapParam(Context ctx, ConstantData data) {
        String oldCls, newCls;
        int i;

        List<?> methods1 = data.methods;
        MtDesc md = Util.shareMD();
        md.owner = data.name;
        int max = 0;
        for (i = 0; i < methods1.size(); i++) {
            Object o = methods1.get(i);
            if(o instanceof MethodSimple) {
                MethodSimple method = (MethodSimple) o;

                /**
                 * Method Name
                 */
                md.name = method.name.getString();
                md.param = method.type.getString();

                String newName = methodMap.get(md);
                if (newName != null) {
                    if (DEBUG) {
                        System.out.println("[" + data.name + "]M-NAME: " + method.name + ' ' + newName);
                    }
                    method.name = data.writer.getUtf(newName);
                }

                /**
                 * Method Parameters
                 */
                oldCls = method.type.getString();
                newCls = Util.transformMethodParam(classMap, oldCls);

                if(!oldCls.equals(newCls)) {
                    if(REPLACE_DESC)
                        method.type.setString(newCls);
                    else
                        method.type = data.writer.getUtf(newCls);
                    if(DEBUG) {
                        System.out.println("[" + data.name + "]M: " + oldCls + ' ' + newCls);
                    }
                }

                mapAttribute(data.cp, method.attributes);
                max = Math.max(transform_LVT_LVTT_ST(data, method), max);
            } else {
                throw new IllegalArgumentException("toByteArray (rewrite) is needed");
            }
        }

        FlDesc fd = Util.shareFD();
        fd.owner = data.name;
        List<FieldSimple> fields = data.fields;
        for (i = 0; i < fields.size(); i++) {
            FieldSimple field = fields.get(i);

            /**
             * Field Name
             */
            fd.name = field.name.getString();

            String newName = fieldMap.get(fd);
            if(newName != null) {
                if (DEBUG) {
                    System.out.println("[" + data.name + "]F-NAME: " + field.name + ' ' + newName);
                }
                field.name = data.writer.getUtf(newName);
            }

            /**
             * Field Type
             */
            oldCls = field.type.getString();
            newCls = Util.transformFieldType(classMap, oldCls);

            if(!oldCls.equals(newCls)) {
                if(REPLACE_DESC)
                    field.type.setString(newCls);
                else
                    field.type = data.writer.getUtf(newCls);

                if(DEBUG) {
                    System.out.println("[" + data.name + "]F: " + oldCls + ' ' + newCls);
                }
            }

            mapAttribute(data.cp, field.attributes);
        }

        List<CstRef> cst = ctx.getFieldConstants();
        for (i = 0; i < cst.size(); i++) {
            CstRef field = cst.get(i);

            /**
             * 修改{@link CstRefField}字段类型
             */
            oldCls = field.desc().getType().getString();
            newCls = Util.transformFieldType(classMap, oldCls);

            if(!oldCls.equals(newCls)) {
                if(REPLACE_DESC)
                    field.desc().getType().setString(newCls);
                else
                    field.desc().setType(data.writer.getUtf(newCls));
                if(DEBUG) {
                    System.out.println("[" + data.name + "]F-REF: " + oldCls + ' ' + newCls);
                }
            }
        }

        cst = ctx.getMethodConstants();
        for (i = 0; i < cst.size(); i++) {
            CstRef method = cst.get(i);
            /**
             * 修改{@link CstRefField}方法调用
             */
            oldCls = method.desc().getType().getString();
            newCls = Util.transformMethodParam(classMap, oldCls);

            if(!newCls.equals(oldCls)) {
                method.desc().getType().setString(newCls);//.setType(data.writer.getUtf(newCls));
                if(DEBUG) {
                    System.out.println("[" + data.name + "]M-REF: " + oldCls + ' ' + newCls);
                }
            }
        }

        List<CstClass> clz = ctx.getClassConstants();
        for (i = 0; i < clz.size(); i++) {
            CstClass clazz = clz.get(i);
            /**
             * 修改class名字
             */
            oldCls = clazz.getValue().getString();
            newCls = Util.mapOwner(classMap, oldCls, false);
            if(newCls != null) {
                if(DEBUG) {
                    System.out.println("[" + data.name + "]C: " + oldCls + ' ' + newCls);
                }
                clazz.setValue(data.writer.getUtf(newCls));
            }
        }

        List<CstDynamic> inD = ctx.getInvokeDynamic();
        for (i = 0; i < inD.size(); i++) {
            CstDynamic dyn = inD.get(i);
            /**
             * Lambda方法的参数
             */
            oldCls = dyn.getDesc().getType().getString();
            newCls = Util.transformMethodParam(classMap, oldCls);

            if(!oldCls.equals(newCls)) {
                dyn.getDesc().getType().setString(newCls);//.setType(data.writer.getUtf(newCls));
                if (DEBUG) {
                    System.out.println("[" + data.name + "]M-REF-LAMBDA: " + oldCls + " => " + newCls);
                }
            }
        }

        if(max > 0)
            data.cp.reload(data.writer);
    }

    final int transform_LVT_LVTT_ST(ConstantData data, MethodSimple method) {
        int max = 0;
        AttrUnknown au = (AttrUnknown) method.attributes.getByName("Code");
        if(au != null) {
            AttrCode_Simple sc = new AttrCode_Simple(new ByteReader(au.getRawData()), data.cp);

            String methodDesc = method.name.getString() + '|' + method.rawDesc();

            if (sc.lvt != null) {
                List<AttrLVT_Simple.V> list =sc.lvt.list;
                for (int i = 0; i < list.size(); i++) {
                    AttrLVT_Simple.V entry = list.get(i);
                    Type type = (Type) entry.type;
                    if (type.owner != null) {
                        String clazz = Util.mapOwner(classMap, type.owner, false);
                        if (clazz != null) type.owner = clazz;
                    }
                    entry.refType.setString(ParamHelper.getField(type));

                    String n = mapEntryName(data, methodDesc, entry, i);
                    if(!n.equals(entry.name.getString())) {
                        int id = data.writer.getUtfId(n); // should use new cst pool

                        max = Math.max(id, max);

                        ByteList bl = entry.bl;
                        byte[] arr = bl.list;
                        int ni = entry.nameId;
                        arr[bl.offset() + ni] = (byte) (id >>> 8);
                        arr[bl.offset() + ni + 1] = (byte) id;
                    }
                }
            }
            if (sc.lvtt != null) {
                List<AttrLVT_Simple.V> list = sc.lvtt.list;
                for (int i = 0; i < list.size(); i++) {
                    AttrLVT_Simple.V entry = list.get(i);
                    ((Signature) entry.type).rename(NAME_REMAPPER);
                    entry.refType.setString(entry.type.toGeneric());

                    String n = mapEntryName(data, methodDesc, entry, i);
                    if(!n.equals(entry.name.getString())) {
                        int id = data.writer.getUtfId(n);

                        max = Math.max(id, max);

                        ByteList bl = entry.bl;
                        byte[] arr = bl.list;
                        int ni = entry.nameId;
                        arr[bl.offset() + ni] = (byte) (id >>> 8);
                        arr[bl.offset() + ni + 1] = (byte) id;
                    }
                }
            }
        }

        return max;
    }

    @SuppressWarnings("unchecked")
    final String mapEntryName(ConstantData data, String methodDesc, AttrLVT_Simple.V entry, int i) {
        String name = entry.name.getString();

        switch (paramNameType) {
            case 0:
                break;
            case 1:
                Object name1 = paramNameMap.get(name);
                if(name1 != null)
                    return name1.toString();
                break;
            case 2:
            case 3:
                Map<String, List<String>> remapNames = (Map<String, List<String>>) paramNameMap.get(data.name);
                if(remapNames != null) {
                    List<String> vars = remapNames.get(methodDesc);
                    int j = paramNameType == 2 ? i : entry.slot;
                    if(vars != null && vars.size() > j) {
                        String s = vars.get(j);
                        if(s != null) {
                            return s;
                        }
                    }
                }
                break;
        }

        if(name.length() == 0 || !/*Character.isJavaIdentifierPart()*/validVarChars.contains(name.charAt(0))) {
            return "_lvt_" + i + "_";
        }
        return name;
    }

    /**
     * By name
     */
    public void setParamRemapping(Map<String, String> paramMap) {
        this.paramNameMap = paramMap;
        this.paramNameType = (byte) (paramMap == null ? 0 : 1);
    }

    /**
     * By index
     */
    public void setParamRemappingV1(Map<String, Map<String, List<String>>> paramMap) {
        this.paramNameMap = paramMap;
        this.paramNameType = (byte) (paramMap == null ? 0 : 2);
    }

    /**
     * By slot
     */
    public void setParamRemappingV2(Map<String, Map<String, List<String>>> paramMap) {
        this.paramNameMap = paramMap;
        this.paramNameType = (byte) (paramMap == null ? 0 : 3);
    }
}