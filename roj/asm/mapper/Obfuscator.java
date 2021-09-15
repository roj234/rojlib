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

import roj.asm.Parser;
import roj.asm.mapper.util.*;
import roj.asm.tree.AccessData;
import roj.asm.tree.ConstantData;
import roj.asm.tree.simple.FieldSimple;
import roj.asm.tree.simple.MethodSimple;
import roj.asm.tree.simple.MoFNode;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.ConstantWriter;
import roj.asm.util.FlagList;
import roj.collect.CharMap;
import roj.collect.FindMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.io.ZipUtil;
import roj.reflect.ReflectionUtils;
import roj.text.TextUtil;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;
import roj.util.ByteWriter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * class混淆器
 *
 * @author Roj233
 * @since 2021/7/18 18:33
 */
public abstract class Obfuscator extends Mapping {
    public static final String TREMINATE_THIS_CLASS = new String();
    public static final int ADD_SYNTHETIC = 1, ADD_PUBLIC = 2;
    public static final boolean WEAKER_BUT_SAFER = Boolean.parseBoolean(System.getProperty("roj.obf.weakerButSafer", "true"));

    ConstMapper constMapper;
    CodeMapper codeMapper;
    MyHashSet<FlDesc> libFields = new MyHashSet<>();
    MyHashSet<MtDesc> libMethods = new MyHashSet<>();

    MyHashSet<String> notFoundClasses = new MyHashSet<>();
    MyHashMap<String, List<Class<?>>> cachedClassRef = new MyHashMap<>();
    MyHashMap<String, Collection<MethodMoFNode>> cachedClassMof = new MyHashMap<>();

    protected int flags;
    protected boolean fatallyMissingClasses;

    public Obfuscator() {
        constMapper = new ConstMapper();
        constMapper.isMappingForLibraries = false;
        codeMapper = new CodeMapper(constMapper);
        codeMapper.rewrite = true;
    }

    final void genDataInherit(List<File> files) {
        if(files.isEmpty())
            return;

        MyHashSet<FlDesc> libFields = this.libFields;
        libFields.clear();
        MyHashSet<MtDesc> libMethods = this.libMethods;
        libMethods.clear();

        CharMap<FlagList> byAcc = new CharMap<>();

        ZipUtil.ICallback cb = (fileName, s) -> {
            byte[] bytes = IOUtil.read(s);
            if(bytes.length < 32)
                return;

            AccessData data;
            try {
                data = Parser.parseAccessDirect(bytes);
            } catch (Throwable e) {
                CmdUtil.warning("Class " + fileName + " is unable to read", e);
                return;
            }

            List<AccessData.MOF> ent = data.methods;
            MtDesc m = new MtDesc(data.name, "", "");
            for (int i = 0; i < ent.size(); i++) {
                AccessData.MOF method = ent.get(i);
                if(method.name.startsWith("<") || (method.acc & (AccessFlag.STATIC | AccessFlag.PRIVATE | AccessFlag.FINAL)) != 0) {
                    continue;
                }
                m.name = method.name;
                m.param = method.desc;
                m.flags = byAcc.computeIfAbsent((char) method.acc, ConstMapper.fl);
                libMethods.add(m);
                m = new MtDesc(data.name, "", "");
            }

            ent = data.fields;
            FlDesc f = new FlDesc(data.name, "");
            for (int i = 0; i < ent.size(); i++) {
                AccessData.MOF field = ent.get(i);
                if((field.acc & (/*AccessFlag.STATIC | */AccessFlag.PRIVATE)) != 0)
                    continue;
                f.name = field.name;
                f.flags = byAcc.computeIfAbsent((char) field.acc, ConstMapper.fl);
                libFields.add(f);
                f = new FlDesc(data.name, "");
            }
        };

        for (int i = 0; i < files.size(); i++) {
            File fi = files.get(i);
            String f = fi.getName();
            if (!f.startsWith("[noread]"))
                ZipUtil.unzip(fi, cb, (ze) -> ze.getName().endsWith(".class"));
        }
    }

    /** done: 如果要更智能的话，要处理下面这种情况
     *   父类的方法被子类实现的接口使用
     */
    final MyHashSet<SubImpl> genSubImplement(List<Context> ctx) {
        MyHashMap<String, List<? extends MoFNode>> methods = new MyHashMap<>();
        for (int i = 0; i < ctx.size(); i++) {
            ConstantData data = ctx.get(i).getData();
            methods.put(data.name, data.methods);
        }

        MyHashSet<SubImpl> dest = new MyHashSet<>();
        SubImpl si = new SubImpl();

        MyHashSet<NameAndType> duplicate = new MyHashSet<>();
        NameAndType natCheck = new NameAndType();

        for (Map.Entry<String, List<String>> entry : constMapper.selfSupers.entrySet()) {
            par:
            for (String parent : entry.getValue()) {
                // 获取所有的方法
                Collection<? extends MoFNode> nodes = methods.get(parent);
                if(nodes == null) {
                    nodes = getMethods(parent);
                }

                for(MoFNode method : nodes) {
                    natCheck.name = method.name();
                    natCheck.type = method.rawDesc();

                    NameAndType get = duplicate.find(natCheck);
                    if(get != natCheck) {
                        // nat有了

                        if(parent.equals(get.owner)) {
                            // System.out.println("D1 " + parent + " " + get.owner);
                            continue par;
                        }

                        // 若存在继承关系，不是接口
                        if(constMapper.selfSupers.getOrDefault(get.owner, Collections.emptyList()).contains(parent)) {
                            // System.out.println("D2" + parent + " " + get.owner);
                            // 跳过当前class
                            continue par;
                        }

                        /// find
                        si.type = get;
                        // 先add
                        SubImpl s_get = dest.find(si);
                        s_get.owners.add(parent);

                        if(!methods.containsKey(parent))
                            s_get.original = true;

                        if(s_get == si) {
                            // SubImpl 新增
                            // 把那个字段加上去
                            si.owners.add(get.owner);
                            dest.add(si);

                            si = new SubImpl();
                        }
                        // SI存在, class已加，恢复new SubImpl();
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

        return dest;
    }

    public void reset(List<File> libraries) {
        constMapper.generateSuperMap(libraries);
        genDataInherit(libraries);
    }

    public void clearClassRef() {
        notFoundClasses.clear();
        cachedClassRef.clear();
    }

    public void obfuscate(List<Context> arr) {
        ConstMapper t = constMapper;

        t.selfSkipFields = new MyHashSet<>(t.libSkipFields);
        t.selfSkipMethods = new MyHashSet<>(t.libSkipMethods);
        t.selfSupers = new MyHashMap<>(arr.size());
        t.selfMethods = new MyHashMap<>();

        Context cur = null;
        try {
            for (int i = 0; i < arr.size(); i++) {
                t.parse(cur = arr.get(i));
            }

            t.initSelfSuperMap();

            for (int i = 0; i < arr.size(); i++) {
                prepare(cur = arr.get(i));
            }

            FindMap<MtDesc, String> methodMap = constMapper.getMethodMap();
            if(!methodMap.isEmpty()) {
                MtDesc finder = new MtDesc("", "", "");
                MyHashSet<SubImpl> subs = genSubImplement(arr);
                System.out.println(subs);
                for (SubImpl impl : subs) {
                    finder.name = impl.type.name;
                    finder.param = impl.type.type;
                    Iterator<String> itr = impl.owners.iterator();

                    if(!impl.original) {
                        String firstName;
                        do {
                            finder.owner = itr.next(); // findFirst
                            firstName = methodMap.get(finder);
                        } while (itr.hasNext() && firstName == null);

                        if (!itr.hasNext()) {
                            System.out.println("WARNING: no name " + impl.owners);
                            System.out.println(impl.type);
                        }

                        while (itr.hasNext()) {
                            finder.owner = itr.next();
                            if (methodMap.put(finder, firstName) == null) {
                                System.out.println(finder + " : no mapping found.");
                                methodMap.remove(finder);
                            }
                        }
                    } else {
                        while (itr.hasNext()) {
                            finder.owner = itr.next();
                            if (methodMap.remove(finder) == null) {
                                System.out.println(finder + " : no mapping found.");
                            }
                        }
                    }
                }
            }

            for (int i = 0; i < arr.size(); i++) {
                t.mapSelf(cur = arr.get(i));
            }

            for (int i = 0; i < arr.size(); i++) {
                t.mapConstant(cur = arr.get(i));
            }

            beforeMapCode(arr);

            CodeMapper cm = codeMapper;

            for (int i = 0; i < arr.size(); i++) {
                cm.processOne(cur = arr.get(i));
            }

            afterMapCode(arr);
        } catch (Throwable e) {
            throw new RuntimeException("At parsing " + cur, e);
        }
    }

    protected void prepare(Context c) {
        ConstantData data = c.getData();

        String dest = obfClass(data.name);
        if(dest == TREMINATE_THIS_CLASS)
            return;

        ConstMapper t = this.constMapper;
        if(dest != null && t.classMap.put(data.name, dest) != null) {
            System.out.println("重复的class name " + data.name);
        }

        MtDesc desc = new MtDesc(data.name, "", "");
        List<MethodSimple> methods = data.methods;
        for (int i = 0; i < methods.size(); i++) {
            MethodSimple method = methods.get(i);
            FlagList acc = method.accesses;
            if ((flags & ADD_SYNTHETIC) != 0) {
                acc.flag |= AccessFlag.SYNTHETIC;
            }
            if ((flags & ADD_PUBLIC) != 0 && (acc.flag & AccessFlag.PRIVATE) == 0) {
                acc.flag &= ~AccessFlag.PROTECTED;
                acc.flag |= AccessFlag.PUBLIC;
            }

            if ((desc.name = method.name.getString()).charAt(0) == '<') continue; // clinit, init
            desc.param = method.type.getString();
            if (!acc.hasAny(AccessFlag.STATIC | AccessFlag.PRIVATE)) {
                if (isInherited(desc, true)) {
                    continue;
                }
            }
            String ms = obfMethodName(desc);
            if (ms != null) {
                t.methodMap.put(desc, ms);
                desc.flags = acc;
                desc = new MtDesc(data.name, "", "");
            }
        }

        FlDesc fdesc = new FlDesc(data.name, "");
        List<FieldSimple> fields = data.fields;
        for (int i = 0; i < fields.size(); i++) {
            FieldSimple field = fields.get(i);
            FlagList acc = field.accesses;
            if ((flags & ADD_SYNTHETIC) != 0) {
                acc.flag |= AccessFlag.SYNTHETIC;
            }
            if ((flags & ADD_PUBLIC) != 0 && (acc.flag & AccessFlag.PRIVATE) == 0) {
                acc.flag &= ~AccessFlag.PROTECTED;
                acc.flag |= AccessFlag.PUBLIC;
            }

            fdesc.name = field.name.getString();
            //if (!acc.hasAny(AccessFlag.STATIC | AccessFlag.PRIVATE)) {
            //    if (isInherited(fdesc, false)) {
            //        continue;
            //    }
            //}
            String fs = obfFieldName(fdesc);
            if (fs != null) {
                t.fieldMap.put(fdesc, fs);
                fdesc.flags = acc;
                fdesc = new FlDesc(data.name, "");
            }
        }
    }

    public void writeObfuscationMap(File file) throws IOException {
        constMapper.saveToSrg(file);
    }

    protected Collection<? extends MoFNode> getMethods(String name) {
        if (notFoundClasses.contains(name))
            return Collections.emptyList();
        if(cachedClassMof.containsKey(name))
            return cachedClassMof.get(name);

        List<Class<?>> classes = cachedClassRef.get(name);
        if(classes == null) {
            try {
                cachedClassRef.put(name,
                                   classes = ReflectionUtils.getFathersAndItfOrdered(Class.forName(name.replace('/', '.'))));
            } catch (Throwable e) {
                if (!(e instanceof ClassNotFoundException) && !(e instanceof NoClassDefFoundError)) {
                    System.out.println("[Warn]Something went wrong during load " + name + ": " + e);
                }
                notFoundClasses.add(name);
                fatallyMissingClasses = true;
                return Collections.emptyList();
            }
        }

        MyHashSet<MethodMoFNode> list = new MyHashSet<>();
        for (Class<?> clz1 : classes) {
            for (Method method : clz1.getDeclaredMethods()) {
                list.add(new MethodMoFNode(method));
            }
        }
        cachedClassMof.put(name, list);
        return list;
    }

    /**
     * 这个东西是不是这个class第一个创建的
     */
    public boolean isInherited(KEntry k, boolean m) {
        String owner = k.owner;

        List<String> parents = constMapper.selfSupers.getOrDefault(owner, Collections.emptyList());
        for (int i = 0; i < parents.size(); i++) {
            String parent = parents.get(i);
            k.owner = parent;

            KEntry entry = m ? libMethods.find((MtDesc) k) : libFields.find((FlDesc) k);
            if (k != entry) {
                //System.out.println("Found owner " + parent + " for " + k + ", flag is " + entry.flags);
                k.owner = owner;

                FlagList flags = entry.flags;
                if (flags.hasAny(m ? AccessFlag.STATIC | AccessFlag.PRIVATE | AccessFlag.FINAL : AccessFlag.PRIVATE)) {
                    return false;
                } else if (flags.hasAny(AccessFlag.PUBLIC | AccessFlag.PROTECTED)) {
                    return true;
                } else { // may extend
                    return Util.arePackagesSame(owner, parent);
                }
            }
        }
        k.owner = owner;

        // 使用反射查找实现类，避免RT太大之类的问题
        Class<?>[] par;
        if(m) {
            List<Type> pars = ParamHelper.parseMethod(((MtDesc)k).param);
            pars.remove(pars.size() - 1);
            par = new Class<?>[pars.size()];
            for (int i = 0; i < pars.size(); i++) {
                Type type = pars.get(i);

                List<Class<?>> clz = cachedClassRef.get(type.owner);
                if(clz != null) {
                    par[i] = clz.get(0);
                } else {
                    if (notFoundClasses.contains(type.owner))
                        return WEAKER_BUT_SAFER;
                    try {
                        par[i] = type.toJavaClass();
                    } catch (Throwable e) {
                        String o = type.owner;
                        if (!(e instanceof ClassNotFoundException) && !(e instanceof NoClassDefFoundError)) {
                            System.out.println("[Warn]Something went wrong during load " + o + ": " + e);
                        }
                        notFoundClasses.add(o);
                        return WEAKER_BUT_SAFER;
                    }
                    if(type.owner != null)
                        cachedClassRef.put(type.owner, ReflectionUtils.getFathersAndItfOrdered(par[i]));
                }
            }
        } else {
            par = null;
        }

        for (int i = 0; i < parents.size(); i++) {
            String s = parents.get(i);

            if(notFoundClasses.contains(s))
                continue;

            List<Class<?>> pars = cachedClassRef.get(s);
            if (pars == null) {
                try {
                    cachedClassRef.put(s, pars = ReflectionUtils.getFathersAndItfOrdered(Class.forName(s.replace('/', '.'))));
                } catch (Throwable e) {
                    if(!(e instanceof ClassNotFoundException) && ! (e instanceof NoClassDefFoundError)) {
                        System.out.println("[Warn]Something went wrong during load " + s + ": " + e);
                    }
                    notFoundClasses.add(s);
                    return WEAKER_BUT_SAFER;
                }
                //System.out.println("pars " + pars);
            }

            for (int j = 0; j < pars.size(); j++) {
                Class<?> clz = pars.get(j);

                if (m) {
                    try {
                        clz.getDeclaredMethod(k.name, par);
                        return true;
                    } catch (NoSuchMethodException ignored) {
                    } catch (NoClassDefFoundError e) {
                        notFoundClasses.add(e.getMessage());
                        return false;
                    }
                } else if(!clz.isInterface()) {
                    try {
                        clz.getDeclaredField(k.name);
                        return true;
                    } catch (NoSuchFieldException ignored) {
                    } catch (NoClassDefFoundError e) {
                        notFoundClasses.add(e.getMessage());
                        return false;
                    }
                }
            }
        }
        return false;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public void dumpMissingClasses() {
        if(fatallyMissingClasses)
            System.out.println("FATAL ERROR DURING METHOD RENAMING");
        if(!notFoundClasses.isEmpty()) {
            System.out.println("有" + notFoundClasses.size() + "个类没有被找到," + (WEAKER_BUT_SAFER ? "这会导致更低的混淆水平" : "这可能导致潜在的崩溃"));
            try {
                if(UIUtil.readBoolean("你需要查看这些类吗?")) {
                    System.out.println(TextUtil.prettyPrint(notFoundClasses));
                }
            } catch (IOException ignored) {}
        }
    }

    protected void beforeMapCode(List<Context> arr) {}
    protected void afterMapCode(List<Context> arr) {}

    public abstract String obfClass(String origin);
    public abstract String obfMethodName(MtDesc descriptor);
    public abstract String obfFieldName(FlDesc descriptor);

    private static class MethodMoFNode implements MoFNode {
        private final Method method;

        public MethodMoFNode(Method method) {this.method = method;}

        @Override
        public void toByteArray(ConstantWriter pool, ByteWriter w) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String name() {
            return method.getName();
        }

        @Override
        public String rawDesc() {
            return ParamHelper.classDescriptors(method.getParameterTypes(), method.getReturnType());
        }

        @Override
        public FlagList accessFlag() {
            return new FlagList(method.getModifiers());
        }

        @Override
        public int type() {
            return 192;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MethodMoFNode that = (MethodMoFNode) o;

            return method.getName().equals(that.method.getName()) && Arrays.equals(method.getParameterTypes(),
                                                                                   that.method.getParameterTypes());
        }

        @Override
        public int hashCode() {
            return method.getName().hashCode();
        }
    }
}
