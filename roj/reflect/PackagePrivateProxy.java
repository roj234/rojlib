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
import roj.asm.struct.Field;
import roj.asm.struct.Method;
import roj.asm.struct.attr.AttrCode;
import roj.asm.struct.insn.FieldInsnNode;
import roj.asm.struct.insn.InvokeInsnNode;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.asm.util.InsnList;
import roj.asm.util.NodeHelper;
import roj.asm.util.type.ParamHelper;
import roj.asm.util.type.Type;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * 有空再支持非空init吧
 */
public final class PackagePrivateProxy {
    @SuppressWarnings("unchecked")
    public static <T> T proxyIt(Class<T> source_class, Class<?> proxy_class, String custom_package, String... proxy_method) {
        java.lang.reflect.Method[] targetMethods = new java.lang.reflect.Method[proxy_method.length];

        java.lang.reflect.Method[] invokeMethods = proxy_class.getMethods();
        List<java.lang.reflect.Method> targetMethods1 = ReflectionUtils.getMethods(source_class);
        for (int i = 0, len = targetMethods.length; i < len; i++) {
            targetMethods[i] = DirectMethodAccess.findTargetMethod(invokeMethods, targetMethods1, proxy_method[i], proxy_method[i], false);
        }

        int i = DirectMethodAccess.nextId.getAndIncrement();
        String newClassName = custom_package + ".Proxy$" + i;

        try {

            final byte[] code = compile(custom_package.replace('.', '/') + "/Proxy$" + i, proxy_class.getName(), source_class.getName(), targetMethods);
            ClassDefiner.INSTANCE.defineClass(newClassName, code);

            Class<?> clz = Class.forName(newClassName);
            return (T) SunReflection.createClass(clz);
        } catch (ClassFormatError | IllegalAccessException | ClassNotFoundException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException("DMA Internal error!", e);
        }
    }

    static byte[] compile(String self, String proxy, String parent, java.lang.reflect.Method[] methods) {
        Clazz out = new Clazz();

        parent = parent.replace('.', '/');

        makeClassHeader(self, parent, out);

        FlagList pubFlags = new FlagList(AccessFlag.PUBLIC);

        Type field = new Type(proxy.replace('.', '/'), 0);

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

            code.stackSize = Math.max(size, 1);
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
        clz.accesses = new FlagList(AccessFlag.SUPER_OR_SYNC, AccessFlag.PUBLIC);
    }
}