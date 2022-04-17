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
import ilib.asm.util.BoolFn;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.tree.anno.Annotation;
import roj.collect.MyHashMap;
import roj.config.data.CEntry;
import roj.config.data.CObject;
import roj.config.serial.Serializers;
import roj.config.serial.Structs;
import roj.io.IOUtil;
import roj.io.MutableZipFile;
import roj.util.ByteList;

import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Nixim("net.minecraftforge.fml.common.discovery.JarDiscoverer")
abstract class JarDiscoverer extends net.minecraftforge.fml.common.discovery.JarDiscoverer {
    @Copy(staticInitializer = "init")
    static MyHashMap<String, JarInfo> store;

    @SuppressWarnings("unchecked")
    private static void init() {
        store = new MyHashMap<>();

        File file = new File("modAnnotationCache.bson");
        if (file.isFile()) {
            ByteList r = IOUtil.getSharedByteBuf();
            Serializers ser = new Serializers(Serializers.AUTOGEN | Serializers.LENIENT);

            try (InputStream fis = new GZIPInputStream(new FileInputStream(file))) {
                r.readStreamFully(fis);
                Structs st = Structs.newDecompressor();
                st.read(r);
                CObject<?> obj = CEntry.fromBinary(r, st, ser).asObject(MyHashMap.class);
                obj.deserialize();
                store = (MyHashMap<String, JarInfo>) obj.value;
                Loader.logger.info("[IMPLIB]正在使用缓存的注解数据");
            } catch (Throwable e) {
                e.printStackTrace();
                store.clear();
            }
        }
    }

    @Copy
    public static void save() {
        if (Config.cacheAnnotation && store != null) {
            File file = new File("modAnnotationCache.bson");
            try (OutputStream fos = new GZIPOutputStream(new FileOutputStream(file))) {
                Serializers ser = new Serializers(Serializers.AUTOGEN | Serializers.LENIENT);

                ByteList w = IOUtil.getSharedByteBuf();

                CObject<MyHashMap<String, JarInfo>> adt = new CObject<>(store, ser);
                Structs st = Structs.newCompressor();
                adt.toBinary(w, st);

                st.finish().writeToStream(fos);
                w.writeToStream(fos);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        store = null;
    }

    @Inject("discover")
    public List<ModContainer> discover(ModCandidate candidate, ASMDataTable table) {
        List<ModContainer> found = Lists.newArrayList();

        JarInfo list = store == null ? null : store.get(candidate.getModContainer().getName());
        if (list != null) {
            findClassesCache_my(candidate, table, found, list.mc, list);
        } else {
            try (ZipFile jar = new ZipFile(candidate.getModContainer())) {
                ZipEntry modInfo = jar.getEntry("mcmod.info");
                MetadataCollection mc;
                if (modInfo != null) {
                    try (InputStream in = jar.getInputStream(modInfo)) {
                        mc = MetadataCollection.from(in, candidate.getModContainer().getName());
                    }
                } else {
                    mc = MetadataCollection.from(null, "");
                }
                findClassesASM_my(candidate, table, jar, found, mc);
            } catch(Exception e) {
                FMLLog.log.warn("文件 {} 无法读取", candidate.getModContainer().getName(), e);
            }
        }

        if (store != null) Loader.handleASMData(table, store);
        else FMLLog.bigWarning("警告: 尝试在preInit之后继续加载mod: " + candidate.getModContainer());

        return found;
    }

    @Copy
    private void findClassesCache_my(ModCandidate candidate, ASMDataTable table, List<ModContainer> foundMods, MetadataCollection mc, JarInfo jarInfo) {
        for (Map.Entry<String, ClassInfo> entry : jarInfo.classes.entrySet()) {
            String key = entry.getKey();
            candidate.addClassEntry(key);

            ClassInfo info = entry.getValue();

            String normName = info.internalName.replace('.', '/');
            for (Map.Entry<String, List<Annotation>> ma : info.annotations.entrySet()) {
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

            for (Map.Entry<String, List<Annotation>> ma : classInfo.annotations.entrySet()) {
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
        JarInfo ji = store.get(candidate.getModContainer().getName());
        if (ji == null) store.put(candidate.getModContainer().getName(), ji = new JarInfo());
        ji.mc = mc;

        Enumeration<? extends ZipEntry> ee = jar.entries();
        MutableZipFile mzf = null;
        while (ee.hasMoreElements()) {
            ZipEntry ze;
            try {
                ze = ee.nextElement();
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("非UTF-8编码的ZIP文件 " + jar.getName() + " 中出现了非ASCII字符的文件名");
            }
            if (ze.getName().startsWith("__MACOSX")) continue;
            BoolFn operator = Preloader.getFileProcessor(ze.getName());
            if (operator != null) {
                ByteList bb = IOUtil.getSharedByteBuf().readStreamFully(jar.getInputStream(ze));
                if (operator.accept(bb)) {
                    if (mzf == null)
                        mzf = new MutableZipFile(new File(jar.getName()));
                    mzf.put(ze.getName(), new ByteList(bb.toByteArray()));
                }
            }
            if (ze.getName().endsWith(".class")) {
                try (InputStream in = jar.getInputStream(ze)) {
                    ASMModParser mp = new ASMModParser(in);

                    FastParser parser = (FastParser) mp;
                    Map<String, List<Annotation>> map = parser.getAnnotationMap();
                    List<String> itf = parser.getItf();

                    if (map.isEmpty() && itf.isEmpty()) continue;

                    if (Config.cacheAnnotation) {
                        ClassInfo ci = new ClassInfo(mp.getASMType().getInternalName());
                        ji.classes.put(ze.getName(), ci);

                        ci.interfaces = itf;
                        ci.annotations = map;
                    }

                    mp.validate();
                    mp.sendToTable(table, candidate);

                    ModContainer c = ModContainerFactory.instance().build(mp, candidate.getModContainer(), candidate);
                    if (c != null) {
                        ji.mainClasses.add(ze.getName());
                        table.addContainer(c);
                        foundMods.add(c);
                        c.bindMetadata(mc);
                        c.setClassVersion(mp.getClassVersion());
                    }

                    candidate.addClassEntry(ze.getName());
                } catch (LoaderException e) {
                    FMLLog.log.error("无法加载 " + candidate.getModContainer().getName() + "#!" + ze.getName() + " - 也许文件损坏了", e);
                    jar.close();
                    throw e;
                }
            }
        }
        if (mzf != null) {
            mzf.store();
            for (int i = 0; i < 20; i++) {
                Loader.logger.info("有一个mod(" + candidate.getModContainer().getName() + ")已被兼容性修改，这会导致它无法加载，需要再次启动MC。这个提示可能会出现多次");
            }
        }
    }

    private static byte[] doManifest(byte[] bytes) {
        ByteList bl = new ByteList(bytes);
        return bl.slice(0, bl.lastIndexOf(new byte[]{
                '\n', 'N', 'a', 'm', 'e', ':', ' '
        })).toByteArray();
    }
}
