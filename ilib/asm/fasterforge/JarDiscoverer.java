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
package ilib.asm.fasterforge;

import com.google.common.collect.Lists;
import ilib.Config;
import ilib.asm.Loader;
import ilib.asm.Preloader;
import ilib.asm.fasterforge.anc.ClassInfo;
import ilib.asm.fasterforge.anc.FastParser;
import ilib.asm.fasterforge.anc.JarInfo;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.tree.anno.Annotation;
import roj.asm.util.ConstantPool;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.io.MutableZipFile;
import roj.text.StringPool;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Nixim("net.minecraftforge.fml.common.discovery.JarDiscoverer")
abstract class JarDiscoverer extends net.minecraftforge.fml.common.discovery.JarDiscoverer {
    @Copy(staticInitializer = "init")
    static MyHashMap<String, JarInfo> store;

    private static void init() {
        store = new MyHashMap<>();

        File file = new File("modAnnotationCache.bin");
        if (file.isFile()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                ByteReader r = new ByteReader(IOUtil.read(fis));

                if (r.readInt() != 0x22332233) {
                    FMLLog.bigWarning("缓存文件错误");
                    return;
                }

                StringPool pool = new StringPool(r);
                ConstantPool cp = new ConstantPool(r.readUnsignedShort());
                cp.read(r);
                int len = r.readVarInt(false);
                store.ensureCapacity(len);
                for (int i = 0; i < len; i++) {
                    store.put(r.readVString(), JarInfo.fromByteArray(r, pool, cp));
                }
            } catch (Throwable e) {
                e.printStackTrace();
                store.clear();
            }
        }
    }

    @Copy
    public static void save() {
        if (Config.cacheAnnotation) {
            File file = new File("modAnnotationCache.bin");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                StringPool sp = new StringPool();
                ConstantPool cw = new ConstantPool();

                ByteWriter w = new ByteWriter(2333);

                w.writeVarInt(store.size(), false);
                for (Map.Entry<String, JarInfo> entry : store.entrySet()) {
                    w.writeVString(entry.getKey());
                    entry.getValue().toByteArray(w, sp, cw);
                }

                fos.write(0x22);
                fos.write(0x33);
                fos.write(0x22);
                fos.write(0x33);
                sp.writePool(fos);

                ByteList list = w.list;
                w.list = new ByteList();
                cw.write(w);

                w.list.writeToStream(fos);
                list.writeToStream(fos);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        store = null;
    }

    @Inject("discover")
    public List<ModContainer> discover(ModCandidate candidate, ASMDataTable table) {
        List<ModContainer> found = Lists.newArrayList();

        try (ZipFile jar = new JarFile(candidate.getModContainer())) { // 到底要不要验证SHA256?
            ZipEntry modInfo = jar.getEntry("mcmod.info");
            MetadataCollection mc;
            if (modInfo != null) {
                try (InputStream inputStream = jar.getInputStream(modInfo)) {
                    mc = MetadataCollection.from(inputStream, candidate.getModContainer().getName());
                }
            } else {
                mc = MetadataCollection.from(null, "");
            }

            JarInfo list = store.get(candidate.getModContainer().getName());
            if (list != null) {
                findClassesCache_my(candidate, table, jar, found, mc, list);
            } else {
                findClassesASM_my(candidate, table, jar, found, mc);
            }
        } catch (Exception e) {
            FMLLog.log.warn("文件 {} 无法读取", candidate.getModContainer().getName(), e);
        }

        Loader.handleASMData(table, store);

        return found;
    }

    @Copy
    private void findClassesCache_my(ModCandidate candidate, ASMDataTable table, ZipFile jar, List<ModContainer> foundMods, MetadataCollection mc, JarInfo jarInfo) {
        for (Map.Entry<String, ClassInfo> entry : jarInfo.classes.entrySet()) {
            String key = entry.getKey();
            candidate.addClassEntry(key);

            ClassInfo info = entry.getValue();

            String normName = info.internalName.replace('.', '/');
            for (Map.Entry<String, Collection<Annotation>> ma : info.annotations.asMap().entrySet()) {
                String annoClz = ma.getKey();
                for (Annotation primitiveVal : ma.getValue()) {
                    table.addASMData(candidate, annoClz, normName, primitiveVal.clazz, TypeHelper.toPrimitive(primitiveVal.values));
                }
            }
            for (String intf : info.interfaces)
                table.addASMData(candidate, intf, info.internalName, null, null);
        }

        for (String mainClass : jarInfo.mainClasses) {
            ClassInfo classInfo = jarInfo.classes.get(mainClass);

            for (Map.Entry<String, Collection<Annotation>> ma : classInfo.annotations.asMap().entrySet()) {
                String annoClz = ma.getKey();

                Constructor<? extends ModContainer> container = ModContainerFactory.modTypes.get(TypeHelper.asmType(annoClz.replace('.', '/')));

                if (container != null) {
                    String className = classInfo.internalName.replace('/', '.');

                    FMLLog.log.debug("检测到 {} 类型的mod ({}) - 开始加载", annoClz, className);

                    Iterator<Annotation> itr = ma.getValue().iterator();

                    if (itr.hasNext()) {
                        Map<String, Object> prm = TypeHelper.toPrimitive(itr.next().values);

                        try {
                            ModContainer ret = container.newInstance(className, candidate, prm);
                            if (!ret.shouldLoadInEnvironment()) {
                                FMLLog.log.debug("放弃加载 {}, mod提示不应该在这个环境加载", className);
                            } else {
                                table.addContainer(ret);
                                foundMods.add(ret);
                                ret.bindMetadata(mc);
                                // java 8
                                ret.setClassVersion(52 << 16);
                                break;
                            }
                        } catch (Exception var8) {
                            FMLLog.log.error("无法构建mod容器 {}", annoClz, var8);
                        }
                    }
                }
            }
        }
    }

    @Copy
    private void findClassesASM_my(ModCandidate candidate, ASMDataTable table, ZipFile jar, List<ModContainer> foundMods, MetadataCollection mc) throws IOException {
        Enumeration<? extends ZipEntry> ee = jar.entries();
        MutableZipFile mzf = null;
        while (ee.hasMoreElements()) {
            ZipEntry ze;
            try {
                ze = ee.nextElement();
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("非UTF-8编码的ZIP文件 " + jar.getName() + " 中出现了非ASCII字符的文件名");
            }
            if (ze.getName().startsWith("__MACOSX"))
                continue;
            Function<byte[], byte[]> operator = Preloader.getFileProcessor(ze.getName());
            if (operator != null) {
                byte[] result = operator.apply(IOUtil.read(jar.getInputStream(ze)));
                if (result != null) {
                    if (mzf == null)
                        mzf = new MutableZipFile(new File(jar.getName()));
                    mzf.setFileData(ze.getName(), new ByteList(result));
                }
            }
            if (ze.getName().endsWith(".class")) {
                identityEntry_my(candidate, table, jar, foundMods, mc, ze);
            }
        }
        if (mzf != null) {
            mzf.store();
            for (int i = 0; i < 20; i++) {
                System.err.println("有一个mod(" + candidate.getModContainer().getName() + ")已被兼容性修改，这会导致它无法加载，需要再次启动MC。这个提示可能会出现多次");
            }
        }
    }

    private static byte[] doManifest(byte[] bytes) {
        ByteList bl = new ByteList(bytes);
        return bl.subList(0, bl.lastIndexOf(new byte[]{
                '\n', 'N', 'a', 'm', 'e', ':', ' '
        })).getByteArray();
    }

    @Copy
    private void identityEntry_my(ModCandidate candidate, ASMDataTable table, ZipFile jar, List<ModContainer> foundMods, MetadataCollection mc, ZipEntry ze) throws IOException {
        ASMModParser modParser;
        try {
            try (InputStream inputStream = jar.getInputStream(ze)) {
                modParser = new ASMModParser(inputStream);
            }
            candidate.addClassEntry(ze.getName());
        } catch (LoaderException e) {
            FMLLog.log.error("无法加载 " + candidate.getModContainer().getPath() + " - 也许文件损坏了", e);
            jar.close();
            throw e;
        }

        FastParser parser = (FastParser) modParser;
        Map<String, List<Annotation>> map = parser.getAnnotationMap();
        Set<String> itf = parser.getItf();

        if (map.isEmpty() && itf.isEmpty())
            return;

        JarInfo jarInfo = store.computeIfAbsent(candidate.getModContainer().getName(), (s) -> new JarInfo());
        ClassInfo classInfo = jarInfo.classes.computeIfAbsent(ze.getName(), (s) -> new ClassInfo(modParser.getASMType().getInternalName()));

        classInfo.interfaces.addAll(itf);

        for (Map.Entry<String, List<Annotation>> entry : map.entrySet()) {
            for (Annotation annotation1 : entry.getValue()) {
                Annotation annotation2 = new Annotation(annotation1);
                String clz = annotation2.clazz.replace('/', '.');
                annotation2.clazz = entry.getKey();
                classInfo.annotations.put(clz, annotation2);
            }
        }

        modParser.validate();
        modParser.sendToTable(table, candidate);
        ModContainer container = ModContainerFactory.instance().build(modParser, candidate.getModContainer(), candidate);
        if (container != null) {
            jarInfo.mainClasses.add(ze.getName());
            table.addContainer(container);
            foundMods.add(container);
            container.bindMetadata(mc);
            container.setClassVersion(modParser.getClassVersion());
        }
    }
}
