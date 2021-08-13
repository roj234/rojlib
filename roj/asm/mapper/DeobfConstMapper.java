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
import roj.asm.cst.*;
import roj.asm.mapper.util.Context;
import roj.asm.mapper.util.FirstCollection;
import roj.asm.mapper.util.MtDesc;
import roj.asm.tree.AccessData;
import roj.asm.tree.ConstantData;
import roj.asm.tree.attr.AttrBootstrapMethods;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.simple.FieldSimple;
import roj.asm.tree.simple.MethodSimple;
import roj.asm.type.ParamHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.collect.*;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.io.ZipUtil;
import roj.text.CharList;
import roj.ui.CmdUtil;
import roj.util.ByteReader;
import roj.util.Helpers;

import java.io.File;
import java.nio.file.NotDirectoryException;
import java.util.*;

/**
 * 第二代Class映射器
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/19 22:13
 */
public final class DeobfConstMapper extends Mapping {
    public static final int FILE_HEADER = 0xf0E72cEb;
    MyHashMap<MtDesc, String> fieldMap = new MyHashMap<>(1000);

    public MyHashMap<MtDesc, String> getFieldMap1() {
        return fieldMap;
    }

    /**
     * 重新写入ConstantData
     * 刷新常量池
     * Mark all fields as no remap
     * Skip bin fields remap
     */
    public static final boolean
            DEBUG = System.getProperty("roj.constMapper.debug", "f").charAt(0) == 't',
            SKIP_BIN_FIELDS = true,
            NO_FIELD_INHERIT = System.getProperty("roj.constMapper.noFieldInherit", "f").charAt(0) == 't';

    MyHashSet<MtDesc> libSkipFields = new MyHashSet<>();
    MyHashSet<MtDesc> libSkipMethods = new MyHashSet<>();
    MyHashMap<String, List<String>> libSupers = new MyHashMap<>();
    /**
     * Self(Input) data
     */
    FindMap<MtDesc, String> selfMethods;
    FindSet<MtDesc> selfSkipFields, selfSkipMethods;
    Map<String, List<String>> selfSupers;

    public DeobfConstMapper() {}

    // region 缓存
    // endregion

    public void clear() {
        classMap.clear();
        fieldMap.clear();
        methodMap.clear();
    }

    /**
     * Step 1
     */
    void parse(Context c) {
        ConstantData data = c.getData();

        ArrayList<String> list = new ArrayList<>();
        if(!"java/lang/Object".equals(data.parent)) {
            list.add(data.parent);
        }

        List<CstClass> itfs = data.interfaces;
        for (int i = 0; i < itfs.size(); i++) {
            CstClass itf = itfs.get(i);
            String name = itf.getValue().getString();
            if (name.endsWith("_NMR$FAKEIMPL")) {
                int s = name.lastIndexOf('/') + 1;
                int e = name.length() - "_NMR$FAKEIMPL".length();
                name = new CharList(e - s + 1).append(name, s, e - s).replace('_', '/').toString();
                if(DEBUG)
                    System.out.println("[NMR继承] " + data.name + " <= " + name);
            }
            list.add(name);
        }

        if(!list.isEmpty()) {
            selfSupers.put(data.name, list);
        }
    }

    // region Step 2 / 3 Utilities

    /**
     * 这个一定要有严格的顺序
     */
    Collection<String> resolveParentShared(String name) {
        return Util.shareFC(name, selfSupers.getOrDefault(name, Collections.emptyList()));
    }

    /**
     * addAllSupers + this
     * 也可以是lib
     */
    private Collection<String> resolveParent(String name) {
        return new FirstCollection<>(selfSupers.getOrDefault(name, Collections.emptyList()), name);
    }

    /**
     * Set reference name
     */
    private static void setRefName(ConstantData data, CstRef ref, String newName) {
       ref.desc(data.writer.getDesc(newName, ref.desc().getType().getString()));
    }

    // endregion
    // region Step 2

    /**
     * Step 2
     */
    final void mapSelf(Context ctx) {
        ConstantData data = ctx.getData();

        Collection<String> supers = resolveParent(data.name);

        mapSelfMethod(ctx, data.methods, data, supers);

        mapSelfField(ctx, data.fields, data, supers);
    }

    /**
     * Map: self replace super methods
     */
    final void mapSelfMethod(Context ctx, List<MethodSimple> methods, ConstantData data, Collection<String> supers) {
        MtDesc sp = Util.shareMD();

        for (int i = 0; i < methods.size(); i++) {
            MethodSimple method = methods.get(i);
            if (method.accesses.hasAny(AccessFlag.STATIC | AccessFlag.PRIVATE)) {
                if (!classMap.containsKey(data.name)) {
                    if (DEBUG) {
                        System.out.println("[2M-" + data.name + "-SF]: " + method.name.getString() + method.type.getString());
                    }
                    selfSkipMethods.add(new MtDesc(data.name, method.name.getString(), method.type.getString()));
                }
                continue;
            }

            sp.name = method.name.getString();
            sp.param = method.type.getString();

            for (String parent : supers) {
                sp.owner = parent;
                if (selfSkipMethods.contains(sp))
                    break;

                Map.Entry<MtDesc, String> entry = methodMap.find(sp);
                if (entry != null) {
                    FlagList flags = entry.getKey().flags;
                    if (flags.hasAny(AccessFlag.STATIC | AccessFlag.PRIVATE)) {
                        sp.owner = data.name;
                        selfSkipMethods.add(sp.copy());
                        if (DEBUG) {
                            System.out.println("[2M-" + data.name + "-ST/PR]: " + sp.name + sp.param);
                        }
                    } else if (flags.hasAny(AccessFlag.PUBLIC | AccessFlag.PROTECTED)) {
                        // can inherit
                        String newName = entry.getValue();
                        method.name = data.writer.getUtf(newName);
                        sp.owner = data.name;
                        selfMethods.put(sp.copy(), newName);
                        if (DEBUG)
                            System.out.println("[2M-" + data.name + "]: " + sp.name + sp.param + " => " + newName);
                    } else { // may extend
                        if (Util.arePackagesSame(data.name, parent)) {
                            String newName = entry.getValue();
                            method.name = data.writer.getUtf(newName);
                            sp.owner = data.name;
                            selfMethods.put(sp.copy(), newName);
                            if (DEBUG)
                                System.out.println("[2M-" + data.name + "-PP]: " + sp.name + sp.param + " => " + newName);
                        } else {
                            sp.owner = data.name;
                            selfSkipMethods.add(sp.copy());
                            if (DEBUG)
                                System.out.println("[2M-" + data.name + "-PP-NS]: " + sp.name + sp.param + ": " + supers);
                        }
                    }
                    break;
                }
            }
        }
    }

    /**
     * Map: self replace super fields...
     */
    final void mapSelfField(Context ctx, List<FieldSimple> fields, ConstantData data, Collection<String> supers) {
        // skip bin class
        if(SKIP_BIN_FIELDS)
            if(classMap.containsKey(data.name)) return;

        MtDesc sp = Util.shareMD();

        out:
        for (int i = 0; i < fields.size(); i++) {
            FieldSimple field = fields.get(i);
            if (field.accesses.hasAny(AccessFlag.STATIC | AccessFlag.PRIVATE)) {
                selfSkipFields.add(new MtDesc(data.name, field.name.getString(), field.type.getString()));
                continue;
            }

            sp.name = field.name.getString();
            sp.param = field.type.getString();

            // field只能覆盖...
            for (String parent : supers) {
                sp.owner = parent;
                if (selfSkipFields.contains(sp))
                    break out;

                Map.Entry<MtDesc, String> entry = fieldMap.find(sp);
                if (entry != null) {
                    // 还是时间与空间的问题
                    selfSkipFields.add(new MtDesc(data.name, field.name.getString(), field.type.getString()));
                    break out;
                }
            }
        }
    }

    // endregion
    // region Step 3

    /**
     * Step 3
     */
    final void mapConstant(Context ctx) {
        ConstantData data = ctx.getData();

        int i = 0;
        List<CstRef> cst = ctx.getFieldConstants();
        for (; i < cst.size(); i++) {
            mapField(ctx, data, cst.get(i));
        }

        cst = ctx.getMethodConstants();
        for (i = 0; i < cst.size(); i++) {
            mapMethod(ctx, data, cst.get(i));
        }

        List<CstDynamic> lmd = ctx.getInvokeDynamic();
        if(!lmd.isEmpty()) {
            Attribute std = (Attribute) data.attributes.getByName("BootstrapMethods");
            if (std == null)
                throw new IllegalArgumentException("有lambda却无BootstrapMethod at " + data.name);
            AttrBootstrapMethods bs = std instanceof AttrBootstrapMethods ? (AttrBootstrapMethods) std : new AttrBootstrapMethods(new ByteReader(std.getRawData()), data.cp);
            data.attributes.putByName(bs);

            for (i = 0; i < lmd.size(); i++) {
                mapLambda(bs, data, lmd.get(i));
            }
        }

        ctx.refresh();
    }

    /**
     * Map: lambda method name
     */
    final void mapLambda(AttrBootstrapMethods bs, ConstantData data, CstDynamic dyn) {
        AttrBootstrapMethods.BootstrapMethod ibm = bs.methods.get(dyn.bootstrapTableIndex);
        if(ibm == null) {
            throw new IllegalArgumentException("BootstrapMethod id 不存在: " + dyn.bootstrapTableIndex + " at class " + data.name);
        }

        CstMethodType mType = null;
        List<Constant> args = ibm.arguments;
        for (int i = 0; i < args.size(); i++) {
            Constant c = args.get(i);
            if (c.type() == CstType.METHOD_TYPE) {
                mType = (CstMethodType) c;
                break;
            }
        }

        if(mType == null) {
            throw new IllegalArgumentException("METHOD_TYPE argument not found in class " + data.name);
        }

        MtDesc sp = Util.shareMD();

        String methodName = sp.name = dyn.getDesc().getName().getString();
        String methodDesc = sp.param = mType.getValue().getString();

        String allDesc = dyn.getDesc().getType().getString();
        String methodClass = ParamHelper.getReturn(allDesc).owner;

        Collection<String> parents = resolveParentShared(methodClass);
        for(String parent : parents) {
            sp.owner = parent;
            String d = methodMap.get(sp);
            if (d != null) {
                if (DEBUG)
                    System.out.println("[3L-" + data.name + "]: " + methodClass + "." + methodName + " => " + d);

                dyn.setDesc(data.writer.getDesc(d, allDesc));
                return;
            }
        }

        if(DEBUG)
            System.out.println("[3L-" + data.name + "-NF]: " + methodClass + "." + methodName + methodDesc);
    }

    /**
     * Map: use other(non-self) methods
     */
    final void mapMethod(Context ctx, ConstantData data, CstRef ref) {
        MtDesc md = Util.shareMD().read(ref);

        /**
         * Fast path
         */
        String fpName = selfMethods.get(md);

        if(fpName != null) {
            if(DEBUG)
                System.out.println("[3M-" + data.name + "-FP]: " + (!md.owner.equals(data.name) ? md.owner + '.' : "") + md.name + md.param + " => " + fpName);
            setRefName(data, ref, fpName);
            return;
        }

        Collection<String> parents = resolveParentShared(ref.getClassName());
        for(String parent : parents) {
            md.owner = parent;

            if(selfSkipMethods.contains(md)) {
                if(DEBUG)
                    System.out.println("[3M-" + data.name + "-NT]: " + (!md.owner.equals(data.name) ? md.owner + '.' : "") + md.name + md.param);
                return;
            }

            Map.Entry<MtDesc, String> entry = methodMap.find(md);
            if(entry != null) {
                setRefName(data, ref, entry.getValue());
                if(DEBUG)
                    System.out.println("[3M-" + data.name + "]: " + (!md.owner.equals(data.name) ? md.owner + '.' : "") + md.name + md.param + " => " + entry.getValue());
                return;
            }
        }
    }

    /**
     * Map: use other(non-self) fields
     */
    final void mapField(Context ctx, ConstantData data, CstRef ref) {
        MtDesc fd = Util.shareMD().read(ref);

        /**
         * 没有Fast-path
         * 为啥？ 字段哪里会改名字呢...
         */

        Collection<String> parents = resolveParentShared(ref.getClassName());
        for(String parent : parents) {
            fd.owner = parent;

            if(selfSkipFields.contains(fd)) {
                if(DEBUG)
                    System.out.println("[3F-" + data.name + "-NT]: " + (!fd.owner.equals(data.name) ? fd.owner + '.' : "") + fd.name);
                return;
            }

            Map.Entry<MtDesc, String> entry = fieldMap.find(fd);
            if(entry != null) {
                if(DEBUG)
                    System.out.println("[3F-" + data.name + "]: " + (!fd.owner.equals(data.name) ? fd.owner + '.' : "") + fd.name + " => " + entry.getValue());
                setRefName(data, ref, entry.getValue());
                return;
            }
        }
    }

    // endregion

    // region 继承map


    final void generateSuperMap(File folder) {
        if(!folder.isDirectory()) {
            Helpers.throwAny(new NotDirectoryException(folder.getAbsolutePath()));
        }

        generateSuperMap(FileUtil.findAllFiles(folder));
    }

    final void generateSuperMap(List<File> files) {
        libSupers.clear();

        libSkipFields.clear();
        libSkipMethods.clear();
        if(files.isEmpty())
            return;

        Set<String> noMFClasses = new MyHashSet<>();
        Set<AccessData> libClasses = new MyHashSet<>();
        CharMap<FlagList> flagCache = new CharMap<>();

        ZipUtil.ICallback cb = (fileName, s) -> {
            byte[] bytes = IOUtil.readFully(s);
            if(bytes.length < 32)
                return;

            AccessData data;
            try {
                data = Parser.parseAccessDirect(bytes);
            } catch (Throwable e) {
                CmdUtil.warning("Class " + fileName + " is unable to read", e);
                return;
            }

            ArrayList<String> list = new ArrayList<>();
            if(!"java/lang/Object".equals(data.superName)) {
                list.add(data.superName);
            }
            List<String> itf = data.itf;
            for (int i = 0; i < itf.size(); i++) {
                String name = itf.get(i);
                if (name.endsWith("_NMR$FAKEIMPL")) {
                    int ss = name.lastIndexOf('/') + 1;
                    int e = name.length() - "_NMR$FAKEIMPL".length();
                    name = new CharList(e - ss + 1).append(name, ss, e - ss).replace('_', '/').toString();
                    if(DEBUG)
                        System.out.println("[NMR继承-Lib] " + data.name + " <= " + name);
                }
                list.add(name);
            }

            // 构建lib一极继承表
            if(!list.isEmpty())
                libSupers.put(data.name, list);


            List<AccessData.MOF> ent = data.methods;
            MtDesc m = new MtDesc(data.name, "", "");
            for (int i = 0; i < ent.size(); i++) {
                AccessData.MOF method = ent.get(i);
                if(method.name.startsWith("<") || (method.acc & (AccessFlag.STATIC | AccessFlag.PRIVATE | AccessFlag.FINAL)) != 0) {
                    continue;
                }
                m.name = method.name;
                m.param = method.desc;
                m.flags = flagCache.computeIfAbsent((char) method.acc, ConstMapper.fl);
                libSkipMethods.add(m);
                m = new MtDesc(data.name, "", "");
            }

            ent = data.fields;
            for (int i = 0; i < ent.size(); i++) {
                AccessData.MOF field = ent.get(i);
                if((field.acc & AccessFlag.PRIVATE) != 0) {
                    continue;
                }
                m.name = field.name;
                m.param = field.desc;
                m.flags = flagCache.computeIfAbsent((char) field.acc, ConstMapper.fl);
                libSkipFields.add(m);
                m = new MtDesc(data.name, "", "");
            }
        };

        for (int i = 0; i < files.size(); i++) {
            File fi = files.get(i);
            String f = fi.getName();
            if (!f.startsWith("[noread]") && (f.endsWith(".zip") || f.endsWith(".jar")))
                ZipUtil.unzip(fi, cb, (ze) -> ze.getName().endsWith(".class"));
        }

        makeInheritMap(libSupers, false);
    }


    public final void makeInheritMap(Map<String, List<String>> superMap, boolean removeNonInClass) {
        FastSetList<String> l = new FastSetList<>();

        List<String> toRemove = new ArrayList<>();

        // 从一级继承构建所有继承, note: 是所有输入
        for (Map.Entry<String, List<String>> entry : superMap.entrySet()) {
            if(entry.getValue().getClass() == FastSetList.class) continue; // done

            String name = entry.getKey();

            recursionLibSupers(superMap, l, entry.getValue());
            if(removeNonInClass) {
                for (int i = l.size() - 1; i >= 0; i--) {
                    if (!classMap.containsKey(l.get(i))) {
                        l.remove(i); // 删除不存在映射的爹
                    }
                }
            }

            if (!l.isEmpty()) { // 若不是空的，则更新一个
                entry.setValue(l);
                l = new FastSetList<>();
            } else {
                entry.setValue(null); // 迭代器并不支持remove
                toRemove.add(name);
            }
        }

        for (int i = 0; i < toRemove.size(); i++) {
            superMap.remove(toRemove.get(i));
        }
    }

    final void initSelfSuperMap() {
        MyHashMap<String, List<String>> tmp = new MyHashMap<>(libSupers);
        tmp.putAll(selfSupers);
        makeInheritMap(selfSupers = tmp, false);
    }

    static void recursionLibSupers(Map<String, List<String>> finder, FastSetList<String> target, Collection<String> currLevel) {
        target.addAll(currLevel);

        /**
         * excepted order:
         *     fatherclass fatheritf grandclass granditf, etc...
         */
        ArrayList<String> pending = new ArrayList<>();

        Collection<String> tmp;
        for (String s : currLevel) {
            if ((tmp = finder.get(s)) != null) {
                if (tmp.getClass() != FastSetList.class) {
                    pending.addAll(tmp);
                } else {
                    target.addAll(tmp);
                }
            }
        }

        if(!pending.isEmpty())
            recursionLibSupers(finder, target, pending);
    }

    // endregion
}