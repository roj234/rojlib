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
import roj.asm.mapper.util.Context;
import roj.asm.mapper.util.Desc;
import roj.asm.mapper.util.SubImpl;
import roj.asm.tree.*;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.collect.CharMap;
import roj.collect.FindMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.io.ZipUtil;
import roj.text.TextUtil;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * class混淆器
 *
 * @author Roj233
 * @since 2021/7/18 18:33
 */
public abstract class Obfuscator {
    public static final String TREMINATE_THIS_CLASS = new String();

    public static final int ADD_SYNTHETIC = 1, ADD_PUBLIC = 2, REMOVE_SYNTHETIC = 4;

    public static final boolean WEAKER_BUT_SAFER = Boolean.parseBoolean(System.getProperty("roj.obf.weakerButSafer", "true"));

    ConstMapper m1;
    CodeMapper  m2;

    MyHashSet<Desc> libMethods = new MyHashSet<>();

    protected int flags;

    public Obfuscator() {
        m1 = new ConstMapper(true);
        m1.flag = ConstMapper.FLAG_CONSTANTLY_MAP | ConstMapper.FLAG_CHECK_SUB_IMPL;
        m2 = new CodeMapper(m1);
    }

    final void genDataInherit(List<File> files) {
        if(files.isEmpty())
            return;

        MyHashSet<Desc> libMethods = this.libMethods;
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
            Desc m = new Desc(data.name, "", "");
            for (int i = 0; i < ent.size(); i++) {
                AccessData.MOF method = ent.get(i);
                if(method.name.startsWith("<") || (method.acc & (AccessFlag.STATIC | AccessFlag.PRIVATE | AccessFlag.FINAL)) != 0) {
                    continue;
                }
                m.name = method.name;
                m.param = method.desc;
                m.flags = byAcc.computeIfAbsent(method.acc, ConstMapper.fl);
                libMethods.add(m);
                m = new Desc(data.name, "", "");
            }
        };

        for (int i = 0; i < files.size(); i++) {
            File fi = files.get(i);
            String f = fi.getName();
            if (!f.startsWith("[noread]"))
                ZipUtil.unzip(fi, cb, (ze) -> ze.getName().endsWith(".class"));
        }
    }

    public void reset(List<File> libraries) {
        m1.loadLibraries(libraries);
        genDataInherit(libraries);
    }

    public void obfuscate(List<Context> arr) {
        ConstMapper t = m1;

        t.initSelf(arr.size());

        Context cur = null;
        try {
            for (int i = 0; i < arr.size(); i++) {
                t.S1_parse(cur = arr.get(i));
            }

            t.initSelfSuperMap();

            for (int i = 0; i < arr.size(); i++) {
                prepare(cur = arr.get(i));
            }

            FindMap<Desc, String> methodMap = t.getMethodMap();
            if(!methodMap.isEmpty()) {
                Desc finder = new Desc("", "", "");
                MyHashSet<SubImpl> subs = Util.getInstance().gatherSubImplements(arr, t, new MyHashMap<>(arr.size()));
                for (SubImpl impl : subs) {
                    finder.name = impl.type.name;
                    finder.param = impl.type.type;
                    Iterator<String> itr = impl.owners.iterator();

                    // 不是外面的方法
                    if(!impl.original) {
                        String firstName;
                        do {
                            finder.owner = itr.next(); // findFirst
                            firstName = methodMap.get(finder);
                        } while (itr.hasNext() && firstName == null);

                        if (firstName != null) {
                            itr = impl.owners.iterator();
                            while (itr.hasNext()) {
                                finder.owner = itr.next();
                                methodMap.replace(finder, firstName);
                            }
                        }
                    } else {
                        while (itr.hasNext()) {
                            finder.owner = itr.next();
                            methodMap.remove(finder);
                        }
                    }
                }
            }

            for (int i = 0; i < arr.size(); i++) {
                t.S2_mapSelf(cur = arr.get(i));
            }

            for (int i = 0; i < arr.size(); i++) {
                t.S3_mapConstant(cur = arr.get(i));
            }

            beforeMapCode(arr);

            CodeMapper cm = m2;

            for (int i = 0; i < arr.size(); i++) {
                cm.processOne(cur = arr.get(i));
            }

            for (int i = 0; i < arr.size(); i++) {
                cur = arr.get(i);
                cur.compress();
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

        ConstMapper t = this.m1;
        if(dest != null && t.classMap.put(data.name, dest) != null) {
            System.out.println("重复的class name " + data.name);
        }
        if ((flags & ADD_PUBLIC) != 0) {
            data.accesses.flag |= AccessFlag.PUBLIC;
        }

        Desc desc = new Desc(data.name, "", "");
        List<? extends MethodNode> methods = data.methods;
        for (int i = 0; i < methods.size(); i++) {
            if (methods.get(i) instanceof Method) {
                c.get();
                data = c.getData();
                methods = data.methods;
                i = 0;
                continue;
            }
            MethodSimple method = (MethodSimple) methods.get(i);
            FlagList acc = method.accesses;
            if ((flags & ADD_SYNTHETIC) != 0) {
                acc.flag |= AccessFlag.SYNTHETIC;
            } else if ((flags & REMOVE_SYNTHETIC) != 0) {
                acc.flag &= ~AccessFlag.SYNTHETIC;
            }
            if ((flags & ADD_PUBLIC) != 0 && (acc.flag & AccessFlag.PRIVATE) == 0) {
                acc.flag &= ~AccessFlag.PROTECTED;
                acc.flag |= AccessFlag.PUBLIC;
            }

            if ((desc.name = method.name.getString()).charAt(0) == '<') continue; // clinit, init
            desc.param = method.type.getString();
            if (!acc.hasAny(AccessFlag.STATIC | AccessFlag.PRIVATE)) {
                if (isInherited(desc)) {
                    continue;
                }
            }
            String ms = obfMethodName(desc);
            if (ms != null) {
                t.methodMap.put(desc, ms);
                libMethods.add(desc);
                desc.flags = acc;
                desc = new Desc(data.name, "", "");
            }
        }

        List<FieldSimple> fields = data.fields;
        for (int i = 0; i < fields.size(); i++) {
            FieldSimple field = fields.get(i);
            FlagList acc = field.accesses;
            if ((flags & ADD_SYNTHETIC) != 0) {
                acc.flag |= AccessFlag.SYNTHETIC;
            } else if ((flags & REMOVE_SYNTHETIC) != 0) {
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
                desc = new Desc(data.name, "", "");
            }
        }
    }

    private boolean isInherited(Desc k) {
        String owner = k.owner;

        List<String> parents = m1.selfSupers.getOrDefault(owner, Util.OBJECT_INHERIT);
        for (int i = 0; i < parents.size(); i++) {
            String parent = parents.get(i);
            k.owner = parent;

            Desc entry = libMethods.find(k);
            if (k != entry) {
                k.owner = owner;
                return entry.flags.hasAny(AccessFlag.PUBLIC | AccessFlag.PROTECTED) ||
                        Util.arePackagesSame(owner, parent);
            }
        }
        k.owner = owner;
        return Util.getInstance().isInherited(k, m1.selfSupers, WEAKER_BUT_SAFER);
    }

    public void writeObfuscationMap(File file) throws IOException {
        m1.saveMap(file);
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public void dumpMissingClasses() {
        Set<String> notFoundClasses = Util.getInstance().notFoundClasses;
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
    public abstract String obfMethodName(Desc descriptor);
    public abstract String obfFieldName(Desc descriptor);
}
