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
import roj.asm.tree.Clazz;
import roj.asm.tree.Field;
import roj.asm.tree.Method;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.FieldInsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.asm.util.InsnList;
import roj.asm.util.NodeHelper;

import java.util.List;

/**
 * 有空再支持非空init吧
 */
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class PackagePrivateProxy {
    @SuppressWarnings("unchecked")
    public static <T> Class<T> proxyIt(Class<T> source_class, Class<?> proxy_class, String custom_package, String... proxy_method) {
        java.lang.reflect.Method[] targetMethods = new java.lang.reflect.Method[proxy_method.length];

        java.lang.reflect.Method[] invokeMethods = proxy_class.getMethods();
        List<java.lang.reflect.Method> targetMethods1 = ReflectionUtils.getMethods(source_class);
        for (int i = 0, len = targetMethods.length; i < len; i++) {
            targetMethods[i] = DirectMethodAccess.findTargetMethod(invokeMethods, targetMethods1, proxy_method[i], proxy_method[i], false);
        }

        int i = DirectMethodAccess.nextId.getAndIncrement();
        String newClassName = "_" + custom_package + ".Proxy$" + i;

        try {

            final byte[] code = compile("_"+custom_package.replace('.', '/') + "/Proxy$" + i, proxy_class.getName(), source_class.getName(), targetMethods);
            ClassDefiner.INSTANCE.defineClass(newClassName, code);

            return (Class<T>) Class.forName(newClassName);
        } catch (ClassFormatError | ClassNotFoundException e) {
            throw new RuntimeException("3P Internal error!", e);
        }
    }

    static byte[] compile(String self, String proxy, String parent, java.lang.reflect.Method[] methods) {
        Clazz out = new Clazz();

        makeClassHeader(self, parent = parent.replace('.', '/'), out);

        FlagList pubFlags = new FlagList(AccessFlag.PUBLIC);

        Type field = new Type(proxy = proxy.replace('.', '/'), 0);

        /**
         * target self.obj
         * (instance)
         */
        final String INSTANCE_FIELD_NAME = "obj";

        out.fields.add(new Field(pubFlags, INSTANCE_FIELD_NAME, field));

        FieldInsnNode getInst = new FieldInsnNode(Opcodes.GETFIELD, self, INSTANCE_FIELD_NAME, field);

        DirectMethodAccess.makeClassInstanced(parent, out, new FieldInsnNode(Opcodes.PUTFIELD, self, INSTANCE_FIELD_NAME, field), pubFlags);

        for (java.lang.reflect.Method method : methods) {
            Class<?>[] params = method.getParameterTypes();

            String desc = ParamHelper.classDescriptors(params, method.getReturnType());

            Method invoke = new Method(pubFlags, out, method.getName(), desc);
            AttrCode code = invoke.code = new AttrCode(invoke);
            out.methods.add(invoke);

            final InsnList insn = code.instructions;

            insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
            insn.add(getInst);

            int size = 0;
            for (Class<?> param : params) {
                String tag = ParamHelper.XPrefix(param);
                NodeHelper.compress(insn, NodeHelper.X_LOAD(tag.charAt(0)), ++size);
                switch (tag) {
                    case "D":
                    case "L":
                        size++;
                }
            }

            code.stackSize = Math.max(size, 2);
            System.out.println(size);
            code.localSize = size + 1;

            insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, proxy, method.getName(), desc)); // delegate to proxy class
            insn.add(NodeHelper.X_RETURN(ParamHelper.XPrefix(method.getReturnType())));
            insn.add(AttrCode.METHOD_END_MARK);
        }

        makeClassInit(out, pubFlags, parent);

        return Parser.toByteArray(out);
    }

    /**
     * <init>
     * constructor
     */
    static void makeClassInit(Clazz clz, FlagList publicAccess, String parentName) {
        AttrCode code;

        Method init = new Method(publicAccess, clz, "<init>", "()V");
        init.code = code = new AttrCode(init);

        code.stackSize = 1;
        code.localSize = 1;
        final InsnList insn = code.instructions;
        insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
        insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, parentName + ".<init>:()V"));
        insn.add(NodeHelper.cached(Opcodes.RETURN));
        insn.add(AttrCode.METHOD_END_MARK);

        clz.methods.add(init);
    }

    /**
     * Header
     */
    public static void makeClassHeader(String selfName, String parentName, Clazz clz) {
        clz.version = 52 << 16;
        clz.name = selfName.replace('.', '/');

        clz.parent = parentName;
        clz.interfaces.add("roj/reflect/Instanced");
        clz.accesses = new FlagList(AccessFlag.SUPER_OR_SYNC | AccessFlag.PUBLIC);
    }
}