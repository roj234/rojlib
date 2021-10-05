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

package roj.asm.nixim2;

import roj.asm.OpcodesInt;
import roj.asm.cst.*;
import roj.asm.mapper.Util;
import roj.asm.mapper.util.Context;
import roj.asm.nixim.*;
import roj.asm.tree.ConstantData;
import roj.asm.tree.Field;
import roj.asm.tree.Method;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.*;
import roj.asm.tree.attr.AttrAnnotation;
import roj.asm.tree.attr.AttrBootstrapMethods;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.insn.*;
import roj.asm.tree.simple.FieldSimple;
import roj.asm.tree.simple.MethodSimple;
import roj.asm.tree.simple.SimpleComponent;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.*;
import roj.asm.visitor.CodeVisitor;
import roj.collect.Int2IntMap;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import static roj.asm.Opcodes.*;

/**
 * NiximTransformerV2
 *
 * @author Roj234
 * @version 2.0
 * @since 2021/10/3 13:49
 */
public class NiximTransformer2 {
    public static void main(String[] args) throws IOException, NiximException {
        NiximTransformer2 nt2 = new NiximTransformer2();
        nt2.read(IOUtil.read("roj/asm/nixim/Example.class"));
        ByteList sharedOutput = nt2.nixim("roj/asm/nixim2/TestTarget", new ByteList(IOUtil.read("roj/asm/nixim2/TestTarget.class")));
        try (FileOutputStream fos = new FileOutputStream("Example.class")) {
            sharedOutput.writeToStream(fos);
        }
    }

    protected final Map<String, NiximData> registry = new MyHashMap<>();

    public static final String SPEC_M_RETVAL = "$$$RETURN_VAL";
    public static final String SPEC_M_CONTINUE = "$$$CONTINUE";

    public static boolean debug = false;

    public final boolean removeByClass(String target, String source) {
        NiximData nx = registry.get(target);
        NiximData prev = null;
        while (nx != null) {
            if (nx.self.equals(source)) {
                if (prev == null)
                    registry.remove(target);
                else
                    prev.next = nx.next;
                return true;
            }
            prev = nx;
            nx = nx.next;
        }
        return false;
    }

    public final boolean removeByClass(String target) {
        return registry.remove(target) != null;
    }

    // region 应用

    public ByteList nixim(String className, ByteList in) throws NiximException {
        return nixim(new Context(className, in), registry.remove(className));
    }

    public static ByteList nixim(Context ctx, NiximData nx) throws NiximException {
        System.out.println("NiximClass " + ctx.getName());

        while (nx != null) {
            ConstantData data = ctx.getData();

            // 添加接口
            List<String> itfs = nx.addItfs;
            for (int i = 0; i < itfs.size(); i++) {
                data.interfaces.add(data.writer.getClazz(itfs.get(i)));
            }

            DescEntry tester = new DescEntry();
            List<FieldSimple> fields = data.fields;
            List<MethodSimple> methods = data.methods;
            // region 检查 Shadow 兼容性
            if (!nx.shadowChecks.isEmpty()) {
                for (int i = 0; i < fields.size(); i++) {
                    FieldSimple fs = fields.get(i);
                    tester.name = fs.name();
                    tester.desc = fs.rawDesc();
                    DescEntry t = nx.shadowChecks.find(Helpers.cast(tester));
                    // noinspection all
                    if (t instanceof ShadowCheck) {
                        nx.shadowChecks.remove(t);
                        // noinspection all
                        ShadowCheck sc = (ShadowCheck) t;
                        if ((sc.flag & ~AccessFlag.PRIVATE) != (fs.accesses.flag & (AccessFlag.STATIC | AccessFlag.FINAL))) {
                            // 排除我有final你没有的情况
                            if ((sc.flag & AccessFlag.FINAL) == 0)
                                throw new NiximException(data.name + '.' + fs.name() + "  Nixim字段 static/final存在与否不匹配");
                        }
                    }
                }
                for (int i = 0; i < methods.size(); i++) {
                    MethodSimple fs = methods.get(i);
                    tester.name = fs.name();
                    tester.desc = fs.rawDesc();
                    DescEntry t = nx.shadowChecks.find(Helpers.cast(tester));
                    // noinspection all
                    if (t instanceof ShadowCheck) {
                        nx.shadowChecks.remove(t);
                        // noinspection all
                        ShadowCheck sc = (ShadowCheck) t;
                        if ((sc.flag & ~AccessFlag.FINAL) != (fs.accesses.flag & (AccessFlag.STATIC | AccessFlag.PRIVATE))) {
                            // 排除我没有private你有的情况
                            if ((sc.flag & AccessFlag.PRIVATE) != 0)
                                throw new NiximException(
                                        data.name + '.' + fs.name() + "  Nixim方法 private/static存在与否不匹配");
                        }
                    }
                }
                // region 检查存在性
                if (!nx.shadowChecks.isEmpty()) {
                    throw new NiximException("以下Shadow对象没有在目标找到, 源: " + nx.self + ": " + nx.shadowChecks + ", 目标的方法: " + data.methods);
                }
                // endregion
            }
            // endregion
            // region 创建BSM并更新ID
            if (nx.bsm != null && !nx.bsm.isEmpty()) {
                AttrBootstrapMethods selfBSM;
                Attribute attr = data.attrByName("BootstrapMethods");
                if (attr == null) {
                    data.attributes.putByName(selfBSM = new AttrBootstrapMethods());
                } else if (attr instanceof AttrBootstrapMethods) {
                    selfBSM = (AttrBootstrapMethods) attr;
                } else {
                    selfBSM = new AttrBootstrapMethods(new ByteReader(attr.getRawData()), data.cp);
                    data.attributes.putByName(selfBSM);
                }

                for (IntMap.Entry<LambdaInfo> entry : nx.bsm.entrySet()) {
                    int newId = selfBSM.methods.size();
                    LambdaInfo info = entry.getValue();
                    selfBSM.methods.add(info.bootstrapMethod);

                    List<InvokeDynInsnNode> nodes = info.nodes;
                    for (int i = 0; i < nodes.size(); i++) {
                        nodes.get(i).bootstrapTableIndex = newId;
                    }
                }
            }
            // endregion
            // region 实施 Inject (3/3)
            if(!nx.injectMethod.isEmpty()) {
                for (int i = 0; i < methods.size(); i++) {
                    MethodNode ms = methods.get(i);
                    tester.name = ms.name();
                    tester.desc = ms.rawDesc();
                    InjectState state = nx.injectMethod.remove(tester);
                    if (state != null) {
                        doInject(state, data, methods, i);
                    }
                }
                // region 检查存在性
                if (!nx.injectMethod.isEmpty()) {
                    // noinspection all
                    for (Iterator<InjectState> itr = nx.injectMethod.values().iterator(); itr.hasNext(); ) {
                        InjectState state = itr.next();
                        // FLAG_OPTIONAL
                        if ((state.flags & 1) != 0) {
                            itr.remove();
                        }
                    }
                    throw new NiximException("以下Inject方法没有在目标找到, 源: " + nx.self + ": " + nx.injectMethod.keySet() + ", 目标的方法: " + data.methods);
                }
                // endregion
            }
            // endregion
            // region 复制方法, lambda, 字段和初始化器
            if(!nx.copyMethod.isEmpty())
                methods.addAll(Helpers.cast(nx.copyMethod));
            if (!nx.copyField.isEmpty()) {
                fields.addAll(Helpers.cast(nx.copyField.keySet()));
                for (Iterator<FieldInit> itr = nx.copyField.values().iterator(); itr.hasNext(); ) {
                    FieldInit val = itr.next();
                    if (val != null) {
                        Method clinit;
                        int clinitid = data.getMethodByName("<clinit>");
                        if (clinitid >= 0) {
                            MethodSimple msClinit = methods.get(clinitid);
                            clinit = new Method(data, msClinit);
                        } else {
                            clinit = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, data, "<clinit>", "()V");
                            clinit.code = new AttrCode(clinit);
                            methods.add(Helpers.cast(clinit));
                        }
                        AttrCode code = clinit.code;
                        code.computeFrames = true;
                        do {
                            code.localSize = (char) Math.max(val.localSize, code.localSize);
                            code.stackSize = (char) Math.max(val.stackSize, code.stackSize);
                            code.instructions.addAll(val.insn);
                            List<GotoInsnNode> gotos = val.gotos;
                            if (!gotos.isEmpty()) {
                                LabelInsnNode label = new LabelInsnNode();
                                for (int i = 0; i < gotos.size(); i++) {
                                    GotoInsnNode gotox = gotos.get(i);
                                    gotox.setTarget(label);
                                }
                                code.instructions.add(label);
                            }
                            if (!itr.hasNext()) break;
                            val = itr.next();
                        } while (true);
                        code.instructions.add(new NPInsnNode(RETURN));
                        break;
                    }
                }
            }
            // endregion

            try {
                data.verify();
            } catch (IllegalArgumentException e) {
                throw new NiximException("验证失败 " + data.name, e);
            }

            ctx.get();
            nx = nx.next;
        }

        if (debug) {
            try (FileOutputStream fos = new FileOutputStream(ctx.getName().replace('/', '.'))) {
                ctx.get().writeToStream(fos);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return ctx.get();
    }

    private static void doInject(InjectState s, ConstantData data, List<? extends MethodNode> methods, int index) throws NiximException {
        if (!methods.get(index).rawDesc().equals(s.method.rawDesc()))
            throw new NiximException("目标与Nixim方法返回值不匹配 " + methods.get(index));
        switch (s.at) {
            case "REPLACE":
                methods.set(index, Helpers.cast(s.method));
                break;
            case "HEAD": {
                Method tm = new Method(data, (MethodSimple) methods.get(index));
                InsnList insn = tm.code.instructions;

                InsnNode entryPoint = insn.get(0);
                List<GotoInsnNode> gotos = s.gotos();
                for (int i = 0; i < gotos.size(); i++) {
                    gotos.get(i).setTarget(entryPoint);
                }

                int pl = computeParamLength(tm, null);
                InsnList insn2 = s.method.code.instructions;
                // 恢复备份的变量 todo
                if (s.assignId != null) {
                    for (Int2IntMap.Entry entry : s.assignId.entrySet()) {
                        int tKey = entry.getKey() - 1 + pl;
                        NodeHelper.compress(insn2, (byte) entry.v, tKey);
                        byte tCode = (byte) (entry.v + (ISTORE - ILOAD));
                        NodeHelper.compress(insn2, tCode, entry.getKey());
                    }
                }
                insn.addAll(0, insn2);
                tm.code.computeFrames = true;
                tm.code.reIndex(new ConstantWriterEmpty());
                System.out.println(tm.code);
                methods.set(index, Helpers.cast(tm));
            }
            break;
            case "MIDDLE_MATCHING":
            case "MIDDLE_ORDINAL":
                throw new NiximException("qing");
            case "TAIL": {
                System.err.println("TAIL NOT OK YET");
                Method tm = new Method(data, (MethodSimple) methods.get(index));
                Int2IntMap assignedLV = new Int2IntMap();
                int pl = computeParamLength(tm, assignedLV);

                InsnList insn = tm.code.instructions;
                for (int i = 0; i < insn.size(); i++) {
                    InsnNode node = insn.get(i);
                    // 检测参数的assign然后做备份
                    int i2 = NodeHelper.getIndex(node);
                    if (i2 >= 0) {
                        int code = node.getOpcodeInt();
                        if (code >= OpcodesInt.ISTORE && code <= OpcodesInt.ASTORE_3) {
                            Int2IntMap.Entry entry = assignedLV.getEntry(i2);
                            if (entry != null) {
                                if (code >= ISTORE_0)
                                    code = (byte) (((code - ISTORE_0) / 4) + ISTORE);
                                entry.v = code;
                            }
                            // needn't check assign: was loaded
                        }
                    } else if (i > 0 && NodeHelper.isReturn(node.code)) {
                        GotoInsnNode Goto = new GotoInsnNode();
                        s.gotos().add(Goto);
                        // 将返回值（如果存在）存放到指定的变量
                        // 将目标方法中return替换成goto开头
                        // X_STORE, then goto... todo
                        insn.set(i, Goto);
                    }
                }
                // 这里只是修改的，还要和tail用到的取交集 todo
                // 计算tail用到的参数id，若目标方法修改过value则暂存到新的变量id中然后再恢复
                if (!assignedLV.isEmpty()) {
                    InsnList prepend = new InsnList();
                    for (Iterator<Int2IntMap.Entry> itr = assignedLV.entrySet().iterator(); itr.hasNext(); ) {
                        Int2IntMap.Entry entry = itr.next();
                        if (entry.v != 0) {
                            NodeHelper.compress(prepend, (byte) entry.v, entry.getKey());
                            int tKey = entry.getKey() - 1 + pl;
                            byte tCode = (byte) (entry.v + (ISTORE - ILOAD));
                            NodeHelper.compress(prepend, tCode, tKey);
                            // re-load 加上恢复用的指令
                            NodeHelper.compress(insn, (byte) entry.v, tKey);
                        } else {
                            itr.remove();
                        }
                    }
                    if (!assignedLV.isEmpty()) {
                        insn.addAll(0, prepend);
                    }
                }
                insn.addAll(s.method.code.instructions);
            }
            break;
            case "OLD_SUPER_INJECT": {
                ((MethodSimple) methods.get(index)).name = data.writer.getUtf(s.sijName);
                methods.add(Helpers.cast(s.method));
            }
            break;
        }
    }

    // endregion
    // region 读取

    public void read(@Nonnull final byte[] bytes) throws NiximException {
        Context ctx = new Context("", bytes);

        System.out.println("NiximRead " + ctx.getData().name);

        NiximData nx = read0(ctx);
        if (nx != null) {
            nx.next = registry.put(nx.dest, nx);
        } else {
            throw new NiximException("对象没有使用Nixim");
        }
    }

    public static final String A_NIXIM_CLASS_FLAG = ParamHelper.classDescriptor(Nixim.class);
    public static final String A_INJECT           = ParamHelper.classDescriptor(Inject.class);
    public static final String A_IMPL_INTERFACE   = ParamHelper.classDescriptor(ImplInterface.class);
    public static final String A_SHADOW           = ParamHelper.classDescriptor(Shadow.class);
    public static final String A_COPY             = ParamHelper.classDescriptor(Copy.class);

    public static final String A_BASE = "RuntimeInvisibleAnnotations";

    /**
     * 读取一个Nixim类
     * @throws NiximException 出现错误
     */
    @Nullable
    public static NiximData read0(Context ctx) throws NiximException {
        ConstantData data = ctx.getData();

        NiximData nx = new NiximData(data.name);
        
        Boolean keepBridge = checkNiximFlag(data, data.attrByName(A_BASE), nx);
        if (keepBridge == null) {
            throw new NiximException(data.name + " 不是有效的Nixim class （没有找到注解）");
        }

        List<MethodSimple> methods = data.methods;
        // region 检测特殊方法, 删除桥接方法
        for (int i = methods.size() - 1; i >= 0; i--) {
            MethodSimple method = methods.get(i);
            String name = method.name();
            if (name.startsWith(SPEC_M_CONTINUE) || name.startsWith(SPEC_M_RETVAL)) {
                if (method.attrByName(A_BASE) != null) {
                    throw new NiximException("特殊方法($$$)不能包含注解");
                }
                if (!method.accesses.hasAny(AccessFlag.STATIC))
                    throw new NiximException("特殊方法($$$)必须是static的");
                if (!method.rawDesc().contains("()"))
                    throw new NiximException("特殊方法($$$)不能有参数");
            }
            if (!keepBridge && method.accesses.hasAny(AccessFlag.VOLATILE_OR_BRIDGE)) {
                methods.remove(i);
            }
        }
        // endregion
        String destClass = nx.dest;
        // region 检测并处理 Shadow 注解
        processShadow(ctx, true, destClass, nx.shadowChecks);
        processShadow(ctx, false, destClass, nx.shadowChecks);
        // endregion
        MyHashSet<RemapEntry> entries = new MyHashSet<>(nx.shadowChecks);
        // region 检测并处理(一半) Copy 注解
        for (int i = methods.size() - 1; i >= 0; i--) {
            MethodSimple method = methods.get(i);
            Map<String, AnnVal> copy = getAnnotation(data.cp, method, A_COPY);
            if (copy != null) {
                if (copy.containsKey("staticInitializer") || copy.containsKey("targetIsFinal"))
                    throw new NiximException("staticInitializer/targetIsFinal属性只能用在字段上！位置: " + data.name + '.' + method.name + " " + method.type.getString());
                AnnValString newName = (AnnValString) copy.get("newName");

                RemapEntry entry = new RemapEntry(method);
                entry.toClass = destClass;
                entry.toName = newName == null ? null : newName.value;
                entries.add(entry);

                if (newName != null)
                    method.name = data.writer.getUtf(newName.value);
                nx.copyMethod.add(Helpers.cast(method));

                methods.remove(i);
            }
        }
        List<FieldSimple> fields = data.fields;
        Map<FieldSimple, MethodSimple> tmpCopyFields = new MyHashMap<>();
        for (int i = fields.size() - 1; i >= 0; i--) {
            FieldSimple field = fields.get(i);
            Map<String, AnnVal> copy = getAnnotation(data.cp, field, A_COPY);
            if (copy != null) {
                MethodSimple staticInitializer = null;

                AnnValString val = (AnnValString) copy.get("staticInitializer");
                if (val != null) {
                    int id = data.getMethodByName(val.value);
                    if(id == -1)
                        throw new NiximException("字段的staticInitializer不存在: 名称 " + val.value + " 位置: " + data.name + '.' + field.name);
                    staticInitializer = methods.get(id);
                }
                AnnValInt boolFlag = (AnnValInt) copy.get("targetIsFinal");
                if (boolFlag != null && boolFlag.value == 1)
                    field.accesses.add(AccessFlag.FINAL);

                AnnValString newName = (AnnValString) copy.get("newName");

                RemapEntry entry = new RemapEntry(field);
                entry.toClass = destClass;
                entry.toName = newName == null ? null : newName.value;
                entries.add(entry);

                if (newName != null)
                    field.name = data.writer.getUtf(newName.value);
                tmpCopyFields.put(field, staticInitializer);
                fields.remove(i);
            }
        }
        // endregion

        Map<MethodSimple, Map<String, AnnVal>> tmpInjects = new MyHashMap<>();
        // region 处理 Inject 注解 (1/3)
        for (int i = methods.size() - 1; i >= 0; i--) {
            MethodSimple method = methods.get(i);
            Map<String, AnnVal> map = getAnnotation(data.cp, method, A_INJECT);
            if (map != null) {
                String remapName = ((AnnValString) map.get("value")).value;

                RemapEntry entry = new RemapEntry(method);
                entry.toClass = destClass;
                entry.toName = remapName;
                entries.add(entry);

                method.name = data.writer.getUtf(remapName);
                tmpInjects.put(method, map);
                methods.remove(i);
            }
        }
        // endregion
        RemapEntry tester = new RemapEntry();
        // region 提前检测可能出现的 IllegalAccessError/NoSuchFieldError (应该可以全部检测出来，改过顺序了)
        MyHashSet<DescEntry> inaccessible = new MyHashSet<>();
        boolean isSamePackage = Util.arePackagesSame(data.name, destClass);
        for (int i = 0; i < methods.size(); i++) {
            MethodSimple remain = methods.get(i);
            if (remain.name().startsWith("$$$")) continue;
            int acc = remain.accesses.flag;
            if(((acc & (AccessFlag.PRIVATE | AccessFlag.STATIC)) != AccessFlag.STATIC) ||
                    ((acc & AccessFlag.PUBLIC) == 0 && !isSamePackage)) {
                inaccessible.add(new DescEntry(remain));
            }
        }
        for (int i = 0; i < fields.size(); i++) {
            FieldSimple remain = fields.get(i);
            int acc = remain.accesses.flag;
            if(((acc & (AccessFlag.PRIVATE | AccessFlag.STATIC)) != AccessFlag.STATIC) ||
                    ((acc & AccessFlag.PUBLIC) == 0 && !isSamePackage)) {
                inaccessible.add(new DescEntry(remain));
            }
        }

        MyCodeVisitor cv = new MyCodeVisitor(data, inaccessible, tester, destClass);
        for (MethodSimple entry : tmpInjects.keySet()) {
            cv.tester2.name = entry.name();
            cv.tester2.desc = entry.rawDesc();
            cv.MyVisit(entry.attrByName("Code").getRawData());
        }
        cv.tester2 = null;
        List<MethodSimple> copyMethod1 = Helpers.cast(nx.copyMethod);
        for (MethodSimple entry : copyMethod1) {
            cv.MyVisit(entry.attrByName("Code").getRawData());
        }
        // endregion
        // region 统一在常量模式做映射，降低在操作码模式的工作量
        List<CstRef> refs = ctx.getMethodConstants();
        for (int i = 0; i < refs.size(); i++) {
            CstRef ref = refs.get(i);
            if (!ref.getClassName().equals(data.name)) continue;
            RemapEntry target = entries.find(tester.read(ref));
            if (target != tester) {
                String name = target.toClass;
                if (name != null && !name.equals(data.name))
                    ref.setClazz(data.writer.getClazz(name));
                name = target.toName;
                if (name != null && !name.equals(tester.name))
                    ref.desc(data.writer.getDesc(name, tester.desc));
            }
        }
        refs = ctx.getFieldConstants();
        for (int i = 0; i < refs.size(); i++) {
            CstRef ref = refs.get(i);
            if (!ref.getClassName().equals(data.name)) continue;
            RemapEntry target = entries.find(tester.read(ref));
            if (target != tester) {
                String name = target.toClass;
                if (name != null && !name.equals(data.name))
                    ref.setClazz(data.writer.getClazz(name));
                name = target.toName;
                if (name != null && !name.equals(tester.name))
                    ref.desc(data.writer.getDesc(name, tester.desc));
            }
        }
        List<CstClass> clzs = ctx.getClassConstants();
        for (int i = 0; i < clzs.size(); i++) {
            CstClass clz = clzs.get(i);
            if (clz.getValue().getString().equals(data.name)) {
                clz.setValue(data.writer.getUtf(destClass));
            }
        }
        entries.clear();
        for (Iterator<ShadowCheck> itr = nx.shadowChecks.iterator(); itr.hasNext(); ) {
            ShadowCheck sc = itr.next();
            itr.remove();
            sc.name = sc.toName;
            nx.shadowChecks.add(sc);
        }
        // endregion

        Attribute attr = data.attrByName("BootstrapMethods");
        AttrBootstrapMethods bsms = attr == null ? null : new AttrBootstrapMethods(new ByteReader(attr.getRawData()), data.cp);
        IntMap<LambdaInfo> lambdaBSM = nx.bsm = bsms == null ? null : new IntMap<>(bsms.methods.size());
        List<Method> copyMethod = nx.copyMethod;
        // region 后处理 Copy 注解
        for (int i = 0; i < copyMethod.size(); i++) {
            MethodSimple ms = copyMethod1.get(i);
            Method method = new Method(data, ms);

            if (bsms != null) {
                InsnList insn = method.code.instructions;
                for (int j = 0; j < insn.size(); j++) {
                    InsnNode node = insn.get(j);
                    if (node.code == INVOKEDYNAMIC) {
                        InvokeDynInsnNode idn = (InvokeDynInsnNode) node;
                        processInvokeDyn(idn, data.name, destClass);
                        AttrBootstrapMethods.BootstrapMethod bsm = bsms.methods.get(idn.bootstrapTableIndex);
                        lambdaBSM.computeIfAbsentSp(idn.bootstrapTableIndex, () -> new LambdaInfo(bsm)).nodes
                                .add(idn);
                    }
                }
            }

            postProcNxMd(destClass, method);

            copyMethod.set(i, method);
        }
        Map<Field, FieldInit> copyField = nx.copyField;
        for (Map.Entry<FieldSimple, MethodSimple> entry : tmpCopyFields.entrySet()) {
            Field field = new Field(data, entry.getKey());
            MethodSimple ms = entry.getValue();
            FieldInit iz = null;
            AttrCode code = ms == null ? null : new AttrCode(ms, ms.attrByName("Code").getRawData(), data.cp);
            if (code != null) {
                InsnList insn = code.instructions;
                // noinspection all
                while (insn.remove(insn.size() - 1).code != RETURN);
                List<GotoInsnNode> gotos = new ArrayList<>();
                for (int i = 0; i < insn.size(); i++) {
                    InsnNode node = insn.get(i);
                    switch (node.code) {
                        case RETURN:
                            GotoInsnNode gt = new GotoInsnNode();
                            gotos.add(gt);
                            insn.set(i, gt);
                            break;
                        case PUTFIELD:
                        case PUTSTATIC:
                            FieldInsnNode fin = (FieldInsnNode) node;
                            if (!fin.name.equals(field.name) || !fin.type.equals(field.type)) {
                                System.out.println("NiximWarn: 在static{}修改了不属于自己的字段 " + fin);
                            }
                            break;
                    }
                }
                iz = new FieldInit();
                iz.insn = insn;
                iz.stackSize = code.stackSize;
                iz.localSize = code.localSize;
                iz.gotos = gotos;
            }
            copyField.put(field, iz);
        }
        tmpCopyFields.clear();
        // endregion
        // region 处理 Inject 注解 (2/3)
        for (Map.Entry<MethodSimple, Map<String, AnnVal>> entry : tmpInjects.entrySet()) {
            MethodSimple ms = entry.getKey();
            Map<String, AnnVal> map = entry.getValue();

            Method remap = new Method(data, ms);
            remap.name = ((AnnValString) map.get("value")).value;

            postProcNxMd(destClass, remap);

            InjectState state = new InjectState(remap, map);

            InsnList insn = remap.code.instructions;
            for (int i = 0; i < insn.size(); i++) {
                InsnNode node = insn.get(i);
                switch (node.code) {
                    case INVOKESPECIAL: {
                        InvokeInsnNode inv = (InvokeInsnNode) node;
                        if (remap.name.equals("<init>") && inv.owner().equals("//MARKER")) {
                            state.superCallEnd = i + 1;
                        }
                    }
                    // not check fallthrough
                    case INVOKEVIRTUAL:
                    case INVOKEINTERFACE:
                    case INVOKESTATIC: {
                        InvokeInsnNode inv = (InvokeInsnNode) node;
                        if (inv.owner.equals("//MARKER")) {
                            if (state.at.equals("OLD_SUPER_INJECT"))
                                state.delegations().add(inv);
                            inv.owner = destClass;
                        }
                    }
                    break;
                    case INVOKEDYNAMIC:
                        if (bsms == null) {
                            throw new NiximException("在没有BootstrapMethods的类中找到了InvokeDynamic!");
                        }
                        InvokeDynInsnNode idn = (InvokeDynInsnNode) node;
                        processInvokeDyn(idn, data.name, destClass);
                        AttrBootstrapMethods.BootstrapMethod bsm = bsms.methods.get(idn.bootstrapTableIndex);
                        lambdaBSM.computeIfAbsentSp(idn.bootstrapTableIndex, () -> new LambdaInfo(bsm)).nodes
                                .add(idn);
                        break;
                }
            }

            processInject(state);

            nx.injectMethod.put(new DescEntry(remap), state);
        }
        // endregion
        // region 复制用到的 lambda 方法
        if (lambdaBSM != null)
        for (LambdaInfo info : lambdaBSM.values()) {
            List<Constant> args = info.bootstrapMethod.arguments;
            find:
            for (int i = 0; i < args.size(); i++) {
                Constant c = args.get(i);
                if (c.type() != CstType.METHOD_HANDLE) continue;
                CstMethodHandle handle = (CstMethodHandle) c;
                CstRef ref = handle.getRef();

                if (ref.getClassName().equals(data.name)) {
                    ref.setClazz(new CstClass(destClass));
                    throw new NiximException("测试异常！没转换lambda的ref");
                }

                for (MethodSimple method : data.methods) {
                    if (method.name.equals(ref.desc().getName()) && method.type.equals(ref.desc().getType())) {
                        Method lmd = new Method(data, method);

                        postProcNxMd(destClass, lmd);

                        copyMethod.add(lmd);
                        break find;
                    }
                }

                throw new NiximException("无法找到符合条件的 lambda 方法: " + ref.desc());
            }
        }
        // endregion

        return nx.isUsed() ? nx : null;
    }

    // 核心之一 (2/4)
    private static void processInject(InjectState s) throws NiximException {
        Method method = s.method;
        switch (s.at) {
            // 替换无需修改
            case "REPLACE":
                return;
            case "HEAD": {
                Int2IntMap assignedLV = new Int2IntMap();
                int paramLength = computeParamLength(method, assignedLV);
                InsnList insn = method.code.instructions;
                insn.remove(insn.size() - 1);
                for (int i = 0; i < insn.size(); i++) {
                    InsnNode node = insn.get(i);
                    // 检测参数的assign然后做备份
                    int index = NodeHelper.getIndex(node);
                    if (index >= 0) {
                        int code = node.getOpcodeInt();
                        if (code >= OpcodesInt.ISTORE && code <= OpcodesInt.ASTORE_3) {
                            Int2IntMap.Entry entry = assignedLV.getEntry(index);
                            if (entry != null) {
                                if (code >= ISTORE_0)
                                    code = (byte) (((code - ISTORE_0) / 4) + ISTORE);
                                entry.v = code;
                            } else if (index < paramLength)
                                throw new NiximException("无效的assign " + method + "# " + insn.get(i));
                        }
                    } else if (i > 0 && NodeHelper.isReturn(node.code)) {
                        node = insn.get(i - 1);
                        if (node.code == INVOKESTATIC) {
                            InvokeInsnNode iin = (InvokeInsnNode) node;
                            // 用特殊的字段名(startWith: $$$CONTINUE)指定【我还要继续执行】
                            if (iin.name.startsWith(SPEC_M_CONTINUE)) {
                                GotoInsnNode Goto = new GotoInsnNode();
                                s.gotos().add(Goto);
                                insn.remove(i - 1)._i_replace(Goto);
                                insn.set(i - 1, Goto);
                            }
                        }
                    }
                }
                // 备份参数
                if (!assignedLV.isEmpty()) {
                    InsnList prepend = new InsnList();
                    for (Iterator<Int2IntMap.Entry> itr = assignedLV.entrySet().iterator(); itr.hasNext(); ) {
                        Int2IntMap.Entry entry = itr.next();
                        if (entry.v != 0) {
                            NodeHelper.compress(prepend, (byte) entry.v, entry.getKey());
                            int tKey = entry.getKey() - 1 + paramLength;
                            byte tCode = (byte) (entry.v + (ISTORE - ILOAD));
                            System.out.println(toString0(tCode));
                            NodeHelper.compress(prepend, tCode, tKey);
                        } else {
                            itr.remove();
                        }
                    }
                    if (!assignedLV.isEmpty()) {
                        insn.addAll(0, prepend);
                        s.assignId = assignedLV;
                    }
                }
            }
            break;
            case "MIDDLE_MATCHING":
            case "MIDDLE_ORDINAL":
                throw new NiximException("wait");
            case "TAIL": {
                Int2IntMap assignedLV = new Int2IntMap();
                int paramLength = computeParamLength(method, assignedLV);
                InsnList insn = method.code.instructions;
                insn.remove(insn.size() - 1);
                for (int i = s.superCallEnd; i < insn.size(); i++) {
                    InsnNode node = insn.get(i);
                    int index = NodeHelper.getIndex(node);
                    if (index >= 0) {
                        int code = node.getOpcodeInt();
                        if (code >= OpcodesInt.ILOAD && code <= OpcodesInt.ALOAD_3) {
                            // 计算tail用到的参数id，若目标方法修改过value则暂存到新的变量id中然后再恢复
                            Int2IntMap.Entry entry = assignedLV.getEntry(index);
                            if (entry != null) {
                                if (code >= ILOAD_0)
                                    code = (byte) (((code - ILOAD_0) / 4) + ILOAD);
                                entry.v = code;
                            }// else if (index < paramLength)
                             //   throw new NiximException("无效的assign " + method + "# " + insn.get(i));
                        } else if (i > 0 && code >= OpcodesInt.ISTORE && code <= OpcodesInt.ASTORE_3) {
                            node = insn.get(i - 1);
                            if (node.code == INVOKESTATIC) {
                                InvokeInsnNode iin = (InvokeInsnNode) node;
                                // 将返回值（如果存在）存放到指定的变量，用特殊的字段名(startWith: $$$RETURN_VAL)指定
                                if (iin.name.startsWith(SPEC_M_RETVAL)) {
                                    s.retVal().add(i - 1);
                                    System.out.println("(todo)Replacing getField at " + i);
                                }
                            }
                        }
                    }
                }
                // 备份参数
                if (!assignedLV.isEmpty()) {
                    // noinspection all
                    for (Iterator<Int2IntMap.Entry> itr = assignedLV.entrySet().iterator(); itr.hasNext(); ) {
                        Int2IntMap.Entry entry = itr.next();
                        if (entry.v == 0) {
                            itr.remove();
                        }
                    }
                    if (!assignedLV.isEmpty())
                        s.assignId = assignedLV;
                }
            }
            break;
            // 注入super方法
            case "OLD_SUPER_INJECT": {
                List<InvokeInsnNode> sd = s.delegations();
                if (sd.isEmpty()) {
                    System.out.println("No super call found");
                    s.at = "REPLACE";
                    return;
                }
                String sijName = s.sijName = method.name + "_" + (System.nanoTime() % 10000);
                for (int i = 0; i < sd.size(); i++) {
                    sd.get(i).name = sijName;
                }
            }
            break;
        }
        if (s.superCallEnd != 0) {
            method.code.instructions.removeRange(0, s.superCallEnd);
        }
    }

    private static int computeParamLength(Method method, Int2IntMap assigned) {
        int paramLength = !method.accesses.hasAny(AccessFlag.STATIC) ? 1 : 0;
        List<Type> params = method.parameters();
        for (int i = 0; i < params.size(); i++) {
            Type t = params.get(i);
            if (assigned != null)
                assigned.putInt(paramLength + 1, 0);
            paramLength += t.length();
        }
        return paramLength;
    }

    private static void processInvokeDyn(InvokeDynInsnNode ind, String name, String dest) {
        for (Type type : ind.parameters()) {
            if (name.equals(type.owner)) {
                type.owner = dest;
            }
        }

        if (name.equals(ind.returnType().owner)) {
            ind.returnType().owner = dest;
        }
    }

    private static void processShadow(Context ctx, boolean b, String targetDef, Set<ShadowCheck> shadowChecks) {
        ConstantData data = ctx.getData();
        List<? extends SimpleComponent> target = b ? data.fields : data.methods;
        for (int j = target.size() - 1; j >= 0; j--) {
            SimpleComponent obj = target.get(j);
            Map<String, AnnVal> shadow = getAnnotation(data.cp, obj, A_SHADOW);
            if (shadow != null) {
                ShadowCheck check = new ShadowCheck(obj);

                AnnValString owner = (AnnValString) shadow.get("owner");
                check.toClass = owner == null ? targetDef : owner.value;
                check.toName = ((AnnValString) shadow.get("value")).value;

                check.flag = (byte) (obj.accesses.flag & (AccessFlag.FINAL | AccessFlag.STATIC | AccessFlag.PRIVATE));
                shadowChecks.add(check);

                target.remove(j);
            }
        }
    }

    private static boolean postProcNxMd(String dst, Method m) throws NiximException {
        if(m.code == null)
            throw new NiximException("方法不能是抽象的: " + m.owner + '.' + m.name + ' ' + m.rawDesc());

        if (m.rawDesc().contains(m.owner)) {
            List<Type> params = m.parameters();
            boolean ch = false;
            for (int i = 0; i < params.size(); i++) {
                Type type = params.get(i);
                if (m.owner.equals(type.owner)) {
                    type.owner = dst;
                    ch = true;
                }
            }
            if (m.owner.equals(m.getReturnType().owner)) {
                ch = true;
                m.getReturnType().owner = dst;
            }

            if (ch)
                m.resetParam(true);
        }

        AttributeList ca = m.code.attributes;
        ca.removeByName("LocalVariableTable");
        ca.removeByName("LocalVariableTypeTable");

        AttrAnnotation anno = m.getInvisibleAnnotations();
        if (anno != null) {
            List<Annotation> annos = anno.annotations;
            for (int i = annos.size() - 1; i >= 0; i--) {
                if (annos.get(i).type.owner.startsWith("roj/asm/nixim"))
                    annos.remove(i);
            }
        }

        return m.code.frames != null;
    }

    private static Boolean checkNiximFlag(ConstantData data, Attribute attribute, NiximData nx) {
        if (attribute == null)
            return null;
        AttrAnnotation attrAnn = new AttrAnnotation(false, new ByteReader(attribute.getRawData()), data.cp);

        boolean keepBridge = false;
        List<Annotation> anns = attrAnn.annotations;
        for (int j = 0; j < anns.size(); j++) {
            Annotation ann = anns.get(j);
            if (ann.rawDesc.equals(A_NIXIM_CLASS_FLAG)) {
                if (ann.values != null) {
                    AnnVal av = ann.values.get("value");
                    nx.dest = ((AnnValString) av).value.replace('.', '/');
                    av = ann.values.get("copyItf");
                    if (av != null) {
                        if (((AnnValInt) av).value == 1) {
                            List<CstClass> cstClasses = data.interfaces;
                            for (int i = 0; i < cstClasses.size(); i++) {
                                CstClass clz = cstClasses.get(i);
                                nx.addItfs.add(clz.getValue().getString());
                            }
                        }
                    }
                    AnnValInt avi = (AnnValInt) ann.values.get("checkBridge");
                    if(avi != null && avi.value == 1)
                        keepBridge = true;
                }
            } else if (ann.rawDesc.equals(A_IMPL_INTERFACE)) {
                if (ann.values != null) {
                    List<AnnVal> annVals = ((AnnValArray) ann.values.get("value")).value;
                    for (int i = 0; i < annVals.size(); i++) {
                        AnnValClass clazz = (AnnValClass) annVals.get(i);
                        nx.addItfs.add(clazz.value.owner);
                    }
                }
            }
        }
        return keepBridge;
    }

    private static Map<String, AnnVal> getAnnotation(ConstantPool pool, SimpleComponent cmp, String aClass) {
        Attribute obj = cmp.attrByName(A_BASE);
        if (obj == null)
            return null;
        AttrAnnotation attrAnn = obj instanceof AttrAnnotation ? (AttrAnnotation) obj : new AttrAnnotation(A_BASE, new ByteReader(obj.getRawData()), pool);
        cmp.attributes.putByName(attrAnn);

        List<Annotation> anns = attrAnn.annotations;
        for (int i = 0; i < anns.size(); i++) {
            Annotation ann = anns.get(i);
            if (ann.rawDesc.equals(aClass)) {
                if (ann.values != null) {
                    anns.remove(i);
                    return ann.values;
                }
            }
        }
        return null;
    }

    // endregion
    // region 工具人

    static final class InjectState {
        final  Method method;
        String at;
        final  int    flags, pos;

        int superCallEnd;
        List<?> nodeList;

        // Head
        Int2IntMap         assignId;
        @SuppressWarnings("unchecked")
        public List<GotoInsnNode> gotos() {
            return (List<GotoInsnNode>) nodeList;
        }
        // Head
        // Tail
        @SuppressWarnings("unchecked")
        public List<Integer> retVal() {
            return (List<Integer>) nodeList;
        }
        // Tail
        // SuperInject
        String               sijName;
        @SuppressWarnings("unchecked")
        public List<InvokeInsnNode> delegations() {
            return (List<InvokeInsnNode>) nodeList;
        }
        // SuperInject

        InjectState(Method method, Map<String, AnnVal> map) {
            this.method = method;
            this.at = ((AnnValEnum) map.get("at")).value;
            AnnValInt avi = (AnnValInt) map.get("flags");
            this.flags = avi == null ? 0 : avi.value;
            avi = (AnnValInt) map.get("injectMiddlePosition");
            this.pos = avi == null ? -1 : avi.value;
            this.nodeList = new ArrayList<>();
        }
    }

    public static final class NiximData {
        // Nixim
        public final String self;
        public String       dest;
        public final List<String> addItfs;

        // Inject
        public final Map<DescEntry, InjectState> injectMethod;

        // Copy
        public final MyHashMap<Field, FieldInit> copyField;
        public final List<Method> copyMethod;

        // Shadow
        public final MyHashSet<ShadowCheck> shadowChecks;

        // lambda
        public IntMap<LambdaInfo> bsm;

        NiximData next;

        public NiximData(String self) {
            this.self = self;
            this.addItfs = new ArrayList<>();
            this.injectMethod = new MyHashMap<>();
            this.copyField = new MyHashMap<>();
            this.copyMethod = new ArrayList<>();
            this.shadowChecks = new MyHashSet<>();
        }

        public boolean isUsed() {
            return !addItfs.isEmpty() || !injectMethod.isEmpty() || !copyField.isEmpty() || !copyMethod.isEmpty();
        }
    }

    private static class MyCodeVisitor extends CodeVisitor {
        private final ConstantData         data;
        private final MyHashSet<DescEntry> unaccessible;
        private final RemapEntry tester;
        DescEntry  tester2;
        private final String     destClass;

        public MyCodeVisitor(ConstantData data, MyHashSet<DescEntry> unaccessible, RemapEntry tester, String destClass) {
            this.data = data;
            this.unaccessible = unaccessible;
            this.tester = tester;
            this.tester2 = new DescEntry();
            this.destClass = destClass;

            this.cw = new ConstantWriterEmpty();
            this.bw = new ByteWriter(new ByteList.EmptyByteList());
            this.br = new ByteReader();
        }

        @Override
        public void invoke_interface(CstRefItf itf, short argc) {
            if (tester2 != null) checkInvokeTarget(itf);
        }

        private void checkAccess(CstRef ref) {
            if (ref.getClassName().equals(data.name) && unaccessible.contains(tester.read(ref))) {
                Helpers.throwAny(new NiximException("无法访问" + data.name + '.' + tester + ": 会出现 IllegalAccessError / NoSuchFieldError"));
            }
        }

        @Override
        public void invoke(byte code, CstRef method) {
            if (code == INVOKEVIRTUAL && method.desc().getName().getString().equals("<init>")) {
                System.out.println("INVOKEINIT " + (br.getBytes().get(br.index - 3) == INVOKEVIRTUAL));
                br.getBytes().set(br.index - 3, INVOKESPECIAL);
            }
            checkAccess(method);
            if (tester2 != null) checkInvokeTarget(method);
        }

        private void checkInvokeTarget(CstRef ref) {
            if (ref.getClassName().equals(destClass) && tester.read(ref).equals(tester2)) {
                int id = data.writer.getMethodRefId("//MARKER", ref.desc().getName().getString(), ref.desc().getType().getString());
                br.getBytes().set(br.index - 2, (byte) ((byte) id >> 8));
                br.getBytes().set(br.index - 1, (byte) id);
            }
        }

        @Override
        public void field(byte code, CstRefField field) {
            checkAccess(field);
        }

        public void MyVisit(ByteList code) {
            br.refresh(code);
            visit(data.cp);
        }
    }

    static class FieldInit {
        int stackSize, localSize;
        InsnList insn;
        List<GotoInsnNode> gotos;
    }

    // endregion
}
