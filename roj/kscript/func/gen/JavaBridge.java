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
package roj.kscript.func.gen;

import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.tree.Clazz;
import roj.asm.tree.Field;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.*;
import roj.asm.type.NativeType;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.asm.util.InsnList;
import roj.asm.util.NodeHelper;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.concurrent.OperationDone;
import roj.kscript.api.IObject;
import roj.kscript.type.KObject;
import roj.reflect.ClassDefiner;
import roj.reflect.Instantiator;
import roj.util.Helpers;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since  2020/10/17 18:12
 */
public final class JavaBridge {
    static AtomicInteger clazzId = new AtomicInteger();

    Clazz holder;
    private void initHolder() {
        Clazz clz = holder = new Clazz();
        clz.version = 52 << 16;
        clz.interfaces.add("roj/kscript/func/gen/KFuncJava");
        clz.accesses = new FlagList(AccessFlag.SUPER_OR_SYNC | AccessFlag.PUBLIC);

        Field id = new Field(new FlagList(), "id", Type.std(NativeType.INT));
        clz.fields.add(id);

        Field obj = new Field(new FlagList(), "obj", (Type) null);
        clz.fields.add(obj);

        roj.asm.tree.Method call = new roj.asm.tree.Method(AccessFlag.PUBLIC, clz, "invoke", "(Lroj/kscript/data/IObject;Lroj/kscript/api/ArgList;)Lroj/kscript/type/KType;");
        clz.methods.add(call);
        (call.code = new AttrCode(call)).interpretFlags = AttrCode.COMPUTE_FRAMES | AttrCode.COMPUTE_SIZES;
        InsnList insn = call.code.instructions;
        insn.add(NodeHelper.npc(Opcodes.ALOAD_0));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, clz, 0));
        SwitchInsnNode swch = new SwitchInsnNode(Opcodes.TABLESWITCH);
        insn.add(swch);
        InsnNode def = NodeHelper.npc(Opcodes.ACONST_NULL);
        swch.def = def;
        insn.add(def);
        insn.add(NodeHelper.npc(Opcodes.ARETURN));

        roj.asm.tree.Method copyAs = new roj.asm.tree.Method(0, clz, "copyAs", "(I)Lroj/kscript/func/gen/KFuncJava;");
        clz.methods.add(copyAs);
        (copyAs.code = new AttrCode(copyAs)).interpretFlags = AttrCode.COMPUTE_FRAMES | AttrCode.COMPUTE_SIZES;
        insn = copyAs.code.instructions;
        insn.add(new ClassInsnNode(Opcodes.NEW, ""));
        insn.add(NodeHelper.npc(Opcodes.DUP));
        insn.add(NodeHelper.npc(Opcodes.ILOAD_1));
        insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, "", "<init>", "(I)V"));
        insn.add(NodeHelper.npc(Opcodes.ARETURN));

        roj.asm.tree.Method __init__ = new roj.asm.tree.Method(0, clz, "<init>", "(I)V");
        clz.methods.add(__init__);
        AttrCode code = __init__.code = new AttrCode(__init__);
        code.stackSize = 2;
        code.localSize = 1;

        insn = code.instructions;
        insn.add(NodeHelper.npc(Opcodes.ALOAD_0));
        insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, clz.parent, "<init>", "()V"));
        insn.add(NodeHelper.npc(Opcodes.ALOAD_0));
        insn.add(NodeHelper.npc(Opcodes.ILOAD_1));
        insn.add(new FieldInsnNode(Opcodes.PUTFIELD, clz, 0));
        insn.add(NodeHelper.npc(Opcodes.RETURN));

        roj.asm.tree.Method gsObject = new roj.asm.tree.Method(0, clz, "get_set_Object", "(Ljava/lang/Object;)Ljava/lang/Object;");
        clz.methods.add(gsObject);
        code = gsObject.code = new AttrCode(gsObject);
        code.stackSize = 3;
        code.localSize = 1;

        insn = code.instructions;
        insn.add(NodeHelper.npc(Opcodes.ALOAD_0));
        insn.add(new FieldInsnNode(Opcodes.GETFIELD, clz, 1));

        insn.add(NodeHelper.npc(Opcodes.ALOAD_0));
        insn.add(NodeHelper.npc(Opcodes.ALOAD_1));
        insn.add(new ClassInsnNode(Opcodes.CHECKCAST, ""));
        insn.add(new FieldInsnNode(Opcodes.PUTFIELD, clz, 1));

        insn.add(NodeHelper.npc(Opcodes.ARETURN));

        roj.asm.tree.Method __init_def__ = new roj.asm.tree.Method(0, clz, "<init>", "()V");
        clz.methods.add(__init_def__);
        code = __init_def__.code = new AttrCode(__init_def__);
        code.stackSize = 1;
        code.localSize = 1;

        insn = code.instructions;
        insn.add(NodeHelper.npc(Opcodes.ALOAD_0));
        insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, clz.parent, "<init>", "()V"));
        insn.add(NodeHelper.npc(Opcodes.RETURN));
    }

    private void forReuse(String className, String methodOwner) {
        Clazz clz = holder;
        clz.name = className;

        InsnList in = clz.methods.get(0).code.instructions;
        InsnNode a = in.get(0), b = in.get(1), c = in.get(2), d = in.get(3), e = in.get(4);
        ((FieldInsnNode)b).owner = className;
        in.clear(); in.add(a); in.add(b); in.add(c); in.add(d); in.add(e);
        ((SwitchInsnNode)c).switcher.clear();

        in = clz.methods.get(1).code.instructions;
        ((ClassInsnNode) in.get(0)).owner = className;
        ((InvokeInsnNode) in.get(3)).owner = className;

        ((FieldInsnNode)clz.methods.get(2).code.instructions.get(4)).owner = className;

        Type ct = new Type(methodOwner);

        in = clz.methods.get(3).code.instructions;
        FieldInsnNode fn = (FieldInsnNode) in.get(1);
        fn.owner = className; fn.type = ct;

        fn = ((FieldInsnNode) in.get(5));
        fn.owner = className; fn.type = ct;

        ((ClassInsnNode) in.get(4)).owner = methodOwner;

        clz.fields.get(0).type = ct;
    }

    public IObject createJavaFn(Class<?> clazz) {
        if(holder == null)
            initHolder();

        String nativeName = clazz.getName().replace('.', '/');
        forReuse("roj/kscript/func/gen/GJavaFn$" + clazzId.getAndIncrement(), nativeName);

        Map<String, Method> map = new MyHashMap<>();

        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            JvavMethod annotation = method.getDeclaredAnnotation(JvavMethod.class);
            if (annotation != null) {
                if (!Modifier.isPublic(method.getModifiers()))
                    throw new IllegalArgumentException(clazz.getName() + '.' + method.toGenericString() + " should be public.");
                String name = annotation.name().equals("") ? method.getName() : annotation.name();
                map.put(name, method);
            }
        }

        Clazz clz = holder;
        roj.asm.tree.Method call = clz.methods.get(0);

        int i = 0;
        Set<Map.Entry<String, Object>> set = Helpers.cast(map.entrySet());
        for (Map.Entry<String, Object> entry : set) {
            addMethod((Method) entry.getValue(), call.code, i++);
        }

        KFuncJava fn = defineClazz(clz);

        i = 0;
        for (Map.Entry<String, Object> entry : set) {
            entry.setValue(fn.copyAs(i++));
        }

        return new KObject(Helpers.cast(map));
    }

    private static KFuncJava defineClazz(Clazz clz) {
        String name;
        try {
            ClassDefiner.INSTANCE.defineClass(name = clz.name.replace('/', '.'), Parser.toByteArray(clz));
            Class<?> cz = Class.forName(name);
            return (KFuncJava) Instantiator._new(cz);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("JavaBridge internal error", e);
        }
    }

    private void addMethod(Method value, AttrCode call, int id) {
        InsnList insn = call.instructions;
        IntMap<InsnNode> targets = ((SwitchInsnNode) insn.get(3)).switcher;

        String types = ParamHelper.classDescriptors(value.getParameterTypes(), value.getReturnType());

        InsnNode target;
        insn.add(target = new NPInsnNode(Opcodes.ALOAD_0));
        targets.put(id, target);

        insn.add(new FieldInsnNode(Opcodes.GETFIELD, holder, 1));
        insn.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, value.getDeclaringClass().getName().replace('.', '/'), value.getName(), types));
        if(types.endsWith("V"))
            // todo make wrapper
        insn.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, "roj/kscript/vm/VMUtil", "toJavaObject", "(Ljava/lang/Object;)Lroj/kscript/type/KJavaObject;"));
        insn.add(NodeHelper.npc(Opcodes.ARETURN));

    }

    public static IObject createReflectiveJavaFunction(Class<?> clazz) {
        throw OperationDone.NEVER;
    }
}
