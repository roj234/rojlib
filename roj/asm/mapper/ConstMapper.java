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
import roj.asm.mapper.util.FlDesc;
import roj.asm.mapper.util.MtDesc;
import roj.asm.struct.AccessData;
import roj.asm.struct.ConstantData;
import roj.asm.struct.attr.AttrBootstrapMethods;
import roj.asm.struct.attr.Attribute;
import roj.asm.struct.simple.FieldSimple;
import roj.asm.struct.simple.MethodSimple;
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
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * 第二代Class映射器
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/19 22:13
 */
public final class ConstMapper extends Mapping {
    public static final int FILE_HEADER = 0xf0E72cEb;

    // todo 多线程
    private static final boolean CONCURRENT = false;

    /**
     * 重新写入ConstantData
     * 刷新常量池
     * Mark all fields as no remap
     * Skip bin fields remap
     */
    public static final boolean
            DEBUG = System.getProperty("fmd.debugRemap", "f").charAt(0) == 't',
            REFRESH = false,
            SKIP_BIN_FIELDS = true,
            NO_FIELD_INHERIT = System.getProperty("fmd.noFieldInherit", "f").charAt(0) == 't';
    
    /**
     * Library data
     */
    MyHashSet<FlDesc> libSkipFields = new MyHashSet<>();
    MyHashSet<MtDesc> libSkipMethods = new MyHashSet<>();
    MyHashMap<String, Collection<String>> libSupers;

    /**
     * Self(Input) data
     */
    FindMap<MtDesc, String> selfMethods;
    FindSet<FlDesc> selfSkipFields;
    FindSet<MtDesc> selfSkipMethods;
    Map<String, Collection<String>> selfSupers;

    public ConstMapper() {
        libSupers = new MyHashMap<>(128);
    }

    /**
     * 继承数据
     */
    public ConstMapper(ConstMapper rmp) {
        this.classMap = rmp.classMap;
        this.fieldMap = rmp.fieldMap;
        this.methodMap = rmp.methodMap;
        this.libSupers = rmp.libSupers;
        this.libSkipFields = rmp.libSkipFields;
        this.libSkipMethods = rmp.libSkipMethods;
    }

    /**
     * 复制数据
     */
    public ConstMapper copyFrom(ConstMapper rmp) {
        clear();
        this.classMap.putAll(rmp.classMap);
        this.fieldMap.putAll(rmp.fieldMap);
        this.methodMap.putAll(rmp.methodMap);
        this.libSupers.putAll(rmp.libSupers);
        this.libSkipFields.addAll(rmp.libSkipFields);
        this.libSkipMethods.addAll(rmp.libSkipMethods);
        return this;
    }

    public void clearLibs() {
        libSkipFields.clear();
        libSkipMethods.clear();
    }

    public void reverse() {
        this.classMap = classMap.flip();
        MyHashMap<FlDesc, String> fieldMap1 = new MyHashMap<>(this.fieldMap.size());
        for (Map.Entry<FlDesc, String> entry : fieldMap.entrySet()) {
            FlDesc desc = entry.getKey();
            FlDesc target = new FlDesc(entry.getValue(), desc.owner, desc.flags);
            fieldMap1.put(target, entry.getValue());
        }
        this.fieldMap = fieldMap1;

        MyHashMap<MtDesc, String> methodMap1 = new MyHashMap<>(this.methodMap.size());
        for (Map.Entry<MtDesc, String> entry : methodMap.entrySet()) {
            MtDesc desc = entry.getKey();
            MtDesc target = new MtDesc(entry.getValue(), desc.owner, Util.transformMethodParam(classMap, desc.param), desc.flags);
            methodMap1.put(target, entry.getValue());
        }
        this.methodMap = methodMap1;
    }

    // region 缓存

    private void saveCache(long hash, File cache) throws IOException {
        StringPool pool = new StringPool();

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
        for (Map.Entry<FlDesc, String> s : fieldMap.entrySet()) {
            pool.writeString(pool.writeString(pool.writeString(w, s.getKey().owner),
                    s.getKey().name)
                            .writeShort(s.getKey().flags.flag),
                    s.getValue());
        }
        w.writeInt(FILE_HEADER);

        w.writeVarInt(methodMap.size(), false);
        for (Map.Entry<MtDesc, String> s : methodMap.entrySet()) {
            MtDesc descriptor = s.getKey();
            pool.writeString(pool.writeString(pool.writeString(pool.writeString(w,
                    descriptor.owner),
                    descriptor.name),
                    descriptor.param)
                    .writeShort(descriptor.flags.flag),
                    s.getValue());
        }
        w.writeInt(FILE_HEADER);

        w.writeVarInt(libSupers.size(), false);
        for (Map.Entry<String, Collection<String>> s : libSupers.entrySet()) {
            pool.writeString(w, s.getKey());

            Collection<String> list = s.getValue();
            w.writeVarInt(list.size(), false);
            for (String c : list) {
                pool.writeString(w, c);
            }
        }
        w.writeInt(FILE_HEADER);

        w.writeVarInt(libSkipFields.size(), false);
        for (FlDesc s : libSkipFields) {
            pool.writeString(pool.writeString(w, s.owner),
                    s.name);
        }
        w.writeInt(FILE_HEADER);

        w.writeVarInt(libSkipMethods.size(), false);
        for (MtDesc s : libSkipMethods) {
            pool.writeString(pool.writeString(pool.writeString(w, s.owner),
                    s.name), s.param);
        }

        globalWriter.writeLong(hash);
        pool.writePool(globalWriter);
        globalWriter.writeBytes(w);

        try (FileOutputStream fos = new FileOutputStream(cache)) {
            globalWriter.writeToStream(fos);
            fos.flush();
        }
    }

    private void readCache(long hash, File cache) throws IOException {
        if(cache.length() == 0) throw new FileNotFoundException();
        ByteReader r;
        try (FileInputStream fis = new FileInputStream(cache)) {
            r = new ByteReader(IOUtil.readFully(fis));
        }

        IntMap<FlagList> flagCache = new IntMap<>();

        if(r.readLong() != hash)
            throw new IllegalArgumentException("缓存过期");

        int len;
        StringPool pool = new StringPool(r);

        len = r.readVarInt(false);
        //classMap.ensureCapacity(len);
        for (int i = 0; i < len; i++) {
            classMap.put(pool.readString(r), pool.readString(r));
        }
        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("class map");

        len = r.readVarInt(false);
        fieldMap.ensureCapacity(len);
        for (int i = 0; i < len; i++) {
            fieldMap.put(new FlDesc(pool.readString(r), pool.readString(r), flagCache.computeIfAbsent(r.readShort(), fl)), pool.readString(r));
        }
        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("field map");

        len = r.readVarInt(false);
        methodMap.ensureCapacity(len);
        for (int i = 0; i < len; i++) {
            methodMap.put(new MtDesc(pool.readString(r), pool.readString(r), pool.readString(r), flagCache.computeIfAbsent(r.readShort(), fl)), pool.readString(r));
        }
        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("method map");

        len = r.readVarInt(false);
        for (int i = 0; i < len; i++) {
            String name = pool.readString(r);

            int len2 = r.readVarInt(false);
            FastSetList<String> list2 = new FastSetList<>(len2);
            for (int j = 0; j < len2; j++) {
                list2.add(pool.readString(r));
            }

            libSupers.put(name, list2);
        }
        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("library super map");

        len = r.readVarInt(false);
        for (int i = 0; i < len; i++) {
            libSkipFields.add(new FlDesc(pool.readString(r), pool.readString(r)));
        }

        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("library field map");

        len = r.readVarInt(false);
        for (int i = 0; i < len; i++) {
            libSkipMethods.add(new MtDesc(pool.readString(r), pool.readString(r), pool.readString(r)));
        }
    }
    static final IntFunction<FlagList> fl = FlagList::new;

    // endregion

    /**
     * Remap
     */
    public List<Context> remap(@Nonnull Map<String, InputStream> classes, boolean singleThread) {
        List<Context> arr = Util.prepareContexts(classes);

        remap(singleThread, arr);

        return arr;
    }

    public void remap(boolean singleThread, List<Context> arr) {
        if(selfSupers != null) {
            selfSkipFields.clear();
            selfSkipMethods.clear();
            selfSupers.clear();
            selfMethods.clear();
        }

        if(singleThread || arr.size() < 50 * Util.CPU || !CONCURRENT) {
            selfSkipFields = new MyHashSet<>(libSkipFields);
            selfSkipMethods = new MyHashSet<>(libSkipMethods);
            selfSupers = new MyHashMap<>(arr.size());
            selfMethods = new MyHashMap<>();

            Context curr = null;
            try {
                for (int i = 0; i < arr.size(); i++) {
                    parse(curr = arr.get(i));
                }

                initSelfSuperMap();
                if(DEBUG)
                    System.out.println("[1-SuperMap]: " + selfSupers);

                for (int i = 0; i < arr.size(); i++) {
                    mapSelf(curr = arr.get(i));
                }

                for (int i = 0; i < arr.size(); i++) {
                    mapConstant(curr = arr.get(i));
                }

            } catch (Throwable e) {
                throw new RuntimeException("At parsing " + curr.getName(), e);
            }
        } else {
            selfSkipFields = new ConcurrentFindHashSet<>(libSkipFields);
            selfSkipMethods = new ConcurrentFindHashSet<>(libSkipMethods);
            selfSupers = new ConcurrentHashMap<>(arr.size());
            selfMethods = new ConcurrentFindHashMap<>();

            System.out.println("警告: ConcurrentFindSet/Map还处于试验阶段!");

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

            Util.waitfor("RV2WorkerP", this::parse, splatted);

            initSelfSuperMap();

            Util.waitfor("RV2WorkerR", this::mapSelf, splatted);

            Util.waitfor("RV2WorkerW", this::mapConstant, splatted);
        }
    }

    public void remapIncrement(List<Context> arr) {
        if(selfSupers == null)
            throw new IllegalStateException("Did not parse previously");

        selfSkipFields = new MyHashSet<>(libSkipFields);
        selfSkipMethods = new MyHashSet<>(libSkipMethods);
        selfMethods = new MyHashMap<>();

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
            throw new RuntimeException("At parsing " + curr.getName(), e);
        }
    }

    public void clear() {
        classMap.clear();
        fieldMap.clear();
        methodMap.clear();
        libSupers.clear();
        libSkipMethods.clear();
        libSkipFields.clear();
    }

    public State snapshot() {
        State state = new State();
        state.selfSupers = state.selfSupers.getClass() == MyHashMap.class ? new MyHashMap<>(selfSupers) : new ConcurrentHashMap<>(selfSupers);
        return state;
    }

    public void state(State state) {
        selfSupers = state.selfSupers.getClass() == MyHashMap.class ? new MyHashMap<>(state.selfSupers) : new ConcurrentHashMap<>(state.selfSupers);
    }

    /**
     * Step 1
     */
    private void parse(Context c) {
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
    private Collection<String> resolveParentShared(String name) {
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
            if (method.accesses.has(AccessFlag.STATIC | AccessFlag.PRIVATE)) {
                if (!classMap.containsKey(data.name))
                    selfSkipMethods.add(new MtDesc(data.name, method.name.getString(), method.type.getString()));
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
                    if (flags.has(AccessFlag.STATIC | AccessFlag.PRIVATE)) {
                        sp.owner = data.name;
                        selfSkipMethods.add(sp.copy());
                        if (DEBUG) {
                            System.out.println("[2M-" + data.name + "-ST/PR]: " + sp.name + sp.param);
                        }
                    } else if (flags.has(AccessFlag.PUBLIC | AccessFlag.PROTECTED)) {
                        // can inherit
                        String newName = entry.getValue();
                        method.name = data.writer.getUtf(newName);
                        sp.owner = data.name;
                        selfMethods.put(sp.copy(), newName);
                        if (DEBUG)
                            System.out.println("[2M-" + data.name + "]: " + sp.name + sp.param + " => " + newName);
                    } else { // may extend
                        if (Util.isPackageSame(data.name, parent)) {
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

        FlDesc sp = Util.shareFD();

        out:
        for (int i = 0; i < fields.size(); i++) {
            FieldSimple field = fields.get(i);
            if (field.accesses.has(AccessFlag.STATIC | AccessFlag.PRIVATE)) {
                selfSkipFields.add(new FlDesc(data.name, field.name.getString()));
                // 自己覆盖别人的成了static，注：这个情况确实可以跳过，如果有继承的非static方法是不能通过编译的
                continue;
            }

            sp.name = field.name.getString();

            // field只能覆盖...
            for (String parent : supers) {
                sp.owner = parent;
                if (selfSkipFields.contains(sp))
                    break out; // todo 这里可以么

                Map.Entry<FlDesc, String> entry = fieldMap.find(sp);
                if (entry != null) {
                    // 还是时间与空间的问题
                    selfSkipFields.add(new FlDesc(data.name, field.name.getString()));
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
        for (i = 0; i < lmd.size(); i++) {
            mapLambda(ctx, data, lmd.get(i));
        }

        ctx.refresh();
    }

    /**
     * Map: lambda method name
     */
    final void mapLambda(Context ctx, ConstantData data, CstDynamic dyn) {
        AttrBootstrapMethods bs = ctx.bsmCache;
        if(bs == null) {
            Attribute std = (Attribute) data.attributes.getByName("BootstrapMethods");
            if(std == null)
                throw new IllegalArgumentException("有lambda却无BootstrapMethod at " + data.name);
            ctx.bsmCache = bs = new AttrBootstrapMethods(new ByteReader(std.getRawData()), data.cp);
        }

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
        FlDesc fd = Util.shareFD().read(ref);

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

            Map.Entry<FlDesc, String> entry = fieldMap.find(fd);
            if(entry != null) {
                if(DEBUG)
                    System.out.println("[3F-" + data.name + "]: " + (!fd.owner.equals(data.name) ? fd.owner + '.' : "") + fd.name + " => " + entry.getValue());
                setRefName(data, ref, entry.getValue());
                return;
            }
        }
    }

    // endregion
    // region 读取libraries

    final void generateSuperMap(File folder) {
        if(!folder.isDirectory()) {
            throw new IllegalArgumentException(new FileNotFoundException(folder.getAbsolutePath()));
        }

        generateSuperMap(FileUtil.findAllFiles(folder));
    }

    final void generateSuperMap(List<File> files) {
        libSupers.clear();

        libSkipFields.clear();
        libSkipMethods.clear();
        if(files.isEmpty())
            return;

        // 更新FlagList的访问权限
        Map<String, Map<String, MtDesc>> c2mMap = new MyHashMap<>();
        Map<String, Map<String, FlDesc>> c2fMap = new MyHashMap<>();

        final Function<?, Map<?, ?>> nhm = Helpers.cast(Helpers.fnMyHashMap());
        for(MtDesc descriptor : methodMap.keySet()) {
            c2mMap.computeIfAbsent(descriptor.owner, Helpers.cast(nhm)).put(descriptor.name + '|' + descriptor.param, descriptor);
        }
        for(FlDesc descriptor : fieldMap.keySet()) {
            c2fMap.computeIfAbsent(descriptor.owner, Helpers.cast(nhm)).put(descriptor.name, descriptor);
        }

        Set<String> poorClasses = new MyHashSet<>();
        Set<AccessData> libClasses = new MyHashSet<>();

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

            // 标记：不存在于mapping中，即lib
            boolean isLib = true;

            // 更新FlagList的访问权限
            Map<String, MtDesc> descriptors = c2mMap.remove(data.name);
            if(descriptors != null) {
                List<AccessData.D> methods = data.methods;
                for (int i = 0; i < methods.size(); i++) {
                    AccessData.D d = methods.get(i);
                    MtDesc md = descriptors.get(d.name + '|' + d.desc);
                    if (md != null)
                        md.flags = data.getFlagFor(d);
                }
                isLib = false;
            }

            Map<String, FlDesc> descriptors2 = c2fMap.remove(data.name);
            if(descriptors2 != null) {
                List<AccessData.D> methods = data.methods;
                for (int i = 0; i < methods.size(); i++) {
                    AccessData.D d = methods.get(i);
                    FlDesc fd = descriptors2.get(d.name);
                    if (fd != null) {
                        fd.flags = data.getFlagFor(d);
                    }
                }
                isLib = false;
            }

            if(isLib) {
                if(classMap.containsKey(data.name)) { // 有class，却没有method和field
                    if(!data.fields.isEmpty() || !data.methods.isEmpty()) { // 空接口
                        boolean isAllInit = true;
                        List<AccessData.D> methods = data.methods;
                        for (int i = 0; i < methods.size(); i++) {
                            if (!methods.get(i).name.equals("<init>")) {
                                isAllInit = false; // 只带有构造器的类
                                break;
                            }
                        }
                        if(!isAllInit)
                            poorClasses.add(data.name);
                    }
                } else {
                    libClasses.add(data);
                }
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
                    System.out.println("[NMR继承-Lib] " + data.name + " <= " + name);
                }
                list.add(name);
            }

            // 构建lib一极继承表
            if(!list.isEmpty())
                libSupers.put(data.name, list);
        };

        for (int i = 0; i < files.size(); i++) {
            File fi = files.get(i);
            String f = fi.getName();
            if (!f.startsWith("[noread]"))
                ZipUtil.unzip(fi, cb, (ze) -> ze.getName().endsWith(".class"));
        }

        makeInheritMap(libSupers, true);

        for(AccessData data : libClasses) {
            Collection<String> parents = libSupers.get(data.name);
            if(parents == null)
                continue;

            // 根据下面的注释可以看到，这个的功能和fields的for一样
            MtDesc m = new MtDesc("", "", "");

            List<AccessData.D> methods = data.methods;
            for (int i = 0; i < methods.size(); i++) {
                AccessData.D method = methods.get(i);
                m.name = method.name;
                m.param = method.desc;

                for (String parent : parents) {
                    m.owner = parent;
                    Map.Entry<MtDesc, String> entry = methodMap.find(m);
                    if (entry != null) {
                        MtDesc methodDesc = entry.getKey();
                        if (methodDesc.flags == null)
                            throw new RuntimeException("缺少必须的文件: " + parent + " 的class");
                        if (methodDesc.flags.has(AccessFlag.STATIC | AccessFlag.PRIVATE)) {
                            // 无法继承
                            libSkipMethods.add(m);

                            m = new MtDesc("", "", "");
                        } else if (methodDesc.flags.has(AccessFlag.PROTECTED | AccessFlag.PUBLIC)) {
                            // 子类或者同一个package
                            // or 公共

                            // (A) methodMap.put(m, entry.getValue());
                            // 上面这段代码没有任何作用，奇怪,这是不会替换key，value也没有修改，我傻了？
                        } else {
                            // only 同一个package
                            if (!Util.isPackageSame(data.name, parent)) {
                                // 非同package不能调用
                                libSkipMethods.add(m);

                                m = new MtDesc("", "", "");
                            }// else {
                            // methodMap.put(m, entry.getValue());
                            // 同上 (A)
                            //}
                        }

                        // 那么就可以这样
                        break;
                    }
                }
            }

            if(NO_FIELD_INHERIT) {
                List<AccessData.D> fields = data.fields;// 只是为了减小文件体积
                for (int i = 0; i < fields.size(); i++) {
                    AccessData.D field = fields.get(i);
                    FlDesc f = new FlDesc("", field.name);
                    for (String parent : parents) {
                        f.owner = parent;
                        Map.Entry<FlDesc, String> entry = fieldMap.find(f);
                        if (entry != null) {
                            // 只是为了减小文件体积
                            f.owner = data.name;
                            libSkipFields.add(f);
                            break;
                        }
                    }
                }
            }
        }

        FlagList PUBLIC = new FlagList(AccessFlag.PUBLIC);
        for(MtDesc descriptor : methodMap.keySet()) { // 将没有flag的全部填充为public// or private?
            if(descriptor.flags == null)
                descriptor.flags = PUBLIC;
        }
        for(FlDesc descriptor : fieldMap.keySet()) {
            if(descriptor.flags == null)
                descriptor.flags = PUBLIC;
        }

        if(!c2fMap.isEmpty() || !c2mMap.isEmpty()) {
            Set<String> set = new MyHashSet<>(c2fMap.keySet());
            set.addAll(c2mMap.keySet());
            if(DEBUG || set.size() < 100) {
                System.out.print("[ERROR] Missing Deps: ");
                System.out.println(set);
            } else {
                System.out.println(set.size() + " classes is missing!");
            }
        }
        if(!poorClasses.isEmpty()) {
            if(DEBUG || poorClasses.size() < 100) {
                System.out.print("[ERROR] Class Only: ");
                System.out.println(poorClasses);
            } else {
                System.out.println(poorClasses.size() + " 个类只有C映射没有MF映射!");
            }
        }
    }

    @SuppressWarnings("unchecked")
    public final void initEnv(@Nonnull Object map, @Nullable Object libPath, @Nullable File cacheFile, boolean reverse, boolean save) throws IOException {
        clear();

        long hash = FILE_HEADER;
        if(libPath != null) {
            List<File> list;
            if(libPath instanceof File) {
                File folder = (File) libPath;
                if(!folder.isDirectory()) {
                    throw new IllegalArgumentException(new FileNotFoundException(folder.getAbsolutePath()));
                }

                list = FileUtil.findAllFiles(folder);
            } else
                list = (List<File>) libPath;
            hash = libHash(list);
        }

        if(cacheFile == null || !cacheFile.exists()) {
            if(map instanceof File)
                loadFromSrg((File) map, reverse);
            else
                loadFromSrg((InputStream) map, reverse);
            if(libPath != null) {
                if(libPath instanceof File)
                    generateSuperMap((File) libPath);
                else
                    generateSuperMap((List<File>) libPath);
            }
            if(save)
                saveCache(hash, cacheFile);
        } else {
            try {
                readCache(hash, cacheFile);
            } catch (Throwable e) {
                if(!(e instanceof FileNotFoundException)) {
                    if(!(e instanceof IllegalArgumentException)) {
                        CmdUtil.warning("缓存读取失败!", e);
                    } else {
                        CmdUtil.warning("缓存读取失败: " + e.getMessage());
                    }
                }

                clear();

                if(map instanceof File)
                    loadFromSrg((File) map, reverse);
                else
                    loadFromSrg((InputStream) map, reverse);
                if(libPath != null) {
                    if(libPath instanceof File)
                        generateSuperMap((File) libPath);
                    else
                        generateSuperMap((List<File>) libPath);
                }
                saveCache(hash, cacheFile);
            }
        }
    }

    private static long libHash(List<File> list) {
        long hash = 0;
        for (int i = 0; i < list.size(); i++) {
            File f = list.get(i);
            hash = 31 * hash + f.getName().hashCode();
            hash = 31 * hash + (f.length() & 262143);
            hash ^= f.lastModified();
        }

        return hash;
    }

    // endregion

    /**
     * Transfer data
     */
    public void extend(Mapping from) {
        for(Map.Entry<String, String> entry : classMap.entrySet()) {
            classMap.put(entry.getKey(), from.classMap.getOrDefault(entry.getValue(), entry.getValue()));
        }
        FlDesc fd = new FlDesc("", "");
        for(Map.Entry<FlDesc, String> entry : fieldMap.entrySet()) {
            FlDesc descriptor = entry.getKey();
            fd.owner = Util.mapClassName(classMap, descriptor.owner);
            fd.name = entry.getValue();
            entry.setValue(from.fieldMap.getOrDefault(fd, entry.getValue()));
        }
        MtDesc md = new MtDesc("", "", "");
        for(Map.Entry<MtDesc, String> entry : methodMap.entrySet()) {
            MtDesc descriptor = entry.getKey();
            md.owner = Util.mapClassName(classMap, descriptor.owner);
            md.name = entry.getValue();
            md.param = Util.transformMethodParam(classMap, descriptor.param);
            entry.setValue(from.methodMap.getOrDefault(md, entry.getValue()));
        }
    }

    // region 继承map

    public final void makeInheritMap(Map<String, Collection<String>> superMap, boolean removeNonInClass) {
        FastSetList<String> l = new FastSetList<>();

        List<String> toRemove = new ArrayList<>();

        // 从一级继承构建所有继承, note: 是所有输入
        for (Map.Entry<String, Collection<String>> entry : superMap.entrySet()) {
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
        Map<String, Collection<String>> selfSuperMap = new MyHashMap<>(this.libSupers);
        selfSuperMap.putAll(this.selfSupers); // replace lib class
        this.selfSupers = selfSuperMap;

        makeInheritMap(selfSuperMap, true);
    }

    static void recursionLibSupers(Map<String, Collection<String>> finder, FastSetList<String> target, Collection<String> currLevel) {
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

    public static final class State {
        private Map<String, Collection<String>> selfSupers;
    }

    // endregion
}