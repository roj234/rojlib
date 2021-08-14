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
import roj.asm.tree.insn.ClassInsnNode;
import roj.asm.tree.insn.FieldInsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.asm.util.InsnList;
import roj.asm.util.NodeHelper;

import java.util.Arrays;
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
    public static <T> Class<T> proxyIt(Class<T> source_class, Class<?> proxy_class, String... proxy_method) {
        java.lang.reflect.Method[] targetMethods = new java.lang.reflect.Method[proxy_method.length];

        List<java.lang.reflect.Method> targetMethods1 = ReflectionUtils.getMethods(source_class);
        for (int i = 0, len = targetMethods.length; i < len; i++) {
            java.lang.reflect.Method m, m1 = null;
            try {
                m = source_class.getMethod(proxy_method[i]);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Unable to find " + source_class.getName() + '.' + proxy_method[i]);
            }
            Class<?>[] cs = m.getParameterTypes();
            for (int j = 0; j < targetMethods1.size(); j++) {
                java.lang.reflect.Method cmp = targetMethods1.get(j);
                if(cmp.getParameterCount() == m.getParameterCount() && Arrays.equals(cs, cmp.getParameterTypes())) {
                    m1 = cmp;
                    break;
                }
            }
            if(m1 == null)
                throw new IllegalArgumentException("Unable to find " + proxy_class.getName() + '.' + proxy_method[i]);
            targetMethods[i] = m1;
        }

        int i = DirectAccessor.NEXT_ID.getAndIncrement();
        String pkg = proxy_class.getName().substring(0, proxy_class.getName().lastIndexOf('/') + 1);

        try {
            byte[] code = compile(pkg.replace('.', '/') + "/Proxy$" + i, proxy_class.getName(), source_class.getName(), targetMethods);
            return (Class<T>) ClassDefiner.INSTANCE.defineClass(pkg + ".Proxy$" + i, code);
        } catch (ClassFormatError e) {
            throw new RuntimeException("3P Internal error!", e);
        }
    }

    static byte[] compile(String self, String proxy, String parent, java.lang.reflect.Method[] methods) {
        Clazz out = new Clazz();

        makeClassHeader(self, parent = parent.replace('.', '/'), out);

        FlagList pubFlags = new FlagList(AccessFlag.PUBLIC);

        Type field = new Type(proxy = proxy.replace('.', '/'), 0);

        out.fields.add(new Field(pubFlags, "obj", field));

        FieldInsnNode getInst = new FieldInsnNode(Opcodes.GETFIELD, self, "obj", field);

        FieldInsnNode _set = new FieldInsnNode(Opcodes.PUTFIELD, out, 0);
        FieldInsnNode _get = new FieldInsnNode(Opcodes.GETFIELD, out, 0);

        AttrCode code;
        InsnList insn;

        Method set = new Method(pubFlags, out, "setInstance", "(Ljava/lang/Object;)V");
        set.code = code = new AttrCode(set);

        code.stackSize = 2;
        code.localSize = 2;
        insn = code.instructions;
        insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
        insn.add(NodeHelper.cached(Opcodes.ALOAD_1));
        insn.add(new ClassInsnNode(Opcodes.CHECKCAST, proxy));
        insn.add(_set);
        insn.add(NodeHelper.cached(Opcodes.RETURN));
        insn.add(AttrCode.METHOD_END_MARK);

        out.methods.add(set);

        Method clear = new Method(pubFlags, out, "clearInstance", "()V");
        clear.code = code = new AttrCode(clear);

        code.stackSize = 2;
        code.localSize = 1;

        insn = code.instructions;
        insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
        insn.add(NodeHelper.cached(Opcodes.ACONST_NULL));
        insn.add(_set);
        insn.add(NodeHelper.cached(Opcodes.RETURN));
        insn.add(AttrCode.METHOD_END_MARK);

        out.methods.add(clear);

        for (java.lang.reflect.Method method : methods) {
            Class<?>[] params = method.getParameterTypes();

            String desc = ParamHelper.classDescriptors(params, method.getReturnType());

            Method invoke = new Method(pubFlags, out, method.getName(), desc);
            code = invoke.code = new AttrCode(invoke);
            out.methods.add(invoke);

            insn = code.instructions;

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