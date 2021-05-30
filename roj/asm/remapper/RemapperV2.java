package roj.asm.remapper;

import roj.asm.Parser;
import roj.asm.cst.*;
import roj.asm.remapper.util.Context;
import roj.asm.remapper.util.FirstCollection;
import roj.asm.remapper.util.FlDesc;
import roj.asm.remapper.util.MtDesc;
import roj.asm.struct.AccessData;
import roj.asm.struct.ConstantData;
import roj.asm.struct.attr.AttrBootstrapMethods;
import roj.asm.struct.attr.Attribute;
import roj.asm.struct.simple.FieldSimple;
import roj.asm.struct.simple.MethodSimple;
import roj.asm.util.AccessFlag;
import roj.asm.util.FlagList;
import roj.asm.util.type.ParamHelper;
import roj.collect.*;
import roj.concurrent.collect.ConcurrentFindHashMap;
import roj.concurrent.collect.ConcurrentFindHashSet;
import roj.io.IOUtil;
import roj.io.ZipUtil;
import roj.text.CharList;
import roj.text.StringPool;
import roj.ui.CmdUtil;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.Helpers;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static roj.asm.remapper.util.Context.LAMBDA_INDEX;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/19 22:13
 */
public final class RemapperV2 extends IRemapper {
    // todo 多线程能用了吗? 不能, 偶尔有问题
    private static final boolean ENABLE_CONCURRENT = false;
    private static final boolean DISABLE_FIELD_INHERITANCE = System.getProperty("fmd.noFieldInherit", "false").equalsIgnoreCase("true");
    
    /**
     * SrgMap data
     */
    FindSet<FlDesc> libNotRemapFields = new MyHashSet<>();
    FindSet<MtDesc> libNotRemapMethods = new MyHashSet<>();

    /**
     * Private data
     */
    FindMap<MtDesc, String> selfMethods;
    FindSet<FlDesc> notRemapFields;
    FindSet<MtDesc> notRemapMethods;

    /**
     * 多线程时Context 写回 byte[]
     * false: 保留ConstantData
     */
    public static final boolean
            REWRITE = false, REFRESH = System.getProperty("fmd.remapRefresh", "false").equalsIgnoreCase("true"),
            KILL_ALL_FIELDS = false,
            SKIP_BIN_FIELDS = true;

    public RemapperV2() {}

    /**
     * 继承数据
     */
    public RemapperV2(RemapperV2 rmp) {
        super(rmp);
        this.libNotRemapFields = rmp.libNotRemapFields;
        this.libNotRemapMethods = rmp.libNotRemapMethods;
    }

    public void clearLibs() {
        libNotRemapFields.clear();
        libNotRemapMethods.clear();
    }

    final void generateSuperMap(List<File> files) {
        librarySupers.clear();

        libNotRemapFields.clear();
        libNotRemapMethods.clear();
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
                librarySupers.put(data.name, list);
        };

        for (int i = 0; i < files.size(); i++) {
            File fi = files.get(i);
            String f = fi.getName();
            if (!f.startsWith("[noread]"))
                ZipUtil.unzip(fi, cb, (ze) -> ze.getName().endsWith(".class"));
        }

        makeInheritMap(librarySupers);

        for(AccessData data : libClasses) {
            Collection<String> parents = librarySupers.get(data.name);
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
                            libNotRemapMethods.add(m);

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
                                libNotRemapMethods.add(m);

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

            if(DISABLE_FIELD_INHERITANCE) {
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
                            libNotRemapFields.add(f);
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

    final void saveCache(File cache) throws IOException {
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

        w.writeVarInt(librarySupers.size(), false);
        for (Map.Entry<String, Collection<String>> s : librarySupers.entrySet()) {
            pool.writeString(w, s.getKey());

            Collection<String> list = s.getValue();
            w.writeVarInt(list.size(), false);
            for (String c : list) {
                pool.writeString(w, c);
            }
        }
        w.writeInt(FILE_HEADER);

        w.writeVarInt(libNotRemapFields.size(), false);
        for (FlDesc s : libNotRemapFields) {
            pool.writeString(pool.writeString(w, s.owner),
                    s.name);
        }
        w.writeInt(FILE_HEADER);

        w.writeVarInt(libNotRemapMethods.size(), false);
        for (MtDesc s : libNotRemapMethods) {
            pool.writeString(pool.writeString(pool.writeString(w, s.owner),
                    s.name), s.param);
        }

        globalWriter.writeInt(FILE_HEADER);
        pool.writePool(globalWriter);
        globalWriter.writeBytes(w);

        try (FileOutputStream fos = new FileOutputStream(cache)) {
            globalWriter.writeToStream(fos);
            fos.flush();
        }
    }

    final void readCache(File cache) throws IOException {
        if(cache.length() == 0) throw new FileNotFoundException();
        ByteReader r;
        try (FileInputStream fis = new FileInputStream(cache)) {
            r = new ByteReader(IOUtil.readFully(fis));
        }

        IntMap<FlagList> map = new IntMap<>();

        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("文件头错误");

        int len;
        StringPool pool = new StringPool(r);

        len = r.readVarInt(false);
        classMap.ensureCapacity(len);
        for (int i = 0; i < len; i++) {
            classMap.put(pool.readString(r), pool.readString(r));
        }
        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("class map");

        len = r.readVarInt(false);
        fieldMap.ensureCapacity(len);
        for (int i = 0; i < len; i++) {
            fieldMap.put(new FlDesc(pool.readString(r), pool.readString(r), commonFlag(map, r.readShort())), pool.readString(r));
        }
        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("field map");

        len = r.readVarInt(false);
        methodMap.ensureCapacity(len);
        for (int i = 0; i < len; i++) {
            methodMap.put(new MtDesc(pool.readString(r), pool.readString(r), pool.readString(r), commonFlag(map, r.readShort())), pool.readString(r));
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

            librarySupers.put(name, list2);
        }
        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("library super map");

        len = r.readVarInt(false);
        for (int i = 0; i < len; i++) {
            libNotRemapFields.add(new FlDesc(pool.readString(r), pool.readString(r)));
        }

        if(r.readInt() != FILE_HEADER)
            throw new IllegalArgumentException("library field map");

        len = r.readVarInt(false);
        for (int i = 0; i < len; i++) {
            libNotRemapMethods.add(new MtDesc(pool.readString(r), pool.readString(r), pool.readString(r)));
        }
    }

    private static FlagList commonFlag(IntMap<FlagList> map, int i) {
        return map.computeIfAbsent(i, () -> new FlagList(i));
    }

    /**
     * 复制数据
     */
    public RemapperV2 readMapping(RemapperV2 rmp) {
        clear();
        this.classMap.putAll(rmp.classMap);
        this.fieldMap.putAll(rmp.fieldMap);
        this.methodMap.putAll(rmp.methodMap);
        this.librarySupers.putAll(rmp.librarySupers);
        this.libNotRemapFields.addAll(rmp.libNotRemapFields);
        this.libNotRemapMethods.addAll(rmp.libNotRemapMethods);
        return this;
    }

    public final void remap(boolean singleThread, List<Context> arr) {
        if(singleThread || arr.size() < 50 * Util.CPU || !ENABLE_CONCURRENT) {
            notRemapFields = new MyHashSet<>(libNotRemapFields);
            notRemapMethods = new MyHashSet<>(libNotRemapMethods);
            selfSuperMap = new MyHashMap<>(arr.size());
            selfMethods = new MyHashMap<>();

            Context currentContext = null;

            try {
                for (Context ctx : arr) {
                    currentContext = ctx;
                    parseAndAddSelfSuperMap(ctx);
                }

                generateSelfSuperMap();
                if(DEBUG)
                    System.out.println("[1-SuperMap]: " + selfSuperMap);

                for (Context ctx : arr) {
                    currentContext = ctx;
                    mapSelf(ctx);
                }

                for (Context ctx : arr) {
                    currentContext = ctx;
                    mapConstant(ctx);
                }

            } catch (Throwable e) {
                throw new RuntimeException("At parsing " + currentContext.getName(), e);
            }
        } else {
            notRemapFields = new ConcurrentFindHashSet<>(libNotRemapFields);
            notRemapMethods = new ConcurrentFindHashSet<>(libNotRemapMethods);
            selfSuperMap = new ConcurrentHashMap<>(arr.size());
            selfMethods = new ConcurrentFindHashMap<>();

            System.out.println("警告: ConcurrentFindSet/Map还处于试验阶段!");

            List<List<Context>> splitedCtxs = new ArrayList<>();
            List<Context> tmp = new ArrayList<>();

            int splitThreshold = (arr.size() / Util.CPU) + 1;

            int i = 0;
            for (Context ctx : arr) {
                if(i >= splitThreshold) {
                    splitedCtxs.add(tmp);
                    tmp = new ArrayList<>(splitThreshold);
                    i = 0;
                }
                tmp.add(ctx);
                i++;
            }
            splitedCtxs.add(tmp);

            if (DEBUG) {
                for (i = 0; i < splitedCtxs.size(); i++) {
                    System.out.println(splitedCtxs.get(i).size() + " 个由子线程" + i + "处理");
                }
            }

            Util.waitfor("RV2WorkerP", this::parseAndAddSelfSuperMap, splitedCtxs);

            generateSelfSuperMap();

            Util.waitfor("RV2WorkerR", this::mapSelf, splitedCtxs);

            Util.waitfor("RV2WorkerW", this::mapConstant, splitedCtxs);
        }

        selfSuperMap = null;
        selfMethods = null;
        notRemapFields = null;
        notRemapMethods = null;
    }

    public void clear() {
        super.clear();
        libNotRemapMethods.clear();
        libNotRemapFields.clear();
    }

    /**
     * Step 1
     */
    final void parseAndAddSelfSuperMap(Context c) {
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
            selfSuperMap.put(data.name, list);
        }
    }

    /**
     * Step 2
     */
    final void mapSelf(Context ctx) {
        ConstantData data = ctx.getData();

        Collection<String> supers = addAllSupersThis(data.name);

        mapSelfMethod(ctx, data.methods, data, supers);

        mapSelfField(ctx, data.fields, data, supers);
    }

    /**
     * 这个一定要有严格的顺序
     */
    private Collection<String> addAllSupersThisTb(String name) {
        return Util.shareFC(name, selfSuperMap.getOrDefault(name, Collections.emptyList()));
    }

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

        if(REWRITE) {
            ctx.reset();
        } else if(REFRESH) {
            ctx.refresh();
        }
    }

    /**
     * addAllSupers + this
     * 也可以是lib
     */
    final Collection<String> addAllSupersThis(String name) {
        return new FirstCollection<>(selfSuperMap.getOrDefault(name, Collections.emptyList()), name);
    }

    /**
     * Set reference name
     */
    final void setRefName(ConstantData data, CstRef ref, String newName) {
       ref.desc(data.writer.getDesc(newName, ref.desc().getType().getString()));
    }

    /**
     * Map: lambda method name
     */
    final void mapLambda(Context ctx, ConstantData data, CstDynamic dyn) {
        AttrBootstrapMethods bs = (AttrBootstrapMethods) ctx.getCache()[LAMBDA_INDEX];
        if(bs == null) {
            Attribute std = (Attribute) data.attributes.getByName("BootstrapMethods");
            if(std == null)
                throw new IllegalArgumentException("有lambda却无BootstrapMethod " + " at class " + data.name);
            else
                ctx.getCache()[LAMBDA_INDEX] = bs = new AttrBootstrapMethods(new ByteReader(std.getRawData()), data.cp);
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

        Collection<String> parents = addAllSupersThisTb(methodClass);
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
     * Map: use other(non-self) fields
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

        Collection<String> parents = addAllSupersThisTb(ref.getClassName());
        for(String parent : parents) {
            md.owner = parent;

            if(notRemapMethods.contains(md)) {
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

         Collection<String> parents = addAllSupersThisTb(ref.getClassName());
         for(String parent : parents) {
             fd.owner = parent;

             if(notRemapFields.contains(fd)) {
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

    /**
     * Map: self replace super methods
     */
    final void mapSelfMethod(Context ctx, List<MethodSimple> methods, ConstantData data, Collection<String> supers) {
        MtDesc sp = Util.shareMD();

        for (int i = 0; i < methods.size(); i++) {
            MethodSimple method = methods.get(i);
            if (method.accesses.has(AccessFlag.STATIC | AccessFlag.PRIVATE)) {
                if (!classMap.containsKey(data.name))
                    notRemapMethods.add(new MtDesc(data.name, method.name.getString(), method.type.getString()));
                continue;
            }

            sp.name = method.name.getString();
            sp.param = method.type.getString();

            for (String parent : supers) {
                sp.owner = parent;
                if (notRemapMethods.contains(sp))
                    break;

                Map.Entry<MtDesc, String> entry = methodMap.find(sp);
                if (entry != null) {
                    FlagList flags = entry.getKey().flags;
                    if (flags.has(AccessFlag.STATIC | AccessFlag.PRIVATE)) {
                        sp.owner = data.name;
                        notRemapMethods.add(sp.copy());
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
                            notRemapMethods.add(sp.copy());
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
        // put a switch here.
        if(SKIP_BIN_FIELDS)
            if(classMap.containsKey(data.name)) return;

        FlDesc sp = Util.shareFD();

        for (int i = 0; i < fields.size(); i++) {
            FieldSimple field = fields.get(i);
            if (field.accesses.has(AccessFlag.STATIC | AccessFlag.PRIVATE) || KILL_ALL_FIELDS) {
                notRemapFields.add(new FlDesc(data.name, field.name.getString()));
                // 自己覆盖别人的成了static，注：这个情况确实可以跳过，如果有继承的非static方法是不能通过编译的
                continue;
            }

            sp.name = field.name.getString();

            // field只能覆盖...
            for (String parent : supers) {
                sp.owner = parent;
                if (notRemapFields.contains(sp))
                    break;

                Map.Entry<FlDesc, String> entry = fieldMap.find(sp);
                if (entry != null) {
                    // 还是时间与空间的问题
                    notRemapFields.add(new FlDesc(data.name, field.name.getString()));
                    break;
                }
            }
        }
    }
}