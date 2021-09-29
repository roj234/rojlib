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
import roj.asm.mapper.util.Desc;
import roj.asm.mapper.util.FirstIterator;
import roj.asm.mapper.util.MapperList;
import roj.asm.tree.AccessData;
import roj.asm.tree.AccessData.MOF;
import roj.asm.tree.ConstantData;
import roj.asm.tree.attr.AttrBootstrapMethods;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.simple.MethodSimple;
import roj.asm.type.ParamHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.collect.*;
import roj.concurrent.collect.ConcurrentFindHashMap;
import roj.concurrent.collect.ConcurrentFindHashSet;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.io.ZipUtil;
import roj.text.CharList;
import roj.text.StringPool;
import roj.ui.CmdUtil;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.NotDirectoryException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * 第二代Class映射器
 *
 * @author Roj234
 * @version 2.8
 * @since  2020/8/19 22:13
 */
public class ConstMapper extends Mapping {
    // 'CMPC': Const Remapper Cache
    public static final int FILE_HEADER = 0x634d5063;

    public static final boolean DEBUG = System.getProperty("roj.constMapper.debug", "f").charAt(0) == 't';
    
    /**
     * 来自依赖的数据
     */
    private final MyHashSet<Desc>                 libSkipped;
    private final MyHashMap<String, List<String>> libSupers;

    /**
     * 工作中数据
     */
    FindMap<Desc, String>     selfMethods;
    FindSet<Desc>             selfSkipped;
    Map<String, List<String>> selfSupers;

    public boolean isMappingConstant = true, checkFieldType = false;
    private final List<State> extendedSuperList = new SimpleList<>();

    public ConstMapper() {
        libSupers = new MyHashMap<>(128);
        libSkipped = new MyHashSet<>();
    }

    public ConstMapper(ConstMapper rmp) {
        this.classMap = rmp.classMap;
        this.fieldMap = rmp.fieldMap;
        this.methodMap = rmp.methodMap;
        this.libSupers = rmp.libSupers;
        this.libSkipped = rmp.libSkipped;
    }

    // region 缓存

    private void saveCache(long hash, File cache) throws IOException {
        StringPool pool = new StringPool();
        if(!checkFieldType)
            pool.add("");

        ByteWriter globalWriter = new ByteWriter(1000000);
        ByteWriter w = new ByteWriter(200000);
        // 原则上为了压缩可以再把name和desc做成constant

        w.writeVarInt(classMap.size(), false);
        for (Map.Entry<String, String> s : classMap.entrySet()) {
            pool.writeString(pool.writeString(w, s.getKey()),
                    s.getValue());
        }
        w.writeInt(FILE_HEADER);

        w.writeVarInt(fieldMap.size(), false);
        for (Map.Entry<Desc, String> s : fieldMap.entrySet()) {
            Desc descriptor = s.getKey();
            pool.writeString(pool.writeString(pool.writeString(pool.writeString(w,
                    descriptor.owner),
                    descriptor.name),
                    descriptor.param)
                    .writeShort(descriptor.flags.flag),
                    s.getValue());
        }
        w.writeInt(FILE_HEADER);

        w.writeVarInt(methodMap.size(), false);
        for (Map.Entry<Desc, String> s : methodMap.entrySet()) {
            Desc descriptor = s.getKey();
            pool.writeString(pool.writeString(pool.writeString(pool.writeString(w,
                    descriptor.owner),
                    descriptor.name),
                    descriptor.param)
                    .writeShort(descriptor.flags.flag),
                    s.getValue());
        }
        w.writeInt(FILE_HEADER);

        w.writeVarInt(libSupers.size(), false);
        for (Map.Entry<String, List<String>> s : libSupers.entrySet()) {
            pool.writeString(w, s.getKey());

            List<String> list = s.getValue();
            w.writeVarInt(list.size(), false);
            for (String c : list) {
                pool.writeString(w, c);
            }
        }
        w.writeInt(FILE_HEADER);

        w.writeVarInt(libSkipped.size(), false);
        for (Desc s : libSkipped) {
            pool.writeString(pool.writeString(pool.writeString(w, s.owner),
                    s.name), s.param);
        }

        globalWriter.writeInt(FILE_HEADER).writeLong(hash);
        pool.writePool(globalWriter);
        globalWriter.writeBytes(w);

        try (FileOutputStream fos = new FileOutputStream(cache)) {
            globalWriter.writeToStream(fos);
            fos.flush();
        }
    }

    private boolean readCache(long hash, File cache) throws IOException {
        if(cache.length() == 0) throw new FileNotFoundException();
        ByteReader r;
        try (FileInputStream fis = new FileInputStream(cache)) {
            r = new ByteReader(IOUtil.read(fis));
        }

        CharMap<FlagList> flagCache = new CharMap<>();

        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("file header");

        boolean readClassInheritanceMap = r.readLong() == hash;

        int len;
        StringPool pool = new StringPool(r);

        len = r.readVarInt(false);
        for (int i = 0; i < len; i++) {
            classMap.put(pool.readString(r), pool.readString(r));
        }
        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("class map");

        len = r.readVarInt(false);
        fieldMap.ensureCapacity(len);
        for (int i = 0; i < len; i++) {
            fieldMap.put(new Desc(pool.readString(r), pool.readString(r), pool.readString(r), flagCache.computeIfAbsent((char) r.readShort(), fl)), pool.readString(r));
        }
        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("field map");

        len = r.readVarInt(false);
        methodMap.ensureCapacity(len);
        for (int i = 0; i < len; i++) {
            methodMap.put(new Desc(pool.readString(r), pool.readString(r), pool.readString(r), flagCache.computeIfAbsent((char) r.readShort(), fl)), pool.readString(r));
        }
        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("method map");

        if(!readClassInheritanceMap)
            return false;

        len = r.readVarInt(false);
        for (int i = 0; i < len; i++) {
            String name = pool.readString(r);

            int len2 = r.readVarInt(false);
            MapperList list2 = new MapperList(len2);
            for (int j = 0; j < len2; j++) {
                list2.add(pool.readString(r));
            }
            list2._init_();

            libSupers.put(name, list2);
        }
        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("library super map");

        len = r.readVarInt(false);
        for (int i = 0; i < len; i++) {
            libSkipped.add(new Desc(pool.readString(r), pool.readString(r), pool.readString(r)));
        }

        return true;
    }
    static final IntFunction<FlagList> fl = FlagList::new;

    // endregion
    // region 映射

    /**
     * 全量
     */
    public void remap(boolean singleThread, List<Context> arr) {
        if(singleThread || arr.size() < 50 * Util.CPU) {
            initSelf(arr.size());

            Context curr = null;
            try {
                for (int i = 0; i < arr.size(); i++) {
                    parse(curr = arr.get(i));
                }

                initSelfSuperMap();

                for (int i = 0; i < arr.size(); i++) {
                    mapSelf(curr = arr.get(i));
                }

                for (int i = 0; i < arr.size(); i++) {
                    mapConstant(curr = arr.get(i));
                }

            } catch (Throwable e) {
                throw new RuntimeException("At parsing " + curr, e);
            }
        } else {
            selfSkipped = new ConcurrentFindHashSet<>(libSkipped);
            selfSupers = new ConcurrentHashMap<>(arr.size());
            selfMethods = new ConcurrentFindHashMap<>();

            List<List<Context>> splatted = new ArrayList<>();
            List<Context> tmp = new ArrayList<>();

            int splitThreshold = (arr.size() / Util.CPU) + 1;

            int i = 0;
            while (i < arr.size()) {
                tmp.add(arr.get(i++));
                if ((i % splitThreshold) == 0) {
                    splatted.add(tmp);
                    tmp = new ArrayList<>(splitThreshold);
                }
            }
            if(!tmp.isEmpty())
                splatted.add(tmp);

            if (DEBUG) {
                for (i = 0; i < splatted.size(); i++) {
                    System.out.println(splatted.get(i).size() + " => " + i);
                }
            }

            Util.concurrent("RV2WorkerP", this::parse, splatted);

            initSelfSuperMap();

            Util.concurrent("RV2WorkerR", this::mapSelf, splatted);

            Util.concurrent("RV2WorkerW", this::mapConstant, splatted);
        }
    }

    /**
     * 增量
     */
    public void remapIncrement(List<Context> arr) {
        if(selfSupers == null) {
            initSelf(arr.size());
        }

        Context curr = null;

        try {
            MyHashSet<String> modified = new MyHashSet<>();
            for (int i = 0; i < arr.size(); i++) {
                parse(curr = arr.get(i));
                modified.add(curr.getData().name);
            }

            Predicate<Desc> rem = key -> modified.contains(key.owner);
            selfMethods.keySet().removeIf(rem);
            selfSkipped.removeIf(rem);

            initSelfSuperMap();

            for (int i = 0; i < arr.size(); i++) {
                mapSelf(curr = arr.get(i));
            }

            for (int i = 0; i < arr.size(); i++) {
                mapConstant(curr = arr.get(i));
            }

        } catch (Throwable e) {
            throw new RuntimeException("At parsing " + curr, e);
        }
    }

    // endregion

    /**
     * Step 1 Prepare Super Mapping
     */
    final void parse(Context c) {
        ConstantData data = c.getData();

        ArrayList<String> list = new ArrayList<>();
        if(!"java/lang/Object".equals(data.parent)) {
            list.add(data.parent);
        }

        List<CstClass> itfs = data.interfaces;
        for (int i = 0; i < itfs.size(); i++) {
            CstClass itf = itfs.get(i);
            list.add(itf.getValue().getString());
        }

        if(!list.isEmpty()) {
            selfSupers.put(data.name, list);
        }
    }

    /**
     * Step 2 Map Inherited Methods
     */
    final void mapSelf(Context ctx) {
        ConstantData data = ctx.getData();

        List<String> parents = selfSupers.get(data.name);
        if(parents == null) return;

        Desc sp = Util.shareMD();

        List<MethodSimple> methods = data.methods;
        for (int i = 0; i < methods.size(); i++) {
            MethodSimple m = methods.get(i);
            // 这样的方法不是继承的
            if (m.accesses.hasAny(AccessFlag.STATIC | AccessFlag.PRIVATE)) {
                if (!classMap.containsKey(data.name)) {
                    if (DEBUG) {
                        System.out.println("[2M-" + data.name + "-SF]: " + m.name.getString() + m.type.getString());
                    }
                    // 可选操作：检测是否覆盖
                    selfSkipped.add(new Desc(data.name, m.name.getString(), m.type.getString()));
                }
                continue;
            }

            sp.name = m.name.getString();
            sp.param = m.type.getString();

            for (int j = 0; j < parents.size(); j++) {
                sp.owner = parents.get(j);

                Map.Entry<Desc, String> entry = methodMap.find(sp);
                if (entry != null) {
                    FlagList flags = entry.getKey().flags;
                    // 原方法无法被继承
                    if (flags.hasAny(AccessFlag.STATIC | AccessFlag.PRIVATE | AccessFlag.FINAL)) {
                        if (DEBUG)
                            System.out.println("[2M-" + data.name + "-NIH]: " + sp.owner + '.' + sp.name + sp.param);
                        sp.owner = data.name;
                        selfSkipped.add(sp.copy());
                    } else if (flags.hasAny(AccessFlag.PUBLIC | AccessFlag.PROTECTED)) {
                        flags = null;
                    } else { // 包相同可以继承
                        if (!Util.arePackagesSame(data.name, sp.owner)) {
                            if (DEBUG)
                                System.out.println("[2M-" + data.name + "-NSP]: " + sp.owner + '.' + sp.name + sp.param);
                            sp.owner = data.name;
                            selfSkipped.add(sp.copy());
                        } else {
                            flags = null;
                        }
                    }

                    if(flags == null) {
                        String newName = entry.getValue();
                        m.name = data.writer.getUtf(newName);
                        sp.owner = data.name;
                        selfMethods.put(sp.copy(), newName);
                    }
                    break;
                }
            }
        }
    }

    // region Step 3: Map Class Use Other

    /**
     * Step 3
     */
    protected final void mapConstant(Context ctx) {
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
    private void mapLambda(AttrBootstrapMethods bs, ConstantData data, CstDynamic dyn) {
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

        Desc sp = Util.shareMD();

        String methodName = sp.name = dyn.getDesc().getName().getString();
        String methodDesc = sp.param = mType.getValue().getString();

        String allDesc = dyn.getDesc().getType().getString();
        String methodClass = ParamHelper.getReturn(allDesc).owner;

        FirstIterator parents = resolveParentShared(methodClass);
        while (parents.hasNext()) {
            sp.owner = parents.next();
            String d = methodMap.get(sp);
            if (d != null) {
                dyn.setDesc(data.writer.getDesc(d, allDesc));
                return;
            }
        }
    }

    /**
     * Map: use other(non-self) methods
     */
    private void mapMethod(Context ctx, ConstantData data, CstRef ref) {
        Desc md = Util.shareMD().read(ref);

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

        FirstIterator parents = resolveParentShared(ref.getClassName());
        while (parents.hasNext()) {
            md.owner = parents.next();

            if(selfSkipped.contains(md)) {
                if(DEBUG)
                    System.out.println("[3M-" + data.name + "-NT]: " + (!md.owner.equals(data.name) ? md.owner + '.' : "") + md.name + md.param);
                break;
            }

            Map.Entry<Desc, String> entry = methodMap.find(md);
            if (entry != null) {
                setRefName(data, ref, entry.getValue());
                break;
            }
        }
    }

    /**
     * Map: use other(non-self) fields
     */
    void mapField(Context ctx, ConstantData data, CstRef ref) {
        Desc fd = Util.shareMD().read(ref);
        if(!checkFieldType) {
            fd.param = "";
        }

        /**
         * getstatic被傻逼javac继承过
         * 因为上面这点，才需要循环，否则一次get即可
         */

        FirstIterator parents = resolveParentShared(ref.getClassName());
        while (parents.hasNext()) {
            fd.owner = parents.next();

            if(selfSkipped.contains(fd)) {
                if(DEBUG)
                    System.out.println("[3F-" + data.name + "-NT]: " + (!fd.owner.equals(data.name) ? fd.owner + '.' : "") + fd.name);
                break;
            }

            Map.Entry<Desc, String> entry = fieldMap.find(fd);
            if (entry != null) {
                setRefName(data, ref, entry.getValue());
                break;
            }
        }
    }

    // endregion
    // region 读取libraries

    final void generateSuperMap(File folder) {
        if(!folder.isDirectory()) {
            Helpers.throwAny(new NotDirectoryException(folder.getAbsolutePath()));
        }

        generateSuperMap(FileUtil.findAllFiles(folder));
    }

    void generateSuperMap(List<File> files) {
        libSupers.clear();
        libSkipped.clear();

        if(files.isEmpty())
            return;

        // 更新FlagList的访问权限
        Map<String, Map<String, Desc>> flags = new MyHashMap<>();

        final Function<?, Map<?, ?>> nhm = Helpers.cast(Helpers.fnMyHashMap());
        for(Desc dsc : methodMap.keySet()) {
            flags.computeIfAbsent(dsc.owner, Helpers.cast(nhm)).put(dsc.name + '|' + dsc.param, dsc);
        }
        for(Desc dsc : fieldMap.keySet()) {
            flags.computeIfAbsent(dsc.owner, Helpers.cast(nhm)).put(dsc.name, dsc);
        }

        MyHashSet<AccessData> classes = new MyHashSet<>();
        MyHashSet<String> classMapOnly = new MyHashSet<>();
        CharMap<FlagList> flagCache = new CharMap<>();

        ZipUtil.ICallback cb = (fileName, s) -> {
            byte[] bytes = IOUtil.read(s);
            if(bytes.length < 32)
                return;

            AccessData data;
            try {
                data = Parser.parseAccessDirect(bytes);
            } catch (Throwable e) {
                CmdUtil.warning(fileName + " 无法读取", e);
                return;
            }

            ArrayList<String> list = new ArrayList<>();
            if(!"java/lang/Object".equals(data.superName)) {
                list.add(data.superName);
            }
            list.addAll(data.itf);

            // 构建lib一极继承表
            if(!list.isEmpty()) {
                libSupers.put(data.name, list);
                classes.add(data);
            }

            // 更新FlagList的访问权限
            Map<String, Desc> mds = flags.get(data.name);
            if(mds != null) {
                List<AccessData.MOF> mofs = data.methods;
                for (int i = 0; i < mofs.size(); i++) {
                    AccessData.MOF d = mofs.get(i);
                    Desc ent = mds.remove(d.name + '|' + d.desc);
                    if (ent != null)
                        ent.flags = flagCache.computeIfAbsent((char) d.acc, fl);
                }
                mofs = data.fields;
                for (int i = 0; i < mofs.size(); i++) {
                    AccessData.MOF d = mofs.get(i);
                    Desc ent = mds.remove(d.name);
                    if (ent != null)
                        ent.flags = flagCache.computeIfAbsent((char) d.acc, fl);
                }
                if(mds.isEmpty()) {
                    flags.remove(data.name);
                }
                return;
            }

            // 没有method和field, 类名没换
            if(data.name.equals(classMap.get(data.name))) {
                // 空接口
                if(!data.fields.isEmpty() || !data.methods.isEmpty()) {
                    List<AccessData.MOF> methods = data.methods;
                    for (int i = 0; i < methods.size(); i++) {
                        MOF m = methods.get(i);
                        if (!m.name.startsWith("<") &&
                0 == (m.acc & (AccessFlag.NATIVE | AccessFlag.VOLATILE_OR_BRIDGE | AccessFlag.SYNTHETIC))) {
                            // 不只有 <init> / <clinit> / native / bridge / synthetic
                            classMapOnly.add(data.name);
                            break;
                        }
                    }
                }
            }
        };

        for (int i = 0; i < files.size(); i++) {
            File fi = files.get(i);
            String f = fi.getName();
            if (!f.startsWith("[noread]") && (f.endsWith(".zip") || f.endsWith(".jar")))
                ZipUtil.unzip(fi, cb, (ze) -> ze.getName().endsWith(".class"));
        }

        if(!classMapOnly.isEmpty()) {
            System.out.print("[ConstMapper.Error] 一些映射只有class");
            if(DEBUG) {
                System.out.println(classMapOnly);
            } else {
                System.out.println(": " + classMapOnly.size());
            }
        }

        if(!flags.isEmpty()) {
            System.out.print("[ConstMapper.Error] 一些用户类缺失方法和/或字段");
            if(DEBUG) {
                CharList cl = new CharList();
                for (Map.Entry<String, Map<String, Desc>> entry : flags.entrySet()) {
                    cl.append(entry.getKey()).append(":\n  ");
                    for (Desc desc : entry.getValue().values()) {
                        cl.append(desc.name).append(' ').append(desc.param).append("\n  ");
                    }
                    cl.setIndex(cl.length() - 2);
                }
                System.out.println(cl);
            } else {
                System.out.println(": " + flags.size());
            }
        }

        makeInheritMap(libSupers, isMappingConstant ? classMap : null);

        // 下面这段的目的：用户类可能继承了映射中的方法
        // 并对这些方法做了修改，使得继承这些用户类的方法不能再映射
        // 1. 被标记了final
        // 2. static/private的方法，又出现了同名同参的方法
        //    Contract: static 无法继承为 non-static
        // 3. package-private 一旦被覆盖，就无法再继承 (只能cast再调用)
        //    A ~ /: package a
        //    B ~ A: public a
        //    C ~ B: 无法覆盖A的方法a
        //
        // 以及字段的"继承"
        //  垃圾java编译器对于static字段 A.x -> B
        //  会使用 getstatic B.x
        //  所以字段的访问需要筛选
        // 1. 子类拥有同名同类型字段: 筛选掉
        LongBitSet visited = new LongBitSet();
        Desc m = new Desc("", "", "");
        for(AccessData data : classes) {
            List<String> parents = libSupers.get(data.name);
            if(parents == null) continue;

            List<MOF> methods = data.methods;
            List<MOF> fields = data.fields;
            for (int i = 0; i < parents.size(); i++) {
                if(!classMap.containsKey(parents.get(i))) continue;

                m.owner = parents.get(i);
                for (int j = 0; j < methods.size(); j++) {
                    if(visited.contains(j)) continue;
                    MOF method = methods.get(j);
                    m.name = method.name;
                    m.param = method.desc;

                    Map.Entry<Desc, String> entry = methodMap.find(m);
                    if (entry != null) {
                        Desc md = entry.getKey();
                        if (md.flags == null)
                            throw new IllegalStateException("缺少元素 " + md);
                        if ((method.acc & (AccessFlag.FINAL | AccessFlag.STATIC)) != 0 ||
                            md.flags.hasAny(AccessFlag.PRIVATE | AccessFlag.STATIC)) {
                            // 第一类第二类
                            m.owner = data.name;
                            libSkipped.add(m);
                            visited.add(j);

                            m = new Desc(parents.get(i), "", "");
                        } else if (!md.flags.hasAny(AccessFlag.PROTECTED | AccessFlag.PUBLIC)) {
                            // 第三类
                            if (!Util.arePackagesSame(data.name, m.owner)) {
                                m.owner = data.name;
                                libSkipped.add(m);
                                visited.add(j);

                                m = new Desc(parents.get(i), "", "");
                            }
                        }
                    }
                }
                m.param = "";
                for (int j = 0; j < fields.size(); j++) {
                    if (visited.contains(j + methods.size())) continue;
                    MOF field = fields.get(j);
                    m.name = field.name;
                    if(checkFieldType) {
                        m.param = field.desc;
                    }

                    if (fieldMap.containsKey(m)) {
                        m.owner = data.name;
                        m = new Desc(parents.get(i), "", "");
                        libSkipped.add(m);
                    }
                }
            }
            visited.clear();
        }

        // 下策
        FlagList PUBLIC = flagCache.computeIfAbsent((char) AccessFlag.PUBLIC, fl);
        for(Desc descriptor : methodMap.keySet()) { // 将没有flag的全部填充为public
            if(descriptor.flags == null) {
                System.out.println("缺少元素: " + descriptor);
                descriptor.flags = PUBLIC;
            }
        }
        for(Desc descriptor : fieldMap.keySet()) {
            if(descriptor.flags == null) {
                System.out.println("缺少元素: " + descriptor);
                descriptor.flags = PUBLIC;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public final void initEnv(@Nullable Object map, @Nullable Object libPath, @Nullable File cacheFile, boolean reverse) throws IOException {
        clear();

        long hash = FILE_HEADER;
        if(cacheFile != null && libPath != null) {
            List<File> list;
            if(libPath instanceof File) {
                File folder = (File) libPath;
                if(!folder.isDirectory()) {
                    throw new IllegalArgumentException(new FileNotFoundException(folder.getAbsolutePath()));
                }

                list = FileUtil.findAllFiles(folder);
            } else
                list = (List<File>) libPath;
            hash = Util.libHash(list);
        }

        if(cacheFile == null || !cacheFile.exists()) {
            if(map instanceof File) loadMap((File) map, reverse);
            else if(map instanceof InputStream) loadMap((InputStream) map, reverse);
            else if(map instanceof ByteList) loadMap(((ByteList) map).asInputStream(), reverse);

            if(libPath != null) {
                if(libPath instanceof File) generateSuperMap((File) libPath);
                else generateSuperMap((List<File>) libPath);
            }
            if(cacheFile != null)
                saveCache(hash, cacheFile);
        } else {
            Boolean result = null;
            try {
                result = readCache(hash, cacheFile);
            } catch (Throwable e) {
                if(!(e instanceof FileNotFoundException)) {
                    if(!(e instanceof IllegalArgumentException)) {
                        CmdUtil.warning("缓存读取失败!", e);
                    } else {
                        CmdUtil.warning("缓存读取失败: " + e.getMessage());
                    }
                }

                clear();
            } finally {
                if(result == null) {
                    if (map instanceof File) loadMap((File) map, reverse);
                    else if(map instanceof InputStream) loadMap((InputStream) map, reverse);
                    else if(map instanceof ByteList) loadMap(((ByteList) map).asInputStream(), reverse);
                }
                if(libPath != null && result != Boolean.TRUE) {
                    if (libPath instanceof File) generateSuperMap((File) libPath);
                    else generateSuperMap((List<File>) libPath);
                }
                saveCache(hash, cacheFile);
            }
        }
    }

    // endregion
    // region 工具方法

    /**
     * 这个一定要有严格的顺序
     */
    final FirstIterator resolveParentShared(String name) {
        return Util.shareFC(name, selfSupers.getOrDefault(name, Collections.emptyList()));
    }

    /**
     * Set reference name
     */
    static void setRefName(ConstantData data, CstRef ref, String newName) {
        ref.desc(data.writer.getDesc(newName, ref.desc().getType().getString()));
    }

    /**
     * Mapper{A->B} .reverse()   =>>  Mapper{B->A}
     */
    public void reverse() {
        this.classMap = classMap.flip();
        MyHashMap<Desc, String> fieldMap1 = new MyHashMap<>(this.fieldMap.size());
        for (Map.Entry<Desc, String> entry : fieldMap.entrySet()) {
            Desc desc = entry.getKey();
            Desc target = new Desc(desc.owner, entry.getValue(), "", desc.flags);
            fieldMap1.put(target, desc.name);
        }
        this.fieldMap = fieldMap1;

        MyHashMap<Desc, String> methodMap1 = new MyHashMap<>(this.methodMap.size());
        for (Map.Entry<Desc, String> entry : methodMap.entrySet()) {
            Desc desc = entry.getKey();
            Desc target = new Desc(desc.owner, entry.getValue(), Util.transformMethodParam(classMap, desc.param), desc.flags);
            methodMap1.put(target, desc.name);
        }
        this.methodMap = methodMap1;
    }

    /**
     * Mapper{B->C} .extend ( Mapper{A->B} )   =>>  Mapper{A->C}
     */
    public void extend(Mapping from) {
        for(Map.Entry<String, String> entry : classMap.entrySet()) {
            classMap.put(entry.getKey(), from.classMap.getOrDefault(entry.getValue(), entry.getValue()));
        }
        Desc md = new Desc("", "", "");
        for(Map.Entry<Desc, String> entry : fieldMap.entrySet()) {
            Desc descriptor = entry.getKey();
            md.owner = Util.mapClassName(classMap, descriptor.owner);
            md.name = entry.getValue();
            entry.setValue(from.fieldMap.getOrDefault(md, entry.getValue()));
        }
        for(Map.Entry<Desc, String> entry : methodMap.entrySet()) {
            Desc descriptor = entry.getKey();
            md.owner = Util.mapClassName(classMap, descriptor.owner);
            md.name = entry.getValue();
            md.param = Util.transformMethodParam(classMap, descriptor.param);
            entry.setValue(from.methodMap.getOrDefault(md, entry.getValue()));
        }
    }

    public void clear() {
        classMap.clear();
        fieldMap.clear();
        methodMap.clear();
        libSupers.clear();
        libSkipped.clear();
    }

    final void initSelfSuperMap() {
        Map<String, List<String>> universe = new MyHashMap<>(this.libSupers);
        for (int i = 0; i < extendedSuperList.size(); i++) {
            universe.putAll(extendedSuperList.get(i).map);
        }
        universe.putAll(this.selfSupers); // replace lib class
        this.selfSupers = universe;

        makeInheritMap(universe, isMappingConstant ? classMap : null);
    }

    final void initSelf(int size) {
        selfSkipped = new MyHashSet<>(libSkipped);
        selfSupers = new MyHashMap<>(size);
        selfMethods = new MyHashMap<>();
    }

    public final State snapshot() {
        return snapshot(null);
    }

    public final State snapshot(State state) {
        if(state == null) {
            state = new State();
        }

        if(selfSupers == null)
            throw new IllegalStateException();
        if(selfSupers.getClass() == MyHashMap.class) {
            state.map.copyFrom((MyHashMap<String, List<String>>) this.selfSupers);
        } else {
            state.map.clear();
            state.map.putAll(selfSupers);
        }
        return state;
    }

    public final void state(State state) {
        if(selfSupers != null) {
            selfSupers.clear();
            selfSupers.putAll(state.map);
        } else {
            selfSupers = new MyHashMap<>(state.map);
        }
    }

    public final List<State> getExtendedSuperList() {
        return extendedSuperList;
    }

    public static final class State {
        private final MyHashMap<String, List<String>> map = new MyHashMap<>();
    }

    // endregion
}