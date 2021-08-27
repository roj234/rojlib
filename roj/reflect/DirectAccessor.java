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
import roj.asm.cst.CstString;
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
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.SingleBitSet;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.EmptyArrays;

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
import static roj.collect.IntMap.NOT_USING;

/**
 * 替代反射，目前不能修改final字段，然而这是JVM的锅 <br>
 * <br>
 * PackagePrivateProxy已被Nixim替代，能用到它的都是【在应用启动前加载的class】那还不如boot class替换
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/13 20:16
 */
public final class DirectAccessor<T> {
    public static final String  MAGIC_ACCESSOR_CLASS = "sun/reflect/MagicAccessorImpl";
    public static boolean       DEBUG;
    public static final IBitSet EMPTY_BITS           = new SingleBitSet();

    static final boolean        CHECK_CLASS_CAST = System.getProperty("roj.reflect.dac.checkCast") == null;
    static final AtomicInteger  NEXT_ID          = new AtomicInteger();
    static final FlagList       PUBLIC_ACCESS    = new FlagList(AccessFlag.PUBLIC);

    private final MyHashMap<String, Method> methodByName;
    private final MyCustomMap caches;
    private final Class<T> owner;
    private final Clazz    var;
    private       CharList sb;
    private       T        instance;
    private       MyCustomMap.Entry cache;

    static final class MyCustomMap extends MyHashMap<String, Class<?>> {
        static final class Entry extends MyHashMap.Entry<String, Class<?>> {
            public Entry(String s) {
                super(s, (Class<?>) IntMap.NOT_USING);
            }
            FieldInsnNode node;
        }

        @Override
        protected MyHashMap.Entry<String, Class<?>> createEntry(String id) {
            return new Entry(id);
        }

        public Class<?> put(String key, Class<?> e, FieldInsnNode node) {
            if (size > length * loadFactor) {
                length <<= 1;
                resize();
            }

            MyHashMap.Entry<String, Class<?>> entry = getOrCreateEntry(key);
            Class<?> old = entry.v;
            if (old == NOT_USING) {
                size++;
                entry.v = e;
                ((Entry) entry).node = node;
                return null;
            }
            return old;
        }
    }

    private DirectAccessor(Class<T> deClass, String packageName) {
        this.owner = deClass;
        if(!deClass.isInterface())
            throw new IllegalArgumentException(deClass.getName() + " should be a interface");
        Method[] methods = deClass.getMethods();
        this.methodByName = new MyHashMap<>(methods.length);
        for (Method method : methods) {
            if((method.getModifiers() & AccessFlag.STATIC) != 0)
                continue;

            // skip 'internal' methods
            if ("toString".equals(method.getName()) && method.getParameterCount() == 0) continue;

            if(methodByName.put(method.getName(), method) != null) {
                throw new IllegalArgumentException("方法名重复: '" + method.getName() + "' in " + deClass.getName());
            }
        }
        var = new Clazz();
        caches = new MyCustomMap();
        String clsName = packageName + "DAB$" + NEXT_ID.getAndIncrement();
        makeHeader(clsName, deClass.getName().replace('.', '/'), var);
        addInit(var, PUBLIC_ACCESS);
        if(DEBUG)
            this.sb = new CharList().append("实现类: ").append(deClass.getName()).append("\n自身: ").append(var.name);
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
            return instance = (T) InstantiationUtil.createClass(clz);
        } catch (ClassFormatError | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException("不该发生的内部错误", e);
        }
    }

    public String getClassName() {
        return var.name;
    }

    public synchronized byte[] toByteArray() {
        writeDebugInfo();
        methodByName.clear();

        return var.getBytes().toByteArray();
    }

    private void writeDebugInfo() {
        if(sb != null) {
            roj.asm.tree.Method toString = new roj.asm.tree.Method(PUBLIC_ACCESS, var, "toString", "()Ljava/lang/String;");
            AttrCode code = toString.code = new AttrCode(toString);

            code.stackSize = 1;
            code.localSize = 1;
            InsnList insn = code.instructions;
            insn.add(new LoadConstInsnNode(Opcodes.LDC, new CstString(sb.toString())));
            insn.add(NodeHelper.cached(Opcodes.ARETURN));
            insn.add(AttrCode.METHOD_END_MARK);

            var.methods.add(toString);
            sb = null;
        }
    }

    /**
     * @see #makeCache(Class, String, int)
     */
    public DirectAccessor<T> makeCache(Class<?> targetClass) {
        return makeCache(targetClass, "instance", 7);
    }

    /**
     * get,set,clear Instance via Instanced or other... <br>
     * @param methodFlag 1: get 2:set 4:clear 8:check existence, plus them
     */
    public DirectAccessor<T> makeCache(Class<?> targetClass, String name, int methodFlag) {
        if(caches.getEntry(name) != null)
            throw new IllegalStateException("Cache already set!");

        char c = Character.toUpperCase(name.charAt(0));
        String name1 = c == name.charAt(0) ? name : c + name.substring(1);

        String type = targetClass.getName().replace('.', '/');
        FieldInsnNode _set = new FieldInsnNode(Opcodes.PUTFIELD, var.name, name, new Type(type));
        FieldInsnNode _get = new FieldInsnNode(Opcodes.GETFIELD, var.name, name, new Type(type));

        AttrCode code;
        InsnList insn;

        if((methodFlag & 2) != 0) {
            if((methodFlag & 8) != 0) {
                checkExistence("set" + name1);
            }
            roj.asm.tree.Method set = new roj.asm.tree.Method(PUBLIC_ACCESS, var, "set" + name1,
                                                              "(Ljava/lang/Object;)V");
            set.code = code = new AttrCode(set);

            code.stackSize = 2;
            code.localSize = 2;
            insn = code.instructions;
            insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
            insn.add(NodeHelper.cached(Opcodes.ALOAD_1));
            if(CHECK_CLASS_CAST)
                insn.add(new ClassInsnNode(Opcodes.CHECKCAST, type));
            insn.add(_set);
            insn.add(NodeHelper.cached(Opcodes.RETURN));
            insn.add(AttrCode.METHOD_END_MARK);

            var.methods.add(set);
        }

        if((methodFlag & 4) != 0) {
            if((methodFlag & 8) != 0) {
                checkExistence("clear" + name1);
            }
            roj.asm.tree.Method clear = new roj.asm.tree.Method(PUBLIC_ACCESS, var, "clear" + name1, "()V");
            clear.code = code = new AttrCode(clear);

            code.stackSize = 2;
            code.localSize = 1;

            insn = code.instructions;
            insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
            insn.add(NodeHelper.cached(Opcodes.ACONST_NULL));
            insn.add(_set);
            insn.add(NodeHelper.cached(Opcodes.RETURN));
            insn.add(AttrCode.METHOD_END_MARK);

            var.methods.add(clear);
        }

        if((methodFlag & 1) != 0) {
            if((methodFlag & 8) != 0) {
                checkExistence("get" + name1);
            }
            roj.asm.tree.Method get = new roj.asm.tree.Method(PUBLIC_ACCESS, var, "get" + name1,
                                                              "()Ljava/lang/Object;");
            get.code = code = new AttrCode(get);

            code.stackSize = 1;
            code.localSize = 1;


            insn = code.instructions;
            insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
            insn.add(_get);
            insn.add(NodeHelper.cached(Opcodes.ARETURN));
            insn.add(AttrCode.METHOD_END_MARK);

            var.methods.add(get);
        }

        caches.put(name, targetClass, _get);

        return this;
    }

    private void checkExistence(String name) {
        Method method = methodByName.remove(name);
        if (method == null) {
            throw new IllegalArgumentException(owner.getName() + '.' + name + " 不存在或已被占用!");
        }
    }

    public DirectAccessor<T> useCache() {
        return useCache("instance");
    }

    public DirectAccessor<T> useCache(String name) {
        cache = (MyCustomMap.Entry) caches.getEntry(name);
        if(cache == null && name != null) {
            throw new IllegalArgumentException("Cache '" + name + "' not exist");
        }
        return this;
    }

    /**
     * @see #construct(Class, String[], List)
     */
    public DirectAccessor<T> construct(Class<?> target, String methodName) {
        return construct(target, new String[]{methodName}, null);
    }

    /**
     * @see #construct(Class, String[], List)
     */
    public DirectAccessor<T> construct(Class<?> target, String... methodNames) {
        return construct(target, methodNames, null);
    }

    /**
     * @see #construct(Class, String[], List)
     */
    public DirectAccessor<T> construct(Class<?> target, String methodName, Class<?>... methodTypes) {
        return construct(target, new String[] { methodName }, Collections.singletonList(methodTypes));
    }

    /**
     * @see #construct(Class, String[], List)
     */
    public DirectAccessor<T> constructFuzzy(Class<?> target, String... methodNames) {
        return construct(target, methodNames, Collections.emptyList());
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
     * @throws IllegalArgumentException 当提供的参数有误,不支持或者不存在时
     */
    public DirectAccessor<T> construct(Class<?> target, String[] methodNames, List<Class<?>[]> objectModes) throws IllegalArgumentException {
        if(methodNames.length == 0)
            return this;

        Class<?>[] invokerReturns = new Class<?>[methodNames.length];
        Constructor<?>[] targetMethods = new Constructor<?>[methodNames.length];

        Constructor<?>[] constructors = target.getConstructors();
        for (int i = 0; i < methodNames.length; i++) {
            String name = methodNames[i];
            Method method = methodByName.remove(name);
            if (method == null) {
                throw new IllegalArgumentException(owner.getName() + '.' + name + " 不存在或已被占用!");
            }
            if (!method.getReturnType().isAssignableFrom(target)) {
                throw new IllegalArgumentException(
                        owner.getName() + '.' + name + " 的返回值 (" + method.getReturnType()
                                                                         .getName() + ") 不兼容 " + target
                                .getName());
            }
            invokerReturns[i] = method.getReturnType();
            Class<?>[] types = method.getParameterTypes();
            if(objectModes != null) {
                for (int j = 0; j < types.length; j++) {
                    Class<?> type = types[j];
                    if (!type.isPrimitive() && type != Object.class) {
                        throw new IllegalArgumentException(
                                "无法为 " + owner.getName() + '.' + name + " 使用all-object: 第[" + (j + 1) + "]个参数既不是基本类型又不是Object");
                    }
                }
                if(!invokerReturns[i].isPrimitive() && invokerReturns[i] != Object.class)
                    throw new IllegalArgumentException(
                            "无法为 " + owner.getName() + '.' + name + " 使用all-object: 返回值既不是基本类型又不是Object");
            }

            try {
                if (objectModes == null || (!objectModes.isEmpty() && objectModes.get(i) != null)) {
                    // for exception
                    targetMethods[i] = target.getConstructor(objectModes == null ? types : (types = objectModes.get(i)));
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
                                throw new IllegalArgumentException(
                                        "无法为 " + owner.getName() + '.' + name + " 使用模糊模式: 对于指定非基本类型的数量和位置有多个符合的方法\n" +
                                                "其一: " + ParamHelper.classDescriptors(targetMethods[i].getParameterTypes(), void.class) + "\n" +
                                                "其二: " + ParamHelper.classDescriptors(cr.getParameterTypes(), void.class));
                            targetMethods[i] = cr;
                        }
                    }
                    if(found == 0)
                        throw new NoSuchMethodException();
                }
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                        "无法找到 " + target.getName() + " 的构造器, 参数: " + ParamHelper.classDescriptors(types, void.class));
            }
        }
        if(sb != null) {
            sb.append("\n  构造器代理: ").append(target.getName()).append("\n  方法:\n    ");
            for (int i = 0; i < targetMethods.length; i++) {
                Constructor<?> tm = targetMethods[i];
                sb.append(methodNames[i]).append(" (").append(ParamHelper.classDescriptors(
                        tm.getParameterTypes(), void.class)).append(")").append("\n    ");
            }
            sb.setIndex(sb.length() - 5);
        }

        String targetName = target.getName().replace('.', '/');

        for (int i = 0; i < targetMethods.length; i++) {
            Constructor<?> method = targetMethods[i];
            Class<?>[] params = method.getParameterTypes();

            String initParam = ParamHelper.classDescriptors(params, void.class);
            String methodParam = objectDescriptors(params, invokerReturns[i], objectModes == null);

            roj.asm.tree.Method invoke = new roj.asm.tree.Method(PUBLIC_ACCESS, var, methodNames[i], methodParam);

            AttrCode code;
            invoke.code = code = new AttrCode(invoke);

            InsnList insn = code.instructions;
            insn.add(new ClassInsnNode(Opcodes.NEW, targetName));
            insn.add(NodeHelper.cached(Opcodes.DUP));

            int size = 1;
            for (Class<?> param : params) {
                String tag = ParamHelper.XPrefix(param);
                NodeHelper.compress(insn, NodeHelper.X_LOAD(tag.charAt(0)), size++);
                if (CHECK_CLASS_CAST && !param.isPrimitive() && objectModes != null && param != Object.class) // 强制转换再做检查...
                    insn.add(new ClassInsnNode(Opcodes.CHECKCAST, param.getName().replace('.', '/')));
                switch (tag) {
                    case "D":
                    case "L":
                        size++;
                }
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
     * @see #delegate(Class, String[], IBitSet, String[], List)
     */
    public DirectAccessor<T> delegate(Class<?> target, String methodName) {
        String[] arr = new String[] {methodName};
        return delegate(target, arr, EMPTY_BITS, arr, null);
    }

    /**
     * @see #delegate(Class, String[], IBitSet, String[], List)
     */
    public DirectAccessor<T> delegate(Class<?> target, String methodName, String selfName) {
        return delegate(target, new String[] {methodName}, EMPTY_BITS, new String[] {selfName}, null);

    }

    /**
     * @see #delegate(Class, String[], IBitSet, String[], List)
     */
    public DirectAccessor<T> delegate(Class<?> target, String... methodNames) {
        return delegate(target, methodNames, EMPTY_BITS, methodNames, null);
    }

    /**
     * @see #delegate(Class, String[], IBitSet, String[], List)
     */
    public DirectAccessor<T> delegate(Class<?> target, String[] methodNames, String[] selfNames) {
        return delegate(target, methodNames, EMPTY_BITS, selfNames, null);
    }

    /**
     * @see #delegate(Class, String[], IBitSet, String[], List)
     */
    public DirectAccessor<T> delegate_o(Class<?> target, String methodName) {
        String[] arr = new String[] {methodName};
        return delegate(target, arr, EMPTY_BITS, arr, Collections.emptyList());
    }

    /**
     * @see #delegate(Class, String[], IBitSet, String[], List)
     */
    public DirectAccessor<T> delegate_o(Class<?> target, String[] methodNames) {
        return delegate(target, methodNames, EMPTY_BITS, methodNames, Collections.emptyList());
    }

    /**
     * @see #delegate(Class, String[], IBitSet, String[], List)
     */
    public DirectAccessor<T> delegate_o(Class<?> target, String methodName, String selfName) {
        return delegate(target, new String[]{ methodName }, EMPTY_BITS, new String[]{ selfName },
                        Collections.emptyList());
    }

    /**
     * @see #delegate(Class, String[], IBitSet, String[], List)
     */
    public DirectAccessor<T> delegate_o(Class<?> target, String methodName, String selfName, Class<?>... paramType) {
        return delegate(target, new String[]{ methodName }, EMPTY_BITS, new String[]{ selfName },
                        Collections.singletonList(paramType));
    }

    /**
     * @see #delegate(Class, String[], IBitSet, String[], List)
     */
    public DirectAccessor<T> delegate_o(Class<?> target, String[] methodNames, String[] selfNames) {
        return delegate(target, methodNames, EMPTY_BITS, selfNames, Collections.emptyList());
    }

    /**
     * 把 selfMethodNames 中的方法标记为 target 的 targetMethodNames 方法的调用者 <br>
     *     <br>
     * @param invokeType 当set中对应index项为true时代表直接调用此方法(忽略继承)
     * @param objectModes : @see #construct(Class, List, String...)
     * @return this
     * @throws IllegalArgumentException 当提供的参数有误,不支持或者不存在时
     */
    public DirectAccessor<T> delegate(Class<?> target, String[] targetMethodNames, @Nullable IBitSet invokeType, String[] selfMethodNames, List<Class<?>[]> objectModes) throws IllegalArgumentException {
        if(selfMethodNames.length == 0)
            return this;
        boolean useCache = cache != null;
        if(useCache) {
            if (!cache.v.isAssignableFrom(target))
                throw new IllegalArgumentException(
                        "使用了缓存的对象 '" + cache.v.getName() + "', 但是 '" + target.getName() + "' 不能转换为缓存的类 '" + cache.v.getName() + "'.");
        }

        Method[] targetMethods = new Method[selfMethodNames.length];

        List<Method> methods = ReflectionUtils.getMethods(target);
        for (int i = 0; i < selfMethodNames.length; i++) {
            String name = selfMethodNames[i];
            Method method = methodByName.remove(name);
            if (method == null) {
                throw new IllegalArgumentException(owner.getName() + '.' + name + " 不存在或已被占用!");
            }

            Class<?>[] types = method.getParameterTypes();
            if(objectModes != null) {
                for (int j = 0; j < types.length; j++) {
                    Class<?> type = types[j];
                    if (!type.isPrimitive() && type != Object.class) {
                        throw new IllegalArgumentException(
                                "无法为 " + owner.getName() + '.' + name + " 使用all-object: 第[" + (j + 1) + "]个参数既不是基本类型又不是Object");
                    }
                }
                if(!method.getReturnType().isPrimitive() && method.getReturnType() != Object.class)
                    throw new IllegalArgumentException(
                            "无法为 " + owner.getName() + '.' + name + " 使用all-object: 返回值既不是基本类型又不是Object");
            }

            int off = useCache ? 0 : 1;
            String targetMethodName = targetMethodNames[i];
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
                    int off1 = (m.getModifiers() & AccessFlag.STATIC) != 0 ? 0 : off;
                    if (m.getName().equals(targetMethodName) && m.getParameterCount() == types.length - off1) {
                        Class<?>[] types2 = m.getParameterTypes();
                        if (fuzzy) {
                            objectToObject(types2);
                        }
                        for (int k = 0; k < types2.length; k++) {
                            if (types2[k] != types[k + off1]) {
                                continue outer;
                            }
                        }

                        types = method.getParameterTypes();
                        if(off1 == 1 && target.isAssignableFrom(types[0])) {
                            throw new IllegalArgumentException("非缓存方法 " + owner.getName() + '.' + name + " 的第一个参数 (" + types[0].getName() + ") 不能转换为 " + target.getName());
                        }
                        if (found != -1) {
                            if(!Arrays.equals(m.getParameterTypes(), targetMethods[i].getParameterTypes())) {
                                throw new IllegalArgumentException(
                                        "无法为 " + owner.getName() + '.' + name + " 使用模糊模式: 对于指定非基本类型的数量和位置有多个符合的方法\n" +
                                                "其一: " + ParamHelper.classDescriptors(targetMethods[i].getParameterTypes(), targetMethods[i].getReturnType()) + "\n" +
                                                "其二: " + ParamHelper.classDescriptors(m.getParameterTypes(), m.getReturnType()));
                            } else {
                                // 继承，却改变了返回值的类型
                                // 同参同反不考虑
                                m = findInheritLower(m, targetMethods[i]);
                            }
                        }
                        found = j;
                        targetMethods[i] = m;
                    }
                }
                if(found == -1)
                    throw new NoSuchMethodException();
                methods.remove(found);
            } catch (NoSuchMethodException e) {
                if(DEBUG) {
                    for (int j = 0; j < methods.size(); j++) {
                        Method mm = methods.get(j);
                        System.out.println("名称: " + mm.getName() + " , 参数: " + ParamHelper.classDescriptors(mm.getParameterTypes(), mm.getReturnType()));
                    }
                }
                throw new IllegalArgumentException(
                        "无法找到指定的方法: " + target.getName() + '.' + targetMethodName + " 参数 " + ParamHelper.classDescriptors(types, method.getReturnType()));
            }

            if (!method.getReturnType().isAssignableFrom(targetMethods[i].getReturnType())) {
                throw new IllegalArgumentException(
                        owner.getName() + '.' + name + " 的返回值 (" + method.getReturnType()
                                                                                  .getName() + ") 不兼容 " + target
                                .getName());
            }
        }

        if(sb != null) {
            sb.append("\n  方法代理: ").append(target.getName()).append("\n  方法:\n    ");
            for (int i = 0; i < targetMethods.length; i++) {
                Method tm = targetMethods[i];
                sb.append(tm).append(" => ").append(selfMethodNames[i]).append(" (").append(ParamHelper.classDescriptors(
                        tm.getParameterTypes(), tm.getReturnType())).append(")").append("\n    ");
            }
            sb.setIndex(sb.length() - 5);
        }

        String targetName = target.getName().replace('.', '/');

        for (int i = 0, len = targetMethods.length; i < len; i++) {
            Method method = targetMethods[i];

            Class<?>[] params = method.getParameterTypes();

            String desc = ParamHelper.classDescriptors(params, method.getReturnType());

            String selfDesc = objectModes == null ? desc : objectDescriptors(params, method.getReturnType(), false);
            roj.asm.tree.Method invoke = new roj.asm.tree.Method(PUBLIC_ACCESS, var, selfMethodNames[i], selfDesc);
            AttrCode code;
            invoke.code = code = new AttrCode(invoke);
            var.methods.add(invoke);

            InsnList insn = code.instructions;

            int isStatic = (method.getModifiers() & AccessFlag.STATIC) != 0 ? 1 : 0;
            if (isStatic == 0) {
                if (useCache) {
                    insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
                    insn.add(cache.node);
                } else {
                    insn.add(NodeHelper.cached(Opcodes.ALOAD_1));
                    if (CHECK_CLASS_CAST)
                        insn.add(new ClassInsnNode(Opcodes.CHECKCAST, targetName));
                    invoke.parameters().add(0, new Type("java/lang/Object"));
                }
            }

            int size = useCache || isStatic == 1 ? 0 : 1;
            for (Class<?> param : params) {
                String tag = ParamHelper.XPrefix(param);
                NodeHelper.compress(insn, NodeHelper.X_LOAD(tag.charAt(0)), ++size);
                if (CHECK_CLASS_CAST && !param.isPrimitive() && objectModes != null && param != Object.class) // 强制转换再做检查...
                    insn.add(new ClassInsnNode(Opcodes.CHECKCAST, param.getName().replace('.', '/')));
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

    private static Method findInheritLower(Method a, Method b) {
        Class<?> aClass = a.getDeclaringClass();
        Class<?> bClass = b.getDeclaringClass();
                    // b instanceof a
        return aClass.isAssignableFrom(bClass) ? b : a;
    }

    /**
     * @see #access(Class, String[], String[], String[])
     */
    public DirectAccessor<T> access(Class<?> target, String fieldName) {
        return access(target, new String[]{fieldName});
    }

    /**
     * @see #access(Class, String[], String[], String[])
     */
    public DirectAccessor<T> access(Class<?> target, String[] fieldNames) {
        return access(target, fieldNames, capitalize(fieldNames, "get"), capitalize(fieldNames, "set"));
    }

    /**
     * @see #access(Class, String[], String[], String[])
     */
    public DirectAccessor<T> access(Class<?> target, String fieldName, String getterName, String setterName) {
        return access(target, new String[] { fieldName }, new String[] { getterName }, new String[]{ setterName });
    }

    /**
     * 把 setter/getterNames 中的方法标记为 target 的 fieldNames 的 setter / getter <br>
     *     <br>
     * @return this
     * @throws IllegalArgumentException 当提供的参数有误,不支持或者不存在时
     */
    public DirectAccessor<T> access(Class<?> target, String[] fieldNames, String[] getterNames, String[] setterNames) throws IllegalArgumentException {
        if(fieldNames.length == 0)
            return this;
        boolean useCache = cache != null;
        if(useCache) {
            if (!cache.v.isAssignableFrom(target))
                throw new IllegalArgumentException(
                        "使用了缓存的对象 '" + cache.v.getName() + "', 但是 '" + target.getName() + "' 不能转换为缓存的类 '" + cache.v.getName() + "'.");
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
                throw new IllegalArgumentException("无法找到字段 " + target.getName() + '.' + fieldNames[i]);
            int off = useCache || ((targetFields[i] = fields.remove(found)).getModifiers() & AccessFlag.STATIC) != 0 ? 0 : 1;

            String getterName = getterNames == null ? null : getterNames[i];
            if(getterName != null) {
                Method method = methodByName.remove(getterName);
                if (method == null) {
                    throw new IllegalArgumentException(owner.getName() + '.' + getterName + " 不存在或已被占用!");
                }
                if(method.getParameterCount() != off)
                    throw new IllegalArgumentException(owner.getName() + '.' + getterName + " 是个 getter, " +
                                         "不应该有参数, got " + (method.getParameterCount() - off) + '!');
                if(!method.getReturnType().isAssignableFrom(targetFields[i].getType()))
                    throw new IllegalArgumentException(owner.getName() + '.' + getterName + " 是个 getter, " +
                                         "但是返回值不兼容 " + targetFields[i].getType().getName() + " (" + method.getReturnType() + ')');
                getterMethods[i] = method;
            }

            String setterName = setterNames == null ? null : setterNames[i];
            if(setterName != null) {
                Method method = methodByName.remove(setterName);
                if (method == null) {
                    throw new IllegalArgumentException(owner.getName() + '.' + setterName + " 不存在或已被占用!");
                }
                if(method.getParameterCount() != off + 1)
                    throw new IllegalArgumentException(owner.getName() + '.' + setterName + " 是个 setter, " +
                                                               "只因有1个参数, got " + method.getParameterCount() + '!');
                if(!method.getParameterTypes()[off].isAssignableFrom(targetFields[i].getType()))
                    throw new IllegalArgumentException(owner.getName() + '.' + getterName + " 是个 setter, " +
                                                               "但是参数[" + (off + 1) + "]不兼容 " + targetFields[i].getType().getName() + " (" + method.getReturnType() + ')');
                if(method.getReturnType() != void.class)
                    throw new IllegalArgumentException(owner.getName() + '.' + setterName + " 是个 setter, " +
                                                               "但是它的返回值不是void: " + method.getReturnType());
                setterMethods[i] = method;
            }
        }

        if(sb != null) {
            sb.append("\n  字段代理: ").append(target.getName()).append("\n  方法:\n    ");
            for (int i = 0; i < targetFields.length; i++) {
                Field tf = targetFields[i];
                sb.append(tf.getName()).append(' ').append(tf.getType().getName()).append(" => [").append(getterMethods[i]).append(", ").append(setterMethods[i]).append("\n    ");
            }
            sb.setIndex(sb.length() - 5);
        }

        String targetName = target.getName().replace('.', '/');

        for (int i = 0, len = targetFields.length; i < len; i++) {
            Field field = targetFields[i];
            Type fieldType = ParamHelper.parseField(ParamHelper.classDescriptor(field.getType()));
            boolean isStatic = (field.getModifiers() & AccessFlag.STATIC) != 0;

            Method getter = getterMethods[i];
            if(getter != null) {
                roj.asm.tree.Method get = new roj.asm.tree.Method(PUBLIC_ACCESS, var, getter.getName(), ParamHelper.classDescriptors(isStatic || useCache ? EmptyArrays.CLASSES : getter.getParameterTypes(), getter.getReturnType()));
                AttrCode code = get.code = new AttrCode(get);

                char type = get.getReturnType().type;
                code.stackSize = type == NativeType.DOUBLE || type == NativeType.LONG ? 2 : 1;

                InsnList insn = code.instructions;
                if(!isStatic) {
                    code.localSize = useCache ? 1 : 2;
                    if (useCache) {
                        insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
                        insn.add(cache.node);
                    } else {
                        insn.add(NodeHelper.cached(Opcodes.ALOAD_1));
                        if (CHECK_CLASS_CAST)
                            insn.add(new ClassInsnNode(Opcodes.CHECKCAST, targetName));
                    }
                    insn.add(new FieldInsnNode(Opcodes.GETFIELD, targetName, field.getName(), fieldType));
                } else {
                    code.localSize = 1;
                    insn.add(new FieldInsnNode(Opcodes.GETSTATIC, targetName, field.getName(), fieldType));
                }
                insn.add(NodeHelper.X_RETURN(fieldType.nativeName()));
                insn.add(AttrCode.METHOD_END_MARK);

                var.methods.add(get);
            }

            Method setter = setterMethods[i];
            if(setter != null) {
                roj.asm.tree.Method set = new roj.asm.tree.Method(PUBLIC_ACCESS, var, setter.getName(), ParamHelper.classDescriptors(setter.getParameterTypes(), void.class));
                AttrCode code = set.code = new AttrCode(set);

                char type = set.getReturnType().type;
                code.stackSize = type == NativeType.DOUBLE || type == NativeType.LONG ? 3 : 2;

                InsnList insn = code.instructions;
                if(!isStatic) {
                    code.localSize = code.stackSize + (useCache ? 0 : 1);
                    if(useCache) {
                        insn.add(NodeHelper.cached(Opcodes.ALOAD_0));
                        insn.add(cache.node);
                    } else {
                        insn.add(NodeHelper.cached(Opcodes.ALOAD_1));
                        if (CHECK_CLASS_CAST)
                            insn.add(new ClassInsnNode(Opcodes.CHECKCAST, targetName));
                    }
                } else {
                    code.localSize = --code.stackSize;
                }
                insn.add(NodeHelper.X_LOAD_I(fieldType.nativeName().charAt(0), isStatic || useCache ? 1 : 2));
                if (CHECK_CLASS_CAST && fieldType.type == CLASS && !field.getType().isAssignableFrom(setter.getParameterTypes()[isStatic || useCache ? 0 : 1])) // 强制转换再做检查...
                    insn.add(new ClassInsnNode(Opcodes.CHECKCAST, fieldType.owner));
                insn.add(new FieldInsnNode(isStatic ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD, targetName, field.getName(), fieldType));
                insn.add(NodeHelper.cached(Opcodes.RETURN));
                insn.add(AttrCode.METHOD_END_MARK);

                var.methods.add(set);
            }
        }
        return this;
    }

    public DirectAccessor<T> i_construct(String targetName, String initParam, String selfMethodName) {
        Method method = methodByName.remove(selfMethodName);
        if (method == null) {
            throw new IllegalArgumentException(owner.getName() + '.' + selfMethodName + " 不存在或已被占用!");
        }

        targetName = targetName.replace('.', '/');

        String methodParam = ParamHelper.classDescriptors(method.getParameterTypes(), method.getReturnType());

        roj.asm.tree.Method invoke = new roj.asm.tree.Method(PUBLIC_ACCESS, var, selfMethodName, methodParam);
        AttrCode code = invoke.code = new AttrCode(invoke);

        InsnList insn = code.instructions;
        insn.add(new ClassInsnNode(Opcodes.NEW, targetName));
        insn.add(NodeHelper.cached(Opcodes.DUP));

        int size = 1;
        List<Type> params = ParamHelper.parseMethod(initParam);
        params.remove(params.size() - 1);
        for (int j = 0; j < params.size(); j++) {
            char pf;
            switch (params.get(j).type) {
                case NativeType.CLASS:
                    pf = 'A';
                    break;
                case NativeType.BOOLEAN:
                case NativeType.BYTE:
                case NativeType.CHAR:
                case NativeType.SHORT:
                    pf = 'I';
                    break;
                case NativeType.LONG:
                    pf = 'L';
                    break;
                default:
                    pf = params.get(j).type;
            }
            NodeHelper.compress(insn, NodeHelper.X_LOAD(pf), size++);
            switch (params.get(j).type) {
                case NativeType.DOUBLE:
                case NativeType.LONG:
                    size++;
            }
        }

        code.stackSize = code.localSize = size + 1;

        insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, targetName, "<init>", initParam));
        insn.add(NodeHelper.cached(Opcodes.ARETURN));
        insn.add(AttrCode.METHOD_END_MARK);

        var.methods.add(invoke);

        if(sb != null) {
            sb.append("\n  构造器代理[不安全]: ").append(targetName).append("\n  方法:\n    ")
              .append(selfMethodName).append(' ').append(initParam);
        }

        return this;
    }

    public DirectAccessor<T> i_delegate(String targetName, String targetMethodName, String targetMethodDesc, String selfMethodName, boolean isStatic, boolean isDirect) {
        Method method = methodByName.remove(selfMethodName);
        if (method == null) {
            throw new IllegalArgumentException(owner.getName() + '.' + selfMethodName + " 不存在或已被占用!");
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

        if(sb != null) {
            sb.append("\n  方法代理[不安全]: ").append(targetName).append("\n  方法:\n    ")
              .append(targetMethodName).append(' ').append(targetMethodDesc).append(" => ").append(selfMethodName).append(' ').append(selfDesc);
        }

        return this;
    }

    public DirectAccessor<T> i_access(String targetName, String targetFieldName, Type targetType, String setterName, String getterName) {
        targetName = targetName.replace('.', '/');

        if(getterName != null) {
            Method method = methodByName.remove(getterName);
            if (method == null) {
                throw new IllegalArgumentException(owner.getName() + '.' + getterName + " 不存在或已被占用!");
            }
            if(method.getParameterCount() != 2)
                throw new IllegalArgumentException(owner.getName() + '.' + getterName + ": 期待两个参数, got " + method.getParameterCount() + '!');

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
                throw new IllegalArgumentException(owner.getName() + '.' + setterName + " 不存在或已被占用!");
            }
            if(method.getParameterCount() != 1)
                throw new IllegalArgumentException(owner.getName() + '.' + setterName + " 是个 setter, " +
                                                           "只因有1个参数, got " + method.getParameterCount() + '!');
            if(method.getReturnType() != void.class)
                throw new IllegalArgumentException(owner.getName() + '.' + setterName + " 是个 setter, " +
                                                           "但是它的返回值不是void: " + method.getReturnType());

            roj.asm.tree.Method set = new roj.asm.tree.Method(PUBLIC_ACCESS, var, "set", ParamHelper.classDescriptors(method.getParameterTypes(), void.class));
            AttrCode code = set.code = new AttrCode(set);

            char type = set.getReturnType().type;
            code.stackSize = type == NativeType.DOUBLE || type == NativeType.LONG ? 2 : 1;
            code.localSize = code.stackSize + 1;

            InsnList insn = code.instructions;
            insn.add(NodeHelper.cached(Opcodes.ALOAD_1));
            if (CHECK_CLASS_CAST)
                insn.add(new ClassInsnNode(Opcodes.CHECKCAST, targetName));
            insn.add(NodeHelper.X_LOAD_I(targetType.nativeName().charAt(0), 2));
            if (CHECK_CLASS_CAST && targetType.type == CLASS)
                insn.add(new ClassInsnNode(Opcodes.CHECKCAST, targetType.owner));
            insn.add(new FieldInsnNode(Opcodes.PUTFIELD, targetName, targetFieldName, targetType));
            insn.add(NodeHelper.cached(Opcodes.RETURN));
            insn.add(AttrCode.METHOD_END_MARK);

            var.methods.add(set);
        }

        if(sb != null) {
            sb.append("\n  字段代理[不安全]: ").append(targetName).append("\n  方法:\n    ");
            sb.append(targetName).append(' ').append(targetType)
              .append(" => [").append(getterName).append(", ").append(setterName).append(']');
        }

        return this;
    }

    public static <V> DirectAccessor<V> builder(Class<V> deClass) {
        return new DirectAccessor<>(deClass, "roj/reflect/");
    }

    public static <V> DirectAccessor<V> withPackage(Class<V> deClass, Class<?> packageClass) {
        return new DirectAccessor<>(deClass, packageClass.getName().substring(0, packageClass.getName().lastIndexOf('/') + 1));
    }

    /**
     * 首字母大写: xx,set => setXxx
     */
    public static String[] capitalize(String[] orig, String prefix) {
        CharList cl = new CharList();
        String[] dest = new String[orig.length];
        for (int i = 0; i < orig.length; i++) {
            cl.append(prefix).append(orig[i]);
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
    static void addInit(Clazz clz, FlagList publicAccess) {
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
    static Class<?>[] objectToObject(Class<?>[] params) {
        for (int i = 0; i < params.length; i++) {
            if(!params[i].isPrimitive())
                params[i] = Object.class;
        }
        return params;
    }

    static String objectDescriptors(Class<?>[] classes, Class<?> returns, boolean no_obj) {
        CharList sb = ParamHelper.sharedBuffer.get();
        sb.clear();
        sb.append('(');

        for (Class<?> clazz : classes) {
            ParamHelper.classDescriptor(sb, no_obj | clazz.isPrimitive() ? clazz : Object.class);
        }
        sb.append(')');
        return ParamHelper.classDescriptor(sb, no_obj | returns.isPrimitive() ? returns : Object.class).toString();
    }
}
