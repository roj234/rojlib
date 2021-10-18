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
package roj.asm.mapper;

import roj.asm.cst.CstClass;
import roj.asm.mapper.util.*;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.asm.tree.MoFNode;
import roj.asm.type.NativeType;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.concurrent.SharedThreads;
import roj.io.ZipFileWriter;
import roj.reflect.ReflectionUtils;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.FastThreadLocal;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Mapper Util
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/19 21:32
 */
public final class Util {
    private static final FastThreadLocal<Util> ThreadBasedCache = new FastThreadLocal<Util>() {
        @Override
        protected Util initialValue() {
            return new Util();
        }
    };

    public static final int CPU = Runtime.getRuntime().availableProcessors();

    private Util() {}

    public final Desc sharedDC = new Desc("", "", "");

    final MyHashSet<String> notFoundClasses = new MyHashSet<>();
    final MyHashMap<String, List<Class<?>>> cachedClassRef = new MyHashMap<>();
    final MyHashMap<String, IClass> cachedClassMof = new MyHashMap<>();

    private final CharList sharedCL = new CharList();
    private final ArrayList<?> sharedAL = new ArrayList<>();

    public static Desc shareMD() {
        return ThreadBasedCache.get().sharedDC;
    }

    public static Util getInstance() {
        return ThreadBasedCache.get();
    }

    // region 各种可继承性的判断

    public static boolean arePackagesSame(String packageA, String packageB) {
        int ia = packageA.lastIndexOf('/');

        if(packageB.lastIndexOf('/') != ia)
            return false;
        if(ia == -1)
            return true;

        return packageA.regionMatches(0, packageB, 0, ia);
    }

    public List<String> superClasses(String name, Map<String, List<String>> selfSupers) {
        List<String> superItf = selfSupers.get(name);
        if(superItf == null) {
            ReflectClass rc = (ReflectClass) reflectClassInfo(name);
            if(rc == null) return null;
            superItf = rc.i_superClassAll();
        }
        return superItf;
    }

    /**
     *   父类的方法被子类实现的接口使用
     */
    public MyHashSet<SubImpl> gatherSubImplements(List<Context> ctx, ConstMapper mapper, MyHashMap<String, IClass> methods) {
        if(methods.isEmpty())
        for (int i = 0; i < ctx.size(); i++) {
            ConstantData data = ctx.get(i).getData();
            methods.put(data.name, data);
        }

        MyHashSet<SubImpl> dest = new MyHashSet<>();
        SubImpl s_test = new SubImpl();

        MyHashSet<NameAndType> duplicate = new MyHashSet<>();
        NameAndType natCheck = new NameAndType();

        for (int k = 0; k < ctx.size(); k++) {
            ConstantData data = ctx.get(k).getData();
            if("java/lang/Object".equals(data.parent)) continue;

            List<String> superClasses = superClasses(data.parent, mapper.selfSupers);
            if (superClasses == null) continue;

            boolean one = false;
            List<CstClass> itfs = data.interfaces;
            for (int i = 0; i < itfs.size(); i++) {
                CstClass itf = itfs.get(i);
                String name = itf.getValue().getString();
                if(!superClasses.contains(name)) {
                    one = true;
                    break;
                }
            }

            if(!one) continue;

            superClasses = superClasses(data.name, mapper.selfSupers);
            for (int i = 0; i < superClasses.size(); i++) {
                String parent = superClasses.get(i);

                List<? extends MoFNode> nodes;
                // 获取所有的方法
                // 首先尝试从self获取
                IClass clz = methods.get(parent);
                if (clz == null) {
                    // 其次尝试用反射加载rt
                    clz = reflectClassInfo(parent);
                    if (clz != null) {
                        nodes = clz.methods();
                    } else {
                        // 最后尝试从mapper libraries获取 (因为可能不全)
                        nodes = new ArrayList<>();
                        for (Desc key : mapper.methodMap.keySet()) {
                            if(key.owner.equals(parent)) {
                                nodes.add(Helpers.cast(key));
                            }
                        }
                        methods.put(parent, new ReflectClass(parent, Helpers.cast(nodes)));
                        if(nodes.isEmpty()) continue;
                    }
                } else {
                    nodes = clz.methods();
                }

                for (int j = 0; j < nodes.size(); j++) {
                    MoFNode method = nodes.get(j);
                    if((natCheck.name = method.name()).startsWith("<")) continue;
                    natCheck.type = method.rawDesc();

                    NameAndType get = duplicate.find(natCheck);
                    if (get != natCheck) {
                        // 父类存在方法

                        // 若存在继承关系，不是接口
                        if (mapper.selfSupers.getOrDefault(get.owner, Collections.emptyList()).contains(parent)) {
                            // 跳过当前class
                            continue;
                        }

                        // 把新的复制，然后测试能不能找到存在的SI-NAT
                        s_test.type = get;
                        SubImpl s_get = dest.intern(s_test);
                        s_get.owners.add(parent);

                        // 至少有一个类不是要处理的类: 不能混淆
                        if (!methods.containsKey(parent)) s_get.original = true;

                        // 不存在
                        if (s_get == s_test) {
                            // 新的，所以nat没加里面
                            s_test.owners.add(get.owner);

                            s_test = new SubImpl();
                        }
                        // 存在, 啥事没有
                    } else {
                        NameAndType nat = natCheck.copy();
                        // 没有，新增的
                        // 补上空缺的owner字段, 上面要用
                        nat.owner = parent;

                        duplicate.add(nat);
                    }
                }
            }
            duplicate.clear();
        }

        // 加上一步工序, 删除找得到的“找不到的”class
        notFoundClasses.removeAll(methods.keySet());

        return dest;
    }

    public IClass reflectClassInfo(String name) {
        if (notFoundClasses.contains(name))
            return null;
        if(cachedClassMof.containsKey(name))
            return cachedClassMof.get(name);

        List<Class<?>> classes = cachedClassRef.get(name);
        if(classes == null) {
            try {
                cachedClassRef.put(name,
                                   // use boot class loader
                                   classes = ReflectionUtils.getFathersAndItfOrdered(Class.forName(name.replace('/', '.'), false, null)));
            } catch (Throwable e) {
                if (!(e instanceof ClassNotFoundException) && !(e instanceof NoClassDefFoundError)) {
                    System.err.println("Exception Loading " + name);
                    e.printStackTrace();
                }
                notFoundClasses.add(name);
                return null;
            }
        }

        MyHashSet<ReflectMNode> o = new MyHashSet<>();
        for (Class<?> clz1 : classes) {
            try {
                for (Method method : clz1.getDeclaredMethods()) {
                    o.add(new ReflectMNode(method));
                }
            } catch (Throwable e) {
                if (!(e instanceof NoClassDefFoundError)) {
                    System.out.println("[Warn]Exception loading " + name);
                    e.printStackTrace();
                }
            }
        }
        IClass list = new ReflectClass(classes.get(0), Helpers.cast(Arrays.asList(o.toArray())));
        cachedClassMof.put(name, list);
        return list;
    }

    public static final List<String> OBJECT_INHERIT = Collections.singletonList("java/lang/Object");
    // 使用反射查找实现类，避免RT太大不好解析
    public boolean isInherited(Desc k, Map<String, List<String>> selfSupers, boolean unableDefault) {
        if(notFoundClasses.contains(k.owner))
            return unableDefault;

        List<Type> pars = ParamHelper.parseMethod(k.param);
        pars.remove(pars.size() - 1);
        Class<?>[] par = new Class<?>[pars.size()];
        for (int i = 0; i < pars.size(); i++) {
            Type type = pars.get(i);

            List<Class<?>> clz = cachedClassRef.get(type.owner);
            if(clz != null) {
                par[i] = clz.get(0);
            } else {
                if (notFoundClasses.contains(type.owner)) {
                    return unableDefault;
                }
                try {
                    par[i] = type.toJavaClass();
                } catch (Throwable e) {
                    String o = type.owner;
                    if (!(e instanceof ClassNotFoundException) && !(e instanceof NoClassDefFoundError)) {
                        System.err.println("Exception loading " + o);
                        e.printStackTrace();
                    }
                    notFoundClasses.add(o);
                    return unableDefault;
                }
                if(type.owner != null)
                    cachedClassRef.put(type.owner, ReflectionUtils.getFathersAndItfOrdered(par[i]));
            }
        }

        String s = k.owner;
        List<Class<?>> pars2 = cachedClassRef.get(s);
        if (pars2 == null) {
            try {
                cachedClassRef.put(s, pars2 = ReflectionUtils.getFathersAndItfOrdered(Class.forName(s.replace('/', '.'), false, Util.class.getClassLoader())));
            } catch (Throwable e) {
                if(!(e instanceof ClassNotFoundException) && ! (e instanceof NoClassDefFoundError)) {
                    System.err.println("Exception loading " + s);
                    e.printStackTrace();
                }
                notFoundClasses.add(s);
                return unableDefault;
            }
        }

        for (int j = 0; j < pars2.size(); j++) {
            Class<?> clz = pars2.get(j);

            try {
                clz.getDeclaredMethod(k.name, par);
                return true;
            } catch (NoSuchMethodException ignored) {
            } catch (NoClassDefFoundError e) {
                notFoundClasses.add(e.getMessage());
                return false;
            }
        }
        return false;
    }

    // endregion

    public static Thread writeResourceAsync(@Nonnull ZipFileWriter zfw, @Nonnull Map<String, ?> resources) {
        Thread writer = new Thread(new ResWriter(zfw, resources), "Resource Writer");
        writer.setDaemon(true);
        writer.start();
        return writer;
    }

    static void concurrent(String name, Consumer<Context> consumer, List<List<Context>> ctxs) {
        ArrayList<Worker> wait = new ArrayList<>();

        wait.ensureCapacity(ctxs.size());
        for(int i = 0, e = ctxs.size(); i < e; i++) {
            Worker worker = new Worker(ctxs.get(i), consumer, name + i);
            SharedThreads.CPU_POOL.pushTask(worker);
            wait.add(worker);
        }

        try {
            for(Worker t2 : wait) {
                t2.get();
            }
        } catch (InterruptedException ignored) {
        } catch (ExecutionException e) {
            throw (RuntimeException) e.getCause();
        }
    }

    // region 映射各种名字

    public static String mapClassName(Map<String, String> classMap, String name) {
        // should indexOf(';') ?
        String nn = mapClassName(classMap, name, false, 0, name.length());
        return nn == null ? name : nn;
    }

    @Nullable
    public static String mapOwner(Map<? extends CharSequence, String> map, CharSequence name, boolean file) {
        return name == null ? null : mapClassName(map, name, file, 0, name.length());
    }

    @Nullable
    private static String mapClassName(Map<? extends CharSequence, String> map, CharSequence name, boolean file, int s, int e) {
        if (e == 0)
            return "";

        CharList cl = ThreadBasedCache.get().sharedCL;
        cl.clear();

        String b;
        if ((b = map.get(cl.append(name, s, e - s - (file ? 6 : 0)))) != null) {
            return file ? (b + ".class") : b;
        }

        boolean endSemi = name.charAt(e - 1) == ';';
        switch (name.charAt(s)) {
            // This is for [Field type]
            case 'L':
                if (endSemi) {
                    if(file)
                        throw new IllegalArgumentException("Unk cls " + name.subSequence(s + 1, e - 1));
                    String result = mapClassName(map, name, false, s + 1, e - 1);
                    cl.clear();
                    return result == null ? null : cl.append('L').append(result).append(';').toString();
                } // class name starts with L
                break;
            // This is for [Field type, Class type]
            case '[':
                if(file)
                    throw new IllegalArgumentException("Unk arr " + name.subSequence(s, e));

                int arrLv = s;
                while (name.charAt(s) == '[') {
                    s++;
                }
                arrLv = s - arrLv;

                if(name.charAt(s) == 'L') {
                    if(endSemi) {
                        String result = mapClassName(map, name, false, s + 1, e - 1);
                        if(result == null)
                            return null;

                        cl.clear();
                        for (int i = 0; i < arrLv; i++) {
                            cl.append('[');
                        }
                        return cl.append('L').append(result).append(';').toString();
                    } else {
                        throw new IllegalArgumentException("Unk arr 1 " + name);
                    }
                } else if(endSemi) // primitive array ?
                    throw new IllegalArgumentException("Unk arr 2 " + name);
                break;
        }

        int dollar = TextUtil.limitedIndexOf(name, '$', s, e);

        cl.clear();
        if (dollar != -1 && (b = map.get(cl.append(name, s, dollar - s))) != null) {
            cl.clear();
            return cl.append(b).append(name, dollar, e - dollar).toString();
        }

        return file ? name.subSequence(s, e).toString() : null;
    }

    public static String transformMethodParam(Map<String, String> classMap, String md) {
        if(md.length() <= 4) // min = ()La;
            return md;

        ArrayList<Type> types = Helpers.cast(ThreadBasedCache.get().sharedAL);
        types.clear();
        ParamHelper.parseMethod(md, types);

        boolean gt0 = false;
        for (int i = 0; i < types.size(); i++) {
            Type type = types.get(i);
            String str;
            if ((str = mapOwner(classMap, type.owner, false)) != null) {
                type.owner = str;
                gt0 = true;
            }
        }
        try {
            return gt0 ? ParamHelper.getMethod(types) : md;
        } finally {
            types.clear();
        }
    }

    public static String transformFieldType(Map<String, String> classMap, String fd) {
        if(fd.length() == 0) return null;
        char first = fd.charAt(0);
        // 数组
        if(first == NativeType.ARRAY) {
            first = fd.charAt(fd.lastIndexOf(NativeType.ARRAY) + 1);
        }
        // 不是object类型
        if(first != NativeType.CLASS)
            return fd;

        return mapOwner(classMap, fd, false);
    }

    // endregion
    // region 准备上下文

    public static List<Context> ctxFromStream(Map<String, InputStream> streams) throws IOException {
        List<Context> ctx = new ArrayList<>(streams.size());

        ByteList bl = new ByteList();
        for (Map.Entry<String, InputStream> entry : streams.entrySet()) {
            Context c = new Context(entry.getKey().replace('\\', '/'), bl.readStreamArrayFully(entry.getValue()).toByteArray());
            entry.getValue().close();
            c.getData();
            bl.clear();
            ctx.add(c);
        }

        return ctx;
    }

    public static List<Context> ctxFromZip(File input, Charset charset) throws IOException {
        return ctxFromZip(input, charset, Helpers.alwaysTrue());
    }

    public static List<Context> ctxFromZip(File input, Charset charset, Predicate<String> filter) throws IOException {
        ZipFile inputJar = new ZipFile(input, charset);

        List<Context> ctx = new ArrayList<>();

        ByteList bl = new ByteList();
        Enumeration<? extends ZipEntry> en = inputJar.entries();
        while (en.hasMoreElements()) {
            ZipEntry zn;
            try {
                zn = en.nextElement();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("可能是编码错误! 请指定编码", e);
            }
            if (zn.isDirectory()) continue;
            if (zn.getName().endsWith(".class") && filter.test(zn.getName())) {
                InputStream in = inputJar.getInputStream(zn);
                Context c = new Context(zn.getName().replace('\\', '/'), bl.readStreamArrayFully(in).toByteArray());
                in.close();
                bl.clear();
                ctx.add(c);
            }
        }

        inputJar.close();

        return ctx;
    }

    public static List<Context> ctxFromZip(File input, Charset charset, Map<String, byte[]> res) throws IOException {
        ZipFile inputJar = new ZipFile(input, charset);

        List<Context> ctx = new ArrayList<>();

        ByteList bl = new ByteList();
        Enumeration<? extends ZipEntry> en = inputJar.entries();
        while (en.hasMoreElements()) {
            ZipEntry zn;
            try {
                zn = en.nextElement();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("可能是编码错误! 请指定编码", e);
            }
            if (zn.isDirectory()) continue;
            InputStream in = inputJar.getInputStream(zn);
            bl.readStreamArrayFully(in);
            in.close();
            if (zn.getName().endsWith(".class")) {
                Context c = new Context(zn.getName().replace('\\', '/'), bl.toByteArray());
                ctx.add(c);
            } else {
                res.put(zn.getName(), bl.toByteArray());
            }
            bl.clear();
        }

        inputJar.close();

        return ctx;
    }

    // endregion

    public static long libHash(List<?> list) {
        long hash = 0;
        for (int i = 0; i < list.size(); i++) {
            if(!(list.get(i) instanceof File)) continue;
            File f = (File) list.get(i);
            if(f.getName().endsWith(".jar") || f.getName().endsWith(".zip")) {
                hash = 31 * hash + f.getName().hashCode();
                hash = 31 * hash + (f.length() & 262143);
                hash ^= f.lastModified();
            }
        }

        return hash;
    }
}
