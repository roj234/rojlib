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

import roj.asm.mapper.util.Context;
import roj.asm.mapper.util.KEntry;
import roj.asm.mapper.util.MtDesc;
import roj.asm.tree.ConstantData;
import roj.asm.tree.simple.FieldSimple;
import roj.asm.tree.simple.MethodSimple;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.reflect.ReflectionUtils;
import roj.text.TextUtil;
import roj.ui.UIUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static roj.asm.mapper.Obfuscator.TREMINATE_THIS_CLASS;

/**
 * class混淆器
 *
 * @author Roj233
 * @since 2021/7/18 18:33
 */
public abstract class Deobfuscator extends Mapping {
    public static final int    REMOVE_SYNTHETIC     = 1, ADD_PUBLIC = 2;
    public static final boolean WEAKER_BUT_SAFER = Boolean.parseBoolean(System.getProperty("roj.obf.weakerButSafer", "true"));

    DeobfConstMapper dcm;
    CodeMapper       com;

    MyHashSet<String> notFoundClasses = new MyHashSet<>();
    MyHashMap<String, List<Class<?>>> cachedClassRef = new MyHashMap<>();

    int flags;

    public Deobfuscator() {
        dcm = new DeobfConstMapper();
        com = new CodeMapper(dcm);
        com.rewrite = true;
    }
    public void reset(List<File> libraries) {
        dcm.generateSuperMap(libraries);
    }

    public void obfuscate(List<Context> arr) {
        DeobfConstMapper t = dcm;

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

            for (int i = 0; i < arr.size(); i++) {
                t.mapSelf(cur = arr.get(i));
            }

            for (int i = 0; i < arr.size(); i++) {
                t.mapConstant(cur = arr.get(i));
            }

            CodeMapper cm = com;

            for (int i = 0; i < arr.size(); i++) {
                cm.processOne(cur = arr.get(i));
            }

        } catch (Throwable e) {
            throw new RuntimeException("At parsing " + cur, e);
        }
    }

    protected void prepare(Context c) {
        ConstantData data = c.getData();

        String dest = obfClass(data.name);
        if(dest == TREMINATE_THIS_CLASS)
            return;

        DeobfConstMapper t = this.dcm;
        if(dest != null && t.classMap.put(data.name, dest) != null) {
            System.out.println("重复的class name " + data.name);
        }

        MtDesc desc = new MtDesc(data.name, "", "");
        List<MethodSimple> methods = data.methods;
        for (int i = 0; i < methods.size(); i++) {
            MethodSimple method = methods.get(i);
            FlagList acc = method.accesses;
            if ((flags & REMOVE_SYNTHETIC) != 0) {
                acc.flag &= ~AccessFlag.SYNTHETIC;
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

        List<FieldSimple> fields = data.fields;
        for (int i = 0; i < fields.size(); i++) {
            FieldSimple field = fields.get(i);
            FlagList acc = field.accesses;
            if ((flags & REMOVE_SYNTHETIC) != 0) {
                acc.flag &= ~AccessFlag.SYNTHETIC;
            }
            if ((flags & ADD_PUBLIC) != 0 && (acc.flag & AccessFlag.PRIVATE) == 0) {
                acc.flag &= ~AccessFlag.PROTECTED;
                acc.flag |= AccessFlag.PUBLIC;
            }

            desc.name = field.name.getString();
            desc.param = field.type.getString();
            String fs = obfFieldName(desc);
            if (fs != null) {
                t.fieldMap.put(desc, fs);
                desc.flags = acc;
                desc = new MtDesc(data.name, "", "");
            }
        }
    }

    public void writeObfuscationMap(File file) throws IOException {
        dcm.saveToSrg(file);
    }

    /**
     * 这个东西是不是这个class第一个创建的
     */
    public boolean isInherited(MtDesc k, boolean m) {
        String owner = k.owner;

        List<String> parents = dcm.selfSupers.getOrDefault(owner, Collections.emptyList());
        for (int i = 0; i < parents.size(); i++) {
            String parent = parents.get(i);
            k.owner = parent;

            KEntry entry = m ? dcm.libSkipMethods.find(k) : dcm.libSkipFields.find(k);
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
            List<Type> pars = ParamHelper.parseMethod(k.param);
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

    public void dumpMissingClasses() {
        if(!notFoundClasses.isEmpty()) {
            System.out.println("有" + notFoundClasses.size() + "个类没有被找到," + (WEAKER_BUT_SAFER ? "这会导致更低的混淆水平" : "这可能导致潜在的崩溃"));
            try {
                if(UIUtil.readBoolean("你需要查看这些类吗?")) {
                    System.out.println(TextUtil.prettyPrint(notFoundClasses));
                }
            } catch (IOException ignored) {}
        }
    }

    public abstract String obfClass(String origin);
    public abstract String obfMethodName(MtDesc descriptor);
    public abstract String obfFieldName(MtDesc descriptor);
}
