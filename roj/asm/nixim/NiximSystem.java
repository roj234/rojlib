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

package roj.asm.nixim;

import roj.asm.Parser;
import roj.asm.TransformException;
import roj.asm.cst.*;
import roj.asm.tree.*;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValEnum;
import roj.asm.tree.anno.AnnValString;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.*;
import roj.asm.tree.attr.AttrLineNumber.LineNumber;
import roj.asm.tree.insn.*;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.*;
import roj.asm.visitor.CodeVisitor;
import roj.collect.Int2IntMap;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CList;
import roj.io.ZipFileWriter;
import roj.mapper.Util;
import roj.util.ByteList;
import roj.util.ByteList.Streamed;
import roj.util.ByteReader;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.io.File;
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
public class NiximSystem {
    protected final Map<String, NiximData> registry = new MyHashMap<>();

    private static ZipFileWriter debugZFW;
    public static boolean debug = false;

    public static final int
        NO_FIELD_MODIFIER_CHECK = 1,
        NO_METHOD_MODIFIER_CHECK = 2,
        SHADOW_OPTIONAL = 4,
        INJECT_OPTIONAL = 2;

    public Map<String, NiximData> getRegistry() {
        return registry;
    }

    public final boolean remove(String target, String source) {
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

    public final boolean remove(String target) {
        return registry.remove(target) != null;
    }

    // region ??????

    public static final String SPEC_M_RETVAL = "$$$RETURN_VAL";
    public static final String SPEC_M_CONTINUE = "$$$CONTINUE";
    public static final String SPEC_M_CONSTRUCTOR = "$$$CONSTRUCTOR";
    public static final String SPEC_M_CONSTRUCTOR_THIS = "$$$CONSTRUCTOR_THIS";
    public static final class SpecMethods {
        public static int $$$RETURN_VAL_I() { return 0; }
        public static long $$$RETURN_VAL_L() { return 0; }
        public static double $$$RETURN_VAL_D() { return 0; }
        public static float $$$RETURN_VAL_F() { return 0; }
        public static byte $$$RETURN_VAL_B() { return 0; }
        public static char $$$RETURN_VAL_C() { return 0; }
        public static short $$$RETURN_VAL_S() { return 0; }

        public static int $$$CONTINUE_I() { return 0; }
        public static long $$$CONTINUE_L() { return 0; }
        public static double $$$CONTINUE_D() { return 0; }
        public static float $$$CONTINUE_F() { return 0; }
        public static byte $$$CONTINUE_B() { return 0; }
        public static char $$$CONTINUE_C() { return 0; }
        public static short $$$CONTINUE_S() { return 0; }
        public static void $$$CONTINUE_V() {}
    }

    public ByteList nixim(String className, ByteList in) throws TransformException {
        NiximData data = registry.remove(className);
        if (data != null) {
            Context ctx = new Context(className, in);
            nixim(ctx, data, 0);
            return ctx.get(false);
        }
        return in;
    }

    public static void nixim(Context ctx, NiximData nx, int flag) throws TransformException {
        if (debug) zipClass(ctx, "in");

        System.out.println("NiximClass " + ctx.getFileName());

        while (nx != null) {
            ConstantData data = ctx.getData();

            // ????????????
            List<String> itfs = nx.addItfs;
            for (int i = 0; i < itfs.size(); i++) {
                data.interfaces.add(data.cp.getClazz(itfs.get(i)));
            }

            DescEntry tester = new DescEntry();
            List<? extends MoFNode> fields = data.fields;
            List<? extends MethodNode> methods = data.methods;
            // region ?????? Shadow ?????????
            if (!nx.shadowChecks.isEmpty()) {
                for (int i = 0; i < fields.size(); i++) {
                    MoFNode fs = fields.get(i);
                    tester.name = fs.name();
                    tester.desc = fs.rawDesc();
                    DescEntry t = nx.shadowChecks.find(Helpers.cast(tester));
                    // noinspection all
                    if (t instanceof ShadowCheck) {
                        nx.shadowChecks.remove(t);
                        if ((flag & NO_FIELD_MODIFIER_CHECK) != 0) continue;
                        // noinspection all
                        ShadowCheck sc = (ShadowCheck) t;
                        if ((sc.flag & ~AccessFlag.PRIVATE) != (fs.accessFlag() & (AccessFlag.STATIC | AccessFlag.FINAL))) {
                            // ????????????final??????????????????
                            if ((sc.flag & AccessFlag.FINAL) == 0)
                                throw new TransformException(data.name + '.' + fs.name() + "  Nixim?????? static/final?????????????????????");
                        }
                    }
                }
                for (int i = 0; i < methods.size(); i++) {
                    MethodNode fs = methods.get(i);
                    tester.name = fs.name();
                    tester.desc = fs.rawDesc();
                    DescEntry t = nx.shadowChecks.find(Helpers.cast(tester));
                    // noinspection all
                    if (t instanceof ShadowCheck) {
                        nx.shadowChecks.remove(t);
                        if ((flag & NO_METHOD_MODIFIER_CHECK) != 0) continue;
                        // noinspection all
                        ShadowCheck sc = (ShadowCheck) t;
                        if ((sc.flag & ~AccessFlag.FINAL) != (fs.accessFlag() & (AccessFlag.STATIC | AccessFlag.PRIVATE))) {
                            // ???????????????private???????????????
                            if ((sc.flag & AccessFlag.PRIVATE) != 0)
                                throw new TransformException(
                                        data.name + '.' + fs.name() + "  Nixim?????? private/static?????????????????????");
                        }
                    }
                }
                // region ???????????????
                if (!nx.shadowChecks.isEmpty() && (flag & SHADOW_OPTIONAL) == 0) {
                    throw new TransformException("??????Shadow???????????????????????????, ???: " + nx.self + ": " + nx.shadowChecks + ", ???????????????: " + data.methods + ", ???????????????: " + data.fields);
                }
                // endregion
            }
            // endregion
            // region ??????BSM?????????ID
            if (nx.bsm != null && !nx.bsm.isEmpty()) {
                AttrBootstrapMethods selfBSM = AttrHelper.getBootstrapMethods(data.cp, data);
                if (selfBSM == null) {
                    data.attributes().putByName(selfBSM = new AttrBootstrapMethods());
                }

                for (IntMap.Entry<LambdaInfo> entry : nx.bsm.entrySet()) {
                    int newId = selfBSM.methods.size();
                    LambdaInfo info = entry.getValue();
                    selfBSM.methods.add(info.bootstrapMethod);

                    List<InvokeDynInsnNode> nodes = info.nodes;
                    for (int i = 0; i < nodes.size(); i++) {
                        nodes.get(i).tableIdx = (char) newId;
                    }
                }
            }
            // endregion
            // region ?????? Inject (3/3)
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
                // region ???????????????
                if (!nx.injectMethod.isEmpty()) {
                    // noinspection all
                    for (Iterator<InjectState> itr = nx.injectMethod.values().iterator(); itr.hasNext(); ) {
                        InjectState state = itr.next();
                        // FLAG_OPTIONAL
                        if ((state.flags & Inject.FLAG_OPTIONAL) != 0) {
                            itr.remove();
                        }
                    }
                    if (!nx.injectMethod.isEmpty() && (flag & INJECT_OPTIONAL) == 0) {
                        throw new TransformException("??????Inject???????????????????????????, ???: " + nx.self + ": " + nx.injectMethod.keySet() + ", ???????????????: " + data.methods);
                    }
                }
                // endregion
            }
            // endregion
            // region ????????????, lambda, ?????????????????????
            if(!nx.copyMethod.isEmpty())
                methods.addAll(Helpers.cast(nx.copyMethod));
            if (!nx.copyField.isEmpty()) {
                fields.addAll(Helpers.cast(nx.copyField.keySet()));
                for (Iterator<Method> itr = nx.copyField.values().iterator(); itr.hasNext(); ) {
                    Method val = itr.next();
                    if (val == null) continue;

                    Method clinit;
                    int m_id = data.getMethodByName("<clinit>");
                    if (m_id >= 0) {
                        MethodNode msClinit = methods.get(m_id);
                        if (msClinit instanceof Method) clinit = (Method) msClinit;
                        else methods.set(m_id, Helpers.cast(clinit = new Method(data, (MethodSimple) msClinit)));
                    } else {
                        clinit = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, data, "<clinit>", "()V");
                        clinit.code = new AttrCode(clinit);
                        clinit.code.instructions.add(NPInsnNode.of(RETURN));
                        methods.add(Helpers.cast(clinit));
                    }
                    AttrCode code = clinit.code;

                    NPInsnNode _return = (NPInsnNode) code.instructions.remove(code.instructions.size() - 1);

                    o:
                    do {
                        String n = "^" + Integer.toString(data.methods.size(), 36);
                        val.name = n;
                        data.methods.add(Helpers.cast(val));
                        code.instructions.add(new InvokeInsnNode(INVOKESTATIC, data.name, n, "()V"));
                        do {
                            if (!itr.hasNext()) break o;
                            val = itr.next();
                        } while (val == null);
                    } while (true);

                    code.instructions.add(_return);
                }
            }
            // endregion

            try {
                data.verify();
            } catch (IllegalArgumentException e) {
                throw new TransformException("???????????? " + data.name, e);
            }

            ctx.set(ctx.get());
            nx = nx.next;
        }

        ctx.set(new ByteList(ctx.get().toByteArray()));

        if (debug) zipClass(ctx, "out");
    }

    private static void zipClass(Context ctx, String id) {
        try {
            if (debugZFW == null)
                debugZFW = new ZipFileWriter(new File("nixim_debug." + System.currentTimeMillis() + ".zip"), false);
            debugZFW.writeNamed(id + '/' + ctx.getFileName(), ctx.get(true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void doInject(InjectState s, ConstantData data, List<? extends MethodNode> methods, int index) throws
        TransformException {
        if (!methods.get(index).rawDesc().equals(s.method.rawDesc()))
            throw new TransformException("?????????Nixim???????????????????????? " + methods.get(index));
        switch (s.at) {
            case "REMOVE":
                methods.remove(index);
                break;
            case "REPLACE": {
                st:
                if (s.method.name.equals("<init>")) {
                    InvokeInsnNode iin = (InvokeInsnNode) s.method.code.instructions.get(s.superCallEnd);
                    if (iin.name.startsWith(SPEC_M_CONSTRUCTOR_THIS)) {
                        iin.name = "<init>";
                        if (iin.rawDesc().equals(s.method.rawDesc()))
                            throw new TransformException("this??????????????????!");
                        iin.owner = data.name;
                        for (int i = 0; i < methods.size(); i++) {
                            MethodNode mn = methods.get(i);
                            if (mn.rawDesc().equals(iin.rawDesc()))
                                break st;
                        }
                        throw new TransformException("???????????????????????????:S");
                    } else {
                        // ???????????????????????????????????????, ??????Object?????????????????????
                        iin.owner = data.parent;
                        if (data.parent.equals("java/lang/Object") && !"()V".equals(iin.rawDesc()))
                            throw new TransformException("?????????????????????????????????????????????(Object parent)\n" +
                                                             "???????????????[???????????????],?????????[????????????]??????");
                    }
                    s.method.code.interpretFlags = AttrCode.COMPUTE_FRAMES;
                    s.method.owner = data.name;
                    iin.name = "<init>";
                }
                methods.set(index, Helpers.cast(s.method));
            }
            break;
            case "HEAD": {
                Method tm = new Method(data, (MethodSimple) methods.get(index));
                InsnList insn = tm.code.instructions;
                int superBegin = 0;
                if (methods.get(index).name().equals("<init>")) {
                    while (superBegin < insn.size()) {
                        InsnNode node = insn.get(superBegin++);
                        if (node.nodeType() == InsnNode.T_INVOKE) {
                            InvokeInsnNode iin = (InvokeInsnNode) node;
                            if (iin.name.equals("<init>") &&
                                    (iin.owner.equals(data.parent) || iin.owner.equals(data.name))) {
                                break;
                            }
                        }
                    }
                    if (superBegin == insn.size())
                        throw new TransformException(data.name + " ????????????: ??????????????????/????????????????????????");
                }

                int pl = computeParamLength(tm, null);
                InsnList insn2 = s.method.code.instructions;
                InsnNode entryPoint;
                if (s.assignId != null) {
                    int size = insn2.size();
                    for (Int2IntMap.Entry entry : s.assignId.entrySet()) {
                        int tKey = entry.getKey() - 1 + pl;
                        NodeHelper.compress(insn2, (byte) (entry.v - (ISTORE - ILOAD)), tKey);
                        NodeHelper.compress(insn2, (byte) entry.v, entry.getKey());
                    }
                    entryPoint = insn2.get(size);
                } else {
                    entryPoint = insn.get(superBegin);
                }

                List<GotoInsnNode> gotos = s.gotos();
                for (int i = 0; i < gotos.size(); i++) {
                    gotos.get(i).setTarget(entryPoint);
                }

                insn.addAll(superBegin, insn2);
                tm.code.interpretFlags = AttrCode.COMPUTE_FRAMES | AttrCode.COMPUTE_SIZES;
                methods.set(index, Helpers.cast(tm));
            }
            break;
            case "MIDDLE": {
                CList occurrence = s.occurrence;
                // todo find it!
            }
            break;
            case "TAIL": {
                Method tm = new Method(data, (MethodSimple) methods.get(index));
                Int2IntMap assignedLV = new Int2IntMap();
                int pl = 0 != (tm.accesses & AccessFlag.STATIC) ? -1 : 0;
                // ???tail????????????????????????
                if (s.assignId != null) {
                    computeParamLength(tm, assignedLV);
                    // noinspection all
                    for (Iterator<Int2IntMap.Entry> itr = assignedLV.entrySet().iterator(); itr.hasNext(); ) {
                        Int2IntMap.Entry entry = itr.next();
                        if (!s.assignId.containsKey(entry.getKey())) itr.remove();
                        else pl = Math.max(entry.getKey(), pl);
                    }
                }
                pl++;

                String ret = ParamHelper.parseReturn(tm.rawDesc()).nativeName();
                byte base = ret.isEmpty() ? RETURN : NodeHelper.X_LOAD(ret.charAt(0));
                InsnList targetInsn = s.method.code.instructions;
                if (tm.name.equals("<init>")) targetInsn.removeRange(0, s.superCallEnd + 1);
                byte type;
                if (base != RETURN) {
                    List<Integer> retVal = s.retVal();
                    if (retVal.size() == 0) {
                        type = 0;
                    } else if (retVal.size() == 1 && retVal.get(0) == 0) {
                        targetInsn.remove(0);
                        type = 1;
                        if (NodeHelper.getVarId(targetInsn.get(0)) == pl) {
                            targetInsn.remove(0);
                            type |= 4; // same var id
                        }
                    } else {
                        for (int i : retVal) {
                            _compress(pl, base, targetInsn, i);
                        }
                        type = 2;
                    }
                    retVal.clear();
                } else {
                    type = 1;
                }

                InsnNode FIRST = targetInsn.get(0);
                int accessedMax = 0;

                InsnList insn = tm.code.instructions;

                for (int i = 0; i < insn.size(); i++) {
                    InsnNode node = insn.get(i);
                    // ???????????????assign???????????????
                    int i2 = NodeHelper.getVarId(node);
                    if (i2 >= 0) {
                        int code = node.getOpcodeInt();
                        if (code >= ISTORE && code <= ASTORE_3) {
                            Int2IntMap.Entry entry = assignedLV.getEntry(i2);
                            if (entry != null) {
                                if (code >= ISTORE_0)
                                    code = (byte) (((code - ISTORE_0) / 4) + ISTORE);
                                entry.v = code;
                            }
                            // needn't check assign: ??????????????????class
                        }
                        accessedMax = Math.max(accessedMax, i2);
                    } else if (i > 0 && NodeHelper.isReturn(node.code)) {
                        GotoInsnNode Goto = new GotoInsnNode();
                        Goto.setTarget(FIRST);
                        // ??????????????????????????????????????????????????????
                        // ??????????????????return?????????goto??????
                        switch (type & 3) {
                            case 0: // ???????????????
                                insn.set(i, NPInsnNode.of(POP));
                                insn.add(i, Goto);
                                break;
                            case 1: // ??????????????????????????????????????????
                                if (i == insn.size() - 1) {
                                    insn.remove(i)._i_replace(FIRST);
                                } else {
                                    insn.set(i, Goto);
                                }
                                if (type == 5) { // todo ??????
                                    insn.remove(i - 1);
                                }
                                break;
                            case 2:
                                if (i == insn.size() - 1) {
                                    _compress(pl, base, insn, i);
                                } else {
                                    insn.add(_compress(pl, base, insn, i), Goto);
                                }
                                break;
                        }
                    }
                }
                // ??????tail???????????????id???????????????????????????value????????????????????????id??????????????????
                if (!assignedLV.isEmpty()) {
                    InsnList prepend = new InsnList();
                    for (Iterator<Int2IntMap.Entry> itr = assignedLV.entrySet().iterator(); itr.hasNext(); ) {
                        Int2IntMap.Entry entry = itr.next();
                        if (entry.v != 0) {
                            byte tCode = (byte) (entry.v - (ISTORE - ILOAD));
                            NodeHelper.compress(prepend, tCode, entry.getKey());
                            int tKey = ++accessedMax;
                            NodeHelper.compress(prepend, (byte) entry.v, tKey);
                            // re-load ????????????????????????
                            NodeHelper.compress(insn, tCode, tKey);
                            NodeHelper.compress(insn, (byte) entry.v, entry.getKey());
                        } else {
                            itr.remove();
                        }
                    }
                    if (!assignedLV.isEmpty()) {
                        insn.addAll(0, prepend);
                    }
                }
                insn.addAll(targetInsn);
                tm.code.interpretFlags = AttrCode.COMPUTE_FRAMES | AttrCode.COMPUTE_SIZES;
                methods.set(index, Helpers.cast(tm));
            }
            break;
            case "OLD_SUPER_INJECT":
                ((MethodSimple) methods.get(index)).name = data.cp.getUtf(s.name);
                methods.add(Helpers.cast(s.method));
            break;
        }
    }

    private static int _compress(int varId, byte base, InsnList targetInsn, int i) {
        if (varId <= 3) {
            targetInsn.set(i, NodeHelper.loadSore(base, varId));
        } else if (varId <= 255) {
            targetInsn.set(i, new U1InsnNode(base, varId));
        } else if (varId <= 65535) {
            targetInsn.set(i, NPInsnNode.of(WIDE));
            targetInsn.add(i + 1, new U2InsnNode(base, varId));
            return i + 2;
        }
        return i + 1;
    }

    // endregion
    // region ??????

    public void load(Object bytes) throws TransformException {
        Context ctx = new Context("", bytes);

        System.out.println("NiximRead " + ctx.getData().name);

        loadCtx(ctx);
    }

    public void loadCtx(Context ctx) throws TransformException {
        NiximData nx = read0(ctx);
        if (nx != null) {
            nx.next = registry.put(nx.dest, nx);
        } else {
            throw new TransformException("??????????????????Nixim");
        }
    }

    public static final String A_NIXIM_CLASS_FLAG = Nixim.class.getName().replace('.', '/');
    public static final String A_INJECT           = Inject.class.getName().replace('.', '/');
    public static final String A_IMPL_INTERFACE   = ImplInterface.class.getName().replace('.', '/');
    public static final String A_SHADOW           = Shadow.class.getName().replace('.', '/');
    public static final String A_COPY             = Copy.class.getName().replace('.', '/');

    public static final String A_BASE = "RuntimeInvisibleAnnotations";

    /**
     * ????????????Nixim???
     * @throws TransformException ????????????
     */
    @Nullable
    @SuppressWarnings("fallthrough")
    public static NiximData read0(Context ctx) throws TransformException {
        ConstantData data = ctx.getData();
        data.normalize();

        NiximData nx = new NiximData(data.name);

        Boolean keepBridge = checkNiximFlag(data, data.attrByName(A_BASE), nx);
        if (keepBridge == null) {
            throw new TransformException(data.name + " ???????????????Nixim class ????????????????????????");
        }

        List<MethodSimple> methods = Helpers.cast(data.methods);
        // region ??????????????????, ??????????????????
        for (int i = methods.size() - 1; i >= 0; i--) {
            MethodSimple method = methods.get(i);
            String name = method.name();
            if (name.startsWith("$$$")) {
                if (method.attrByName(A_BASE) != null) {
                    throw new TransformException("????????????(" + name + ")??????????????????");
                }
                if (!name.startsWith(SPEC_M_CONSTRUCTOR)) {
                    if (0 == (method.accesses & AccessFlag.STATIC))
                        throw new TransformException("????????????(" + name + ")?????????static???");
                    if (!method.rawDesc().startsWith("()")) {
                        throw new TransformException("????????????(" + name + ")???????????????");
                    }
                } else if (!method.rawDesc().endsWith(")V")) {
                    throw new TransformException("?????????????????????(" + name + ")?????????void??????");
                } else if (0 != (method.accesses & AccessFlag.STATIC))
                    throw new TransformException("?????????????????????(" + name + ")?????????static???");
            }
            if (!keepBridge && 0 != (method.accesses & AccessFlag.VOLATILE_OR_BRIDGE)) {
                methods.remove(i);
            }
        }
        // endregion
        String destClass = nx.dest;
        // region ??????????????? Shadow ??????
        processShadow(ctx, true, destClass, nx.shadowChecks);
        processShadow(ctx, false, destClass, nx.shadowChecks);
        // endregion
        MyHashSet<RemapEntry> entries = new MyHashSet<>(nx.shadowChecks);
        // region ???????????????(??????) Copy ??????
        for (int i = methods.size() - 1; i >= 0; i--) {
            MethodSimple method = methods.get(i);
            Annotation copy = getAnnotation(data.cp, method, A_COPY);
            if (copy != null) {
                if (copy.containsKey("staticInitializer") || copy.containsKey("targetIsFinal"))
                    throw new TransformException("staticInitializer/targetIsFinal????????????????????????????????????: " + data.name + '.' + method.name + " " + method.type.getString());
                String newName = copy.getString("newName");

                RemapEntry entry = new RemapEntry(method);
                entry.toClass = destClass;
                entry.toName = newName;
                entries.add(entry);

                if (newName != null) method.name = data.cp.getUtf(newName);
                nx.copyMethod.add(Helpers.cast(method));

                methods.remove(i);
            }
        }
        List<FieldSimple> fields = Helpers.cast(data.fields);
        Map<FieldSimple, MethodSimple> tmpCopyFields = new MyHashMap<>();
        for (int i = fields.size() - 1; i >= 0; i--) {
            FieldSimple field = fields.get(i);
            Annotation copy = getAnnotation(data.cp, field, A_COPY);
            if (copy != null) {
                MethodSimple staticInitializer = null;

                String val = copy.getString("staticInitializer");
                if (val != null) {
                    int id = data.getMethodByName(val);
                    if(id == -1)
                        throw new TransformException("?????????staticInitializer?????????: ?????? " + val + " ??????: " + data.name + '.' + field.name);
                    staticInitializer = methods.get(id);
                    if (!"()V".equals(staticInitializer.rawDesc()) || (staticInitializer.accesses & AccessFlag.STATIC) == 0)
                        throw new TransformException("?????????staticInitializer??????/???????????????: ?????? " + val + " ??????: " + data.name + '.' + field.name);
                }
                int boolFlag = copy.getInt("targetIsFinal", 0);
                if (boolFlag == 1) field.accesses |= AccessFlag.FINAL;

                String newName = copy.getString("newName");

                RemapEntry entry = new RemapEntry(field);
                entry.toClass = destClass;
                entry.toName = newName;
                entries.add(entry);

                if (newName != null) field.name = data.cp.getUtf(newName);
                tmpCopyFields.put(field, staticInitializer);
                fields.remove(i);
            }
        }
        // endregion

        Map<MethodSimple, Annotation> tmpInjects = new MyHashMap<>();
        // region ?????? Inject ?????? (1/3)
        for (int i = methods.size() - 1; i >= 0; i--) {
            MethodSimple method = methods.get(i);
            Annotation map = getAnnotation(data.cp, method, A_INJECT);
            if (map != null) {
                String remapName = map.getString("value");
                if (remapName.equals("/")) remapName = method.name();

                RemapEntry entry = new RemapEntry(method);
                entry.toClass = destClass;
                entry.toName = remapName;
                entries.add(entry);

                map.put("value", new AnnValString(method.name()));
                method.name = data.cp.getUtf(remapName);
                tmpInjects.put(method, map);
                methods.remove(i);
            }
        }
        // endregion
        RemapEntry tester = new RemapEntry();
        // region ??????????????????????????? IllegalAccessError/NoSuchFieldError (????????????????????????????????????????????????)
        MyHashSet<DescEntry> inaccessible = new MyHashSet<>();
        boolean isSamePackage = Util.arePackagesSame(data.name, destClass);
        for (int i = 0; i < methods.size(); i++) {
            MethodSimple remain = methods.get(i);
            if (remain.name().startsWith("$$$")) continue;
            int acc = remain.accesses;
            if(((acc & (AccessFlag.PRIVATE | AccessFlag.STATIC)) != AccessFlag.STATIC) ||
                    ((acc & AccessFlag.PUBLIC) == 0 && !isSamePackage)) {
                inaccessible.add(new DescEntry(remain));
            }
        }
        for (int i = 0; i < fields.size(); i++) {
            FieldSimple remain = fields.get(i);
            int acc = remain.accesses;
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
            // ????????????????????????
            Attribute code = entry.attrByName("Code");
            if (code != null) cv.MyVisit(code.getRawData());
        }
        // endregion
        // region ?????????????????????????????????????????????????????????????????????
        List<CstRef> refs = ctx.getMethodConstants();
        for (int i = 0; i < refs.size(); i++) {
            CstRef ref = refs.get(i);
            if (!ref.getClassName().equals(data.name)) continue;
            RemapEntry target = entries.find(tester.read(ref));
            if (target != tester) {
                String name = target.toClass;
                if (name != null && !name.equals(data.name))
                    ref.setClazz(data.cp.getClazz(name));
                name = target.toName;
                if (name != null && !name.equals(tester.name))
                    ref.desc(data.cp.getDesc(name, tester.desc));
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
                    ref.setClazz(data.cp.getClazz(name));
                name = target.toName;
                if (name != null && !name.equals(tester.name))
                    ref.desc(data.cp.getDesc(name, tester.desc));
            }
        }
        List<CstClass> clzs = ctx.getClassConstants();
        for (int i = 0; i < clzs.size(); i++) {
            CstClass clz = clzs.get(i);
            if (clz.getValue().getString().equals(data.name)) {
                clz.setValue(data.cp.getUtf(destClass));
            }
        }
        entries.clear();
        for (Iterator<ShadowCheck> itr = nx.shadowChecks.iterator(); itr.hasNext(); ) {
            ShadowCheck sc = itr.next();
            itr.remove();
            // ???????????????owner???shadow
            if (!sc.toClass.equals(nx.dest)) continue;
            sc.name = sc.toName;
            nx.shadowChecks.add(sc);
        }
        // endregion

        AttrBootstrapMethods bsms = AttrHelper.getBootstrapMethods(data.cp, data);
        IntMap<LambdaInfo> lambdaBSM = nx.bsm = bsms == null ? null : new IntMap<>(bsms.methods.size());
        List<Method> copyMethod = nx.copyMethod;
        // region ????????? Copy ??????
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
                        AttrBootstrapMethods.BootstrapMethod bsm = bsms.methods.get(idn.tableIdx);
                        lambdaBSM.computeIfAbsentSp(idn.tableIdx, () -> new LambdaInfo(bsm)).nodes
                                .add(idn);
                    }
                }
            }

            postProcNxMd(destClass, method);

            copyMethod.set(i, method);
        }
        Map<Field, Method> copyField = nx.copyField;
        for (Map.Entry<FieldSimple, MethodSimple> entry : tmpCopyFields.entrySet()) {
            Field field = new Field(data, entry.getKey());
            MethodSimple ms = entry.getValue();

            Method m = ms == null ? null : new Method(data, ms);
            copyField.put(field, m);

            if (m == null) continue;

            InsnList insn = m.code.instructions;
            for (int i = 0; i < insn.size(); i++) {
                InsnNode node = insn.get(i);
                if (node.code == PUTSTATIC) {
                    FieldInsnNode fin = (FieldInsnNode) node;
                    if (fin.owner.equals(data.name) && (!fin.name.equals(field.name) || !fin.rawType.equals(
                        field.rawDesc()))) {
                        System.out.println("NiximWarn: ???static{}????????????????????????????????? " + fin);
                    }
                } else if (node.code == INVOKEDYNAMIC) {
                    InvokeDynInsnNode idn = (InvokeDynInsnNode) node;
                    processInvokeDyn(idn, data.name, destClass);
                    AttrBootstrapMethods.BootstrapMethod bsm = bsms.methods.get(idn.tableIdx);
                    lambdaBSM.computeIfAbsentSp(idn.tableIdx, () -> new LambdaInfo(bsm)).nodes.add(idn);
                }
            }
        }
        tmpCopyFields.clear();
        // endregion
        // region ?????? Inject ?????? (2/3)
        for (Map.Entry<MethodSimple, Annotation> entry : tmpInjects.entrySet()) {
            MethodSimple ms = entry.getKey();
            Annotation map = entry.getValue();

            Method remap = new Method(data, ms);

            postProcNxMd(destClass, remap);

            InjectState state = new InjectState(remap, map);

            state.superCallEnd = 0;
            InsnList insn = remap.code.instructions;
            for (int i = 0; i < insn.size(); i++) {
                InsnNode node = insn.get(i);
                switch (node.code) {
                    case INVOKESPECIAL: {
                        InvokeInsnNode inv = (InvokeInsnNode) node;
                        if (remap.name.equals("<init>") && (inv.owner.equals("//MARKER") || inv.owner.equals(data.parent))) {
                            state.superCallEnd = i;
                        }
                    }
                    // not check fallthrough
                    case INVOKEVIRTUAL:
                    case INVOKEINTERFACE:
                    case INVOKESTATIC: {
                        InvokeInsnNode inv = (InvokeInsnNode) node;
                        if (inv.owner.equals("//MARKER")) {
                            if (state.at.equals("OLD_SUPER_INJECT")) {
                                inv.name = state.name;
                                state.flags |= FLAG_HAS_INVOKE;
                            }
                            inv.owner = destClass;
                        }
                    }
                    break;
                    case INVOKEDYNAMIC:
                        if (bsms == null) {
                            throw new TransformException("?????????BootstrapMethods??????????????????InvokeDynamic!");
                        }
                        InvokeDynInsnNode idn = (InvokeDynInsnNode) node;
                        processInvokeDyn(idn, data.name, destClass);
                        AttrBootstrapMethods.BootstrapMethod bsm = bsms.methods.get(idn.tableIdx);
                        lambdaBSM.computeIfAbsentSp(idn.tableIdx, () -> new LambdaInfo(bsm)).nodes
                                .add(idn);
                        break;
                }
            }

            processInject(state, data.parent);

            if (state.superCallEnd < 0 && state.name.equals("<init>"))
                throw new TransformException("???????????? superCallEnd");

            nx.injectMethod.put(new DescEntry(remap), state);
        }
        // endregion
        // region ??????????????? lambda ??????
        if (lambdaBSM != null)
        for (LambdaInfo info : lambdaBSM.values()) {
            List<Constant> args = info.bootstrapMethod.arguments;
            find:
            for (int i = 0; i < args.size(); i++) {
                Constant c = args.get(i);
                if (c.type() != Constant.METHOD_HANDLE) continue;
                CstMethodHandle handle = (CstMethodHandle) c;
                CstRef ref = handle.getRef();

                if (ref.getClassName().equals(data.name)) {
                    ref.setClazz(new CstClass(destClass));
                    throw new TransformException("????????????????????????lambda???ref");
                }
                if (!ref.getClassName().equals(destClass)) {
                    // not self method
                    break;
                }

                List<? extends MethodNode> nodes = data.methods;
                for (int j = 0; j < nodes.size(); j++) {
                    MethodSimple method = (MethodSimple) nodes.get(j);
                    if (method.name.equals(ref.desc().getName()) && method.type.equals(ref.desc().getType())) {
                        Method lmd = new Method(data, method);

                        postProcNxMd(destClass, lmd);

                        copyMethod.add(lmd);
                        break find;
                    }
                }

                throw new TransformException("??????????????????????????? lambda ??????: " + ref.desc());
            }
        }
        // endregion

        return nx.isUsed() ? nx : null;
    }

    // ???????????? (2/4)
    private static void processInject(InjectState s, String parent) throws TransformException {
        Method method = s.method;
        sw:
        switch (s.at) {
            case "REMOVE":
            break;
            case "REPLACE":
                if (method.name.equals("<init>")) {
                    boolean selfInit = s.name.equals("<init>");
                    InsnList insn = method.code.instructions;
                    for (int i = 0; i < insn.size(); i++) {
                        InsnNode node = insn.get(i);
                        if (node.nodeType() == InsnNode.T_INVOKE) {
                            InvokeInsnNode iin = (InvokeInsnNode) node;
                            // ?????? $$$CONSTRUCT??????????????????????????????
                            if (selfInit ? (iin.name.equals("<init>") && (iin.owner.equals(parent))) : iin.name.startsWith(SPEC_M_CONSTRUCTOR)) {
                                iin.setOpcode(INVOKESPECIAL);
                                s.superCallEnd = i;
                                AttributeList attributes = s.method.attributes();
                                attributes.removeByName("LocalVariableTable");
                                attributes.removeByName("LocalVariableTypeTable");
                                AttrLineNumber ln = (AttrLineNumber) attributes.getByName("LineNumberTable");
                                if (ln != null) {
                                    st:
                                    for (int j = 0; j < ln.list.size(); j++) {
                                        LineNumber ln1 = ln.list.get(j);
                                        for (int k = 0; k < i; k++) {
                                            if (insn.get(k) == ln1.node) {
                                                ln.list.remove(j);
                                                break st;
                                            }
                                        }
                                    }
                                }
                                break sw;
                            }
                        }
                    }
                    throw new TransformException("??????????????? " + method.name + ' ' + method.rawDesc() + " ??????????????? " + SPEC_M_CONSTRUCTOR + " ???????????????????????????");
                }
            break;
            case "HEAD": {
                Int2IntMap assignedLV = new Int2IntMap();
                int paramLength = computeParamLength(method, assignedLV);
                InsnList insn = method.code.instructions;
                if (s.superCallEnd > 0)
                    insn.removeRange(0, s.superCallEnd);

                for (int i = 0; i < insn.size(); i++) {
                    InsnNode node = insn.get(i);
                    // ???????????????assign???????????????
                    int index = NodeHelper.getVarId(node);
                    if (index >= 0) {
                        int code = node.getOpcodeInt();
                        if (code >= ISTORE && code <= ASTORE_3) {
                            Int2IntMap.Entry entry = assignedLV.getEntry(index);
                            if (entry != null) {
                                if (code >= ISTORE_0)
                                    code = (byte) (((code - ISTORE_0) / 4) + ISTORE);
                                entry.v = code;
                            } else if (index < paramLength)
                                throw new TransformException("?????????assign " + method + "# " + insn.get(i) + " i " + index + " " + assignedLV);
                        }
                    } else if (i > 0 && NodeHelper.isReturn(node.code)) {
                        node = insn.get(i - 1);
                        if (node.code == INVOKESTATIC) {
                            InvokeInsnNode iin = (InvokeInsnNode) node;
                            // ?????????????????????(startWith: $$$CONTINUE)?????????????????????????????????
                            if (iin.name.startsWith(SPEC_M_CONTINUE)) {
                                GotoInsnNode Goto = new GotoInsnNode();
                                s.gotos().add(Goto);
                                insn.remove(i - 1)._i_replace(Goto);
                                insn.set(i - 1, Goto);
                            }
                        }
                    }
                }
                // ????????????
                if (!assignedLV.isEmpty()) {
                    InsnList prepend = new InsnList();
                    for (Iterator<Int2IntMap.Entry> itr = assignedLV.entrySet().iterator(); itr.hasNext(); ) {
                        Int2IntMap.Entry entry = itr.next();
                        if (entry.v != 0) {
                            byte tCode = (byte) (entry.v - (ISTORE - ILOAD));
                            NodeHelper.compress(prepend, tCode, entry.getKey());
                            int tKey = entry.getKey() - 1 + paramLength;
                            NodeHelper.compress(prepend, (byte) entry.v, tKey);
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
            case "MIDDLE": {
                // todo backup & restore
                System.err.println("?????");
            }
            break;
            case "TAIL": {
                Int2IntMap assignedLV = new Int2IntMap();
                int paramLength = computeParamLength(method, assignedLV);
                InsnList insn = method.code.instructions;

                for (int i = s.superCallEnd; i < insn.size(); i++) {
                    InsnNode node = insn.get(i);
                    int index = NodeHelper.getVarId(node);
                    if (index >= 0) {
                        int code = node.getOpcodeInt();
                        if (code >= ILOAD && code <= ALOAD_3) {
                            // ??????tail???????????????id???????????????????????????value????????????????????????id??????????????????
                            Int2IntMap.Entry entry = assignedLV.getEntry(index);
                            if (entry != null) {
                                if (code >= ILOAD_0)
                                    code = (byte) (((code - ILOAD_0) / 4) + ILOAD);
                                entry.v = code;
                            }/* else if (index < paramLength)
                                throw new TransformException("?????????assign " + method + "# " + insn.get(i));*/
                        } else if (i > 0 && code >= ISTORE && code <= ASTORE_3) {
                            node = insn.get(i - 1);
                            if (node.code == INVOKESTATIC) {
                                InvokeInsnNode iin = (InvokeInsnNode) node;
                                // ??????????????????????????????????????????????????????????????????????????????(startWith: $$$RETURN_VAL)??????
                                if (iin.name.startsWith(SPEC_M_RETVAL)) {
                                    s.retVal().add(i - 1);
                                }
                            }
                        }
                    }
                }
                // ????????????
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
            // ??????super??????
            case "OLD_SUPER_INJECT":
                if ((s.flags & FLAG_HAS_INVOKE) == 0) {
                    s.at = "REPLACE";
                }
            break;
        }
    }

    private static int computeParamLength(Method method, Int2IntMap assigned) {
        int paramLength = 0 != (method.accesses & AccessFlag.STATIC) ? 0 : 1;
        List<Type> params = method.parameters();
        for (int i = 0; i < params.size(); i++) {
            Type t = params.get(i);
            if (assigned != null)
                assigned.putInt(paramLength, 0);
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
        List<? extends MoFNode> target = b ? data.fields : data.methods;
        for (int j = target.size() - 1; j >= 0; j--) {
            MoFNode obj = target.get(j);
            Annotation shadow = getAnnotation(data.cp, obj, A_SHADOW);
            if (shadow != null) {
                ShadowCheck check = new ShadowCheck(obj);

                String owner = shadow.getString("owner");
                check.toClass = owner == null ? targetDef : unifyClassName(owner);
                check.toName = shadow.getString("value");
                if (check.toName.equals("/")) {
                    check.toName = obj.name();
                }

                check.flag = (byte) (obj.accessFlag() & (AccessFlag.FINAL | AccessFlag.STATIC | AccessFlag.PRIVATE));
                shadowChecks.add(check);

                target.remove(j);
            }
        }
    }

    private static String unifyClassName(String t) {
        return t.replace('.', '/');
    }

    private static boolean postProcNxMd(String dst, Method m) throws TransformException {
        if (m.code == null) return true;
        //    throw new TransformException("????????????????????????: " + m.owner + '.' + m.name + ' ' + m.rawDesc());
        if (m.name.equals("<init>") && !m.rawDesc().endsWith(")V"))
            throw new TransformException("??????????????????????????????void: " + m.owner + '.' + m.name + ' ' + m.rawDesc());

        if (m.rawDesc().contains(m.owner)) {
            List<Type> params = m.parameters();
            for (int i = 0; i < params.size(); i++) {
                Type type = params.get(i);
                if (m.owner.equals(type.owner)) {
                    type.owner = dst;
                }
            }
            if (m.owner.equals(m.getReturnType().owner)) {
                m.getReturnType().owner = dst;
            }
        }

        AttributeList ca = m.code.attributes;
        ca.removeByName("LocalVariableTable");
        ca.removeByName("LocalVariableTypeTable");

        AttrAnnotation anno = m.getInvisibleAnnotations();
        if (anno != null) {
            List<Annotation> annos = anno.annotations;
            for (int i = annos.size() - 1; i >= 0; i--) {
                if (annos.get(i).clazz.startsWith("roj/asm/nixim"))
                    annos.remove(i);
            }
        }

        return m.code.frames != null;
    }

    private static Boolean checkNiximFlag(ConstantData data, Attribute attr, NiximData nx) {
        if (attr == null) return null;

        boolean keepBridge = false;
        List<Annotation> anns = AttrAnnotation.parse(data.cp, Parser.reader(attr));
        for (int j = 0; j < anns.size(); j++) {
            Annotation ann = anns.get(j);
            if (ann.clazz.equals(A_NIXIM_CLASS_FLAG)) {
                nx.dest = unifyClassName(ann.getString("value"));
                if (ann.getInt("copyItf", 0) != 0) {
                    List<CstClass> itf = data.interfaces;
                    for (int i = 0; i < itf.size(); i++) {
                        nx.addItfs.add(itf.get(i).getValue().getString());
                    }
                }
                if(ann.getInt("checkBridge", 0) != 0) keepBridge = true;
            } else if (ann.clazz.equals(A_IMPL_INTERFACE)) {
                List<AnnVal> annVals = ann.getArray("value");
                for (int i = 0; i < annVals.size(); i++) {
                    nx.addItfs.add(annVals.get(i).asClass().owner);
                }
            }
        }
        return keepBridge;
    }

    public static Annotation getAnnotation(ConstantPool pool, MoFNode cmp, String aClass) {
        List<Annotation> anns = AttrHelper.getAnnotations(pool, cmp, false);
        if (anns == null) return null;

        for (int i = 0; i < anns.size(); i++) {
            Annotation ann = anns.get(i);
            if (ann.clazz.equals(aClass)) {
                anns.remove(i);
                return ann;
            }
        }
        return null;
    }

    // endregion
    // region ?????????

    static final int FLAG_HAS_INVOKE = 262144;

    static final class InjectState {
        // ???????????????
        String at;
        int flags;

        // replace??????: ????????????, ?????? $$$CONSTRUCTOR??????
        // super inject??????: SIJ?????????
        String name;

        Method method;

        int superCallEnd;
        final List<?> nodeList;

        // Head
        Int2IntMap assignId;
        @SuppressWarnings("unchecked")
        public List<GotoInsnNode> gotos() {
            return (List<GotoInsnNode>) nodeList;
        }
        // Middle
        CList occurrence;
        // Tail
        @SuppressWarnings("unchecked")
        public List<Integer> retVal() {
            return (List<Integer>) nodeList;
        }

        InjectState(Method method, Annotation map) throws TransformException {
            this.method = method;

            AnnValEnum ave = map.getEnum("at");
            this.at = ave == null ? "OLD_SUPER_INJECT" : ave.value;
            this.flags = map.getInt("flags", 0);

            String avs = map.getString("occurrence");
            try {
                this.occurrence = avs == null ? null : JSONParser.parse(avs, JSONParser.LITERAL_KEY).asList();
            } catch (ParseException e) {
                throw new TransformException("????????????occurrence matcher JSON", e);
            }

            switch (this.at) {
                case "HEAD":
                case "TAIL":
                case "OLD_SUPER_INJECT":
                    this.nodeList = new ArrayList<>();
                    break;
                default:
                    this.nodeList = null;
                    break;
            }
            this.name = this.at.equals("OLD_SUPER_INJECT") ?
                    method.name + '_' + (System.nanoTime() % 10000) :
                    map.getString("value");
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
        public final MyHashMap<Field, Method> copyField;
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

            this.cw = new ConstantPoolEmpty();
            this.bw = new Streamed();
            this.br = new ByteReader();
        }

        @Override
        public void invoke_interface(CstRefItf itf, short argc) {
            if (tester2 != null) checkInvokeTarget(itf);
        }

        private void checkAccess(CstRef ref) {
            if (ref.getClassName().equals(data.name) && unaccessible.contains(tester.read(ref))) {
                Helpers.athrow(new TransformException("????????????" + data.name + '.' + tester + ": ????????? IllegalAccessError / NoSuchFieldError"));
            }
        }

        @Override
        public void invoke(byte code, CstRef method) {
            if (code == INVOKEVIRTUAL && method.desc().getName().getString().equals("<init>")) {
                System.out.println("INVOKEINIT " + (br.bytes().get(br.rIndex - 3) == INVOKEVIRTUAL));
                br.bytes().put(br.rIndex - 3, INVOKESPECIAL);
            }
            checkAccess(method);
            if (tester2 != null) checkInvokeTarget(method);
        }

        private void checkInvokeTarget(CstRef ref) {
            if (ref.getClassName().equals(destClass) && tester.read(ref).equals(tester2)) {
                ref.setClazz(data.cp.getClazz("//MARKER"));
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

    // endregion
}
