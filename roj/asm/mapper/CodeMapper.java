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

import roj.asm.Parser;
import roj.asm.cst.*;
import roj.asm.mapper.util.Context;
import roj.asm.mapper.util.Desc;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldSimple;
import roj.asm.tree.MethodSimple;
import roj.asm.tree.attr.*;
import roj.asm.type.ParamHelper;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.util.AttributeList;
import roj.asm.util.ConstantPool;
import roj.collect.IBitSet;
import roj.collect.LongBitSet;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

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
    // 没有BUG了，有的话也是ASM的问题
    public static final boolean DEBUG = false;

    public static final IBitSet HUMAN_READABLE_TOKENS = LongBitSet.from("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_$");

    private Map<String, ?> paramNameMap;
    private byte paramNameType;

    private IBitSet validVarChars = HUMAN_READABLE_TOKENS;

    private final UnaryOperator<String> NAME_REMAPPER = (old) -> {
        String now = Util.mapOwner(classMap, old, false);
        return now == null ? old : now;
    };

    public CodeMapper(boolean checkFieldType) {
        super(checkFieldType);
    }

    public CodeMapper(CodeMapper o) {
        super(o);
        this.paramNameMap = o.paramNameMap;
        this.paramNameType = o.paramNameType;
        this.validVarChars = o.validVarChars;
    }

    public CodeMapper(Mapping mapping) {
        super(mapping);
    }

    public void setValidVarChars(IBitSet valid) {
        this.validVarChars = valid == null ? HUMAN_READABLE_TOKENS : valid;
    }

    public final void remap(boolean singleThread, Collection<Context> arr) {
        if(singleThread || arr.size() < 50 * Util.CPU) {
            Context cur = null;

            try {
                for(Context entry : arr) {
                    cur = entry;
                    processOne(entry);
                }
            } catch (Throwable e) {
                throw new RuntimeException("At parsing " + cur, e);
            }
        } else {
            List<List<Context>> splitedContexts = new ArrayList<>();
            List<Context> tmp = new ArrayList<>();

            int splitThreshold = (arr.size() / Runtime.getRuntime().availableProcessors()) + 1;

            int i = 0;
            for (Context entry : arr) {
                if(i >= splitThreshold) {
                    splitedContexts.add(tmp);
                    tmp = new ArrayList<>(splitThreshold);
                    i = 0;
                }
                tmp.add(entry);
                i++;
            }
            splitedContexts.add(tmp);

            Util.concurrent("NRWorker", this::processOne, splitedContexts);
        }
    }

    // 致天天想着搞优化的我: 有顺序！！！
    public final void processOne(Context ctx) {
        ConstantData data = ctx.getData();
        data.normalize();

        // 这里都成了String，要第一个！
        Attribute a = (Attribute) data.attributes.getByName("InnerClasses");
        if(a != null) {
            AttrInnerClasses ic;
            if(a instanceof AttrInnerClasses)
                ic = (AttrInnerClasses) a;
            else
                data.attributes.putByName(ic = new AttrInnerClasses(Parser.reader(a), data.cp));

            List<AttrInnerClasses.InnerClass> classes = ic.classes;
            for (int j = 0; j < classes.size(); j++) {
                AttrInnerClasses.InnerClass clz = classes.get(j);
                if (clz.name != null && clz.parent != null) {
                    String name = Util.mapOwner(classMap, clz.parent + '$' + clz.name, false);
                    if (name != null) {
                        int i = name.lastIndexOf('$');
                        if(i == -1) {
                            if(DEBUG)
                                System.out.println("[CM Warn] No '$' sig: " + clz.parent + '$' + clz.name + " => " + name);
                            clz.name = name;
                            name = Util.mapOwner(classMap, clz.parent, false);
                            if(name != null)
                                clz.parent = name;
                        } else {
                            clz.name = name.substring(i + 1);
                            clz.parent = name.substring(0, i);
                        }
                    }
                }
                if (clz.self != null) {
                    String name = Util.mapOwner(classMap, clz.self, false);
                    if (name != null)
                        clz.self = name;
                }
            }
        }

        a = (Attribute) data.attributes.getByName("BootstrapMethods");
        if(a != null) {
            AttrBootstrapMethods bs;
            if(a instanceof AttrBootstrapMethods)
                bs = (AttrBootstrapMethods) a;
            else
                data.attributes.putByName(bs = new AttrBootstrapMethods(Parser.reader(a), data.cp));

            List<AttrBootstrapMethods.BootstrapMethod> methods = bs.methods;
            for (int i = 0; i < methods.size(); i++) {
                AttrBootstrapMethods.BootstrapMethod ibm = methods.get(i);
                List<Constant> args = ibm.arguments;
                for (int j = 0; j < args.size(); j++) {
                    Constant cst = args.get(j);
                    if (cst.type() == CstType.METHOD_TYPE) {
                        CstMethodType type = (CstMethodType) cst;

                        String oldCls = type.getValue().getString();
                        String newCls = Util.transformMethodParam(classMap, oldCls);

                        if (!oldCls.equals(newCls)) {
                            type.setValue(data.cp.getUtf(newCls));
                            if (DEBUG) {
                                System.out.println("[" + data.name + "]BootstrapMethod " + i + "-" + j + ": " + oldCls + ' ' + newCls);
                            }
                        }
                    }
                }
            }
        }

        // 泛型的UTF(几乎)不可能重复，但真碰到了我倒霉，还是放前面，至于和BSM重复？滚蛋
        mapSignature(data.cp, data.attributes);

        mapParam(ctx, data);

        // 以后可能会有attribute序列化的需求，省得忘了
        data.normalize();
    }

    private void mapSignature(ConstantPool pool, AttributeList list) {
        Attribute a = (Attribute) list.getByName("Signature");
        if(a == null) {
            return;
        }
        if(a instanceof AttrUTF) {
            AttrUTF au = (AttrUTF) a;

            Signature generic = Signature.parse(au.value);

            generic.rename(NAME_REMAPPER);

            au.value = generic.toGeneric();
        } else {
            Signature generic = Signature.parse(((CstUTF) pool.array(Parser.reader(a).readUnsignedShort())).getString());

            generic.rename(NAME_REMAPPER);

            list.putByName(new AttrUTF(AttrUTF.SIGNATURE, generic.toGeneric()));
        }
    }

    private void mapClassAndSuper(ConstantData data) {
        String name = Util.mapOwner(classMap, data.name, false);
        if(name != null)
            data.nameCst.setValue(data.cp.getUtf(name));

        name = Util.mapOwner(classMap, data.parent, false);
        if(name != null) // noinspection all
            data.parentCst.setValue(data.cp.getUtf(name));

        List<CstClass> itf = data.interfaces;
        for (int i = 0; i < itf.size(); i++) {
            CstClass clz = itf.get(i);
            name = Util.mapOwner(classMap, clz.getValue().getString(), false);
            if (name != null)
                clz.setValue(data.cp.getUtf(name));
        }
    }

    private void mapParam(Context ctx, ConstantData data) {
        String oldCls, newCls;
        int i;

        List<MethodSimple> methods1 = data.methods;
        Desc md = Util.shareMD();
        md.owner = data.name;
        for (i = 0; i < methods1.size(); i++) {
            MethodSimple method = methods1.get(i);

            /**
             * Method Name
             */
            md.name = method.name.getString();
            md.param = method.type.getString();

            String newName = methodMap.get(md);
            if (newName != null) {
                if (DEBUG) {
                    System.out.println("[" + data.name + "]M-NAME: " + method.name.getString() + ' ' + newName);
                }
                method.name = data.cp.getUtf(newName);
            }

            /**
             * Method Parameters
             */
            oldCls = method.type.getString();
            newCls = Util.transformMethodParam(classMap, oldCls);

            if(!oldCls.equals(newCls)) {
                method.type = data.cp.getUtf(newCls);
                if(DEBUG) {
                    System.out.println("[" + data.name + "]M: " + oldCls + ' ' + newCls);
                }
            }

            mapSignature(data.cp, method.attributes);
        }

        md.owner = data.name;
        md.param = "";
        List<FieldSimple> fields = data.fields;
        for (i = 0; i < fields.size(); i++) {
            FieldSimple field = fields.get(i);

            /**
             * Field Name
             */
            md.name = field.name.getString();
            if(checkFieldType)
                md.param = field.type.getString();

            String newName = fieldMap.get(md);
            if(newName != null) {
                if (DEBUG) {
                    System.out.println("[" + data.name + "]F-NAME: " + field.name.getString() + ' ' + newName);
                }
                field.name = data.cp.getUtf(newName);
            }

            /**
             * Field Type
             */
            oldCls = field.type.getString();
            newCls = Util.transformFieldType(classMap, oldCls);

            if(newCls != null) {
                field.type = data.cp.getUtf(newCls);

                if(DEBUG) {
                    System.out.println("[" + data.name + "]F: " + oldCls + ' ' + newCls);
                }
            }

            mapSignature(data.cp, field.attributes);
        }

        // 十分不幸的是, field rename (when parameterized) 会被 LVT 工序影响
        for (i = 0; i < methods1.size(); i++) {
            transform_LVT_LVTT_ST(data, methods1.get(i));
        }

        List<CstRef> cst = ctx.getFieldConstants();
        for (i = 0; i < cst.size(); i++) {
            CstRef field = cst.get(i);

            /**
             * 修改{@link CstRefField}字段类型
             */
            oldCls = field.desc().getType().getString();
            newCls = Util.transformFieldType(classMap, oldCls);

            if(newCls != null) {
                field.desc().setType(data.cp.getUtf(newCls));
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
                data.cp.setUTFValue(method.desc().getType(), newCls);
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
                clazz.setValue(data.cp.getUtf(newCls));
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
                data.cp.setUTFValue(dyn.getDesc().getType(), newCls);
                if (DEBUG) {
                    System.out.println("[" + data.name + "]M-REF-LAMBDA: " + oldCls + " => " + newCls);
                }
            }
        }
    }

    private void transform_LVT_LVTT_ST(ConstantData data, MethodSimple method) {
        AttrUnknown au = (AttrUnknown) method.attributes.getByName("Code");
        if(au != null) {
            ByteReader r = Parser.reader(au);
            r.index += 4; // stack size
            int codeLen = r.readInt();
            r.index += codeLen; // code

            int len = r.readUnsignedShort(); // exception
            r.index += len << 3;

            String methodDesc = method.name.getString() + '|' + method.rawDesc();
            ConstantPool pool = data.cp;
            len = r.readUnsignedShort();
            for (int i = 0; i < len; i++) {
                String name = ((CstUTF) pool.get(r)).getString();
                int end = r.readInt() + r.index;
                switch (name) {
                    case "LocalVariableTable":
                        List<SimpleVar> list = readVar(pool, r);
                        for (int j = 0; j < list.size(); j++) {
                            SimpleVar entry = list.get(j);
                            String ref = entry.refType.getString();
                            if (ref.endsWith(";")) { // [Lxx; or Lxx;
                                Type type = ParamHelper.parseField(ref);
                                String clazz = Util.mapOwner(classMap, type.owner, false);
                                if (clazz != null) {
                                    type.owner = clazz;
                                    data.cp.setUTFValue(entry.refType, ParamHelper.getField(type));
                                }
                            }

                            String n = mapEntryName(data, methodDesc, entry, j);
                            if(!n.equals(entry.name.getString())) {
                                int id = data.cp.getUtfId(n);

                                ByteList bl = r.getBytes();
                                byte[] arr = bl.list;
                                int ni = entry.nameId;
                                arr[bl.offset() + ni] = (byte) (id >>> 8);
                                arr[bl.offset() + ni + 1] = (byte) id;
                            }
                        }
                        break;
                    case "LocalVariableTypeTable":
                        list = readVar(pool, r);
                        for (int j = 0; j < list.size(); j++) {
                            SimpleVar entry = list.get(j);
                            Signature sign = Signature.parse(entry.refType.getString());
                            sign.rename(NAME_REMAPPER);
                            data.cp.setUTFValue(entry.refType, sign.toGeneric());

                            String n = mapEntryName(data, methodDesc, entry, j);
                            if(!n.equals(entry.name.getString())) {
                                int id = data.cp.getUtfId(n);

                                ByteList bl = r.getBytes();
                                byte[] arr = bl.list;
                                int ni = entry.nameId;
                                arr[bl.offset() + ni] = (byte) (id >>> 8);
                                arr[bl.offset() + ni + 1] = (byte) id;
                            }
                        }
                        break;
                }
                r.index = end;
            }
        }
    }

    static List<SimpleVar> readVar(ConstantPool cp, ByteReader r) {
        int len = r.readUnsignedShort();
        List<SimpleVar> list = new ArrayList<>(len);

        for (int i = 0; i < len; i++) {
            SimpleVar sv = new SimpleVar();
            sv.start = r.readUnsignedShort();
            sv.end = r.readUnsignedShort();
            sv.nameId = r.index;
            sv.name = ((CstUTF) cp.get(r));
            sv.refType = ((CstUTF) cp.get(r));
            sv.slot = r.readUnsignedShort();
            list.add(sv);
        }
        return list;
    }

    static final class SimpleVar {
        CstUTF name, refType;
        int slot, nameId;
        int start, end;

        void write(ByteWriter w) {
            w.writeShort(start).writeShort(end)
             .writeShort(name.getIndex()).writeShort(refType.getIndex())
             .writeShortR(slot);
        }
    }

    @SuppressWarnings("unchecked")
    private String mapEntryName(ConstantData data, String methodDesc, SimpleVar entry, int i) {
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
    public final void setParamRemapping(Map<String, String> paramMap) {
        this.paramNameMap = paramMap;
        this.paramNameType = (byte) (paramMap == null ? 0 : 1);
    }

    /**
     * By index
     */
    public final void setParamRemappingV1(Map<String, Map<String, List<String>>> paramMap) {
        this.paramNameMap = paramMap;
        this.paramNameType = (byte) (paramMap == null ? 0 : 2);
    }

    /**
     * By slot
     */
    public final void setParamRemappingV2(Map<String, Map<String, List<String>>> paramMap) {
        this.paramNameMap = paramMap;
        this.paramNameType = (byte) (paramMap == null ? 0 : 3);
    }
}