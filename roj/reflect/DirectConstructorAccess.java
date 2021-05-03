/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: MIAccessTransformer.java
 */
package roj.reflect;

import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.struct.attr.AttrCode;
import roj.asm.struct.insn.ClassInsnNode;
import roj.asm.struct.insn.InvokeInsnNode;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.asm.util.NodeHelper;
import roj.asm.util.type.ParamHelper;

import java.lang.reflect.InvocationTargetException;

import static roj.reflect.DirectMethodAccess.nextId;

public class DirectConstructorAccess {
    /**
     * 获取实例化器
     *
     * @param invoker IInvoker invoker class
     * @param target  target class to create constructor
     * @return constructor
     */
    public static <T> T get(Class<T> invoker, Class<?> target) {
        return get(invoker, "invoke", target);
    }

    /**
     * 获取实例化器
     *
     * @param invoker IInvoker invoker class
     * @param target  target class to create constructor
     * @return constructor
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> invoker, String invokeMethodName, Class<?> target) {
        java.lang.reflect.Method invoke = null;

        for (java.lang.reflect.Method method : invoker.getMethods()) {
            if (method.getName().equals(invokeMethodName)) {
                invoke = method;
                break;
            }
        }

        if (invoke == null)
            throw new IllegalArgumentException("No " + invoker.getName() + ".invoke(?) found.");

        Class<?>[] param = invoke.getParameterTypes();
        if (!invoke.getReturnType().isAssignableFrom(target))
            throw new IllegalArgumentException("targetClass not instanceof invoke.getReturnType()");

        int i;
        synchronized (nextId) {
            i = nextId.getAndIncrement();
        }

        String clsName = "roj.reflect.DCA$" + i;

        try {
            final byte[] code = getClazz("roj/reflect/DCA$" + i, invoker.getName(), invokeMethodName, target.getName(), invoke);
            ClassDefiner.INSTANCE.defineClass(clsName, code);

            Class<?> clz = Class.forName(clsName);
            return (T) SunReflection.createClass(clz);
        } catch (ClassFormatError | ClassNotFoundException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException("DCA Internal error!", e);
        }
    }

    static byte[] getClazz(String selfName, String invokerName, String invokeMethodName, String targetClass, java.lang.reflect.Method method) {
        Clazz clz = new Clazz();

        DirectMethodAccess.makeClassHeader(selfName, invokerName, clz);

        /**
         * Some data
         */

        FlagList publicAccess = new FlagList(AccessFlag.PUBLIC);

        /**
         * R construct(R)
         */
        Class<?>[] params = method.getParameterTypes();

        String rawParamInit = ParamHelper.classDescriptors(params, void.class);
        String rawParam = ParamHelper.classDescriptors(params, method.getReturnType());

        String nName = targetClass.replace('.', '/');

        Method invoke = new Method(publicAccess, clz, invokeMethodName, rawParam);
        AttrCode code;
        invoke.code = code = new AttrCode(invoke);

        int size = method.getParameterCount();

        code.instructions.add(new ClassInsnNode(Opcodes.NEW, nName));
        code.instructions.add(NodeHelper.cached(Opcodes.DUP));

        for (int i = 0; i < params.length; ) {
            String tag = ParamHelper.nativeType(params[i]);
            switch (tag) {
                case "D":
                case "L":
                    size++;
            }
            NodeHelper.compress(code.instructions, NodeHelper.X_LOAD(tag.charAt(0)), ++i);
        }

        code.stackSize = size + 1;
        code.localSize = size + 1;

        code.instructions.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, nName, "<init>", rawParamInit));
        code.instructions.add(NodeHelper.cached(Opcodes.ARETURN));
        code.instructions.add(AttrCode.METHOD_END_MARK);

        clz.methods.add(invoke);

        DirectMethodAccess.makeClassInit(clz, publicAccess);

        return Parser.toByteArray(clz);
    }
}