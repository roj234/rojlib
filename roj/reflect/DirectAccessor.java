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
import roj.asm.Parser;
import roj.asm.cst.CstString;
import roj.asm.tree.Clazz;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.*;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.InsnList;
import roj.asm.util.NodeHelper;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.concurrent.Ref;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.EmptyArrays;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static roj.asm.type.Type.CLASS;
import static roj.asm.util.AccessFlag.PUBLIC;

/**
 * ?????????????????????????????????final?????????????????????JVM?????? <br>
 * <br>
 * PackagePrivateProxy??????Nixim????????????????????????????????????????????????????????????class???????????????boot class??????
 *
 * @author Roj233
 * @since 2021/8/13 20:16
 */
public class DirectAccessor<T> {
    public static final String  MAGIC_ACCESSOR_CLASS = "sun/reflect/MagicAccessorImpl";
    public static final boolean  DEBUG      = false;
    public static final MyBitSet EMPTY_BITS = new MyBitSet(0);

    static final AtomicInteger NEXT_ID = new AtomicInteger();

    private static final Ref<Object> CallbackBuffer = Ref.from();
    public static void syncCallback(Object handle) {
        CallbackBuffer.set(handle);
    }

    //
    private final MyHashMap<String, Method> methodByName;
    private final Class<T> owner;
    private       Clazz    var;

    // Cached object
    private final MyHashMap<String, Cache> caches;
    private       Cache                    cache;

    // Cast check
    private       boolean check;

    // Debug
    private       CharList sb;

    // State machine mode
    private Class<?> target;
    private String[] from, to;
    private List<Class<?>[]> fuzzy;

    static final class Cache {
        Class<?> clazz;
        FieldInsnNode node;
    }
    
    private DirectAccessor(DirectAccessor<T> prev) {
        this.methodByName = prev.methodByName;
        this.owner = prev.owner;
        this.var = prev.var;
        this.caches = prev.caches;
        this.cache = prev.cache;
        this.check = prev.check;
        this.sb = prev.sb;
        this.target = prev.target;
        this.from = prev.from;
        this.to = prev.to;
        this.fuzzy = prev.fuzzy;
    }

    private DirectAccessor(Class<T> deClass, String pkg, boolean checkDuplicate) {
        this.owner = deClass;
        this.check = true;
        if(!deClass.isInterface())
            throw new IllegalArgumentException(deClass.getName() + " should be a interface");
        Method[] methods = deClass.getMethods();
        this.methodByName = new MyHashMap<>(methods.length);
        for (Method method : methods) {
            if((method.getModifiers() & AccessFlag.STATIC) != 0)
                continue;

            // skip 'internal' methods
            if (("toString".equals(method.getName()) ||
                    "clone".equals(method.getName())) && method.getParameterCount() == 0) continue;

            if(methodByName.put(method.getName(), method) != null && checkDuplicate) {
                throw new IllegalArgumentException("???????????????: '" + method.getName() + "' in " + deClass.getName());
            }
        }
        var = new Clazz();
        caches = new MyHashMap<>();
        String clsName = pkg + "DAB$" + NEXT_ID.getAndIncrement();
        makeHeader(clsName, deClass.getName().replace('.', '/'), var);
        addInit(var);
        if(DEBUG)
            this.sb = new CharList().append("?????????: ").append(deClass.getName()).append("\n??????: ").append(var.name);
    }

    /**
     * ??????DirectAccessor
     * @return T
     */
    @SuppressWarnings("unchecked")
    public final synchronized T build() {
        if(var == null)
            throw new IllegalStateException("Already built");

        writeDebugInfo();
        methodByName.clear();

        try {
            return (T) i_build(var);
        } finally {
            var = null;
        }
    }

    public static Object i_build(Clazz var) {
        Object obj;
        ByteList list = Parser.toByteArrayShared(var);
        ClassDefiner.INSTANCE.defineClassC(var.name.replace('/', '.'), list.list, 0, list.wIndex());
        synchronized (CallbackBuffer) {
            try {
                Class.forName(var.name.replace('/', '.'), true, ClassDefiner.INSTANCE);
                if (null == (obj = CallbackBuffer.get())) {
                    throw new IllegalStateException("????????????: ACCESSOR_TMP.get() == null");
                }
            } catch (Throwable e) {
                throw new IllegalStateException("????????????: ???????????????", e);
            }
            CallbackBuffer.set(null);
        }
        return obj;
    }

    public final Clazz getInternal() {
        return var;
    }

    public final DirectAccessor<T> cloneable() {
        if (!var.interfaces.contains("java/lang/Cloneable")) {
            cloneable(var);
        }
        return this;
    }

    private void writeDebugInfo() {
        if(sb != null) {
            roj.asm.tree.Method toString = new roj.asm.tree.Method(PUBLIC, var, "toString", "()Ljava/lang/String;");
            AttrCode code = toString.code = new AttrCode(toString);

            code.stackSize = 1;
            code.localSize = 1;
            InsnList insn = code.instructions;
            insn.add(new LdcInsnNode(Opcodes.LDC, new CstString(sb.toString())));
            insn.add(NPInsnNode.of(Opcodes.ARETURN));

            var.methods.add(toString);
            sb = null;
        }
    }

    /**
     * @see #makeCache(Class, String, int)
     */
    public final DirectAccessor<T> makeCache(Class<?> targetClass) {
        return makeCache(targetClass, "instance", 7);
    }

    /**
     * get,set,clear Instance via Instanced or other... <br>
     * @param methodFlag 1: get 2:set 4:clear 8:check existence, plus them
     */
    public final DirectAccessor<T> makeCache(Class<?> targetClass, String name, int methodFlag) {
        if(caches.getEntry(name) != null)
            throw new IllegalStateException("Cache already set!");

        char c = Character.toUpperCase(name.charAt(0));
        String name1 = c == name.charAt(0) ? name : c + name.substring(1);

        String type = targetClass.getName().replace('.', '/');
        Type type1 = new Type(type);
        FieldInsnNode _set = new FieldInsnNode(Opcodes.PUTFIELD, var.name, name, type1);
        FieldInsnNode _get = new FieldInsnNode(Opcodes.GETFIELD, var.name, name, type1);

        roj.asm.tree.Field f = new roj.asm.tree.Field(PUBLIC, name, type1);
        var.fields.add(f);

        AttrCode code;
        InsnList insn;

        if((methodFlag & 2) != 0) {
            if((methodFlag & 8) != 0) {
                checkExistence("set" + name1);
            }
            roj.asm.tree.Method set = new roj.asm.tree.Method(PUBLIC, var, "set" + name1,
                                                              "(Ljava/lang/Object;)V");
            set.code = code = new AttrCode(set);

            code.stackSize = 2;
            code.localSize = 2;
            insn = code.instructions;
            insn.add(NPInsnNode.of(Opcodes.ALOAD_0));
            insn.add(NPInsnNode.of(Opcodes.ALOAD_1));
            if(check)
                insn.add(new ClassInsnNode(Opcodes.CHECKCAST, type));
            insn.add(_set);
            insn.add(NPInsnNode.of(Opcodes.RETURN));

            var.methods.add(set);
        }

        if((methodFlag & 4) != 0) {
            if((methodFlag & 8) != 0) {
                checkExistence("clear" + name1);
            }
            roj.asm.tree.Method clear = new roj.asm.tree.Method(PUBLIC, var, "clear" + name1, "()V");
            clear.code = code = new AttrCode(clear);

            code.stackSize = 2;
            code.localSize = 1;

            insn = code.instructions;
            insn.add(NPInsnNode.of(Opcodes.ALOAD_0));
            insn.add(NPInsnNode.of(Opcodes.ACONST_NULL));
            insn.add(_set);
            insn.add(NPInsnNode.of(Opcodes.RETURN));

            var.methods.add(clear);
        }

        if((methodFlag & 1) != 0) {
            if((methodFlag & 8) != 0) {
                checkExistence("get" + name1);
            }
            roj.asm.tree.Method get = new roj.asm.tree.Method(PUBLIC, var, "get" + name1,
                                                              "()Ljava/lang/Object;");
            get.code = code = new AttrCode(get);

            code.stackSize = 1;
            code.localSize = 1;


            insn = code.instructions;
            insn.add(NPInsnNode.of(Opcodes.ALOAD_0));
            insn.add(_get);
            insn.add(NPInsnNode.of(Opcodes.ARETURN));

            var.methods.add(get);
        }

        Cache cache = new Cache();
        cache.clazz = targetClass;
        cache.node = _get;
        caches.put(name, cache);

        return this;
    }

    private void checkExistence(String name) {
        Method method = methodByName.remove(name);
        if (method == null) {
            throw new IllegalArgumentException(owner.getName() + '.' + name + " ????????????????????????!");
        }
    }

    public final DirectAccessor<T> useCache() {
        return useCache("instance");
    }

    public final DirectAccessor<T> useCache(String name) {
        cache = caches.get(name);
        if(cache == null && name != null) {
            throw new IllegalArgumentException("Cache '" + name + "' not exist");
        }
        return this;
    }

    /**
     * @see #construct(Class, String[], List)
     */
    public final DirectAccessor<T> construct(Class<?> target, String name) {
        return construct(target, new String[]{name}, null);
    }

    /**
     * @see #construct(Class, String[], List)
     */
    public final DirectAccessor<T> construct(Class<?> target, String... names) {
        if (names.length == 0) throw new IllegalArgumentException("Wrong call");
        return construct(target, names, null);
    }

    /**
     * @see #construct(Class, String[], List)
     */
    public final DirectAccessor<T> construct(Class<?> target, String name, Class<?>... param) {
        if (param.length == 0) throw new IllegalArgumentException("Wrong call");
        return construct(target, new String[] { name }, Collections.singletonList(param));
    }

    /**
     * @see #construct(Class, String[], List)
     */
    public final DirectAccessor<T> constructFuzzy(Class<?> target, String... names) {
        if (names.length == 0) throw new IllegalArgumentException("Wrong call");
        return construct(target, names, Collections.emptyList());
    }

    /**
     * ??? names ????????????????????? target ??????????????? <br>
     *     <br>
     *     all-object ??????: ????????????????????? Object, ???????????????????????????????????????????????????<br>
     *         #????????????<br>
     *     <br>
     * @param fuzzy <br>
     *     ???????????????null: ????????? all-object ?????? <br>
     *     ????????????????????????: ?????? ????????? all-object ?????? <br>
     *     ??????????????????????????? (?????????????????? names.length): <br>
     *         ???????????????null????????????????????? all-object ?????? <br>
     *         ????????????????????? all-object ?????? <br>
     *      <br>
     * @return this
     * @throws IllegalArgumentException ????????????????????????,???????????????????????????
     */
    public DirectAccessor<T> construct(Class<?> target, String[] names, List<Class<?>[]> fuzzy) throws IllegalArgumentException {
        if(names.length == 0)
            return this;

        Method[] sMethods = new Method[names.length];
        Constructor<?>[] tMethods = new Constructor<?>[names.length];

        Constructor<?>[] constructors = fuzzy == null ? null : target.getDeclaredConstructors();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            Method m = methodByName.remove(name);
            if (m == null) {
                throw new IllegalArgumentException(owner.getName() + '.' + name + " ????????????????????????!");
            }
            if (!m.getReturnType().isAssignableFrom(target)) {
                throw new IllegalArgumentException(
                        owner.getName() + '.' + name + " ???????????? (" + m.getReturnType()
                                                                         .getName() + ") ????????? " + target
                                .getName());
            }
            sMethods[i] = m;
            Class<?> r = m.getReturnType();
            Class<?>[] types = m.getParameterTypes();
            if(fuzzy != null) {
                for (int j = 0; j < types.length; j++) {
                    Class<?> type = types[j];
                    if (!type.isPrimitive() && type != Object.class) {
                        throw new IllegalArgumentException(
                                "????????? " + owner.getName() + '.' + name + " ??????all-object: ???[" + (j + 1) + "]???????????????????????????????????????Object");
                    }
                }
                if(!r.isPrimitive() && r != Object.class)
                    throw new IllegalArgumentException(
                            "????????? " + owner.getName() + '.' + name + " ??????all-object: ???????????????????????????????????????Object");
            }

            try {
                if (fuzzy == null || (!fuzzy.isEmpty() && fuzzy.get(i) != null)) {
                    // for exception
                    tMethods[i] = target.getDeclaredConstructor(fuzzy == null ? types : (types = fuzzy.get(i)));
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
                                        "????????? " + owner.getName() + '.' + name + " ??????????????????: ?????????????????????????????????????????????????????????????????????\n" +
                                                "??????: " + ParamHelper.class2asm(tMethods[i].getParameterTypes(), void.class) + "\n" +
                                                "??????: " + ParamHelper.class2asm(cr.getParameterTypes(), void.class));
                            tMethods[i] = cr;
                        }
                    }
                    if(found == 0)
                        throw new NoSuchMethodException();
                }
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                        "???????????? " + target.getName() + " ????????????, ??????: " + ParamHelper.class2asm(types, void.class));
            }
        }
        if(sb != null) {
            sb.append("\n  ???????????????: ").append(target.getName()).append("\n  ??????:\n    ");
            for (int i = 0; i < tMethods.length; i++) {
                Constructor<?> tm = tMethods[i];
                sb.append(names[i]).append(" (").append(ParamHelper.class2asm(
                        tm.getParameterTypes(), void.class)).append(")").append("\n    ");
            }
            sb.setLength(sb.length() - 5);
        }

        String tName = target.getName().replace('.', '/');

        for (int i = 0; i < tMethods.length; i++) {
            Constructor<?> m = tMethods[i];
            Class<?>[] params = m.getParameterTypes();
            String tDesc = ParamHelper.class2asm(params, void.class);

            Method sm = sMethods[i];
            Class<?>[] params2 = sm.getParameterTypes();
            String sDesc = objectDescriptors(params, sm.getReturnType(), fuzzy == null);
            roj.asm.tree.Method invoke = new roj.asm.tree.Method(PUBLIC, var, sm.getName(), sDesc);

            AttrCode code;
            invoke.code = code = new AttrCode(invoke);

            InsnList insn = code.instructions;
            insn.add(new ClassInsnNode(Opcodes.NEW, tName));
            insn.add(NPInsnNode.of(Opcodes.DUP));

            int size = 1;
            for (int j = 0; j < params.length; j++) {
                Class<?> param = params[j];
                String tag = NodeHelper.XPrefix(param);
                NodeHelper.compress(insn, NodeHelper.X_LOAD(tag.charAt(0)), size++);
                if (check && !param.isAssignableFrom(params2[j]))
                    insn.add(new ClassInsnNode(Opcodes.CHECKCAST, param.getName().replace('.', '/')));
                switch (tag) {
                    case "D":
                    case "L":
                        size++;
                }
            }

            code.stackSize = code.localSize = (char) (size + 1);

            insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, tName, "<init>", tDesc));
            insn.add(NPInsnNode.of(Opcodes.ARETURN));

            var.methods.add(invoke);
        }

        return this;
    }

    /**
     * @see #delegate(Class, String[], MyBitSet, String[], List)
     */
    public final DirectAccessor<T> delegate(Class<?> target, String name) {
        String[] arr = new String[] {name};
        return delegate(target, arr, EMPTY_BITS, arr, null);
    }

    /**
     * @see #delegate(Class, String[], MyBitSet, String[], List)
     */
    public final DirectAccessor<T> delegate(Class<?> target, String name, String selfName) {
        return delegate(target, new String[] {name}, EMPTY_BITS, new String[] {selfName}, null);

    }

    /**
     * @see #delegate(Class, String[], MyBitSet, String[], List)
     */
    public final DirectAccessor<T> delegate(Class<?> target, String... names) {
        if (names.length == 0) throw new IllegalArgumentException("Wrong call");
        return delegate(target, names, EMPTY_BITS, names, null);
    }

    /**
     * @see #delegate(Class, String[], MyBitSet, String[], List)
     */
    public final DirectAccessor<T> delegate(Class<?> target, String[] names, String[] selfNames) {
        return delegate(target, names, EMPTY_BITS, selfNames, null);
    }

    /**
     * @see #delegate(Class, String[], MyBitSet, String[], List)
     */
    public final DirectAccessor<T> delegate_o(Class<?> target, String name) {
        String[] arr = new String[] {name};
        return delegate(target, arr, EMPTY_BITS, arr, Collections.emptyList());
    }

    /**
     * @see #delegate(Class, String[], MyBitSet, String[], List)
     */
    public final DirectAccessor<T> delegate_o(Class<?> target, String[] methodNames) {
        return delegate(target, methodNames, EMPTY_BITS, methodNames, Collections.emptyList());
    }

    /**
     * @see #delegate(Class, String[], MyBitSet, String[], List)
     */
    public final DirectAccessor<T> delegate_o(Class<?> target, String method, String self) {
        return delegate(target, new String[]{ method }, EMPTY_BITS, new String[]{ self },
                        Collections.emptyList());
    }

    /**
     * @see #delegate(Class, String[], MyBitSet, String[], List)
     */
    public final DirectAccessor<T> delegate_o(Class<?> target, String method, String self, Class<?>... param) {
        if (param.length == 0) throw new IllegalArgumentException("Wrong call");
        return delegate(target, new String[]{ method }, EMPTY_BITS, new String[]{ self },
                        Collections.singletonList(param));
    }

    /**
     * @see #delegate(Class, String[], MyBitSet, String[], List)
     */
    public final DirectAccessor<T> delegate_o(Class<?> target, String[] methodNames, String[] selfNames) {
        return delegate(target, methodNames, EMPTY_BITS, selfNames, Collections.emptyList());
    }

    /**
     * ??? selfMethodNames ????????????????????? target ??? methodNames ?????????????????? <br>
     *     <br>
     * @param flags ???set?????????index??????true??????????????????????????????(????????????)
     * @param fuzzyMode : {@link #construct(Class, String[], List)}
     * @return this
     * @throws IllegalArgumentException ????????????????????????,???????????????????????????
     */
    public DirectAccessor<T> delegate(Class<?> target, String[] methodNames, @Nullable MyBitSet flags, String[] selfNames, List<Class<?>[]> fuzzyMode) throws IllegalArgumentException {
        if(selfNames.length == 0)
            return this;
        boolean useCache = cache != null;
        if(useCache) {
            if (!target.isAssignableFrom(cache.clazz))
                throw new IllegalArgumentException(
                        "???????????????????????? '" + cache.clazz.getName() + "', ?????? '" + target.getName() + "' ??????????????????????????? '" + cache.clazz.getName() + "'.");
        }

        Method[] tMethods = new Method[selfNames.length];
        Method[] sMethods = new Method[selfNames.length];

        List<Method> methods = ReflectionUtils.getMethods(target);
        for (int i = 0; i < selfNames.length; i++) {
            String name = selfNames[i];
            Method method = methodByName.remove(name);
            if (method == null) {
                throw new IllegalArgumentException(owner.getName() + '.' + name + " ????????????????????????!");
            }
            sMethods[i] = method;

            Class<?>[] types = method.getParameterTypes();
            if(fuzzyMode != null) {
                for (int j = 0; j < types.length; j++) {
                    Class<?> type = types[j];
                    if (!type.isPrimitive() && type != Object.class) {
                        throw new IllegalArgumentException(
                                "????????? " + owner.getName() + '.' + name + " ??????all-object: ???[" + (j + 1) + "]???????????????????????????????????????Object");
                    }
                }
                if(!method.getReturnType().isPrimitive() && method.getReturnType() != Object.class)
                    throw new IllegalArgumentException(
                            "????????? " + owner.getName() + '.' + name + " ??????all-object: ???????????????????????????????????????Object");
            }

            int off = useCache ? 0 : 1;
            String targetMethodName = methodNames[i];
            try {
                boolean fuzzy = false;
                if(fuzzyMode != null) {
                    if (!fuzzyMode.isEmpty() && fuzzyMode.get(i) != null) {
                        types = fuzzyMode.get(i);
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
                    // NCI ???????????????????????????
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
                        if(off1 == 1 && !types[0].isAssignableFrom(target)) {
                            throw new IllegalArgumentException("??????????????? " + owner.getName() + '.' + name + " ?????????????????? (" + types[0].getName() + ") ??????????????? " + target.getName());
                        }
                        if (found != -1) {
                            if(!Arrays.equals(m.getParameterTypes(), tMethods[i].getParameterTypes())) {
                                throw new IllegalArgumentException(
                                        "????????? " + owner.getName() + '.' + name + " ??????????????????: ?????????????????????????????????????????????????????????????????????\n" +
                                                "??????: " + ParamHelper.class2asm(tMethods[i].getParameterTypes(), tMethods[i].getReturnType()) + "\n" +
                                                "??????: " + ParamHelper.class2asm(m.getParameterTypes(), m.getReturnType()));
                            } else {
                                // ???????????????????????????????????????
                                // ?????????????????????
                                m = findInheritLower(m, tMethods[i]);
                            }
                        }
                        found = j;
                        tMethods[i] = m;
                    }
                }
                if(found == -1)
                    throw new NoSuchMethodException();
                methods.remove(found);
            } catch (NoSuchMethodException e) {
                if(DEBUG) {
                    for (int j = 0; j < methods.size(); j++) {
                        Method mm = methods.get(j);
                        System.out.println("??????: " + mm.getName() + " , ??????: " + ParamHelper.class2asm(mm.getParameterTypes(), mm.getReturnType()));
                    }
                }
                throw new IllegalArgumentException(
                        "???????????????????????????: " + target.getName() + '.' + targetMethodName + " ?????? " + ParamHelper.class2asm(types, method.getReturnType()));
            }

            if (!method.getReturnType().isAssignableFrom(tMethods[i].getReturnType())) {
                throw new IllegalArgumentException(
                        owner.getName() + '.' + name + " ???????????? (" + method.getReturnType()
                                                                                  .getName() + ") ????????? " + target
                                .getName());
            }
        }

        if(sb != null) {
            sb.append("\n  ????????????: ").append(target.getName()).append("\n  ??????:\n    ");
            for (int i = 0; i < tMethods.length; i++) {
                Method tm = tMethods[i];
                sb.append(tm).append(" => ").append(selfNames[i]).append(" (").append(ParamHelper.class2asm(
                        tm.getParameterTypes(), tm.getReturnType())).append(")").append("\n    ");
            }
            sb.setLength(sb.length() - 5);
        }

        String tName = target.getName().replace('.', '/');

        for (int i = 0, len = tMethods.length; i < len; i++) {
            Method tm = tMethods[i];
            Class<?>[] params = tm.getParameterTypes();
            String tDesc = ParamHelper.class2asm(params, tm.getReturnType());

            Method sm = sMethods[i];
            Class<?>[] params2 = sm.getParameterTypes();
            String sDesc = objectDescriptors(params2, sm.getReturnType(), fuzzyMode == null);
            roj.asm.tree.Method invoke = new roj.asm.tree.Method(PUBLIC, var, selfNames[i], sDesc);
            AttrCode code;
            invoke.code = code = new AttrCode(invoke);
            var.methods.add(invoke);

            InsnList insn = code.instructions;

            int isStatic = (tm.getModifiers() & AccessFlag.STATIC) != 0 ? 1 : 0;
            if (isStatic == 0) {
                if (useCache) {
                    insn.add(NPInsnNode.of(Opcodes.ALOAD_0));
                    insn.add(cache.node);
                } else {
                    insn.add(NPInsnNode.of(Opcodes.ALOAD_1));
                    if (check && !target.isAssignableFrom(params2[0]))
                        insn.add(new ClassInsnNode(Opcodes.CHECKCAST, tName));
                }
            }

            int size = useCache || isStatic != 0 ? 0 : 1;
            int j = size;
            for (Class<?> param : params) {
                String tag = NodeHelper.XPrefix(param);
                NodeHelper.compress(insn, NodeHelper.X_LOAD(tag.charAt(0)), ++size);
                if (check && !param.isPrimitive() && !param.isAssignableFrom(params2[j])) // ????????????????????????...
                    insn.add(new ClassInsnNode(Opcodes.CHECKCAST, param.getName().replace('.', '/')));
                j++;
                switch (tag) {
                    case "D":
                    case "L":
                        size++;
                }
            }

            code.stackSize = (char) Math.max(size + isStatic, 1);
            code.localSize = (char) (size + 1);

            if (isStatic != 0) {
                insn.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, tName, tm.getName(), tDesc));
            } else if (target.isInterface()) {
                insn.add(new InvokeItfInsnNode(tName, tm.getName(), tDesc));
            } else {
                insn.add(new InvokeInsnNode((flags != null && flags.contains(i) ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL), tName, tm.getName(), tDesc));
            }

            insn.add(NodeHelper.X_RETURN(NodeHelper.XPrefix(tm.getReturnType())));
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
    public final DirectAccessor<T> access(Class<?> target, String fieldName) {
        return access(target, new String[]{fieldName});
    }

    /**
     * @see #access(Class, String[], String[], String[])
     */
    public final DirectAccessor<T> access(Class<?> target, String[] fields) {
        return access(target, fields, capitalize(fields, "get"), capitalize(fields, "set"));
    }

    /**
     * @see #access(Class, String[], String[], String[])
     */
    public final DirectAccessor<T> access(Class<?> target, String field, String getter, String setter) {
        return access(target, new String[] { field }, new String[] { getter }, new String[]{ setter });
    }

    /**
     * ??? setter/getters ????????????????????? target ??? fields ??? setter / getter <br>
     *     <br>
     * @return this
     * @throws IllegalArgumentException ????????????????????????,???????????????????????????
     */
    public DirectAccessor<T> access(Class<?> target, String[] fields, String[] getters, String[] setters) throws IllegalArgumentException {
        if(fields.length == 0)
            return this;
        boolean useCache = cache != null;
        if(useCache) {
            if (!target.isAssignableFrom(cache.clazz))
                throw new IllegalArgumentException(
                        "???????????????????????? '" + cache.clazz.getName() + "', ?????? '" + target.getName() + "' ??????????????????????????? '" + cache.clazz.getName() + "'.");
        }

        Field[] fieldFs = new Field[fields.length];
        Method[] setterMs = new Method[fields.length];
        Method[] getterMs = new Method[fields.length];

        List<Field> allFields = ReflectionUtils.getFields(target);
        for (int i = 0; i < fields.length; i++) {
            String name = fields[i];

            int found = -1;
            for (int j = 0; j < allFields.size(); j++) {
                Field f = allFields.get(j);
                if (f.getName().equals(name)) {
                    if (found != -1) {
                        throw new IllegalArgumentException("?????????????????????????????????????????????????????????, ?????????????????????????????????'??????'??????????????????");
                    }
                    found = j;
                }
            }

            if(found == -1)
                throw new IllegalArgumentException("?????????????????? " + target.getName() + '.' + fields[i]);
            fieldFs[i] = allFields.remove(found);
            int off = useCache || (fieldFs[i].getModifiers() & AccessFlag.STATIC) != 0 ? 0 : 1;

            name = getters == null ? null : getters[i];
            if(name != null) {
                Method method = methodByName.remove(name);
                if (method == null) {
                    throw new IllegalArgumentException(owner.getName() + '.' + name + " ????????????????????????!");
                }
                if(method.getParameterCount() != off)
                    throw new IllegalArgumentException(owner.getName() + '.' + name + " ?????? getter, " +
                                         "??????????????????, got " + (method.getParameterCount() - off) + '!');
                if(!method.getReturnType().isAssignableFrom(fieldFs[i].getType()))
                    throw new IllegalArgumentException(owner.getName() + '.' + name + " ?????? getter, " +
                                         "???????????????????????? " + fieldFs[i].getType().getName() + " (" + method.getReturnType().getName() + ')');
                getterMs[i] = method;
            }

            name = setters == null ? null : setters[i];
            if(name != null) {
                Method method = methodByName.remove(name);
                if (method == null) {
                    throw new IllegalArgumentException(owner.getName() + '.' + name + " ????????????????????????!");
                }
                if(method.getParameterCount() != off + 1)
                    throw new IllegalArgumentException(owner.getName() + '.' + name + " ?????? setter, " +
                                                               "?????????1?????????, got " + method.getParameterCount() + '!');
                if(!method.getParameterTypes()[off].isAssignableFrom(fieldFs[i].getType()))
                    throw new IllegalArgumentException(owner.getName() + '.' + name + " ?????? setter, " +
                                                               "????????????[" + (off + 1) + "]????????? " + fieldFs[i].getType().getName() + " (" + method.getReturnType().getName() + ')');
                if(method.getReturnType() != void.class)
                    throw new IllegalArgumentException(owner.getName() + '.' + name + " ?????? setter, " +
                                                               "???????????????????????????void: " + method.getReturnType().getName());
                setterMs[i] = method;
            }
        }

        if(sb != null) {
            sb.append("\n  ????????????: ").append(target.getName()).append("\n  ??????:\n    ");
            for (int i = 0; i < fieldFs.length; i++) {
                Field tf = fieldFs[i];
                sb.append(tf.getName()).append(' ').append(tf.getType().getName()).append(" => [").append(getterMs[i]).append(", ").append(setterMs[i]).append("\n    ");
            }
            sb.setLength(sb.length() - 5);
        }

        String tName = target.getName().replace('.', '/');

        for (int i = 0, len = fieldFs.length; i < len; i++) {
            Field field = fieldFs[i];
            Type fType = ParamHelper.parseField(ParamHelper.class2asm(field.getType()));
            boolean isStatic = (field.getModifiers() & AccessFlag.STATIC) != 0;

            Method getter = getterMs[i];
            if(getter != null) {
                Class<?>[] params2 = isStatic || useCache ? EmptyArrays.CLASSES : getter.getParameterTypes();
                roj.asm.tree.Method get = new roj.asm.tree.Method(PUBLIC, var, getter.getName(), ParamHelper.class2asm(params2, getter.getReturnType()));
                AttrCode code = get.code = new AttrCode(get);

                byte type = fType.type;
                code.stackSize = (char) (type == Type.DOUBLE || type == Type.LONG ? 2 : 1);

                InsnList insn = code.instructions;
                if(!isStatic) {
                    code.localSize = (char) (useCache ? 1 : 2);
                    if (useCache) {
                        insn.add(NPInsnNode.of(Opcodes.ALOAD_0));
                        insn.add(cache.node);
                    } else {
                        insn.add(NPInsnNode.of(Opcodes.ALOAD_1));
                        if (check && !target.isAssignableFrom(params2[0]))
                            insn.add(new ClassInsnNode(Opcodes.CHECKCAST, tName));
                    }
                    insn.add(new FieldInsnNode(Opcodes.GETFIELD, tName, field.getName(), fType));
                } else {
                    code.localSize = 1;
                    insn.add(new FieldInsnNode(Opcodes.GETSTATIC, tName, field.getName(), fType));
                }
                insn.add(NodeHelper.X_RETURN(fType.nativeName()));

                var.methods.add(get);
            }

            Method setter = setterMs[i];
            if(setter != null) {
                Class<?>[] params2 = setter.getParameterTypes();
                roj.asm.tree.Method set = new roj.asm.tree.Method(PUBLIC, var, setter.getName(), ParamHelper.class2asm(params2, void.class));
                AttrCode code = set.code = new AttrCode(set);

                byte type = fType.type;
                code.stackSize = (char) (type == Type.DOUBLE || type == Type.LONG ? 3 : 2);

                InsnList insn = code.instructions;
                if(!isStatic) {
                    code.localSize = (char) (code.stackSize + (useCache ? 0 : 1));
                    if(useCache) {
                        insn.add(NPInsnNode.of(Opcodes.ALOAD_0));
                        insn.add(cache.node);
                    } else {
                        insn.add(NPInsnNode.of(Opcodes.ALOAD_1));
                        if (check && !target.isAssignableFrom(params2[0]))
                            insn.add(new ClassInsnNode(Opcodes.CHECKCAST, tName));
                    }
                } else {
                    code.localSize = code.stackSize--;
                }
                insn.add(NodeHelper.X_LOAD_I(fType.nativeName().charAt(0), isStatic || useCache ? 1 : 2));
                if (check && type == CLASS && !field.getType().isAssignableFrom(params2[isStatic || useCache ? 0 : 1]))
                    insn.add(new ClassInsnNode(Opcodes.CHECKCAST, fType.owner));
                insn.add(new FieldInsnNode(isStatic ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD, tName, field.getName(), fType));
                insn.add(NPInsnNode.of(Opcodes.RETURN));

                var.methods.add(set);
            }
        }
        return this;
    }

    public final DirectAccessor<T> i_construct(String target, String desc, String self) {
        Method mm = methodByName.remove(self);
        if (mm == null) {
            throw new IllegalArgumentException(owner.getName() + '.' + self + " ????????????????????????!");
        }

        return i_construct(target, desc, mm);
    }

    public final DirectAccessor<T> i_construct(String target, String desc, Method self) {
        target = target.replace('.', '/');

        roj.asm.tree.Method invoke = new roj.asm.tree.Method(PUBLIC, var, self.getName(),
                                     ParamHelper.class2asm(self.getParameterTypes(), self.getReturnType()));

        AttrCode code;
        invoke.code = code = new AttrCode(invoke);

        InsnList insn = code.instructions;
        insn.add(new ClassInsnNode(Opcodes.NEW, target));
        insn.add(NPInsnNode.of(Opcodes.DUP));

        List<Type> params = ParamHelper.parseMethod(desc);
        params.remove(params.size() - 1);
        int size = 1;
        for (int i = 0; i < params.size(); i++) {
            Type param = params.get(i);
            char x = param.nativeName().charAt(0);
            NodeHelper.compress(insn, NodeHelper.X_LOAD(x), ++size);
            switch (x) {
                case 'D':
                case 'L':
                    size++;
            }
        }

        code.stackSize = code.localSize = (char) (size + 1);

        insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, target, "<init>", desc));
        insn.add(NPInsnNode.of(Opcodes.ARETURN));

        var.methods.add(invoke);

        if(sb != null) {
            sb.append("\n  ???????????????[?????????]: ").append(target).append("\n  ??????:\n    ")
              .append(self.getName()).append(' ').append(desc);
        }

        return this;
    }

    public final DirectAccessor<T> i_delegate(String target, String name, String desc, String self, byte opcode) {
        Method m = methodByName.remove(self);
        if (m == null) {
            throw new IllegalArgumentException(owner.getName() + '.' + self + " ????????????????????????!");
        }
        return i_delegate(target, name, desc, m, opcode);
    }

    public final DirectAccessor<T> i_delegate(String target, String name, String desc, Method self, byte opcode) {
        target = target.replace('.', '/');

        String sDesc = ParamHelper.class2asm(self.getParameterTypes(), self.getReturnType());

        roj.asm.tree.Method invoke = new roj.asm.tree.Method(PUBLIC, var, self.getName(), sDesc);
        AttrCode code;
        invoke.code = code = new AttrCode(invoke);
        var.methods.add(invoke);

        InsnList insn = code.instructions;

        boolean isStatic = opcode == Opcodes.INVOKESTATIC;
        if (!isStatic) {
            insn.add(NPInsnNode.of(Opcodes.ALOAD_1));
        }

        List<Type> params = ParamHelper.parseMethod(desc);
        params.remove(params.size() - 1);
        int size = isStatic ? 0 : 1;
        for (int i = 0; i < params.size(); i++) {
            Type param = params.get(i);
            char x = param.nativeName().charAt(0);
            NodeHelper.compress(insn, NodeHelper.X_LOAD(x), ++size);
            switch (x) {
                case 'D':
                case 'L':
                    size++;
            }
        }

        code.stackSize = (char) Math.max(size + (isStatic ? 1 : 0), 1);
        code.localSize = (char) (size + 1);

        if (isStatic) {
            insn.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, target, self.getName(), desc));
        } else if (opcode == Opcodes.INVOKEINTERFACE) {
            insn.add(new InvokeItfInsnNode(target, self.getName(), desc));
        } else {
            insn.add(new InvokeInsnNode(opcode, target, self.getName(), desc));
        }
        insn.add(NodeHelper.X_RETURN(NodeHelper.XPrefix(self.getReturnType())));

        if(sb != null) {
            sb.append("\n  ????????????[?????????]: ").append(target).append("\n  ??????:\n    ")
              .append(name).append(' ').append(desc).append(" => ").append(self.getName()).append(' ').append(sDesc);
        }

        return this;
    }

    public final DirectAccessor<T> i_access(String target, String name, Type type, String getter, String setter, boolean isStatic) {
        Method g = methodByName.remove(getter);
        if (g == null && getter != null) {
            throw new IllegalArgumentException(owner.getName() + '.' + getter + " ????????????????????????!");
        }
        Method s = methodByName.remove(setter);
        if (s == null && setter != null) {
            throw new IllegalArgumentException(owner.getName() + '.' + setter + " ????????????????????????!");
        }
        return i_access(target, name, type, g, s, isStatic);
    }

    public final DirectAccessor<T> i_access(String target, String name, Type type, Method getter, Method setter, boolean isStatic) {
        target = target.replace('.', '/');

        if(getter != null) {
            Class<?>[] params2 = isStatic ? EmptyArrays.CLASSES : getter.getParameterTypes();
            roj.asm.tree.Method get = new roj.asm.tree.Method(PUBLIC, var, getter.getName(), ParamHelper.class2asm(params2, getter.getReturnType()));
            AttrCode code = get.code = new AttrCode(get);

            byte typeId = type.type;
            code.stackSize = (char) (typeId == Type.DOUBLE || typeId == Type.LONG ? 2 : 1);

            InsnList insn = code.instructions;
            if(!isStatic) {
                code.localSize = 2;
                insn.add(NPInsnNode.of(Opcodes.ALOAD_1));
                insn.add(new FieldInsnNode(Opcodes.GETFIELD, target, name, type));
            } else {
                code.localSize = 1;
                insn.add(new FieldInsnNode(Opcodes.GETSTATIC, target, name, type));
            }
            insn.add(NodeHelper.X_RETURN(type.nativeName()));

            var.methods.add(get);
        }

        if(setter != null) {
            Class<?>[] params2 = setter.getParameterTypes();
            roj.asm.tree.Method set = new roj.asm.tree.Method(PUBLIC, var, setter.getName(), ParamHelper.class2asm(params2, void.class));
            AttrCode code = set.code = new AttrCode(set);

            byte typeId = type.type;
            code.stackSize = (char) (typeId == Type.DOUBLE || typeId == Type.LONG ? 3 : 2);

            InsnList insn = code.instructions;
            if(!isStatic) {
                code.localSize = (char) (code.stackSize + 1);
                insn.add(NPInsnNode.of(Opcodes.ALOAD_1));
            } else {
                code.localSize = code.stackSize--;
            }
            insn.add(NodeHelper.X_LOAD_I(type.nativeName().charAt(0), isStatic ? 1 : 2));
            insn.add(new FieldInsnNode(isStatic ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD, target, name, type));
            insn.add(NPInsnNode.of(Opcodes.RETURN));

            var.methods.add(set);
        }

        if(sb != null) {
            sb.append("\n  ????????????[?????????]: ").append(target).append("\n  ??????:\n    ");
            sb.append(target).append(' ').append(type)
              .append(" => [").append(getter == null ? "null" : getter.getName()).append(", ")
              .append(setter == null ? "null" : setter.getName()).append(']');
        }

        return this;
    }

    public final DirectAccessor<T> delayErrorToInvocation() {
        return getClass() == DirectAccessor.class ? new DirectAccessor<T>(this) {
            @Override
            public DirectAccessor<T> delegate(Class<?> target, String[] methodNames, @Nullable MyBitSet flags, String[] selfNames, List<Class<?>[]> fuzzyMode) {
                try {
                    return super.delegate(target, methodNames, flags, selfNames, fuzzyMode);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    return this;
                }
            }

            @Override
            public DirectAccessor<T> access(Class<?> target, String[] fields, String[] getters, String[] setters) {
                try {
                    return super.access(target, fields, getters, setters);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    return this;
                }
            }

            @Override
            public DirectAccessor<T> construct(Class<?> target, String[] names, List<Class<?>[]> fuzzy) {
                try {
                    return super.construct(target, names, fuzzy);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    return this;
                }
            }
        } : this;
    }

    public static <V> DirectAccessor<V> builder(Class<V> impl) {
        return new DirectAccessor<>(impl, "roj/reflect/", true);
    }

    public static <V> DirectAccessor<V> builderInternal(Class<V> impl) {
        return new DirectAccessor<>(impl, "roj/reflect/", false);
    }

    public static <V> DirectAccessor<V> withPackage(Class<V> impl, Class<?> pkg) {
        return new DirectAccessor<>(impl, pkg.getName().substring(0, pkg.getName().lastIndexOf('/') + 1), true);
    }

    public final DirectAccessor<T> unchecked() {
        check = false;
        return this;
    }

    public final DirectAccessor<T> from(String... names) {
        if (names.length == 0) throw new IllegalArgumentException("Wrong parameter count");
        this.from = names;
        return this;
    }

    public final DirectAccessor<T> in(Class<?> target) {
        this.target = target;
        this.to = null;
        this.fuzzy = null;
        return this;
    }

    public final DirectAccessor<T> to(String... names) {
        if (names.length == 0) throw new IllegalArgumentException("Wrong parameter count");
        this.to = names;
        this.fuzzy = null;
        return this;
    }

    public final DirectAccessor<T> withFuzzy(boolean fuzzy) {
        this.fuzzy = fuzzy ? Collections.emptyList() : null;
        return this;
    }

    public final DirectAccessor<T> withFuzzy(Class<?>... names) {
        if (names.length == 0) throw new IllegalArgumentException("Wrong parameter count");
        this.fuzzy = Collections.singletonList(names);
        return this;
    }

    public final DirectAccessor<T> withFuzzy(List<Class<?>[]> names) {
        this.fuzzy = names;
        return this;
    }

    public final DirectAccessor<T> op(String op) {
        if (target == null || from == null)
            throw new IllegalStateException("Missing arguments");
        switch (op) {
            case "access":
                return access(target, to,
                              capitalize(from, "get"),
                              capitalize(from, "set"));
            case "delegate":
                return delegate(target, to, EMPTY_BITS, from, fuzzy);
            case "construct":
                return construct(target, from, fuzzy);
            default:
                throw new IllegalArgumentException("Invalid operation " + op);
        }
    }

    /**
     * ???????????????: xx,set => setXxx
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

    public static void cloneable(Clazz clz) {
        roj.asm.tree.Method cl = new roj.asm.tree.Method(PUBLIC, clz, "clone", "()Ljava/lang/Object;");
        AttrCode code = cl.code = new AttrCode(cl);

        code.stackSize = 1;
        code.localSize = 1;
        InsnList insn = code.instructions;
        insn.add(NPInsnNode.of(Opcodes.ALOAD_0));
        insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object",
                                    "clone", "()Ljava/lang/Object;"));
        insn.add(new ClassInsnNode(Opcodes.CHECKCAST, clz.name));
        insn.add(NPInsnNode.of(Opcodes.ARETURN));

        clz.interfaces.add("java/lang/Cloneable");
        clz.methods.add(cl);
    }

    /**
     * <init>
     * constructor
     */
    public static void addInit(Clazz clz) {
        AttrCode code;

        roj.asm.tree.Method init = new roj.asm.tree.Method(PUBLIC, clz, "<init>", "()V");
        init.code = code = new AttrCode(init);

        code.stackSize = 1;
        code.localSize = 1;
        InsnList insn = code.instructions;
        insn.add(NPInsnNode.of(Opcodes.ALOAD_0));
        insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, clz.parent, "<init>", "()V"));
        insn.add(NPInsnNode.of(Opcodes.RETURN));

        clz.methods.add(init);

        init = new roj.asm.tree.Method(PUBLIC | AccessFlag.STATIC, clz, "<clinit>", "()V");
        code = init.code = new AttrCode(init);

        code.stackSize = 2;
        code.localSize = 0;
        insn = code.instructions;
        insn.add(new ClassInsnNode(Opcodes.NEW, clz.name));
        insn.add(NPInsnNode.of(Opcodes.DUP));
        insn.add(new InvokeInsnNode(Opcodes.INVOKESPECIAL, clz.name, "<init>", "()V"));
        insn.add(new InvokeInsnNode(Opcodes.INVOKESTATIC,
                                    DirectAccessor.class.getName().replace('.', '/'),
                                    "syncCallback", "(Ljava/lang/Object;)V"));
        insn.add(NPInsnNode.of(Opcodes.RETURN));

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
        clz.accesses = AccessFlag.SUPER_OR_SYNC | PUBLIC;
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
        CharList sb = IOUtil.getSharedCharBuf().append('(');

        for (Class<?> clazz : classes) {
            ParamHelper.class2asm(sb, no_obj | clazz.isPrimitive() ? clazz : Object.class);
        }
        sb.append(')');
        return ParamHelper.class2asm(sb, no_obj | returns.isPrimitive() ? returns : Object.class).toString();
    }
}
