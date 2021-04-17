package roj.kscript.func.gen;

import roj.asm.Parser;
import roj.asm.struct.Clazz;
import roj.asm.struct.attr.AttrCode;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.collect.MyHashMap;
import roj.concurrent.OperationDone;
import roj.kscript.api.IGettable;
import roj.kscript.func.KFunction;
import roj.kscript.type.KObject;
import roj.kscript.type.Type;
import roj.reflect.ClassDefiner;
import roj.reflect.SunReflection;
import roj.util.Helpers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/10/17 18:12
 */
public final class ReflectiveMethodGenerator {
    static int i;

    // todo

    public static IGettable createRegisteredJavaFunction(Class<?> clazz) {
        Clazz clz = new Clazz();
        clz.version = 52 << 16;
        clz.name = "roj/kscript/func/gen/GeneratedAccessor$" + (i++);
        clz.interfaces.add("roj/kscript/func/gen/GeneratedFunction");
        clz.accesses = new FlagList(AccessFlag.SUPER_OR_SYNC, AccessFlag.PUBLIC);

        roj.asm.struct.Method call = new roj.asm.struct.Method(0x0001, clz, "call", "(ILroj/kscript/data/KObject;Lroj/kscript/Arguments;)Lroj/kscript/data/KBase;");
        clz.methods.add(call);

        AttrCode c = call.code = new AttrCode(call);

        Map<String, Method> map = new MyHashMap<>();

        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (method.isSynthetic() || method.isBridge())
                continue;
            JvavMethod jvavMethod = method.getDeclaredAnnotation(JvavMethod.class);
            if (jvavMethod != null) {
                if (!Modifier.isPublic(method.getModifiers()))
                    throw new IllegalArgumentException(clazz.getName() + '.' + method.toGenericString() + " should be public.");
                String name = jvavMethod.name().equals("") ? method.getName() : jvavMethod.name();
                map.put(name, method);
            }
        }

        c.computeFrames = true;
        //c.instructions.add()

        Set<Map.Entry<String, Object>> set = Helpers.cast(map.entrySet());

        int i = 0;
        for (Map.Entry<String, Object> entry : set) {
            construct((Method) entry.getValue(), call.code);
            entry.setValue(i++);
        }

        GeneratedFunction function = defineClazz(clz);

        for (Map.Entry<String, Object> entry : set) {
            entry.setValue(new KFuncGenerated(function, (Integer) entry.getValue()));
        }

        return new KObject(Type.OBJECT, null, Helpers.cast(map));
    }

    private static GeneratedFunction defineClazz(Clazz clz) {
        String newClassName;
        try {
            ClassDefiner.INSTANCE.defineClass(newClassName = clz.name.replace('/', '.'), Parser.toByteArray(clz));
            Class<?> cz = Class.forName(newClassName);
            return (GeneratedFunction) SunReflection.createClass(cz);
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException | ClassNotFoundException e) {
            throw new RuntimeException("RMG internal error", e);
        }
    }

    private static void construct(Method value, AttrCode code) {

    }

    public static IGettable createReflectiveJavaFunction(Class<?> clazz) {
        throw OperationDone.NEVER;
    }

    public static KFunction createReflectiveJavaFunction(Method method) {
        throw OperationDone.NEVER;
    }
}
