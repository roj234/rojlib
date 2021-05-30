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
import roj.asm.struct.insn.ClassInsnNode;
import roj.asm.struct.insn.FieldInsnNode;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.asm.util.NodeHelper;
import roj.asm.util.type.ParamHelper;
import roj.asm.util.type.Type;

import java.util.List;

import static roj.asm.util.type.NativeType.*;

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
            loader.defineClass(newClassName, code);

            Class<?> clz = Class.forName(newClassName);

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
            /*if (p.type == CLASS || p.type == NativeType.ARRAY) {
                if (g_sClass == DirectFieldAccessor.class) {
                    p = OBJI;
                } else {
                    p = p;
                }
            } else {
                p = new Type(p.type, 0);
            }*/
            types.add(p);
            len++;
        } else {
            types.clear();
            m.setReturnType(p/*(p.type == CLASS || p.type == NativeType.ARRAY) ? (g_sClass == DirectFieldAccessor.class ? OBJ : p) : new Type(p.type, 0)*/);
        }
        String t = null;
        switch (p.type) {
            case CLASS:
            case ARRAY:
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