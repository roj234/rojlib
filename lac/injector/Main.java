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
package lac.injector;

import lac.client.AccessHelper;
import lac.injector.mapper.Mover;
import roj.asm.Parser;
import roj.asm.TransformException;
import roj.asm.nixim.NiximSystem;
import roj.asm.nixim.NiximSystem.NiximData;
import roj.asm.tree.*;
import roj.asm.util.AccessFlag;
import roj.asm.util.Context;
import roj.asm.util.Pack125;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.TaskExecutor;
import roj.config.data.CMapping;
import roj.crypt.SM3;
import roj.io.BoxFile;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.io.ZipFileWriter;
import roj.mapper.CodeMapper;
import roj.mapper.Mapping;
import roj.mapper.SimpleObfuscator;
import roj.mapper.obf.policy.ClassicABC;
import roj.mapper.obf.policy.SimpleNamer;
import roj.mapper.util.Desc;
import roj.mod.MCLauncher;
import roj.mod.Shared;
import roj.text.TextUtil;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;
import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.ComboRandom;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static lac.server.LACMod.VERSION;
import static roj.mapper.SimpleObfuscator.CLEAR_ATTR;
import static roj.mapper.SimpleObfuscator.CLEAR_CODE_ATTR;
import static roj.mod.MCLauncher.error;
import static roj.mod.Shared.CONFIG;

/**
 * LAC Main Gui
 *
 * @author Roj234
 * @since 2021/7/8 18:42
 */
public class Main extends JFrame {
    static final TaskExecutor EX = new TaskExecutor();

    JButton jb;
    static JProgressBar stateBar;
    File   minecraftDir, modInfo;
    public static final String AH_CLASS = AccessHelper.class.getName().replace('.', '/');

    public Main(File minecraftDir, File modInfo) {
        super("LAC " + VERSION);
        this.minecraftDir = minecraftDir;
        this.modInfo = modInfo;

        JButton terminal = new JButton("退出");
        terminal.addActionListener((e) -> System.exit(0));

        if(modInfo == null) {
            JButton inst = jb = new JButton("安装");

            inst.addActionListener((ev) -> {
                inst.setEnabled(false);
                inst.setText("请稍候");
                EX.execute(this::install);
            });

            add(inst);
        } else {
            JButton uninst = jb = new JButton("卸载");

            uninst.addActionListener((ev) -> {
                uninst.setEnabled(false);
                uninst.setText("请稍候");
                EX.execute(this::uninstall);
            });

            add(uninst);
        }

        add(terminal);
        add(stateBar = new JProgressBar());
        stateBar.setMinimum(0);
        stateBar.setMaximum(100);
        stateBar.setStringPainted(true);
        JProgressBar memoryBar;
        add(memoryBar = new JProgressBar());
        memoryBar.setMinimum(0);
        memoryBar.setMaximum(10000);
        memoryBar.setStringPainted(true);
        Thread t = new Thread(() -> {
            while (true) {
                LockSupport.parkNanos(200_000_000);
                Runtime runtime = Runtime.getRuntime();
                double used = runtime.totalMemory() - runtime.freeMemory();
                double total = used / runtime.maxMemory();
                memoryBar.setString(((long)used >> 20) + "M");
                if (total > .9) {
                    memoryBar.setForeground(Color.RED);
                } else if (total > .8) {
                    memoryBar.setForeground(Color.YELLOW);
                } else if (total > .5) {
                    memoryBar.setForeground(Color.CYAN);
                } else {
                    memoryBar.setForeground(Color.GREEN);
                }
                memoryBar.setValue((int) (10000 * total));
            }
        });
        t.setName("Memory Logger");
        t.setDaemon(true);
        t.start();

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        pack();
        setLayout(new FlowLayout());
        setBounds(0, 0, 230, 120);
        UIUtil.center(this);
        setVisible(true);
        setResizable(false);

        validate();
    }

    CMapping config = new CMapping();

    private void install() {
        CMapping mc_conf = MCLauncher.config.get("mc_conf").asMap();

        try {
            preInject(mc_conf, ComboRandom.from("fdsgh r78e95hf8d89entb90 v bf0fRE%$&UJ"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void uninstall() {
        try {
            uninstall0();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void uninstall0() throws IOException {
        BoxFile file = new BoxFile(modInfo);
        ByteList list = new ByteList();
        Inflater inf = new Inflater(true);
        try (InputStream in = new InflaterInputStream(file.get("lib classes", list).asInputStream(), inf)) {
            ByteList shared = IOUtil.getSharedByteBuf();
            Pack125.unpack(shared.readStreamFully(in), (name, data) -> {

            });
        }
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("fmd.base_path", ".");
        System.setProperty("fmd.launch_only", "true");
        System.setProperty("roj.asm.ldc.clone", "true");
        MCLauncher.load();

        UIUtil.systemLook();
        JFileChooser fc = new JFileChooser(new File("."));
        fc.setDialogTitle("选择客户端位置(.minecraft文件夹)");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (args.length > 0 || fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            File mcRoot = args.length > 0 ? new File(args[0]) : fc.getSelectedFile();

            CONFIG.get("通用").asMap().put("MC目录", mcRoot.getAbsolutePath());
            List<File> versions = MCLauncher.findVersions(new File(mcRoot, "/versions/"));

            File mcJson;
            if(versions.size() < 1) {
                error("没有找到MC");
                return;
            } else {
                String[] obj = new String[versions.size()];
                int i = 0;
                for (; i < versions.size(); i++) {
                    String s = versions.get(i).getName();
                    final int index = s.lastIndexOf('.');
                    obj[i] = index == -1 ? s : s.substring(0, index);
                }

                if(i >= obj.length)
                    i = 0;

                String s = (String) JOptionPane.showInputDialog(null,"请选择你的MC版本:\n", "询问", JOptionPane.QUESTION_MESSAGE, null, obj, obj[i]);
                if(s == null)
                    return;
                for (i = 0; i < obj.length; i++) {
                    if(s == obj[i])
                        break;
                }
                mcJson = versions.get(i);
                if (!MCLauncher.installMinecraftClient(mcRoot, mcJson, false))
                    return;
            }

            File modInfo = null;
            /*fc.setDialogTitle("选择服务端描述文件 (没安装过则取消)");
            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fc.setFileFilter(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    return f.getName().endsWith(".mif");
                }

                @Override
                public String getDescription() {
                    return "mod描述文件, *.mif";
                }
            });

            File modInfo = fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION ? null : fc.getSelectedFile();*/

            new Main(mcRoot, modInfo);
            EX.setName("任务");
            EX.start();
        }
    }

    static final class Owner {
        String name;
        File owner;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Owner owner = (Owner) o;
            return name.equals(owner.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    public static void preInject(CMapping mc_conf, ComboRandom rnd) throws Exception {
        Predicate<File> findJar = file -> file.getName().endsWith(".jar") || file.getName().endsWith(".zip");

        stage(0, "加载class");
        BoxFile box = new BoxFile(new File("lac-server.mif"));
        box.reset();

        Pack125 packer = new Pack125();

        MyHashMap<Owner, Context> modClasses = new MyHashMap<>(2000);

        SM3 sm3 = new SM3();
        ByteList shared = IOUtil.getSharedByteBuf();

        File modDir = new File(mc_conf.getString("root") + "/mods");
        int beginLength = modDir.getAbsolutePath().length() + 1;
        for (File file : FileUtil.findAllFiles(modDir, findJar)) {
            sm3.reset();
            try (FileInputStream in = new FileInputStream(file)) {
                shared.readStream(in, 4096);
                sm3.update(shared.list, 0, shared.wIndex());
                shared.clear();
            }
            String fn = file.getPath().substring(beginLength);
            box.append("DIG|" + fn, new ByteList(sm3.digest()));

            packer.section("M|" + fn);
            ZipFile modZip;
            try {
                modZip = new ZipFile(file);
            } catch (IOException e) {
                CmdUtil.error("Failed read " + file);
                continue;
            }
            Enumeration<? extends ZipEntry> entries = modZip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                String name = ze.getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.startsWith("assets/")) {
                    Owner owner = new Owner();
                    owner.name = name;
                    owner.owner = file;
                    Context ctx = new Context(name, modZip.getInputStream(ze));
                    packer.pack(ctx.get());
                    x:
                    if (null != modClasses.putIfAbsent(owner, ctx)) {
                        if (name.startsWith("org/spongepowered/")) break x;
                        System.out.println("M警告 重复 " + name);
                        System.out.println(modClasses.find(owner).getKey().owner.getPath().substring(beginLength) + " <=> " + file.getPath().substring(beginLength));
                    } else {
                        ctx.getData();
                    }
                }
            }
            modZip.close();
        }

        packer.section("LIB");
        MyHashMap<Owner, Context> libClasses = new MyHashMap<>(10000);
        List<String> libraries = TextUtil.split(new ArrayList<>(), mc_conf.getString("libraries"), ';');
        libraries.add(mc_conf.getString("jar"));
        for (String file : libraries) {
            int li = file.lastIndexOf("libraries");
            if (file.indexOf("minecraft", li) == -1) {
                continue;
            }
            File file1 = new File(file);
            if (!file1.isFile()) continue;
            ZipFile libZip = new ZipFile(file1);
            Enumeration<? extends ZipEntry> entries = libZip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                String name = ze.getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.startsWith("assets/")) {
                    Owner owner = new Owner();
                    owner.name = name;
                    owner.owner = file1;
                    Context e = new Context(name, libZip.getInputStream(ze));
                    packer.pack(e.get());
                    if (null != libClasses.putIfAbsent(owner, e)) {
                        System.out.println("L警告 重复的 " + name);
                    } else {
                        e.getData();
                        if (null != modClasses.remove(owner)) {
                            System.out.println("LM警告 重复的 " + name);
                        }
                    }
                }
            }
            libZip.close();
        }
        stage(10, "备份class");
        packer.writeCompressed(box.streamAppend("classes.pak"));
        box.close();
        stage(20, "代码注入");

        File lac = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsoluteFile();
        ZipFile modZip = new ZipFile(lac);
        Enumeration<? extends ZipEntry> entries = modZip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry ze = entries.nextElement();
            String name = ze.getName();
            if (!name.startsWith("lac/client/") || name.endsWith("/")) continue;

            Owner owner = new Owner();
            owner.name = name;
            owner.owner = lac;
            Context e = new Context(name, modZip.getInputStream(ze));
            //sideOnly(e);
            e.getData();
            modClasses.put(owner, e);
        }
        modZip.close();

        //Owner owner = new Owner();
        //owner.name = "lac/injector/patch/Insert.class";
        //owner.owner = lac;
        //modClasses.put(owner, new Context(owner.name, IOUtil.read(owner.name)));

        //patch(libClasses);
        stage(40, "移动mod方法");
        Mover mover = new Mover();
        SimpleList<Context> ctxs = new SimpleList<>(modClasses.values());

        SimpleList<Desc> methodsAll = new SimpleList<>(ctxs.size());
        mover.setFallback(desc -> {
            desc = desc.copy();
            desc.name = "func_" + rnd.nextInt(999999) + "_" + (char)TextUtil.digits[rnd.nextInt(TextUtil.digits.length - 10) + 10];
            desc.owner = methodsAll.get(rnd.nextInt(methodsAll.size())).owner;
            return desc;
        });
        for (int i = 0; i < ctxs.size(); i++) {
            ConstantData data = ctxs.get(i).getData();
            if (!data.name.equals(AH_CLASS) && rnd.nextFloat() > 0.66f)
                insertAccessHelper(data, rnd);
            List<? extends MethodNode> ms = data.methods;
            for (int j = 0; j < ms.size(); j++) {
                MethodSimple m = (MethodSimple) ms.get(j);
                int acc = m.accesses;
                if (AccessFlag.STATIC == (acc & (AccessFlag.STATIC | AccessFlag.NATIVE)) &&
                        !m.name().startsWith("<")) {
                    m.accesses = (char) (m.accesses & ~(AccessFlag.PRIVATE | AccessFlag.PROTECTED) | AccessFlag.PUBLIC | AccessFlag.SYNTHETIC);
                    Desc desc = new Desc(data.name, m.name(), m.rawDesc());
                    methodsAll.add(desc);
                }
            }
            List<? extends FieldNode> fs = data.fields;
            for (int j = 0; j < fs.size(); j++) {
                FieldSimple m = (FieldSimple) fs.get(j);
                m.accesses = (char) (m.accesses & ~(AccessFlag.PRIVATE | AccessFlag.PROTECTED) | AccessFlag.PUBLIC | AccessFlag.SYNTHETIC);
            }
        }
        ArrayUtil.shuffle(methodsAll, rnd);
        int size = methodsAll.size();
        if ((size & 1) != 0)
            size--;
        for (int i = 0; i < size; i += 2) {
            mover.putIfAbsent(methodsAll.get(i), methodsAll.get(i + 1));
        }
        System.out.println("MOD STATIC覆盖率 " + 200f * mover.getMethodMove().size() / methodsAll.size());
        mover.map(ctxs);
        mover.clear();

        System.out.println("处理文件: " + ctxs.size());

        stage(70, "混淆");

        SimpleObfuscator so = new SimpleObfuscator(rnd);
        so.method = new SimpleNamer() {
            @Override
            protected String obfName0(Random rand) {
                if (rand.nextBoolean()) return null;
                return "func_" + rand.nextInt(999999) + "_" + (char)TextUtil.digits[rand.nextInt(TextUtil.digits.length - 10) + 10];
            }
        };
        so.field = new ClassicABC();
        so.classExclusions = new MyHashSet<>();
        so.setFlags(/*FAKE_SIGN | */CLEAR_CODE_ATTR | CLEAR_ATTR);
        so.obfuscate(ctxs);

        stage(90, "写入文件(测试)");
        File path = new File(Shared.TMP_DIR, "ctx_out");
        path.mkdirs();

        MyHashMap<File, ZipFileWriter> map2 = new MyHashMap<>();

        for (Map.Entry<Owner, Context> entry : libClasses.entrySet()) {
            Owner o = entry.getKey();
            ZipFileWriter zfw = map2.get(o.owner);
            if (zfw == null) {
                map2.put(o.owner, zfw = new ZipFileWriter(new File(path, o.owner.getName())));
            }

            Context ctx = entry.getValue();
            zfw.writeNamed(ctx.getFileName(), ctx.get());
        }

        for (Map.Entry<Owner, Context> entry : modClasses.entrySet()) {
            Owner o = entry.getKey();
            ZipFileWriter zfw = map2.get(o.owner);
            if (zfw == null) {
                map2.put(o.owner, zfw = new ZipFileWriter(new File(path, o.owner.getName())));
            }

            Context ctx = entry.getValue();
            zfw.writeNamed(ctx.getFileName(), ctx.get());
        }

        for (ZipFileWriter zfw : map2.values()) {
            zfw.close();
        }
        stage(100, "完成");
    }

    static ConstantData accessHelperFake;

    static {
        try {
            accessHelperFake = Parser.parseConstants(IOUtil.read("lac/injector/patch/AccessHelperFake.class"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void insertAccessHelper(ConstantData data, ComboRandom rnd) {
        data.interfaces.add(data.cp.getClazz(AH_CLASS));
        // todo insert random impls
    }

    private static void stage(int percent, String value) {
        stateBar.setValue(percent);
        stateBar.setString(value);
    }

//    private static void sideOnly(Context e) {
//        ConstantData data = e.getData();
//        List<MethodSimple> methods = data.methods;
//        boolean ch = false;
//        for (int i = methods.size() - 1; i >= 0; i--) {
//            MethodSimple ms = methods.get(i);
//            Map<String, AnnVal> serverOnly = NiximSystem
//                    .getAnnotation(data.cp, ms, "lac/injector/note/ServerOnly");
//            if (serverOnly != null) {
//                methods.remove(i);
//                AnnValString client = (AnnValString) serverOnly.get("client");
//                if (client != null) {
//                    int j = data.getMethodByName(client.value);
//                    assert j >= 0;
//                    MethodSimple s1 = methods.get(j);
//                    assert s1.type.equals(ms.type);
//                    s1.name = ms.name;
//                }
//                ch = true;
//            }
//        }
//        if (ch)
//            e.compress();
//    }

    private static void patch(MyHashMap<Owner, Context> classes) throws IOException, TransformException {
        Owner check = new Owner();

        NiximSystem system = new NiximSystem();

        Mapping map = new Mapping();
        map.loadMap(new File("deobf.srg"), true);
        CodeMapper cm = new CodeMapper(map);

        Context ctx0 = new Context("", IOUtil.read("lac/injector/patch/NxC00Handshake.class"));
        cm.processOne(ctx0);
        system.load(ctx0.getCompressedShared().toByteArray());
        ctx0 = new Context("", IOUtil.read("lac/injector/patch/NxEncryption.class"));
        cm.processOne(ctx0);
        system.load(ctx0.getCompressedShared().toByteArray());

        system.load(IOUtil.read("lac/injector/patch/NxLaunchClassLoader.class"));
        system.load(IOUtil.read("lac/injector/patch/NxModList.class"));
        system.load(IOUtil.read("lac/injector/patch/NxSecurity.class"));

        for (Map.Entry<String, NiximData> entry : system.getRegistry().entrySet()) {
            check.name = entry.getKey() + ".class";
            Context target = classes.get(check);
            if (target == null) {
                if (check.name.startsWith("net/minecraft/")) {
                    target = findMinecraftClass(entry.getKey());
                }
                if (target == null)
                    throw new TransformException("无法找到 " + check.name);
            }
            NiximSystem.nixim(target, entry.getValue(), 0);
        }

        Map<String, String> replace = new MyHashMap<>();
        replace.put("net/minecraftforge/fml/common/network/handshake/FMLHandshakeClientState$2",
                    "lac/injector/patch/NxFMLHS_C$2.class");
        replace.put("net/minecraftforge/fml/common/network/handshake/FMLHandshakeClientState$3",
                    "lac/injector/patch/NxFMLHS_C$3.class");
        replace.put("net/minecraftforge/fml/common/network/handshake/FMLHandshakeClientState$4",
                    "lac/injector/patch/NxFMLHS_C$4.class");

        ByteList sb = IOUtil.getSharedByteBuf();
        for (Map.Entry<String, String> entry : replace.entrySet()) {
            check.name = entry.getKey() + ".class";
            Context target = classes.get(check);
            if (target == null) {
                throw new TransformException("无法找到 " + check.name);
            }
            sb.clear();
            sb.readStreamFully(Main.class.getClassLoader().getResourceAsStream(entry.getValue()));
            ConstantData data = Parser.parseConstants(sb);
            data.nameCst.getValue().setString(entry.getKey());

            target.set(new ByteList(Parser.toByteArray(data)));
        }
    }

    private static Context findMinecraftClass(String key) {
        return null;
    }
    private static String c2f(Class<?> clz) {
        return clz.getName().replace('.', '/') + ".class";
    }
}