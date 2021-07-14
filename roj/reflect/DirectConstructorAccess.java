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
import roj.asm.struct.Method;
import roj.asm.struct.attr.AttrCode;
import roj.asm.struct.insn.ClassInsnNode;
import roj.asm.struct.insn.InvokeInsnNode;
import roj.asm.type.ParamHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.asm.util.NodeHelper;

import java.lang.reflect.InvocationTargetException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
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

        int i = DirectMethodAccess.nextId.getAndIncrement();

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
            String tag = ParamHelper.XPrefix(params[i]);
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