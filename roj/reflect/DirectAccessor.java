/*
 * This file is a part of MoreItems
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
import roj.asm.cst.CstUTF;
import roj.asm.tree.Clazz;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.ClassInsnNode;
import roj.asm.tree.insn.FieldInsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.tree.insn.LoadConstInsnNode;
import roj.asm.type.NativeType;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.asm.util.InsnList;
import roj.asm.util.NodeHelper;
import roj.collect.IBitSet;
import roj.collect.MyHashMap;
import roj.collect.SingleBitSet;
import roj.text.CharList;
import roj.util.ByteList;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static roj.asm.type.NativeType.CLASS;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/13 20:16
 */
public final class DirectAccessor<T> {
    public static final String MAGIC_ACCESSOR_CLASS = "sun/reflect/MagicAccessorImpl";
    public static boolean      DEBUG; // no-op currently

    static final AtomicInteger        NEXT_ID       = new AtomicInteger();
    static final FlagList             PUBLIC_ACCESS = new FlagList(AccessFlag.PUBLIC);
    public static final IBitSet       EMPTY_BITS    = new SingleBitSet();

    private final MyHashMap<String, Method> methodByName;
    private final Class<T> owner;
    private final Clazz    var;
    private       CharList sb;
    private       T        instance;

    private FieldInsnNode getInstance;
    private Class<?> cacheClass;

    private DirectAccessor(Class<T> deClass) {
        this.owner = deClass;
        if(!deClass.isInterface())
            throw new IllegalArgumentException(deClass.getName() + " should be a interface");
        Method[] methods = deClass.getDeclaredMethods();
        this.methodByName = new MyHashMap<>(methods.length);
        for (Method method : methods) {
            if((method.getModifiers() & AccessFlag.STATIC) != 0)
                throw new IllegalArgumentException(deClass.getName() + " should not have static methods");

            switch (method.getName()) { // skip 'internal' methods
                case "toString":
                case "getInstance":
                case "clearInstance":
                    if(method.getParameterCount() == 0)
                        continue;
                    break;
                case "setInstance":
                    if(method.getParameterCount() == 1 && method.getParameterTypes()[0] == Object.class)
                        continue;
                    break;
            }

            if(methodByName.put(method.getName(), method) != null) {
                throw new IllegalArgumentException("Duplicate method '" + method.getName() + "' in " + deClass.getName());
            }
        }
        var = new Clazz();
        String clsName = "roj/reflect/DAB$" + NEXT_ID.getAndIncrement();
        makeHeader(clsName, deClass.getName().replace('.', '/'), var);
        addInit(var, PUBLIC_ACCESS);
        if(DEBUG)
            this.sb = new CharList().append("[owner: ").append(deClass.getName()).append(", self: ").append(var.name).append(", via: [");
    }

    /**
     * 构建DirectAccessor
     * @return T
     */
    @SuppressWarnings("unchecked")
    public synchronized T build() {
        if(instance != null)
            return instance;

        writeDebugInfo();
        methodByName.clear();

        try {
            ByteList list = var.getBytes();
            Class<?> clz = ClassDefiner.INSTANCE.defineClassC(var.name.replace('/', '.'), list.list, 0, list.pos());
            return instance = (T) SunReflection.createClass(clz);
        } catch (ClassFormatError | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException("Internal Error!", e);
        }
    }

    public String getClassName() {
        return var.name;
    }

    public byte[] toByteArray() {
        writeDebugInfo();
        methodByName.clear();

        return var.getBytes().toByteArray();
    }

    private void writeDebugInfo() {
        if(sb != null) {
            roj.asm.tree.Method toString = new roj.asm.tree.Method(PUBLIC_ACCESS, var, "toString",
                                                                   "()Ljava/lang/String;");
            AttrCode code = toString.code = new AttrCode(toString);

            code.stackSize = 1;
            code.localSize = 1;
            InsnList insn = code.instructions;
            insn.add(new LoadConstInsnNode(Opcodes.LDC, new CstUTF(sb.append(']').toString())));
            insn.add(NodeHelper.cached(Opcodes.ARETURN));
            insn.add(AttrCode.METHOD_END_MARK);

            var.methods.add(toString);
            sb = null;
        }
    }

    /**
     * get,set,clear Instance via Instanced or other...
     */
    public DirectAccessor<T> makeCache(Class<?> targetClass) {
        if(cacheClass != null)
            throw new IllegalStateException("Cache already set!");
        cacheClass = targetClass;

        AttrCode code;

        roj.asm.tree.Method set = new roj.asm.tree.Method(PUBLIC_ACCESS, var, "setInstance", "(Ljava/lang/Object;)V");
        set.code = code = new AttrCode(set);

        String type = targetClass.getName().replace('.', '/');
        FieldInsnNode setInstance = new FieldInsnNode(Opcodes.PUTFIELD, var.name, "instance", new Type(type));

        code.stackSize = 2;
        code.localSize = 2;
        InsnList insn = code.instructions;
        insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
        insn.add(NodeHelper.cached(Opcodes.ALOAD_1));
        insn.add(new ClassInsnNode(Opcodes.CHECKCAST, type));
        insn.add(setInstance);
        insn.add(NodeHelper.cached(Opcodes.RETURN));
        insn.add(AttrCode.METHOD_END_MARK);

        var.methods.add(set);

        roj.asm.tree.Method clear = new roj.asm.tree.Method(PUBLIC_ACCESS, var, "clearInstance", "()V");
        clear.code = code = new AttrCode(clear);

        code.stackSize = 2;
        code.localSize = 1;

        insn = code.instructions;
        insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
        insn.add(NodeHelper.cached(Opcodes.ACONST_NULL));
        insn.add(setInstance);
        insn.add(NodeHelper.cached(Opcodes.RETURN));
        insn.add(AttrCode.METHOD_END_MARK);

        var.methods.add(clear);

        roj.asm.tree.Method get = new roj.asm.tree.Method(PUBLIC_ACCESS, var, "getInstance", "()Ljava/lang/Object;");
        get.code = code = new AttrCode(get);

        code.stackSize = 1;
        code.localSize = 1;

        insn = code.instructions;
        insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
        insn.add(getInstance = new FieldInsnNode(Opcodes.GETFIELD, var.name, "instance", new Type(type)));
        insn.add(NodeHelper.cached(Opcodes.ARETURN));
        insn.add(AttrCode.METHOD_END_MARK);

        var.methods.add(get);

        return this;
    }

    /**
     * @see #construct(Class, List, String...)
     */
    public DirectAccessor<T> construct(Class<?> target, String methodName) {
        return construct(target, (List<Class<?>[]>) null, methodName);
    }

    /**
     * @see #construct(Class, List, String...)
     */
    public DirectAccessor<T> construct(Class<?> target, String... methodNames) {
        return construct(target, null, methodNames);
    }

    /**
     * @see #construct(Class, List, String...)
     */
    public DirectAccessor<T> constructFuzzy(Class<?> target, String... methodNames) {
        return construct(target, Collections.emptyList(), methodNames);
    }

    /**
     * 把 methodNames 中的方法标记为 target 的实例化器 <br>
     *     <br>
     *     all-object 模式: 输入和输出均为 Object, 当你无法在代码中访问目标类时有奇效<br>
     *         #双重动态<br>
     *     <br>
     * @param objectModes <br>
     *     当这个值为null: 不使用 all-object 模式 <br>
     *     当这个值为空列表: 使用 模糊的 all-object 模式 <br>
     *     当这个值为非空列表 (长度必须等于 methodNames.length): <br>
     *         对其中值为null的项使用模糊的 all-object 模式 <br>
     *         否则使用精确的 all-object 模式 <br>
     *      <br>
     * @return this
     */
    public DirectAccessor<T> construct(Class<?> target, List<Class<?>[]> objectModes, String... methodNames) {
        if(methodNames.length == 0)
            return this;

        Class<?>[] invokerReturns = new Class<?>[methodNames.length];
        Constructor<?>[] targetMethods = new Constructor<?>[methodNames.length];

        Constructor<?>[] constructors = target.getConstructors();
        for (int i = 0; i < methodNames.length; i++) {
            String name = methodNames[i];
            Method method = methodByName.remove(name);
            if (method == null) {
                throw new IllegalArgumentException(owner.getName() + '.' + name + " not exist or already in use!");
            }
            if (!method.getReturnType().isAssignableFrom(target)) {
                throw new IllegalArgumentException(
                        owner.getName() + '.' + name + "'s return type (" + method.getReturnType()
                                                                                  .getName() + ") cannot cast to " + target
                                .getName());
            }
            invokerReturns[i] = method.getReturnType();
            Class<?>[] types = method.getParameterTypes();
            try {
                if (objectModes == null || (!objectModes.isEmpty() && objectModes.get(i) != null)) {
                    targetMethods[i] = target.getConstructor(objectModes == null ? types : objectModes.get(i));
                } else {
                    objectToObject(types);
                    int found = 0;
                    outer:
                    for (Constructor<?> cr : constructors) {
                        if (cr.getParameterCount() == types.length) {
                            Class<?>[] constParams = objectToObject(cr.getParameterTypes());
                            for (int j = 0; j < constParams.length; j++) {
                                if (constParams[j] != types[j]) {
                                    continue outer;
                                }
                            }
                            if(found++ > 0)
                                throw new IllegalArgumentException("Couldn't use 'all-object' mode: ambiguous method descriptor (are same after fuzzed)");
                            targetMethods[i] = cr;
                        }
                    }
                    if(found == 0)
                        throw new NoSuchMethodException();
                }
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                        "Unable to find " + target.getName() + ".<init> with parameter " + ParamHelper.classDescriptors(
                                types, void.class));
            }
        }
        if(sb != null) {
            sb.append("{C, ").append(target.getName()).append(": [");
            for (int i = 0; i < targetMethods.length; i++) {
                sb.append(methodNames[i]).append(", ");
            }
            sb.append("]}");
        }

        String targetName = target.getName().replace('.', '/');

        for (int i = 0; i < targetMethods.length; i++) {
            Constructor<?> method = targetMethods[i];
            Class<?>[] params = method.getParameterTypes();

            String initParam = ParamHelper.classDescriptors(params, void.class);
            String methodParam = ParamHelper.classDescriptors(objectModes != null ? objectToObject(params) : params, invokerReturns[i]);

            roj.asm.tree.Method invoke = new roj.asm.tree.Method(PUBLIC_ACCESS, var, methodNames[i], methodParam);

            AttrCode code;
            invoke.code = code = new AttrCode(invoke);

            InsnList insn = code.instructions;
            insn.add(new ClassInsnNode(Opcodes.NEW, targetName));
            insn.add(NodeHelper.cached(Opcodes.DUP));

            int size = method.getParameterCount();
            for (int j = 0; j < params.length; ) {
                String tag = ParamHelper.XPrefix(params[j]);
                switch (tag) {
                    case "D":
                    case "L":
                        size++;
                }
                NodeHelper.compress(insn, NodeHelper.X_LOAD(tag.charAt(0)), ++j);
            }

            code.stackSize = code.localSize = size + 1;

            insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, targetName, "<init>", initParam));
            insn.add(NodeHelper.cached(Opcodes.ARETURN));
            insn.add(AttrCode.METHOD_END_MARK);

            var.methods.add(invoke);
        }

        return this;
    }

    /**
     * @see #delegate(Class, String[], IBitSet, String[], boolean, List)
     */
    public DirectAccessor<T> delegate(Class<?> target, String methodName) {
        String[] arr = new String[] {methodName};
        return delegate(target, arr, EMPTY_BITS, arr, false, null);
    }

    /**
     * @see #delegate(Class, String[], IBitSet, String[], boolean, List)
     */
    public DirectAccessor<T> delegate(Class<?> target, String methodName, String selfName) {
        return delegate(target, new String[] {methodName}, EMPTY_BITS, new String[] {selfName}, false, null);

    }

    /**
     * @see #delegate(Class, String[], IBitSet, String[], boolean, List)
     */
    public DirectAccessor<T> delegate(Class<?> target, String... methodNames) {
        return delegate(target, methodNames, EMPTY_BITS, methodNames, false, null);
    }

    /**
     * @see #delegate(Class, String[], IBitSet, String[], boolean, List)
     */
    public DirectAccessor<T> delegate(Class<?> target, String[] methodNames, String[] selfNames) {
        return delegate(target, methodNames, EMPTY_BITS, selfNames, false, null);
    }

    /**
     * @see #delegate(Class, String[], IBitSet, String[], boolean, List)
     */
    public DirectAccessor<T> delegate_o(Class<?> target, String methodName) {
        String[] arr = new String[] {methodName};
        return delegate(target, arr, EMPTY_BITS, arr, false, Collections.emptyList());
    }

    /**
     * @see #delegate(Class, String[], IBitSet, String[], boolean, List)
     */
    public DirectAccessor<T> delegate_o(Class<?> target, String methodName, String selfName) {
        return delegate(target, new String[]{ methodName }, EMPTY_BITS, new String[]{ selfName }, false, Collections.emptyList());
    }

    /**
     * @see #delegate(Class, String[], IBitSet, String[], boolean, List)
     */
    public DirectAccessor<T> delegate_o(Class<?> target, String[] methodNames, String[] selfNames) {
        return delegate(target, methodNames, EMPTY_BITS, selfNames, false, Collections.emptyList());
    }

    /**
     * 把 selfMethodNames 中的方法标记为 target 的 targetMethodNames 方法的调用者 <br>
     *     <br>
     * @param objectModes : @see #construct(Class, List, String...)
     * @param invokeType 当set中对应index项为true时代表直接调用此方法(忽略继承)
     * @param useCache 使用缓存field中的对象
     * @return this
     */
    public DirectAccessor<T> delegate(Class<?> target, String[] targetMethodNames, @Nullable IBitSet invokeType, String[] selfMethodNames, boolean useCache, List<Class<?>[]> objectModes) {
        if(selfMethodNames.length == 0)
            return this;
        if(useCache) {
            if(getInstance == null)
                throw new IllegalArgumentException("Use cache, but no cache available");
            if(!cacheClass.isAssignableFrom(target))
                throw new IllegalArgumentException("Use cache '" + cacheClass.getName() + "', but '" + target.getName() + "' can't cast to it.");
        }

        Method[] targetMethods = new Method[selfMethodNames.length];

        List<Method> methods = ReflectionUtils.getMethods(target);
        for (int i = 0; i < selfMethodNames.length; i++) {
            String name = selfMethodNames[i];
            Method method = methodByName.remove(name);
            if (method == null) {
                throw new IllegalArgumentException(owner.getName() + '.' + name + " not exist or already in use!");
            }

            Class<?>[] types = method.getParameterTypes();
            if(!useCache && types[0] != Object.class)
                throw new IllegalArgumentException(
                        owner.getName() + '.' + name + "'s first parameter (" + types[0].getName() + ") is not Object");

            int off = useCache ? 0 : 1;
            try {
                boolean fuzzy = false;
                if(objectModes != null) {
                    if (!objectModes.isEmpty() && objectModes.get(i) != null) {
                        types = objectModes.get(i);
                        off = 0;
                    } else {
                        objectToObject(types);
                        fuzzy = true;
                    }
                }

                int found = -1;
                outer:
                for (int j = 0; j < methods.size(); j++) {
                    Method m = methods.get(j);
                    // NCI 无法用在静态方法上
                    if(off != 0 && (m.getModifiers() & AccessFlag.STATIC) != 0) continue;
                    if (m.getParameterCount() == types.length - off) {
                        Class<?>[] types2 = m.getParameterTypes();
                        if (fuzzy) {
                            objectToObject(types2);
                        }
                        for (int k = 0; k < types2.length; k++) {
                            if (types2[k] != types[k + off]) {
                                continue outer;
                            }
                        }
                        if (found != -1) {
                            throw new IllegalArgumentException("Couldn't use 'all-object' mode: ambiguous method descriptor (are same after fuzzed)");
                        }
                        found = j;
                        targetMethods[i] = m;
                    }
                }
                if(found == -1)
                    throw new NoSuchMethodException();
                methods.remove(found);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                        "Unable to find " + target.getName() + '.' + targetMethodNames[i] + " with parameter " + Arrays.toString(
                                types));
            }

            if (!method.getReturnType().isAssignableFrom(targetMethods[i].getReturnType())) {
                throw new IllegalArgumentException(
                        owner.getName() + '.' + name + "'s return type (" + method.getReturnType()
                                                                                  .getName() + ") cannot cast to " + target
                                .getName());
            }
        }

        if(sb != null) {
            sb.append("{M, ").append(target.getName()).append(": [");
            for (int i = 0; i < targetMethods.length; i++) {
                sb.append(targetMethodNames[i]).append(" => ").append(selfMethodNames[i]).append(", ");
            }
            sb.append("]}");
        }

        String targetName = target.getName().replace('.', '/');

        for (int i = 0, len = targetMethods.length; i < len; i++) {
            Method method = targetMethods[i];

            Class<?>[] params = method.getParameterTypes();

            String desc = ParamHelper.classDescriptors(params, method.getReturnType());

            String selfDesc = objectModes == null ? desc : ParamHelper.classDescriptors(objectToObject(params),
                            method.getReturnType().isPrimitive() ? method.getReturnType() : Object.class);
            roj.asm.tree.Method invoke = new roj.asm.tree.Method(PUBLIC_ACCESS, var, selfMethodNames[i], selfDesc);
            AttrCode code;
            invoke.code = code = new AttrCode(invoke);
            var.methods.add(invoke);

            InsnList insn = code.instructions;

            int isStatic = (method.getModifiers() & AccessFlag.STATIC) != 0 ? 1 : 0;
            if (isStatic == 0) {
                if (useCache) {
                    insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
                    insn.add(getInstance);
                } else {
                    insn.add(NodeHelper.cached(Opcodes.ALOAD_1));
                    invoke.parameters().add(0, new Type("java/lang/Object"));
                }
            }

            int size = useCache ? 0 : 1;
            for (Class<?> param : params) {
                String tag = ParamHelper.XPrefix(param);
                NodeHelper.compress(insn, NodeHelper.X_LOAD(tag.charAt(0)), ++size);
                switch (tag) {
                    case "D":
                    case "L":
                        size++;
                }
            }

            code.stackSize = Math.max(size + isStatic, 1);
            code.localSize = size + 1;

            insn.add(new InvokeInsnNode(isStatic == 1 ? Opcodes.INVOKESTATIC : (invokeType != null && invokeType.contains(i) ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL), targetName, method.getName(), desc));
            insn.add(NodeHelper.X_RETURN(ParamHelper.XPrefix(method.getReturnType())));
            insn.add(AttrCode.METHOD_END_MARK);
        }
        return this;
    }

    /**
     * @see #access(Class, String[], String[], String[], boolean)
     */
    public DirectAccessor<T> access(Class<?> target, String fieldName) {
        return access(target, new String[]{fieldName});
    }

    /**
     * @see #access(Class, String[], String[], String[], boolean)
     */
    public DirectAccessor<T> access(Class<?> target, String... fieldNames) {
        return access(target, fieldNames, capitalize(fieldNames, "get"), capitalize(fieldNames, "set"), false);
    }

    /**
     * @see #access(Class, String[], String[], String[], boolean)
     */
    public DirectAccessor<T> access_cached(Class<?> target, String fieldName) {
        return access_cached(target, new String[]{fieldName});
    }

    /**
     * @see #access(Class, String[], String[], String[], boolean)
     */
    public DirectAccessor<T> access_cached(Class<?> target, String... fieldNames) {
        return access(target, fieldNames, capitalize(fieldNames, "get"), capitalize(fieldNames, "set"), true);
    }

    /**
     * 把 setter/getterNames 中的方法标记为 target 的 fieldNames 的 setter / getter <br>
     *     <br>
     * @param useCache 使用缓存field中的对象
     * @return this
     */
    public DirectAccessor<T> access(Class<?> target, String[] fieldNames, String[] getterNames, String[] setterNames, boolean useCache) {
        if(fieldNames.length == 0)
            return this;
        if(useCache) {
            if(getInstance == null)
                throw new IllegalArgumentException("Use cache, but no cache available");
            if(!cacheClass.isAssignableFrom(target))
                throw new IllegalArgumentException("Use cache '" + cacheClass.getName() + "', but '" + target.getName() + "' can't cast to it.");
        }

        Field[] targetFields = new Field[fieldNames.length];
        Method[] setterMethods = new Method[fieldNames.length];
        Method[] getterMethods = new Method[fieldNames.length];

        List<Field> fields = ReflectionUtils.getFields(target);
        for (int i = 0; i < fieldNames.length; i++) {
            String name = fieldNames[i];

            int found = -1;
            for (int j = 0; j < fields.size(); j++) {
                Field f = fields.get(j);
                if (f.getName().equals(name)) {
                    if (found != -1) {
                        throw new IllegalArgumentException("卧槽！居然有同名字段！这大概是被混淆了, 你这时候应该使用那几个'弃用'的内部方法了");
                    }
                    found = j;
                }
            }

            if(found == -1)
                throw new IllegalArgumentException("Unable to find " + target.getName() + '.' + fieldNames[i]);
            targetFields[i] = fields.remove(found);
            int off = useCache ? 0 : 1;

            String getterName = getterNames[i];
            if(getterName != null) {
                Method method = methodByName.remove(getterName);
                if (method == null) {
                    throw new IllegalArgumentException(owner.getName() + '.' + getterName + " not exist or already in use!");
                }
                if(method.getParameterCount() != off)
                    throw new IllegalArgumentException(owner.getName() + '.' + getterName + " is a getter, " +
                                         "should not have parameters, got " + method.getParameterCount() + '!');
                if(method.getReturnType().isAssignableFrom(targetFields[i].getType()))
                    throw new IllegalArgumentException(owner.getName() + '.' + getterName + " is a getter, " +
                                         "but couldn't handle " + targetFields[i].getType().getName() + " (" + method.getReturnType() + ')');
                getterMethods[i] = method;
            }

            String setterName = getterNames[i];
            if(setterName != null) {
                Method method = methodByName.remove(setterName);
                if (method == null) {
                    throw new IllegalArgumentException(owner.getName() + '.' + setterName + " not exist or already in use!");
                }
                if(method.getParameterCount() != off + 1)
                    throw new IllegalArgumentException(owner.getName() + '.' + setterName + " is a setter, " +
                                                               "should have only 1 parameter, got " + method.getParameterCount() + '!');
                if(method.getReturnType() != void.class)
                    throw new IllegalArgumentException(owner.getName() + '.' + setterName + " is a setter, " +
                                                               "but its return type is not void: " + method.getReturnType());
                setterMethods[i] = method;
            }
        }

        if(sb != null) {
            sb.append("{F, ").append(target.getName()).append(": [");
            for (int i = 0; i < fieldNames.length; i++) {
                sb.append(fieldNames[i]).append(" => [").append(getterMethods[i]).append(", ").append(setterMethods[i]).append("], ");
            }
            sb.append("]}");
        }

        String targetName = target.getName().replace('.', '/');

        for (int i = 0, len = targetFields.length; i < len; i++) {
            Field field = targetFields[i];
            Type fieldType = ParamHelper.parseField(ParamHelper.classDescriptor(field.getType()));

            Method getter = getterMethods[i];
            if(getter != null) {
                roj.asm.tree.Method get = new roj.asm.tree.Method(PUBLIC_ACCESS, var, "get", "()" + ParamHelper.classDescriptor(getter.getReturnType()));
                AttrCode code = get.code = new AttrCode(get);

                char type = get.getReturnType().type;
                code.stackSize = type == NativeType.DOUBLE || type == NativeType.LONG ? 2 : 1;
                code.localSize = useCache ? 1 : 2;

                InsnList insn = code.instructions;
                if(useCache) {
                    insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
                    insn.add(getInstance);
                } else {
                    insn.add(NodeHelper.cached(Opcodes.ALOAD_1));
                }
                insn.add(new FieldInsnNode(Opcodes.GETFIELD, targetName, field.getName(), fieldType));
                insn.add(NodeHelper.X_RETURN(fieldType.nativeName()));
                insn.add(AttrCode.METHOD_END_MARK);

                var.methods.add(get);
            }

            Method setter = setterMethods[i];
            if(setter != null) {
                roj.asm.tree.Method set = new roj.asm.tree.Method(PUBLIC_ACCESS, var, "set", ParamHelper.classDescriptors(setter.getParameterTypes(), void.class));
                AttrCode code = set.code = new AttrCode(set);

                char type = set.getReturnType().type;
                code.stackSize = type == NativeType.DOUBLE || type == NativeType.LONG ? 2 : 1;
                code.localSize = code.stackSize + (useCache ? 0 : 1);

                InsnList insn = code.instructions;
                if(useCache) {
                    insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
                    insn.add(getInstance);
                } else {
                    insn.add(NodeHelper.cached(Opcodes.ALOAD_1));
                }
                insn.add(NodeHelper.X_LOAD_I(fieldType.nativeName().charAt(0), useCache ? 1 : 2));
                if (fieldType.type == CLASS)
                    insn.add(new ClassInsnNode(Opcodes.CHECKCAST, fieldType.owner));
                insn.add(new FieldInsnNode(Opcodes.PUTFIELD, targetName, field.getName(), fieldType));
                insn.add(NodeHelper.cached(Opcodes.RETURN));
                insn.add(AttrCode.METHOD_END_MARK);

                var.methods.add(set);
            }
        }
        return this;
    }

    public DirectAccessor<T> internal_construct(String targetName, String initParam, String selfMethodName) {
        Method method = methodByName.remove(selfMethodName);
        if (method == null) {
            throw new IllegalArgumentException(owner.getName() + '.' + selfMethodName + " not exist or already in use!");
        }

        targetName = targetName.replace('.', '/');

        String methodParam = ParamHelper.classDescriptors(method.getParameterTypes(), method.getReturnType());

        roj.asm.tree.Method invoke = new roj.asm.tree.Method(PUBLIC_ACCESS, var, selfMethodName, methodParam);
        AttrCode code = invoke.code = new AttrCode(invoke);

        InsnList insn = code.instructions;
        insn.add(new ClassInsnNode(Opcodes.NEW, targetName));
        insn.add(NodeHelper.cached(Opcodes.DUP));

        int size = method.getParameterCount();
        List<Type> params = ParamHelper.parseMethod(initParam);
        params.remove(params.size() - 1);
        for (int j = 0; j < params.size(); j++) {
            switch (params.get(j).type) {
                case 'D':
                case 'L':
                    size++;
            }
            NodeHelper.compress(insn, NodeHelper.X_LOAD(params.get(j).type), ++j);
        }

        code.stackSize = code.localSize = size + 1;

        insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, targetName, "<init>", initParam));
        insn.add(NodeHelper.cached(Opcodes.ARETURN));
        insn.add(AttrCode.METHOD_END_MARK);

        var.methods.add(invoke);

        return this;
    }

    public DirectAccessor<T> internal_delegate(String targetName, String targetMethodName, String targetMethodDesc, String selfMethodName, boolean isStatic, boolean isDirect) {
        Method method = methodByName.remove(selfMethodName);
        if (method == null) {
            throw new IllegalArgumentException(owner.getName() + '.' + selfMethodName + " not exist or already in use!");
        }

        targetName = targetName.replace('.', '/');

        String selfDesc = ParamHelper.classDescriptors(method.getParameterTypes(), method.getReturnType());

        roj.asm.tree.Method invoke = new roj.asm.tree.Method(PUBLIC_ACCESS, var, selfMethodName, selfDesc);
        AttrCode code;
        invoke.code = code = new AttrCode(invoke);
        var.methods.add(invoke);

        InsnList insn = code.instructions;

        if (!isStatic) {
            insn.add(NodeHelper.cached(Opcodes.ALOAD_1));
            invoke.parameters().add(0, new Type("java/lang/Object"));
        }

        List<Type> params = ParamHelper.parseMethod(targetMethodDesc);
        params.remove(params.size() - 1);
        int size = 1;
        for (Type param : params) {
            NodeHelper.compress(insn, NodeHelper.X_LOAD(param.type), ++size);
            switch (param.type) {
                case 'D':
                case 'L':
                    size++;
            }
        }

        code.stackSize = Math.max(size + (isStatic ? 1 : 0), 1);
        code.localSize = size + 1;

        insn.add(new InvokeInsnNode(isStatic ? Opcodes.INVOKESTATIC : (isDirect ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL), targetName, method.getName(), targetMethodDesc));
        insn.add(NodeHelper.X_RETURN(ParamHelper.XPrefix(method.getReturnType())));
        insn.add(AttrCode.METHOD_END_MARK);

        return this;
    }

    public DirectAccessor<T> internal_access(String targetName, String targetFieldName, Type targetType, String setterName, String getterName) {
        targetName = targetName.replace('.', '/');

        if(getterName != null) {
            Method method = methodByName.remove(getterName);
            if (method == null) {
                throw new IllegalArgumentException(owner.getName() + '.' + getterName + " not exist or already in use!");
            }
            if(method.getParameterCount() != 2)
                throw new IllegalArgumentException(owner.getName() + '.' + getterName + ": par: except 2, got " + method.getParameterCount() + '!');

            roj.asm.tree.Method get = new roj.asm.tree.Method(PUBLIC_ACCESS, var, "get", "()" + ParamHelper.classDescriptor(method.getReturnType()));
            AttrCode code = get.code = new AttrCode(get);

            char type = get.getReturnType().type;
            code.stackSize = type == NativeType.DOUBLE || type == NativeType.LONG ? 2 : 1;
            code.localSize = 2;

            InsnList insn = code.instructions;
            insn.add(NodeHelper.cached(Opcodes.ALOAD_1));
            insn.add(new FieldInsnNode(Opcodes.GETFIELD, targetName, targetFieldName, targetType));
            insn.add(NodeHelper.X_RETURN(targetType.nativeName()));
            insn.add(AttrCode.METHOD_END_MARK);

            var.methods.add(get);
        }

        if(setterName != null) {
            Method method = methodByName.remove(setterName);
            if (method == null) {
                throw new IllegalArgumentException(owner.getName() + '.' + setterName + " not exist or already in use!");
            }
            if(method.getParameterCount() != 1)
                throw new IllegalArgumentException(owner.getName() + '.' + setterName + " is a setter, " +
                                                           "should have only 1 parameter, got " + method.getParameterCount() + '!');
            if(method.getReturnType() != void.class)
                throw new IllegalArgumentException(owner.getName() + '.' + setterName + " is a setter, " +
                                                           "but its return type is not void: " + method.getReturnType());

            roj.asm.tree.Method set = new roj.asm.tree.Method(PUBLIC_ACCESS, var, "set", ParamHelper.classDescriptors(method.getParameterTypes(), void.class));
            AttrCode code = set.code = new AttrCode(set);

            char type = set.getReturnType().type;
            code.stackSize = type == NativeType.DOUBLE || type == NativeType.LONG ? 2 : 1;
            code.localSize = code.stackSize + 1;

            InsnList insn = code.instructions;
            insn.add(NodeHelper.cached(Opcodes.ALOAD_1));
            insn.add(NodeHelper.X_LOAD_I(targetType.nativeName().charAt(0), 2));
            if (targetType.type == CLASS)
                insn.add(new ClassInsnNode(Opcodes.CHECKCAST, targetType.owner));
            insn.add(new FieldInsnNode(Opcodes.PUTFIELD, targetName, targetFieldName, targetType));
            insn.add(NodeHelper.cached(Opcodes.RETURN));
            insn.add(AttrCode.METHOD_END_MARK);

            var.methods.add(set);
        }

        if(sb != null) {
            sb.append("{NF, ").append(targetName).append(": [")
            .append(targetFieldName).append(" => [").append(getterName).append(", ").append(setterName).append("]")
            .append("]}");
        }

        return this;
    }

    public static <V> DirectAccessor<V> builder(Class<V> deClass) {
        return new DirectAccessor<>(deClass);
    }

    /**
     * 首字母大写: xx,set => setXxx
     */
    public static String[] capitalize(String[] orig, String prefix) {
        CharList cl = new CharList();
        String[] dest = new String[orig.length];
        for (int i = 0; i < orig.length; i++) {
            cl.append(prefix).append(dest);
            cl.set(prefix.length(), Character.toUpperCase(cl.charAt(prefix.length())));
            dest[i] = cl.toString();
            cl.clear();
        }
        return dest;
    }

    /**
     * <init>
     * constructor
     */
    public static void addInit(Clazz clz, FlagList publicAccess) {
        AttrCode code;

        roj.asm.tree.Method init = new roj.asm.tree.Method(publicAccess, clz, "<init>", "()V");
        init.code = code = new AttrCode(init);

        code.stackSize = 1;
        code.localSize = 1;
        final InsnList insn = code.instructions;
        insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
        insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, MAGIC_ACCESSOR_CLASS + ".<init>:()V"));
        insn.add(NodeHelper.cached(Opcodes.RETURN));
        insn.add(AttrCode.METHOD_END_MARK);

        clz.methods.add(init);
    }

    /**
     * Header
     */
    public static void makeHeader(String selfName, String invokerName, Clazz clz) {
        clz.version = 52 << 16;
        clz.name = selfName.replace('.', '/');

        clz.parent = MAGIC_ACCESSOR_CLASS;
        clz.interfaces.add(invokerName.replace('.', '/'));
        clz.accesses = new FlagList(AccessFlag.SUPER_OR_SYNC | AccessFlag.PUBLIC);
    }

    /**
     * cast non-primitive to Object
     */
    public static Class<?>[] objectToObject(Class<?>[] params) {
        for (int i = 0; i < params.length; i++) {
            if(!params[i].isPrimitive())
                params[i] = Object.class;
        }
        return params;
    }
}
