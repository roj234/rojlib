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
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.asm.util.NodeHelper;

import java.util.List;

import static roj.asm.type.NativeType.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public class DirectFieldAccess {
    public static DirectFieldAccessor get(Object obj, String field) {
        return get(obj, findField((Class<?>) obj, field));
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String field) {
        java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
        for (java.lang.reflect.Field field1 : fields) {
            if (field1.getName().equals(field))
                return field1;
        }
        throw new RuntimeException("No such STATIC field in " + clazz + " : " + field);
    }

    public static DirectFieldAccessor get(Object obj, java.lang.reflect.Field field) {
        return get(obj, field, DirectFieldAccessor.class);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Instanced> T get(Object obj, java.lang.reflect.Field field, Class<T> getter_setter_class) {
        int i = DirectMethodAccess.nextId.getAndIncrement();

        String className = obj.getClass() == Class.class ? ((Class<?>) obj).getName() : obj.getClass().getName();
        Type par = ParamHelper.parseField(ParamHelper.classDescriptor(field.getType()));

        String newClassName = "roj.reflect.DFA$" + i;

        try {
            ClassDefiner loader = ClassDefiner.INSTANCE;
            byte[] code = getClassCode("roj/reflect/DFA$" + i, className, field.getName(), par, getter_setter_class);
            Class<?> clz = loader.defineClass(newClassName, code);

            return (T) SunReflection.createClass(clz);
        } catch (Exception e) {
            throw new RuntimeException("DFA Internal error!", e);
        }
    }

    public static DirectFieldAccessor getStatic(Object obj, java.lang.reflect.Field field) {
        return get(obj, field, DirectFieldAccessor.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getStatic(Class<?> clazz, java.lang.reflect.Field field, Class<T> getter_setter_class) {
        int i = DirectMethodAccess.nextId.getAndIncrement();

        String newClassName = "roj.reflect.DFA$" + i;

        String className = clazz.getName();

        Type par = ParamHelper.parseField(ParamHelper.classDescriptor(field.getType()));
        try {
            ClassDefiner loader = ClassDefiner.INSTANCE;
            final byte[] code = getStaticClassCode("roj/reflect/DFA$" + i, className, field.getName(), par, getter_setter_class);
            loader.defineClass(newClassName, code);

            Class<?> clz = Class.forName(newClassName);
            return (T) SunReflection.createClass(clz);
        } catch (Exception e) {
            throw new RuntimeException("DFA Internal error!", e);
        }
    }

    static byte[] getClassCode(String selfName, String targetName, String fieldName, Type fieldType, Class<?> g_sClass) {
        Clazz clz = new Clazz();
        DirectMethodAccess.makeClassHeader(selfName, g_sClass.getName(), clz);

        targetName = targetName.replace('.', '/');

        Type clsType = new Type(targetName, 0);

        final String INSTANCE_FIELD_NAME = "obj";

        Field instanceField = new Field(new FlagList(), INSTANCE_FIELD_NAME, clsType);

        clz.fields.add(instanceField);

        FieldInsnNode getInstance = new FieldInsnNode(Opcodes.GETFIELD, selfName, INSTANCE_FIELD_NAME, clsType);
        FieldInsnNode setInstance = new FieldInsnNode(Opcodes.PUTFIELD, selfName, INSTANCE_FIELD_NAME, clsType);

        FlagList publicAccess = new FlagList(AccessFlag.PUBLIC);

        Method get = new Method(publicAccess, clz, "get", null);
        int stack = setMethodNameAndType(get, fieldType, g_sClass);
        AttrCode code;
        get.code = code = new AttrCode(get);

        FieldInsnNode targetField = new FieldInsnNode(Opcodes.GETFIELD, targetName, fieldName, fieldType);

        code.stackSize = stack;
        code.localSize = 1;
        code.instructions.add(NodeHelper.cached(Opcodes.ALOAD_0));
        code.instructions.add(getInstance);
        code.instructions.add(targetField);
        code.instructions.add(NodeHelper.X_RETURN(fieldType.nativeName()));
        code.instructions.add(AttrCode.METHOD_END_MARK);

        clz.methods.add(get);

        DirectMethodAccess.makeClassInit(clz, publicAccess);

        /**
         * Set
         */

        Method set = new Method(publicAccess, clz, "set", null);
        stack = setMethodNameAndType(set, fieldType, g_sClass);
        set.code = code = new AttrCode(set);

        targetField = new FieldInsnNode(Opcodes.PUTFIELD, targetName, fieldName, fieldType);

        code.stackSize = stack;
        code.localSize = stack;
        code.instructions.add(NodeHelper.cached(Opcodes.ALOAD_0));
        code.instructions.add(getInstance);
        code.instructions.add(NodeHelper.X_LOAD_I(fieldType.nativeName().charAt(0), 1));
        if (fieldType.type == CLASS)
            code.instructions.add(new ClassInsnNode(Opcodes.CHECKCAST, fieldType.owner));
        code.instructions.add(targetField);
        code.instructions.add(NodeHelper.cached(Opcodes.RETURN));
        code.instructions.add(AttrCode.METHOD_END_MARK);

        clz.methods.add(set);

        DirectMethodAccess.makeClassInstanced(targetName, clz, setInstance, publicAccess);

        return Parser.toByteArray(clz);
    }

    static int setMethodNameAndType(Method m, Type p, Class<?> g_sClass) {
        String prefix = m.name;
        List<Type> types = m.parameters();
        int len = 1;
        if ("set".equals(prefix)) {
            m.setReturnType(Type.std(VOID));
            types.clear();
            if (p.type == CLASS && g_sClass == DirectFieldAccessor.class) {
                p = new Type("java/lang/Object");
            }
            types.add(p);
            len++;
        } else {
            types.clear();
            m.setReturnType(p.type == CLASS && g_sClass == DirectFieldAccessor.class ? new Type("java/lang/Object") : p);
        }
        String t = null;
        switch (p.type) {
            case CLASS:
                t = "Object";
                break;
            case BOOLEAN:
                t = "Boolean";
                break;
            case BYTE:
                t = "Byte";
                break;
            case CHAR:
                t = "Char";
                break;
            case SHORT:
                t = "Short";
                break;
            case INT:
                t = "Int";
                break;
            case FLOAT:
                t = "Float";
                break;
            case DOUBLE:
                t = "Double";
                len++;
                break;
            case LONG:
                t = "Long";
                len++;
                break;
        }
        m.name = prefix + t;
        return len;
    }

    static byte[] getStaticClassCode(String selfName, String targetName, String fieldName, Type fieldType, Class<?> g_sClass) {
        Clazz clz = new Clazz();
        DirectMethodAccess.makeClassHeader(selfName, g_sClass.getName(), clz);

        FlagList publicAccess = new FlagList(AccessFlag.PUBLIC);

        /**
         * Get
         */

        Method get = new Method(publicAccess, clz, "get", null);
        setMethodNameAndType(get, fieldType, g_sClass);
        AttrCode code;
        get.code = code = new AttrCode(get);

        FieldInsnNode targetField = new FieldInsnNode(Opcodes.GETSTATIC, targetName, fieldName, fieldType);

        code.stackSize = 1;
        code.localSize = 1;
        code.instructions.add(NodeHelper.cached(Opcodes.ALOAD_0));
        code.instructions.add(targetField);
        code.instructions.add(NodeHelper.X_RETURN(fieldType.nativeName()));
        code.instructions.add(AttrCode.METHOD_END_MARK);

        clz.methods.add(get);

        DirectMethodAccess.makeClassInit(clz, publicAccess);

        /**
         * Set
         */

        Method set = new Method(publicAccess, clz, "set", null);
        setMethodNameAndType(set, fieldType, g_sClass);
        set.code = code = new AttrCode(set);

        targetField = new FieldInsnNode(Opcodes.PUTSTATIC, targetName, fieldName, fieldType);

        code.stackSize = 1;
        code.localSize = 2;
        code.instructions.add(NodeHelper.cached(Opcodes.ALOAD_0));
        code.instructions.add(targetField);
        code.instructions.add(NodeHelper.cached(Opcodes.RETURN));
        code.instructions.add(AttrCode.METHOD_END_MARK);

        clz.methods.add(set);

        return Parser.toByteArray(clz);
    }
}