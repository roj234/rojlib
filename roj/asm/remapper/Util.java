package roj.asm.remapper;

import roj.asm.remapper.util.*;
import roj.asm.util.type.ParamHelper;
import roj.asm.util.type.Type;
import roj.collect.MyHashMap;
import roj.io.ZipUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.ui.CmdUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/19 21:32
 */
public final class Util {
    static final ThreadLocal<Object[]> ThreadBasedCache = ThreadLocal.withInitial(() -> new Object[] {
            new FlDesc("", ""),
            new MtDesc("", "", ""),
            new MyHashMap<>(),
            new FirstCollection<>(null, null),
            new CharList(),
            new ArrayList<>()
    });

    static final int CPU = Runtime.getRuntime().availableProcessors();

    public static FlDesc shareFD() {
        return (FlDesc) ThreadBasedCache.get()[0];
    }

    public static MtDesc shareMD() {
        return (MtDesc) ThreadBasedCache.get()[1];
    }

    public static <T> FirstCollection<T> shareFC(T first, Collection<T> collection) {
        FirstCollection<T> fc = Helpers.cast(ThreadBasedCache.get()[3]);
        fc.first = first;
        fc.target = collection;
        return fc;
    }

    public static void write(Collection<Context> contexts, ZipOutputStream zos, boolean close) throws IOException {
        for(Context ctx : contexts) {
            ZipEntry ze = new ZipEntry(ctx.getName());
            try {
                zos.putNextEntry(ze);
                ctx.get().writeToStream(zos);
                zos.closeEntry();
            } catch (Throwable e) {
                if(e instanceof ZipException && e.getMessage().startsWith("duplicate entry")) {
                    CmdUtil.warning("重复的文件名! " + ctx.getName());
                } else throw e;
            }
        }

        if(close)
            ZipUtil.close(zos);
    }

    @Nonnull
    public static List<Context> prepareContexts(@Nonnull Map<String, InputStream> classes) {
        List<Context> arr = new ArrayList<>(classes.size());
        for(Map.Entry<String, InputStream> entry : classes.entrySet()) {
            arr.add(new Context(entry.getKey().replace('\\', '/'), entry.getValue()));
        }
        return arr;
    }

    public static Thread createResourceWriter(@Nonnull ZipOutputStream zos, @Nonnull Map<String, InputStream> resources) {
        Thread resourceWriter = new Thread(new ResWriter(zos, resources), "Resource Writer");
        resourceWriter.setDaemon(true);
        resourceWriter.start();
        return resourceWriter;
    }

    static void waitfor(String name, Consumer<Context> consumer, List<List<Context>> ctxs) {
        ArrayList<Thread> threads = new ArrayList<>();

        Thread t;

        threads.ensureCapacity(ctxs.size());
        for(int i = 0, e = ctxs.size();i < e; i++) {
            threads.add(t = new Worker(ctxs.get(i), consumer, name + i));
            t.start();
        }

        try {
            for(Thread t2 : threads) {
                t2.join();
            }
        } catch (InterruptedException ignored) {}

        threads.clear();
    }

    public static boolean isPackageSame(String packageA, String packageB) {
        int ia = packageA.lastIndexOf('/');

        if(packageB.lastIndexOf('/') != ia)
            return false;
        if(ia == -1)
            return true;

        return packageA.regionMatches(0, packageB, 0, ia);
    }

    public static String mapClassName(Map<String, String> classMap, String name) {
        // should indexOf(';') ?
        String nn = mapClassName(classMap, name, false, 0, name.length());
        return nn == null ? name : nn;
    }

    @Nullable
    public static String mapOwner(Map<? extends CharSequence, String> map, CharSequence name, boolean file) {
        return name == null ? null : mapClassName(map, name, file, 0, name.length());
    }

    @Nullable
    private static String mapClassName(Map<? extends CharSequence, String> map, CharSequence name, boolean file, int s, int e) {
        if (e == 0)
            return "";
        String b;

        CharList cl = (CharList) ThreadBasedCache.get()[4];
        cl.clear();

        if ((b = map.get(cl.append(name, s, e - s - (file ? 6 : 0)))) != null) {
            return file ? (b + ".class") : b;
        }

        boolean endSemi = name.charAt(e - 1) == ';';
        switch (name.charAt(s)) {
            // This is for [Field type]
            case 'L':
                if (endSemi) {
                    if(file)
                        throw new IllegalArgumentException("Unk cls " + name.subSequence(s + 1, e - 1));
                    String result = mapClassName(map, name, false, s + 1, e - 1);
                    cl.clear();
                    return result == null ? null : cl.append('L').append(result).append(';').toString();
                } // class name starts with L
                break;
            // This is for [Field type, Class type]
            case '[':
                if(file)
                    throw new IllegalArgumentException("Unk arr " + name.subSequence(s, e));

                int arrLv = s;
                while (name.charAt(s) == '[') {
                    s++;
                }
                arrLv = s - arrLv;

                if(name.charAt(s) == 'L') {
                    if(endSemi) {
                        String result = mapClassName(map, name, false, s + 1, e - 1);
                        if(result == null)
                            return null;

                        cl.clear();
                        for (int i = 0; i < arrLv; i++) {
                            cl.append('[');
                        }
                        return cl.append('L').append(result).append(';').toString();
                    } else {
                        throw new IllegalArgumentException("Unk arr 1 " + name);
                    }
                } else if(endSemi) // primitive array ?
                    throw new IllegalArgumentException("Unk arr 2 " + name);
                break;
        }

        int dollar = TextUtil.limitedIndexOf(name, '$', s, e);

        cl.clear();
        if (dollar != -1 && (b = map.get(cl.append(name, s, dollar - s))) != null) {
            cl.clear();
            return cl.append(b).append('$').append(name, dollar + s, e - s - dollar).toString();
        }

        return file ? name.subSequence(s, e).toString() : null;
    }

    @SuppressWarnings("unchecked")
    public static String transformMethodParam(Map<String, String> classMap, String md) {
        if(md.length() <= 4) // min = ()La;
            return md;

        ArrayList<Type> types = (ArrayList<Type>) ThreadBasedCache.get()[5];
        types.clear();
        ParamHelper.parseMethod(md, types);

        boolean gt0 = false;
        String str;
        for (int i = 0; i < types.size(); i++) {
            Type type = types.get(i);
            if ((str = mapOwner(classMap, type.owner, false)) != null) {
                type.owner = str;
                gt0 = true;
            }
        }
        try {
            return gt0 ? ParamHelper.getMethod(types) : md;
        } finally {
            types.clear();
        }
    }

    public static String transformFieldType(Map<String, String> classMap, String fd) {
        //if(fd.length() <= 2) // min = La;
        //    return fd;

        char first = fd.charAt(0);
        if(first == '[') {
            int pos = fd.lastIndexOf('[') + 1;
            first = fd.charAt(pos);
        }
        if(first != 'L')
            return fd;

        //Type type = ParamHelper.parseField(fd);
        String str = mapOwner(classMap, fd/*type.owner*/, false);
        //if(str != null) {
            //type.owner = str;
            //return ParamHelper.getField(type);
        //}
        return str == null ? fd : str;
    }

    public static List<Context> prepareContextFromZip(File input, Charset charset) throws IOException {
        ZipInputStream inputJar = new ZipInputStream(new FileInputStream(input), charset);

        List<Context> ctxs = new ArrayList<>(1024);

        while (true) {
            ZipEntry zn;
            try {
                zn = inputJar.getNextEntry();
                if(zn == null)
                    break;
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("可能是编码错误! 请指定编码", e);
            }
            if (zn.isDirectory()) continue;
            if (zn.getName().endsWith(".class")) {
                ctxs.add(new Context(zn.getName().replace('\\', '/'), new ByteList().readStreamArrayFully(inputJar)));
            }
        }

        inputJar.close();

        return ctxs;
    }

    public static ZipFile prepareInputFromZip(File input, Charset charset, Map<String, InputStream> streams) throws IOException {
        ZipFile inputJar = new ZipFile(input, ZipFile.OPEN_READ, charset);

        Enumeration<? extends ZipEntry> en = inputJar.entries();
        while (en.hasMoreElements()) {
            ZipEntry zn;
            try {
                zn = en.nextElement();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("可能是编码错误! 请指定编码", e);
            }
            if (zn.isDirectory()) continue;
            //if (zn.getName().endsWith(".class")) {
                streams.put(zn.getName(), inputJar.getInputStream(zn));
            //} else {
            //    streams.put(zn.getName(), inputJar.getInputStream(zn));
            //}
        }

        return inputJar;
    }
}
