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
import ilib.asm.fasterforge.anc.ItfGet;
import ilib.asm.fasterforge.anc.JarInfo;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.RemapTo;
import roj.asm.struct.anno.Annotation;
import roj.asm.type.Type;
import roj.collect.MyHashMap;
import roj.collect.SingleBitSet;
import roj.io.IOUtil;
import roj.io.ZipUtil;
import roj.reflect.DirectMethodAccess;
import roj.text.StringPool;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Nixim("net.minecraftforge.fml.common.discovery.JarDiscoverer")
abstract class JarDiscoverer extends net.minecraftforge.fml.common.discovery.JarDiscoverer {

    @Copy
    // jarName : fileName -> annotationName -> annotation value
    static MyHashMap<String, JarInfo> store;

    @Copy
    private static void init() {
        if (store == null)
            store = new MyHashMap<>();

        File file = new File("modAnnotationCache.bin");
        if (file.isFile()) {
            store.clear();

            try (FileInputStream fis = new FileInputStream(file)) {
                ByteReader reader = new ByteReader(IOUtil.readFully(fis));

                if (reader.readInt() != 0x22332233) {
                    FMLLog.bigWarning("缓存文件错误");
                    return;
                }

                roj.text.StringPool pool = new roj.text.StringPool(reader);
                System.err.println("Pool size " + pool.size());
                int len = reader.readVarInt(false);
                store.ensureCapacity(len);
                for (int i = 0; i < len; i++) {
                    String name = reader.readVString();
                    JarInfo info = JarInfo.fromByteArray(reader, pool);

                    store.put(name, info);
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
                roj.text.StringPool stringPool = new StringPool();

                ByteWriter w = new ByteWriter(2333);

                w.writeVarInt(store.size(), false);
                for (Map.Entry<String, JarInfo> entry : store.entrySet()) {
                    w.writeVString(entry.getKey());
                    entry.getValue().toByteArray(w, stringPool);
                }

                fos.write(0x22);
                fos.write(0x33);
                fos.write(0x22);
                fos.write(0x33);
                System.err.println("Pool size " + stringPool.size());
                stringPool.writePool(fos);

                w.list.writeToStream(fos);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        store = null;
        fileProcessors = null;
        myMatcher = null;
        itfGetImpl = null;
    }

    @Copy
    static boolean init;
    @Copy
    static boolean jarModified;

    @Copy
    static List<Function<String, UnaryOperator<byte[]>>> fileProcessors;

    @Copy
    static Matcher myMatcher;

    @RemapTo("discover")
    public List<ModContainer> discover(ModCandidate candidate, ASMDataTable table) {
        if (!init) {
            itfGetImpl = DirectMethodAccess.getNCI(ItfGet.class, new String[]{
                    "getItf", "getMap"
            }, new SingleBitSet(), ASMModParser.class, new String[]{
                    "getItf", "getFieldAnnotationMap"
            });

            init();
            fileProcessors = Preloader.getFileProcessors();
            myMatcher = classFile.matcher("");
            init = true;
        }

        /*if(Preloader.visitedFiles.contains(candidate.getModContainer().getAbsolutePath())) {
            FMLLog.log.info("Skipped preloaded file {}.", candidate.getModContainer().getName());
            return Collections.emptyList();
        }*/

        List<ModContainer> foundMods = Lists.newArrayList();

        //FMLLog.log.info("Examining file {} for potential mods", candidate.getModContainer().getName());
        try (ZipFile jar = new JarFile(candidate.getModContainer())) { // 到底要不要验证SHA256?
            ZipEntry modInfo = jar.getEntry("mcmod.info");
            MetadataCollection mc;
            if (modInfo != null) {
                //FMLLog.log.trace("Located mcmod.info file in file {}", candidate.getModContainer().getName());
                try (InputStream inputStream = jar.getInputStream(modInfo)) {
                    mc = MetadataCollection.from(inputStream, candidate.getModContainer().getName());
                }
            } else {
                //FMLLog.log.debug("The mod container {} appears to be missing an mcmod.info file", candidate.getModContainer().getName());
                mc = MetadataCollection.from(null, "");
            }

            JarInfo list = store.get(candidate.getModContainer().getName());
            if (list != null) {
                findClassesCache_my(candidate, table, jar, foundMods, mc, list);
            } else {
                findClassesASM_my(candidate, table, jar, foundMods, mc);
            }
        } catch (Exception e) {
            FMLLog.log.warn("Zip file {} failed to read properly, it will be ignored", candidate.getModContainer().getName(), e);
        }

        if (jarModified) {
            System.err.println("有一个mod(" + candidate.getModContainer().getName() + ")已被兼容性修改，这会导致它无法加载，需要再次启动, 注：因为一次只能修改一个，所以这个提示可能会出现多次");
            while (true) {
                System.exit(233);
            }
        }

        Loader.handleASMData(table, store);

        return foundMods;
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
                    table.addASMData(candidate, annoClz, normName, primitiveVal.type.owner, TypeHelper.toPrimitive(primitiveVal.values));
                }
            }
            for (String intf : info.interfaces)
                table.addASMData(candidate, intf, info.internalName, null, null);
        }

        for (String mainClass : jarInfo.mainClasses) {
            ClassInfo classInfo = jarInfo.classes.get(mainClass);

            for (Map.Entry<String, Collection<Annotation>> ma : classInfo.annotations.asMap().entrySet()) {
                String annoClz = ma.getKey();

                Constructor<? extends ModContainer> container = ModContainerFactory.modTypes.get(org.objectweb.asm.Type.getType('L' + annoClz.replace('.', '/') + ';'));

                if (container != null) {

                    String className = classInfo.internalName.replace('/', '.');

                    FMLLog.log.debug("Identified a mod of type {} ({}) - loading", annoClz, className);

                    Iterator<Annotation> itr = ma.getValue().iterator();

                    if (itr.hasNext()) {
                        Map<String, Object> prm = TypeHelper.toPrimitive(itr.next().values);

                        try {
                            ModContainer ret = container.newInstance(className, candidate, prm);
                            if (!ret.shouldLoadInEnvironment()) {
                                FMLLog.log.debug("Skipping mod {}, container opted to not load.", className);
                            } else {
                                table.addContainer(ret);
                                foundMods.add(ret);
                                ret.bindMetadata(mc);
                                // java 8
                                ret.setClassVersion(52 << 16);
                            }
                        } catch (Exception var8) {
                            FMLLog.log.error("Unable to construct {} container", annoClz, var8);
                        }
                    }
                }
            }
        }
    }

    @Copy
    private void findClassesASM_my(ModCandidate candidate, ASMDataTable table, ZipFile jar, List<ModContainer> foundMods, MetadataCollection mc) throws IOException {
        Enumeration<? extends ZipEntry> ee = jar.entries();
        Map<String, byte[]> map = null;
        while (ee.hasMoreElements()) {
            ZipEntry ze;
            try {
                ze = ee.nextElement();
            } catch (IllegalArgumentException e) {
                FMLLog.bigWarning("非UTF-8编码的ZIP文件 " + jar.getName() + " 中出现了非ASCII字符的文件名, 这是mod作者的锅. 我被它坑了很多次, 所以专门搞了个提示,,,");
                throw new RuntimeException("请看debug.log");
            }
            if (ze.getName().startsWith("__MACOSX"))
                continue;
            if (!fileProcessors.isEmpty()) {
                for (Function<String, UnaryOperator<byte[]>> supplier : fileProcessors) {
                    UnaryOperator<byte[]> operator = supplier.apply(ze.getName());
                    if (operator != null) {
                        byte[] result = operator.apply(IOUtil.readFully(jar.getInputStream(ze)));
                        if (result != null) {
                            if (map == null)
                                map = new MyHashMap<>();
                            map.put(ze.getName(), result);
                        }
                    }
                }
            }
            if (myMatcher.reset(ze.getName()).matches()) {
                identityEntry_my(candidate, table, jar, foundMods, mc, ze);
            }
        }
        if (map != null) {
            File jarFile = new File(jar.getName());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream os = new ZipOutputStream(bos)) {
                ee = jar.entries();
                while (ee.hasMoreElements()) {
                    ZipEntry entry = ee.nextElement();
                    if (entry.getName().endsWith(".SF") || entry.getName().endsWith(".DSA"))
                        continue;
                    os.putNextEntry(new ZipEntry(entry.getName()));
                    if (entry.getName().equals("MANIFEST.MF")) {
                        os.write(map.getOrDefault(entry.getName(), doManifest(IOUtil.readFully(jar.getInputStream(entry)))));
                    } else {
                        os.write(map.getOrDefault(entry.getName(), IOUtil.readFully(jar.getInputStream(entry))));
                    }
                    os.closeEntry();
                }
                ZipUtil.close(os);
            }
            jar.close();
            bos.writeTo(new FileOutputStream(jarFile));
            jarModified = true;
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
            FMLLog.log.error("There was a problem reading the entry {} in the jar {} - probably a corrupt zip", candidate.getModContainer().getPath(), e);
            jar.close();
            throw e;
        }

        Map<String, List<Annotation>> map = itfGetImpl.getMap(modParser);
        Set<String> itf = itfGetImpl.getItf(modParser);

        if (map.isEmpty() && itf.isEmpty())
            return;

        JarInfo jarInfo = store.computeIfAbsent(candidate.getModContainer().getName(), (s) -> new JarInfo());
        ClassInfo classInfo = jarInfo.classes.computeIfAbsent(ze.getName(), (s) -> new ClassInfo(modParser.getASMType().getInternalName()));

        classInfo.interfaces.addAll(itf);

        for (Map.Entry<String, List<Annotation>> entry : map.entrySet()) {
            for (Annotation annotation1 : entry.getValue()) {
                Annotation annotation2 = new Annotation(annotation1);
                String clz = annotation2.type.owner.replace('/', '.');
                annotation2.type = new Type(entry.getKey(), 0);
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

    @Copy
    static ItfGet itfGetImpl;

}
