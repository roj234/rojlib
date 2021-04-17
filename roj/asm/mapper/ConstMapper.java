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
import roj.asm.cst.CstClass;
import roj.asm.cst.CstDynamic;
import roj.asm.cst.CstRef;
import roj.asm.mapper.util.*;
import roj.asm.tree.*;
import roj.asm.tree.attr.AttrBootstrapMethods;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.ParamHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.collect.*;
import roj.concurrent.collect.ConcurrentFindHashMap;
import roj.concurrent.collect.ConcurrentFindHashSet;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.io.ZipUtil;
import roj.text.DottedStringPool;
import roj.ui.CmdUtil;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.NotDirectoryException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * 第二代Class映射器
 *
 * @author Roj234
 * @version 2.9
 * @since  2020/8/19 22:13
 */
public class ConstMapper extends Mapping {
    public static final int FLAG_CONSTANTLY_MAP = 1, FLAG_CHECK_SUB_IMPL = 2;

    // 'CMPC': Const Remapper Cache
    private static final int FILE_HEADER = 0x634d5063;

    private static final boolean DEBUG = false;

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

    public byte flag = FLAG_CONSTANTLY_MAP;
    private final List<State> extendedSuperList = new SimpleList<>();

    public ConstMapper() {
        this(false);
    }

    public ConstMapper(boolean checkFieldType) {
        super(checkFieldType);
        libSupers = new MyHashMap<>(128);
        libSkipped = new MyHashSet<>();
    }

    public ConstMapper(ConstMapper o) {
        super(o);
        this.libSupers = o.libSupers;
        this.libSkipped = o.libSkipped;
    }

    // region 缓存

    private void saveCache(long hash, File cache) throws IOException {
        DottedStringPool pool = new DottedStringPool('/');
        if(!checkFieldType)
            pool.add("");

        ByteWriter globalWriter = new ByteWriter(1000000);
        ByteWriter w = new ByteWriter(200000);
        // 原则上为了压缩可以再把name和desc做成constant

        w.writeVarInt(classMap.size(), false);
        for (Map.Entry<String, String> s : classMap.entrySet()) {
            pool.writeDlm(pool.writeDlm(w, s.getKey()),
                    s.getValue());
        }
        w.writeInt(FILE_HEADER);

        w.writeVarInt(fieldMap.size(), false);
        for (Map.Entry<Desc, String> s : fieldMap.entrySet()) {
            Desc descriptor = s.getKey();
            pool.writeString(pool.writeString(pool.writeString(pool.writeDlm(w,
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
            pool.writeString(pool.writeString(pool.writeString(pool.writeDlm(w,
                    descriptor.owner),
                    descriptor.name),
                    descriptor.param)
                    .writeShort(descriptor.flags.flag),
                    s.getValue());
        }
        w.writeInt(FILE_HEADER);

        w.writeVarInt(libSupers.size(), false);
        for (Map.Entry<String, List<String>> s : libSupers.entrySet()) {
            pool.writeDlm(w, s.getKey());

            List<String> list = s.getValue();
            w.writeVarInt(list.size(), false);
            for (String c : list) {
                pool.writeDlm(w, c);
            }
        }
        w.writeInt(FILE_HEADER);

        w.writeVarInt(libSkipped.size(), false);
        for (Desc s : libSkipped) {
            pool.writeString(pool.writeString(pool.writeDlm(w, s.owner),
                    s.name), s.param);
        }

        pool.writePool(globalWriter.writeInt(FILE_HEADER).writeLong(hash)).writeBytes(w);

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

        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("file header");

        boolean readClassInheritanceMap = r.readLong() == hash;

        DottedStringPool pool = new DottedStringPool(r, '/');

        int len = r.readVarInt(false);
        for (int i = 0; i < len; i++) {
            classMap.put(pool.readDlm(r), pool.readDlm(r));
        }
        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("class map");

        len = r.readVarInt(false);
        fieldMap.ensureCapacity(len);
        CharMap<FlagList> flagCache = new CharMap<>();
        for (int i = 0; i < len; i++) {
            fieldMap.put(new Desc(pool.readDlm(r), pool.readString(r), pool.readString(r), flagCache.computeIfAbsent((char) r.readShort(), fl)), pool.readString(r));
        }
        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("field map");

        len = r.readVarInt(false);
        methodMap.ensureCapacity(len);
        for (int i = 0; i < len; i++) {
            methodMap.put(new Desc(pool.readDlm(r), pool.readString(r), pool.readString(r), flagCache.computeIfAbsent((char) r.readShort(), fl)), pool.readString(r));
        }
        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("method map");

        if(!readClassInheritanceMap)
            return false;

        len = r.readVarInt(false);
        for (int i = 0; i < len; i++) {
            String name = pool.readDlm(r);

            int len2 = r.readVarInt(false);
            MapperList list2 = new MapperList(len2);
            for (int j = 0; j < len2; j++) {
                list2.add(pool.readDlm(r));
            }
            list2._init_();

            libSupers.put(name, list2);
        }
        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("library super map");

        len = r.readVarInt(false);
        for (int i = 0; i < len; i++) {
            libSkipped.add(new Desc(pool.readDlm(r), pool.readString(r), pool.readString(r)));
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
                    S1_parse(curr = arr.get(i));
                }

                initSelfSuperMap();
                if((flag & FLAG_CHECK_SUB_IMPL) != 0)
                    S15_ignoreSubImpl(arr);

                for (int i = 0; i < arr.size(); i++) {
                    S2_mapSelf(curr = arr.get(i));
                }

                for (int i = 0; i < arr.size(); i++) {
                    S3_mapConstant(curr = arr.get(i));
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

            Util.concurrent("RV2WorkerP", this::S1_parse, splatted);

            initSelfSuperMap();
            if((flag & FLAG_CHECK_SUB_IMPL) != 0)
                S15_ignoreSubImpl(arr);

            Util.concurrent("RV2WorkerR", this::S2_mapSelf, splatted);

            Util.concurrent("RV2WorkerW", this::S3_mapConstant, splatted);
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
                S1_parse(curr = arr.get(i));
                modified.add(curr.getData().name);
            }

            Predicate<Desc> rem = key -> modified.contains(key.owner);
            selfMethods.keySet().removeIf(rem);
            selfSkipped.removeIf(rem);
            if((flag & FLAG_CHECK_SUB_IMPL) != 0)
                S15_ignoreSubImpl(arr);

            initSelfSuperMap();

            for (int i = 0; i < arr.size(); i++) {
                S2_mapSelf(curr = arr.get(i));
            }

            for (int i = 0; i < arr.size(); i++) {
                S3_mapConstant(curr = arr.get(i));
            }

        } catch (Throwable e) {
            throw new RuntimeException("At parsing " + curr, e);
        }
    }

    // endregion

    /**
     * Step 1 Prepare Super Mapping
     */
    public final void S1_parse(Context c) {
        ConstantData data = c.getData();

        ArrayList<String> list = new ArrayList<>();
        if(!"java/lang/Object".equals(data.parent)) {
            list.ensureCapacity(4);
            list.add(data.parent);
        }

        List<CstClass> itfs = data.interfaces;
        list.ensureCapacity(itfs.size() + 1);
        for (int i = 0; i < itfs.size(); i++) {
            CstClass itf = itfs.get(i);
            list.add(itf.getValue().getString());
        }

        if(!list.isEmpty()) {
            selfSupers.put(data.name, list);
        }
    }

    /**
     * Step 1.5 (Optional) Find and Ignore SubImpl types
     */
    public final List<Desc> S15_ignoreSubImpl(List<Context> ctxs) {
        List<Desc> filled = new ArrayList<>();
        MyHashMap<String, IClass> methods = new MyHashMap<>(ctxs.size());
        MyHashSet<SubImpl> subs = Util.getInstance().gatherSubImplements(ctxs, this, methods);
        for (SubImpl impl : subs) {
            String targetName = null, foundClass = null;
            Desc desc = new Desc("", impl.type.name, impl.type.type);
            for (String owner : impl.owners) {
                desc.owner = owner;
                String name1 = methodMap.get(desc);
                if (name1 != null) {
                    if(targetName == null) {
                        foundClass = owner;
                        targetName = name1;
                    } else if (!targetName.equals(name1)) {
                        throw new IllegalStateException(impl + ": 同参方法映射后名称不同");
                    }
                }
            }
            if (targetName != null) {
                for (String owner : impl.owners) {
                    if(owner.equals(foundClass)) continue;
                    desc.owner = owner;
                    if (null == methodMap.putIfAbsent(desc, targetName)) {
                        filled.add(desc);
                        List<? extends MoFNode> methods1 = methods.get(desc.owner).methods();
                        for (int i = 0; i < methods1.size(); i++) {
                            MoFNode m = methods1.get(i);
                            if (desc.param.equals(m.rawDesc()) && desc.name.equals(m.name())) {
                                desc.flags = m.accessFlag();
                                break;
                            }
                        }
                        if (desc.flags == null) {
                            throw new IllegalArgumentException(desc + ": 无法适配权限");
                        }
                        desc = desc.copy();
                    }
                }
            }
        }
        return filled;
    }

    /**
     * Step 2 Map Inherited Methods
     */
    public final void S2_mapSelf(Context ctx) {
        ConstantData data = ctx.getData();
        data.normalize();

        List<String> parents = selfSupers.getOrDefault(data.name, Collections.emptyList());

        Desc sp = Util.shareMD();

        List<MethodSimple> methods = data.methods;
        for (int i = 0; i < methods.size(); i++) {
            MethodSimple m = methods.get(i);

            sp.owner = data.name;
            sp.name = m.name.getString();
            sp.param = m.type.getString();

            int j = 0;
            while (true) {
                Map.Entry<Desc, String> entry = methodMap.find(sp);
                if (entry != null) {
                    FlagList flags = entry.getKey().flags;
                    // 原方法无法被继承
                    if (flags.hasAny(AccessFlag.STATIC | AccessFlag.PRIVATE | AccessFlag.FINAL)) {
                        if (DEBUG)
                            System.out.println(
                                    "[2M-" + data.name + "-I]: " + sp.owner + '.' + sp.name + sp.param);
                        // noinspection all
                        if (sp.owner != data.name) {
                            sp.owner = data.name;
                            selfSkipped.add(sp.copy());
                        }
                    } else if (flags.hasAny(AccessFlag.PUBLIC | AccessFlag.PROTECTED)) {
                        flags = null;
                    } else { // 包相同可以继承
                        if (!Util.arePackagesSame(data.name, sp.owner)) {
                            if (DEBUG)
                                System.out.println(
                                        "[2M-" + data.name + "-P]: " + sp.owner + '.' + sp.name + sp.param);
                            // noinspection all
                            if (sp.owner != data.name) {
                                sp.owner = data.name;
                                selfSkipped.add(sp.copy());
                            }
                        } else {
                            flags = null;
                        }
                    }

                    if(flags == null) {
                        String newName = entry.getValue();
                        m.name = data.cp.getUtf(newName);
                        sp.owner = data.name;
                        selfMethods.put(sp.copy(), newName);
                    }
                    break;
                }

                if (j == parents.size()) break;
                sp.owner = parents.get(j++);
            }
        }

        // 警告: field不能继承, 默认不改name
        List<FieldSimple> fields = data.fields;
        for (int i = 0; i < fields.size(); i++) {
            FieldSimple f = fields.get(i);

            sp.owner = data.name;
            sp.name = f.name.getString();
            sp.param = checkFieldType ? f.type.getString() : "";

            int j = 0;
            while (true) {
                if (fieldMap.containsKey(sp)) {
                    if (DEBUG)
                        System.out.println("[2F-" + data.name + "]: " + sp.owner + '.' + sp.name);
                    // noinspection all
                    if (sp.owner != data.name) {
                        sp.owner = data.name;
                        selfSkipped.add(sp.copy());
                    }
                    break;
                }

                if (j == parents.size()) break;
                sp.owner = parents.get(j++);
            }
        }
    }

    /**
     * Step 2.5 (Optional) Field Name <br>
     *     Also implemented in CodeMapper
     */
    public final void S25_mapFieldName(ConstantData data) {
        Desc md = Util.shareMD();
        md.owner = data.name;
        List<FieldSimple> fields = data.fields;
        for (int i = 0; i < fields.size(); i++) {
            FieldSimple field = fields.get(i);

            md.name = field.name.getString();
            md.param = checkFieldType ? field.type.getString()  :"";

            String newName = fieldMap.get(md);
            if(newName != null) {
                if (DEBUG) {
                    System.out.println("[F25-" + data.name + "]: " + field.name + " => " + newName);
                }
                field.name = data.cp.getUtf(newName);
            }
        }
    }

    // region Step 3: Map Class Use Other

    /**
     * Step 3
     */
    public final void S3_mapConstant(Context ctx) {
        ConstantData data = ctx.getData();

        int i = 0;
        List<CstRef> cst = ctx.getFieldConstants();
        for (; i < cst.size(); i++) {
            mapRef(data, cst.get(i), false);
        }

        cst = ctx.getMethodConstants();
        for (i = 0; i < cst.size(); i++) {
            mapRef(data, cst.get(i), true);
        }

        List<CstDynamic> lmd = ctx.getInvokeDynamic();
        if(!lmd.isEmpty()) {
            Attribute std = (Attribute) data.attributes.getByName("BootstrapMethods");
            if (std == null)
                throw new IllegalArgumentException("有lambda却无BootstrapMethod at " + data.name);
            AttrBootstrapMethods bs = std instanceof AttrBootstrapMethods ? (AttrBootstrapMethods) std : new AttrBootstrapMethods(Parser.reader(std), data.cp);
            data.attributes.putByName(bs);

            for (i = 0; i < lmd.size(); i++) {
                mapLambda(bs, data, lmd.get(i));
            }
        }
    }

    /**
     * Map: lambda method name
     */
    private void mapLambda(AttrBootstrapMethods bs, ConstantData data, CstDynamic dyn) {
        if(dyn.tableIdx > bs.methods.size()) {
            throw new IllegalArgumentException("BootstrapMethod id 不存在: " + (int)dyn.tableIdx + " at class " + data.name);
        }
        AttrBootstrapMethods.BootstrapMethod ibm = bs.methods.get(dyn.tableIdx);

        Desc md = Util.shareMD();

        md.name = dyn.getDesc().getName().getString();
        // FP: init / clinit
        if(md.name.startsWith("<")) return;
        md.param = ibm.getMethodType();

        String allDesc = dyn.getDesc().getType().getString();
        md.owner = ParamHelper.getReturn(allDesc).owner;

        List<String> parents = selfSupers.getOrDefault(md.owner, Collections.emptyList());
        int i = 0;
        while (true) {
            String name = methodMap.get(md);
            if (name != null) {
                dyn.setDesc(data.cp.getDesc(name, allDesc));
                return;
            }

            if(selfSkipped.contains(md)) {
                if(DEBUG)
                    System.out.println("[3L-" + data.name + "]: " + (!md.owner.equals(data.name) ? md.owner + '.' : "") + md.name + md.param);
                break;
            }

            if(i == parents.size()) break;
            md.owner = parents.get(i++);
        }
    }

    /**
     * Map: use other(non-self) methods/fields
     */
    private void mapRef(ConstantData data, CstRef ref, boolean method) {
        Desc md = Util.shareMD().read(ref);

        if(method) {
            // FP: init / clinit
            if(ref.desc().getName().getString().startsWith("<")) return;
            /**
             * Fast path
             */
            String fpName = selfMethods.get(md);

            if (fpName != null) {
                setRefName(data, ref, fpName);
                return;
            }
        } else {
            if(!checkFieldType) {
                md.param = "";
            }
        }

        MyHashMap<Desc, String> map = method ? methodMap : fieldMap;
        List<String> parents = selfSupers.getOrDefault(ref.getClassName(), Collections.emptyList());
        int i = 0;
        while (true) {
            String name = map.get(md);
            if (name != null) {
                setRefName(data, ref, name);
                break;
            }

            if(selfSkipped.contains(md)) {
                if(DEBUG)
                    System.out.println("[3" + (method ? "M" : "F") + "-" + data.name + "]: " + (!md.owner.equals(data.name) ? md.owner + '.' : "") + md.name + md.param);
                break;
            }

            if(i == parents.size()) break;
            md.owner = parents.get(i++);
        }
    }

    // endregion
    // region 读取libraries

    public final void loadLibraries(File folder) {
        if(!folder.isDirectory()) {
            Helpers.throwAny(new NotDirectoryException(folder.getAbsolutePath()));
        }

        loadLibraries(FileUtil.findAllFiles(folder), null);
    }

    public void loadLibraries(List<?> files) {
        loadLibraries(files, null);
    }

    public void loadLibraries(List<?> files, AccessFallbackHandler fallback) {
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

        if(fallback == null)
            fallback = new WarningHandler();

        MyHashSet<IClass> classes = new MyHashSet<>();

        FileReader cb = new FileReader(classes, flags, fallback);

        for (int i = 0; i < files.size(); i++) {
            Object o = files.get(i);
            if(o instanceof File) {
                File fi = (File) o;
                String f = fi.getName();
                if (!f.startsWith("[noread]") && (f.endsWith(".zip") || f.endsWith(".jar")))
                    ZipUtil.unzip(fi, cb, (ze) -> ze.getName().endsWith(".class"));
            } else {
                List<Context> ctx = Helpers.cast(o);
                for (int j = 0; j < ctx.size(); j++) {
                    cb.read(ctx.get(j).getData());
                }
            }
        }

        for (String k : cb.emptyClasses) {
            classMap.remove(k);
        }

        makeInheritMap(libSupers, (flag & FLAG_CONSTANTLY_MAP) != 0 ? classMap : null);

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
        for(IClass data : classes) {
            List<String> parents = libSupers.get(data.className());
            if(parents == null) continue;

            List<? extends MoFNode> methods = data.methods();
            List<? extends MoFNode> fields = data.fields();
            for (int i = 0; i < parents.size(); i++) {
                if(!classMap.containsKey(parents.get(i))) continue;

                m.owner = parents.get(i);
                for (int j = 0; j < methods.size(); j++) {
                    if(visited.contains(j)) continue;
                    MoFNode method = methods.get(j);
                    m.name = method.name();
                    m.param = method.rawDesc();

                    Map.Entry<Desc, String> entry = methodMap.find(m);
                    if (entry != null) {
                        Desc md = entry.getKey();
                        if (md.flags == null)
                            throw new IllegalStateException("缺少元素 " + md);
                        if ((method.accessFlag2() & (AccessFlag.FINAL | AccessFlag.STATIC)) != 0 ||
                            md.flags.hasAny(AccessFlag.PRIVATE | AccessFlag.STATIC)) {
                            // 第一类第二类
                            m.owner = data.className();
                            libSkipped.add(m);
                            visited.add(j);

                            m = new Desc(parents.get(i), "");
                        } else if (!md.flags.hasAny(AccessFlag.PROTECTED | AccessFlag.PUBLIC)) {
                            // 第三类
                            if (!Util.arePackagesSame(data.className(), m.owner)) {
                                m.owner = data.className();
                                libSkipped.add(m);
                                visited.add(j);

                                m = new Desc(parents.get(i), "");
                            }
                        }
                    }
                }
                m.param = "";
                for (int j = 0; j < fields.size(); j++) {
                    if (visited.contains(j + methods.size())) continue;
                    MoFNode field = fields.get(j);
                    m.name = field.name();
                    if(checkFieldType) {
                        m.param = field.rawDesc();
                    }

                    if (fieldMap.containsKey(m)) {
                        m.owner = data.className();
                        if(DEBUG)
                            System.out.println("Dbg: skip inherit " + m);
                        libSkipped.add(m);
                        m = new Desc(parents.get(i), "");
                    }
                }
            }
            visited.clear();
        }

        fallback.handleUnmatched(flags, cb.flagCache);
    }

    @SuppressWarnings("unchecked")
    public final void initEnv(@Nullable Object map, @Nullable Object libPath, @Nullable File cacheFile, boolean reverse) throws IOException {
        clear();

        long hash = FILE_HEADER;
        if(cacheFile != null && libPath != null) {
            List<?> list;
            if(libPath instanceof File) {
                File folder = (File) libPath;
                if(!folder.isDirectory()) {
                    throw new IllegalArgumentException(new FileNotFoundException(folder.getAbsolutePath()));
                }

                list = FileUtil.findAllFiles(folder);
            } else
                list = (List<Object>) libPath;
            hash = Util.libHash(list);
        }

        if(cacheFile == null || !cacheFile.exists()) {
            if(map instanceof File) loadMap((File) map, reverse);
            else if(map instanceof InputStream) loadMap((InputStream) map, reverse);
            else if(map instanceof ByteList) loadMap(((ByteList) map).asInputStream(), reverse);

            if(libPath != null) {
                if(libPath instanceof File) loadLibraries((File) libPath);
                else loadLibraries((List<Object>) libPath);
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
                    if (libPath instanceof File) loadLibraries((File) libPath);
                    else loadLibraries((List<Object>) libPath);
                }
                saveCache(hash, cacheFile);
            }
        }
    }

    // endregion
    // region 工具方法

    /**
     * Set reference name
     */
    static void setRefName(ConstantData data, CstRef ref, String newName) {
        ref.desc(data.cp.getDesc(newName, ref.desc().getType().getString()));
    }

    /**
     * Mapper{A->B} .reverse()   =>>  Mapper{B->A}
     */
    public void reverse() {
        this.classMap = classMap.flip();
        MyHashMap<Desc, String> fieldMap1 = new MyHashMap<>(this.fieldMap.size());
        for (Map.Entry<Desc, String> entry : fieldMap.entrySet()) {
            Desc desc = entry.getKey();
            Desc target = new Desc(desc.owner, entry.getValue(), desc.param, desc.flags);
            String param = Util.transformFieldType(classMap, desc.param);
            if(param != null)
                target.param = param;
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
            String param = Util.transformFieldType(classMap, md.param = descriptor.param);
            if(param != null)
                md.param = param;
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

    public final void initSelfSuperMap() {
        Map<String, List<String>> universe = new MyHashMap<>(this.libSupers);
        for (int i = 0; i < extendedSuperList.size(); i++) {
            universe.putAll(extendedSuperList.get(i).map);
        }
        universe.putAll(this.selfSupers); // replace lib class
        this.selfSupers = universe;

        makeInheritMap(universe, (flag & (FLAG_CONSTANTLY_MAP | FLAG_CHECK_SUB_IMPL)) == FLAG_CONSTANTLY_MAP ? classMap : null);
    }

    public final void initSelf(int size) {
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

    private static final class WarningHandler implements AccessFallbackHandler {
        public WarningHandler() {}

        @Override
        public boolean fillAccessFlags(Desc desc, CharMap<FlagList> interner) {
            return false;
        }

        @Override
        public void handleUnmatched(Map<String, Map<String, Desc>> rest, CharMap<FlagList> interner) {
            if(!rest.isEmpty()) {
                System.out.println("[CM-Warn] 缺少元素: ");
                for (Map.Entry<String, Map<String, Desc>> entry : rest.entrySet()) {
                    System.out.print(entry.getKey());
                    System.out.print(": ");
                    Iterator<Desc> itr = entry.getValue().values().iterator();
                    while (true) {
                        Desc desc = itr.next();
                        System.out.print(desc.name);
                        if(!desc.param.isEmpty()) {
                            System.out.print(' ');
                            System.out.print(desc.param);
                        }
                        if(!itr.hasNext()) {
                            System.out.println();
                            break;
                        }
                        System.out.print("  ");
                    }
                }
                FlagList PUBLIC = interner.computeIfAbsent(AccessFlag.PUBLIC, fl);
                for (Map<String, Desc> map : rest.values()) {
                    for (Desc desc : map.values()) { // 将没有flag的全部填充为public
                        desc.flags = PUBLIC;
                    }
                }
            }
        }
    }

    private final class FileReader implements ZipUtil.ICallback {
        private final MyHashSet<IClass>              classes;
        private final Map<String, Map<String, Desc>> flags;
        private final AccessFallbackHandler          fallback;
        final         MyHashSet<String>              emptyClasses = new MyHashSet<>();
        final CharMap<FlagList> flagCache = new CharMap<>();

        public FileReader(MyHashSet<IClass> classes, Map<String, Map<String, Desc>> flags, AccessFallbackHandler fallback) {
            this.classes = classes;
            this.flags = flags;
            this.fallback = fallback;
        }

        @Override
        public void onRead(String fileName, InputStream s) throws IOException {
            byte[] bytes = IOUtil.read(s);
            if (bytes.length < 32)
                return;

            AccessData data;
            try {
                data = Parser.parseAccessDirect(bytes);
            } catch (Throwable e) {
                CmdUtil.warning(fileName + " 无法读取", e);
                return;
            }
            read(data);
        }

        void read(IClass data) {
            if(!libSupers.containsKey(data.className())) {
                ArrayList<String> list = new ArrayList<>();
                if (!"java/lang/Object".equals(data.parentName())) {
                    list.add(data.parentName());
                }
                list.addAll(data.interfaces());

                // 构建lib一极继承表
                if (!list.isEmpty()) {
                    libSupers.put(data.className(), list);
                    if (!classMap.containsKey(data.className()))
                        classes.add(data);
                }
            }

            emptyClasses.remove(data.className());
            // 更新FlagList的访问权限
            Map<String, Desc> mds = flags.get(data.className());
            if (mds != null) {
                List<? extends MoFNode> mofs = data.methods();
                for (int i = 0; i < mofs.size(); i++) {
                    MoFNode d = mofs.get(i);
                    Desc ent = mds.remove(d.name() + '|' + d.rawDesc());
                    if (ent != null)
                        ent.flags = flagCache.computeIfAbsent(d.accessFlag2(), fl);
                }
                mofs = data.fields();
                for (int i = 0; i < mofs.size(); i++) {
                    MoFNode d = mofs.get(i);
                    Desc ent = mds.remove(d.name());
                    if (ent != null)
                        ent.flags = flagCache.computeIfAbsent(d.accessFlag2(), fl);
                }

                // noinspection all
                for (Iterator<Map.Entry<String, Desc>> itr = mds.entrySet().iterator(); itr.hasNext(); ) {
                    Map.Entry<String, Desc> entry = itr.next();
                    if (fallback.fillAccessFlags(entry.getValue(), flagCache)) {
                        itr.remove();
                    }
                }

                if (mds.isEmpty()) {
                    flags.remove(data.className());
                }
                return;
            }

            // 没有method和field, 类名没换
            if (data.className().equals(classMap.get(data.className()))) {
                // 空接口
                if (!data.fields().isEmpty() || !data.methods().isEmpty()) {
                    List<? extends MoFNode> methods = data.methods();
                    for (int i = 0; i < methods.size(); i++) {
                        MoFNode m = methods.get(i);
                        if (!m.name().startsWith("<") &&
                                0 == (m.accessFlag2() & (AccessFlag.NATIVE | AccessFlag.VOLATILE_OR_BRIDGE | AccessFlag.SYNTHETIC))) {
                            // 不只有 <init> / <clinit> / native / bridge / synthetic
                            return;
                        }
                    }
                }
                emptyClasses.add(data.className());
            }
        }
    }

    // endregion
}