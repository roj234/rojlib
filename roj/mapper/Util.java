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
package roj.mapper;

import roj.asm.cst.CstClass;
import roj.asm.misc.ReflectClass;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.asm.tree.MoFNode;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.concurrent.TaskPool;
import roj.io.IOUtil;
import roj.io.ZipFileWriter;
import roj.mapper.util.*;
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
 * @since  2020/8/19 21:32
 */
public final class Util {
    private static final FastThreadLocal<Util> ThreadBasedCache = FastThreadLocal.withInitial(Util::new);

    public static final int CPU = Runtime.getRuntime().availableProcessors();

    private Util() {}

    public final Desc sharedDC = new Desc("", "", "");

    final MyHashSet<String>                 notFoundClasses = new MyHashSet<>();
    final MyHashMap<String, List<Class<?>>> cParents        = new MyHashMap<>();
    final MyHashMap<String, IClass>         cachedClassMof  = new MyHashMap<>();

    private final CharList sharedCL = IOUtil.getSharedCharBuf();
    private final CharList sharedCL2 = new CharList(12);
    private final ArrayList<?> sharedAL = new ArrayList<>();

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
            if(rc == null) return Collections.emptyList();
            superItf = rc.i_superClassAll();
        }
        return superItf;
    }

    /**
     *   父类的方法被子类实现的接口使用
     */
    public MyHashSet<SubImpl> gatherSubImplements(List<Context> ctx, ConstMapper mapper, Map<String, IClass> methods) {
        if(methods.isEmpty())
        for (int i = 0; i < ctx.size(); i++) {
            ConstantData data = ctx.get(i).getData();
            methods.put(data.name, data);
        }

        Map<String, List<Desc>> mapperMethods = null;

        MyHashSet<SubImpl> dest = new MyHashSet<>();
        SubImpl s_test = new SubImpl();

        MyHashSet<NameAndType> duplicate = new MyHashSet<>();
        NameAndType natCheck = new NameAndType();

        for (int k = 0; k < ctx.size(); k++) {
            ConstantData data = ctx.get(k).getData();
            if ((data.accessFlag() & (AccessFlag.INTERFACE | AccessFlag.ANNOTATION)) != 0) continue;

            List<String> superClasses = superClasses(data.parent, mapper.selfSupers);
            if (superClasses.isEmpty()) {
                if (data.interfaces.isEmpty()) continue;
            } else {
                boolean one = false;
                List<CstClass> itfs = data.interfaces;
                for (int i = 0; i < itfs.size(); i++) {
                    CstClass itf = itfs.get(i);
                    String name = itf.getValue().getString();
                    if (!superClasses.contains(name)) {
                        one = true;
                        break;
                    }
                }

                if (!one) continue;
            }

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
                        if (mapperMethods == null) {
                            mapperMethods = new MyHashMap<>();
                            for (Desc key : mapper.methodMap.keySet()) {
                                mapperMethods.computeIfAbsent(key.owner, Helpers.fnArrayList()).add(key);
                            }
                        }
                        nodes = mapperMethods.getOrDefault(parent, Collections.emptyList());
                        methods.put(parent, new ReflectClass(parent, Helpers.cast(nodes)));
                        if(nodes.isEmpty()) continue;
                    }
                } else {
                    nodes = clz.methods();
                }

                for (int j = 0; j < nodes.size(); j++) {
                    MoFNode method = nodes.get(j);
                    if ((method.accessFlag() & AccessFlag.PRIVATE) != 0) continue;
                    if((natCheck.name = method.name()).startsWith("<")) continue;
                    natCheck.param = method.rawDesc();

                    NameAndType get = duplicate.find(natCheck);
                    if (get != natCheck) {
                        // 父类存在方法

                        // 若存在继承关系，不是接口
                        if (mapper.selfSupers.getOrDefault(get.owner, Collections.emptyList()).contains(parent)) {
                            // 跳过当前class
                            continue;
                        }

                        // 把新的复制，然后测试能不能找到存在的SI-NAT
                        s_test.type = new Desc(data.name, get.name, get.param);
                        SubImpl s_get = dest.intern(s_test);
                        s_get.owners.add(parent);

                        // native不能
                        if ((method.accessFlag() & AccessFlag.NATIVE) != 0) s_get.immutable = true;
                        // 至少有一个类不是要处理的类: 不能混淆
                        if (!methods.containsKey(parent)) s_get.immutable = true;

                        // 不存在
                        if (s_get == s_test) {
                            // 新的，所以nat没加里面
                            s_test.owners.add(get.owner);

                            s_test = new SubImpl();
                        }
                        // 存在, 啥事没有
                    } else {
                        // 没有，新增的
                        // 补上空缺的owner字段, 上面要用
                        NameAndType nat = natCheck.copy(parent);

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
        IClass me = cachedClassMof.get(name);
        if (me != null || cachedClassMof.containsKey(name)) return me;

        List<Class<?>> ref = mkRefs(name);
        if (ref == null) return null;

        try {
            IClass list = ReflectClass.from(ref.get(0));
            cachedClassMof.put(name, list);
            return list;
        } catch (Error e) {
            cachedClassMof.put(name, null);
            return null;
        }
    }

    private List<Class<?>> mkRefs(String name) {
        if (notFoundClasses.contains(name)) return null;
        List<Class<?>> ref = cParents.get(name);
        if(ref == null) {
            try {
                cParents.put(name,
                             // use boot class loader
                             ref = ReflectionUtils.getFathersAndItfOrdered(Class.forName(
                                     name.replace('/', '.'), false, null)));
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                notFoundClasses.add(name);
                return null;
            } catch (Throwable e) {
                System.err.println("Exception Loading " + name);
                e.printStackTrace();
                notFoundClasses.add(name);
                return null;
            }
        }
        return ref;
    }

    public static final List<String> OBJECT_INHERIT = Collections.singletonList("java/lang/Object");
    // 使用反射查找实现类，避免RT太大不好解析
    public boolean isInherited(Desc k, List<String> toTest, boolean def) {
        if(notFoundClasses.contains(k.owner)) return def;

        if (checkObjectInherit(k)) return true;

        List<Type> pars = Helpers.cast(sharedAL); pars.clear();
        ParamHelper.parseMethod(k.param, pars);
        pars.remove(pars.size() - 1);
        Class<?>[] par = new Class<?>[pars.size()];
        for (int i = 0; i < pars.size(); i++) {
            Type type = pars.get(i);

            List<Class<?>> p = cParents.get(type.owner);
            if(p != null) {
                par[i] = p.get(0);
            } else {
                if (notFoundClasses.contains(type.owner)) return def;
                try {
                    par[i] = type.toJavaClass();
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    notFoundClasses.add(type.owner);
                    return def;
                } catch (Throwable e) {
                    String o = type.owner;
                    System.err.println("Exception loading " + o);
                    e.printStackTrace();
                    notFoundClasses.add(o);
                    return def;
                }
                if(type.owner != null)
                    cParents.put(type.owner, ReflectionUtils.getFathersAndItfOrdered(par[i]));
            }
        }

        if (toTest == null || toTest.isEmpty()) {
            List<Class<?>> sup = mkRefs(k.owner);
            if (sup == null) return def;
            return test(k.name, par, sup);
        } else {
            for (int i = 0; i < toTest.size(); i++) {
                List<Class<?>> sup = mkRefs(toTest.get(i));
                if (sup == null) continue;
                if (test(k.name, par, sup)) return true;
            }
        }
        return false;
    }

    public static boolean checkObjectInherit(Desc k) {
        // 检查Object的继承
        // final不用管
        if (k.param.startsWith("()")) {
            switch (k.name) {
                case "clone":
                case "toString":
                case "hashCode":
                case "finalize":
                    return true;
            }
        } else return k.param.equals("(Ljava/lang/Object;)Z") && k.name.equals("equals");
        return false;
    }

    private boolean test(String name, Class<?>[] par, List<Class<?>> sup) {
        for (int j = 0; j < sup.size(); j++) {
            Class<?> clz = sup.get(j);

            try {
                clz.getDeclaredMethod(name, par);
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

    private static TaskPool POOL = new TaskPool(1, CPU, 1, 60000, "异步映射");
    public static void setAsyncPool(TaskPool task) {
        POOL = task;
    }
    static void async(Consumer<Context> action, List<List<Context>> ctxs) {
        ArrayList<Worker> wait = new ArrayList<>(ctxs.size());
        for(int i = 0; i < ctxs.size(); i++) {
            Worker w = new Worker(ctxs.get(i), action);
            POOL.pushTask(w);
            wait.add(w);
        }

        for (int i = 0; i < wait.size(); i++) {
            try {
                wait.get(i).get();
            } catch (InterruptedException ignored) {
            } catch (ExecutionException e) {
                Helpers.athrow(e.getCause());
            }
        }
    }

    // region 映射各种名字

    public String mapClassName(Map<String, String> classMap, String name) {
        // should indexOf(';') ?
        String nn = mapClassName(classMap, name, false, 0, name.length());
        return nn == null ? name : nn;
    }

    @Nullable
    public String mapOwner(Map<? extends CharSequence, String> map, CharSequence name, boolean file) {
        return mapClassName(map, name, file, 0, name.length());
    }

    @Nullable
    private String mapClassName(Map<? extends CharSequence, String> map, CharSequence name, boolean file, int s, int e) {
        if (e == 0)
            return "";

        CharList cl = sharedCL;
        cl.clear();

        String b;
        if ((b = map.get(cl.append(name, s, e - (file ? 6 : 0)))) != null) {
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
        if (dollar != -1 && (b = map.get(cl.append(name, s, dollar))) != null) {
            cl.clear();
            return cl.append(b).append(name, dollar, e).toString();
        }

        return file ? name.subSequence(s, e).toString() : null;
    }

    public String transformMethodParam(Map<String, String> classMap, String md) {
        if(md.length() <= 4) // min = ()La;
            return md;

        boolean changed = false;

        CharList out = sharedCL2;
        out.clear();

        for (int i = 0; i < md.length(); ) {
            char c = md.charAt(i++);
            out.append(c);
            if (c == 'L') {
                int j = md.indexOf(';', i);
                if (j == -1) throw new IllegalStateException("Illegal descriptor");

                String s = mapClassName(classMap, md, false, i, j);
                if (s != null) {
                    changed = true;
                    out.append(s);
                } else {
                    out.append(md, i, j);
                }
                i = j;
            }
        }
        return changed ? out.toString() : md;
    }

    public String transformFieldType(Map<String, String> classMap, String fd) {
        if(fd.length() == 0) return null;
        char first = fd.charAt(0);
        // 数组
        if(first == Type.ARRAY) {
            first = fd.charAt(fd.lastIndexOf(Type.ARRAY) + 1);
        }
        // 不是object类型
        if(first != Type.CLASS) return null;

        return mapClassName(classMap, fd, false, 0, fd.length());
    }

    // endregion
    // region 准备上下文

    public static List<Context> ctxFromStream(Map<String, InputStream> streams) throws IOException {
        List<Context> ctx = new ArrayList<>(streams.size());

        ByteList bl = new ByteList();
        for (Map.Entry<String, InputStream> entry : streams.entrySet()) {
            Context c = new Context(entry.getKey().replace('\\', '/'), bl.readStreamFully(entry.getValue()).toByteArray());
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
                Context c = new Context(zn.getName().replace('\\', '/'), bl.readStreamFully(in).toByteArray());
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
            bl.readStreamFully(in);
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
