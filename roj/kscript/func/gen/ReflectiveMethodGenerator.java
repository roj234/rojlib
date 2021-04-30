package roj.kscript.func.gen;

import roj.asm.Parser;
import roj.asm.struct.Clazz;
import roj.asm.struct.attr.AttrCode;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.collect.MyHashMap;
import roj.concurrent.OperationDone;
import roj.kscript.api.IObject;
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

    public static IObject createRegisteredJavaFunction(Class<?> clazz) {
        Clazz clz = new Clazz();
        clz.version = 52 << 16;
        clz.name = "roj/kscript/func/gen/GJavaFn$" + (i++);
        clz.interfaces.add("roj/kscript/func/gen/KFuncJava");
        clz.accesses = new FlagList(AccessFlag.SUPER_OR_SYNC, AccessFlag.PUBLIC);

        roj.asm.struct.Method call = new roj.asm.struct.Method(AccessFlag.PUBLIC, clz, "invoke", "(Lroj/kscript/data/IObject;Lroj/kscript/Arguments;)Lroj/kscript/data/KType;");
        clz.methods.add(call);

        roj.asm.struct.Method copyAs = new roj.asm.struct.Method(0, clz, "copyAs", "(I)Lroj/kscript/func/gen/KFuncJava;");
        clz.methods.add(copyAs);

        AttrCode c = copyAs.code = new AttrCode(copyAs);

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

        Set<Map.Entry<String, Object>> set = Helpers.cast(map.entrySet());
        for (Map.Entry<String, Object> entry : set) {
            addMethod((Method) entry.getValue(), copyAs.code);
        }

        KFuncJava fn = defineClazz(clz);

        int i = 0;
        for (Map.Entry<String, Object> entry : set) {
            entry.setValue(fn.copyAs(i++));
        }

        return new KObject(Type.OBJECT, null, Helpers.cast(map));
    }

    private static KFuncJava defineClazz(Clazz clz) {
        String name;
        try {
            ClassDefiner.INSTANCE.defineClass(name = clz.name.replace('/', '.'), Parser.toByteArray(clz));
            Class<?> cz = Class.forName(name);
            return (KFuncJava) SunReflection.createClass(cz);
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException | ClassNotFoundException e) {
            throw new RuntimeException("RMG internal error", e);
        }
    }

    private static void addMethod(Method value, AttrCode code) {

    }

    public static IObject createReflectiveJavaFunction(Class<?> clazz) {
        throw OperationDone.NEVER;
    }

    public static KFunction createReflectiveJavaFunction(Method method) {
        throw OperationDone.NEVER;
    }
}
