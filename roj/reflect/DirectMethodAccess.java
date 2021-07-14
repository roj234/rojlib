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

package roj.reflect;

import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.struct.Clazz;
import roj.asm.struct.Field;
import roj.asm.struct.Method;
import roj.asm.struct.attr.AttrCode;
import roj.asm.struct.insn.ClassInsnNode;
import roj.asm.struct.insn.FieldInsnNode;
import roj.asm.struct.insn.InvokeInsnNode;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.asm.util.InsnList;
import roj.asm.util.NodeHelper;
import roj.collect.IBitSet;
import roj.collect.SingleBitSet;

import javax.annotation.Nonnull;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class DirectMethodAccess {
    public static final String MAGIC_ACCESSOR_CLASS = "sun/reflect/MagicAccessorImpl";
    public static boolean DEBUG; // no-op currently

    static final AtomicInteger nextId = new AtomicInteger();

    /**
     * @param i_Cls         实现IInvoker的class
     * @param i_Method      IInvoker的方法名字
     * @param t_Cls_or_Inst 目标的实例or类
     * @param t_Method      方法名字
     */
    public static <T extends Instanced> T get(Class<T> i_Cls, String i_Method, Object t_Cls_or_Inst, String t_Method) {
        if (t_Cls_or_Inst.getClass() == Class.class)
            return getSingle(false, i_Cls, i_Method, (Class<?>) t_Cls_or_Inst, t_Method);

        T t = getSingle(false, i_Cls, i_Method, t_Cls_or_Inst.getClass(), t_Method);
        t.setInstance(t_Cls_or_Inst);
        return t;
    }

    /**
     * @param i_Cls    实现IInvoker的class
     * @param i_Method IInvoker的方法名字
     * @param t_Cls    目标的类
     * @param t_Method 方法名字
     */
    public static <T extends Instanced> T get(Class<T> i_Cls, String i_Method, Class<?> t_Cls, String t_Method) {
        return getSingle(false, i_Cls, i_Method, t_Cls, t_Method);
    }

    /**
     * @param i_Cls    实现IInvoker的class
     * @param i_Flag   IInvoker的方法调用参数
     * @param i_Method IInvoker的方法名字
     * @param t_Cls    目标的类
     * @param t_Method 方法名字
     */
    public static <T extends Instanced> T get(Class<T> i_Cls, String[] i_Method, IBitSet i_Flag, Class<?> t_Cls, String[] t_Method) {
        return getDMA(i_Flag, i_Cls, i_Method, t_Cls, t_Method, false);
    }

    public static <T> T getNCI(Class<T> i_Cls, String[] i_Method, IBitSet i_Flag, Class<?> t_Cls, String[] t_Method) {
        return getDMA(i_Flag, i_Cls, i_Method, t_Cls, t_Method, true);
    }

    /**
     * (实例方法)获得IInvoker 需要手动setInstance() <BR>
     * 这个方法使用的是INVOKESPECIAL => 有时候需要用到
     *
     * @param i_Cls    实现IInvoker的class
     * @param i_Method IInvoker的方法名字
     * @param t_Cls    目标的类
     * @param t_Method 方法名字
     */
    public static <T extends Instanced> T getSpecial(Class<T> i_Cls, String i_Method, Class<?> t_Cls, String t_Method) {
        return getSingle(true, i_Cls, i_Method, t_Cls, t_Method);
    }

    /**
     * @param invoker          实现IInvoker的class
     * @param invokeMethodName IInvoker的方法名字
     * @param tClass           目标的类
     * @param methodName       方法名字
     */
    public static <T> T getStatic(Class<T> invoker, String invokeMethodName, Class<?> tClass, String methodName) {
        return getSingle(false, invoker, invokeMethodName, tClass, methodName);
    }

    /**
     * @param invoker          实现IInvoker的class
     * @param invokeMethodName IInvoker的方法名字
     * @param tClass           目标的类
     * @param methodName       方法名字
     */
    public static <T> T getStatic(Class<T> invoker, String[] invokeMethodName, Class<?> tClass, String[] methodName) {
        return getDMA(null, invoker, invokeMethodName, tClass, methodName, false);
    }

    private static <T> T getSingle(boolean opcode, Class<T> invoker, String invokeMethodName, Class<?> tClass, String methodName) {
        SingleBitSet sb = new SingleBitSet();
        if (opcode)
            sb.add(0);
        return getDMA(sb, invoker, new String[]{invokeMethodName}, tClass, new String[]{methodName}, false);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getDMA(IBitSet opcode, Class<T> invoker, String[] invokeMethodName, Class<?> tClass, String[] methodName, boolean nci) {
        //assert invokeMethodName.length == methodName.length;
        java.lang.reflect.Method[] targetMethods = new java.lang.reflect.Method[invokeMethodName.length];

        java.lang.reflect.Method[] invokeMethods = invoker.getMethods();
        List<java.lang.reflect.Method> targetMethods1 = ReflectionUtils.getMethods(tClass);
        for (int i = 0, len = targetMethods.length; i < len; i++) {
            targetMethods[i] = findTargetMethod(invokeMethods, targetMethods1, invokeMethodName[i], methodName[i], nci);
        }

        int i = nextId.getAndIncrement();
        String newClassName = "roj.reflect.DMA$" + i;

        try {

            final byte[] code = compile(opcode, "roj/reflect/DMA$" + i, invoker.getName(), invokeMethodName, tClass.getName(), targetMethods, nci);
            ClassDefiner.INSTANCE.defineClass(newClassName, code);

            Class<?> clz = Class.forName(newClassName);
            return (T) SunReflection.createClass(clz);
        } catch (ClassFormatError | IllegalAccessException | ClassNotFoundException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException("DMA Internal error!", e);
        }
    }

    @Nonnull
    static java.lang.reflect.Method findTargetMethod(java.lang.reflect.Method[] invokerMethods, List<java.lang.reflect.Method> targetMethods, String invokeMethodName, String methodName, boolean nci) {
        java.lang.reflect.Method invokeMethod = null;

        for (java.lang.reflect.Method method : invokerMethods) {
            if (method.getName().equals(invokeMethodName)) {
                invokeMethod = method;
                break;
            }
        }
        if (invokeMethod == null)
            throw new IllegalArgumentException("No invoke(?) found.");

        Class<?>[] invokeParams = invokeMethod.getParameterTypes();

        if ((invokeMethod.getModifiers() & AccessFlag.STATIC) != 0)
            throw new IllegalArgumentException("invokeMethod should not be static.");

        java.lang.reflect.Method targetMethod = null;

        outer:
        for (java.lang.reflect.Method method : targetMethods) {
            boolean i_ist = (method.getModifiers() & AccessFlag.STATIC) == 0;
            int off = (nci && i_ist ? 1 : 0);
            if (method.getName().equals(methodName) && method.getParameterCount() == invokeParams.length - off) {
                if (off == 1 && invokeParams[0] != Object.class) {
                    throw new IllegalArgumentException("NCI.param[0] should be 'Object'");
                }
                Class<?>[] methodParams = method.getParameterTypes();
                for (int i = 0; i < methodParams.length; i++) {
                    if (methodParams[i] != invokeParams[i + off]) {
                        continue outer;
                    }
                }
                targetMethod = method;
                break;
            }
        }

        if (targetMethod == null)
            throw new IllegalArgumentException("No such method in dest " + methodName + " '" + ParamHelper.classDescriptors(invokeParams, invokeMethod.getReturnType()) + '\'');

        return targetMethod;
    }

    static byte[] compile(IBitSet invokeCode, String selfName, String i_Cls, String[] i_Methods, String t_Cls, java.lang.reflect.Method[] t_Methods, boolean nonCachedInstance) {
        Clazz out = new Clazz();

        makeClassHeader(selfName, i_Cls, out);

        t_Cls = t_Cls.replace('.', '/');

        FlagList pubFlags = new FlagList(AccessFlag.PUBLIC);

        FieldInsnNode getInstance = null;
        if (invokeCode != null && !nonCachedInstance) {
            Type targetType = new Type(t_Cls.replace('.', '/'), 0);

            /**
             * target selfName.obj
             * (instance)
             */
            final String INSTANCE_FIELD_NAME = "obj";

            out.fields.add(new Field(pubFlags, INSTANCE_FIELD_NAME, targetType));

            getInstance = new FieldInsnNode(Opcodes.GETFIELD, selfName, INSTANCE_FIELD_NAME, targetType);

            makeClassInstanced(t_Cls, out, new FieldInsnNode(Opcodes.PUTFIELD, selfName, INSTANCE_FIELD_NAME, targetType), pubFlags);
        }

        /**
         * R invoke(R)
         */
        for (int k = 0, len = t_Methods.length; k < len; k++) {
            java.lang.reflect.Method method = t_Methods[k];

            Class<?>[] params = method.getParameterTypes();


            String desc = ParamHelper.classDescriptors(params, method.getReturnType());
            if(DEBUG)
                System.out.println("Desc " + desc);

            Method invoke = new Method(pubFlags, out, i_Methods[k], desc);
            AttrCode code;
            invoke.code = code = new AttrCode(invoke);
            out.methods.add(invoke);

            final InsnList insn = code.instructions;

            int i_stc = (method.getModifiers() & AccessFlag.STATIC) != 0 ? 1 : 0;
            if (i_stc == 0) {
                if (!nonCachedInstance) {
                    insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
                    if (getInstance == null) {
                        throw new IllegalArgumentException("Non-static method found.");
                    }
                    insn.add(getInstance);
                } else {
                    insn.add(NodeHelper.cached(Opcodes.ALOAD_1));

                    Class<?>[] a1 = new Class<?>[params.length + 1];
                    a1[0] = Object.class;
                    System.arraycopy(params, 0, a1, 1, params.length);
                    invoke.setDesc(ParamHelper.classDescriptors(a1, method.getReturnType()));
                }
            }

            int size = nonCachedInstance ? 1 : 0;
            for (Class<?> param : params) {
                String tag = ParamHelper.XPrefix(param);
                NodeHelper.compress(insn, NodeHelper.X_LOAD(tag.charAt(0)), ++size);
                switch (tag) {
                    case "D":
                    case "L":
                        size++;
                }
            }

            code.stackSize = Math.max(size + i_stc, 1);
            code.localSize = size + 1;

            insn.add(new InvokeInsnNode(invokeCode == null || i_stc == 1 ? Opcodes.INVOKESTATIC : (invokeCode.contains(k) ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL), t_Cls, method.getName(), desc));
            insn.add(NodeHelper.X_RETURN(ParamHelper.XPrefix(method.getReturnType())));
            insn.add(AttrCode.METHOD_END_MARK);
        }

        makeClassInit(out, pubFlags);

        return Parser.toByteArray(out);
    }

    /**
     * <init>
     * constructor
     */
    static void makeClassInit(Clazz clz, FlagList publicAccess) {
        AttrCode code;

        Method init = new Method(publicAccess, clz, "<init>", "()V");
        init.code = code = new AttrCode(init);

        code.stackSize = 1;
        code.localSize = 1;
        final InsnList insn = code.instructions;
        insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
        insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, MAGIC_ACCESSOR_CLASS + ".<init>:()V"));
        insn.add(NodeHelper.cached(Opcodes.RETURN));
        insn.add(AttrCode.METHOD_END_MARK);

        clz.methods.add(init);
    }

    /**
     * setInstance
     * clearInstance
     */
    static void makeClassInstanced(String invokeTarget, Clazz clz, FieldInsnNode setInstance, FlagList publicAccess) {
        AttrCode code;

        Method set = new Method(publicAccess, clz, "setInstance", "(Ljava/lang/Object;)V");
        set.code = code = new AttrCode(set);

        code.stackSize = 2;
        code.localSize = 2;
        InsnList insn = code.instructions;
        insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
        insn.add(NodeHelper.cached(Opcodes.ALOAD_1));
        insn.add(new ClassInsnNode(Opcodes.CHECKCAST, invokeTarget));
        insn.add(setInstance);
        insn.add(NodeHelper.cached(Opcodes.RETURN));
        insn.add(AttrCode.METHOD_END_MARK);

        clz.methods.add(set);

        Method clear = new Method(publicAccess, clz, "clearInstance", "()V");
        clear.code = code = new AttrCode(clear);

        code.stackSize = 2;
        code.localSize = 1;

        insn = code.instructions;
        insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
        insn.add(NodeHelper.cached(Opcodes.ACONST_NULL));
        insn.add(setInstance);
        insn.add(NodeHelper.cached(Opcodes.RETURN));
        insn.add(AttrCode.METHOD_END_MARK);

        clz.methods.add(clear);
    }

    /**
     * Header
     */
    public static void makeClassHeader(String selfName, String invokerName, Clazz clz) {
        clz.version = 52 << 16;
        clz.name = selfName.replace('.', '/');

        clz.parent = MAGIC_ACCESSOR_CLASS;
        clz.interfaces.add(invokerName.replace('.', '/'));
        clz.accesses = new FlagList(AccessFlag.SUPER_OR_SYNC, AccessFlag.PUBLIC);
    }
}