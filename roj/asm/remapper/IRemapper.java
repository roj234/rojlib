package roj.asm.remapper;

import roj.asm.remapper.util.Context;
import roj.asm.remapper.util.FlDesc;
import roj.asm.remapper.util.MtDesc;
import roj.collect.FastSetList;
import roj.collect.MyHashMap;
import roj.ui.CmdUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/20 12:46
 */
public abstract class IRemapper extends Mapping {
    public static final int FILE_HEADER = 0xf0E72cEb;

    public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("fmd.debugRemap", "false"));

    /**
     * SrgMap data
     */
    Map<String, Collection<String>> librarySupers;

    /**
     * Self data
     */
    Map<String, Collection<String>> selfSuperMap;

    public IRemapper() {
        librarySupers = new MyHashMap<>(128);
    }

    public IRemapper(IRemapper remapper) {
        this.classMap = remapper.classMap;
        this.fieldMap = remapper.fieldMap;
        this.methodMap = remapper.methodMap;
        this.librarySupers = remapper.librarySupers;
    }

    abstract void saveCache(File file) throws IOException;

    abstract void readCache(File file) throws IOException;


    final void generateSuperMap(File folder) {
        if(!folder.isDirectory()) {
            throw new IllegalArgumentException(new FileNotFoundException(folder.getAbsolutePath()));
        }

        generateSuperMap(Arrays.asList(folder.listFiles()));
    }

    abstract void generateSuperMap(List<File> file);

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

    /**
     * Remap
     */

    public final List<Context> remap(@Nonnull Map<String, InputStream> classes, boolean singleThread) {
        List<Context> arr = Util.prepareContexts(classes);

        remap(singleThread, arr);

        return arr;
    }

    public abstract void remap(boolean singleThread, List<Context> arr);

    protected final void makeInheritMap(Map<String, Collection<String>> superMap) {
        FastSetList<String> l = new FastSetList<>();

        List<String> toRemove = new ArrayList<>();

        // 从一级继承构建所有继承, note: 是所有输入
        for (Map.Entry<String, Collection<String>> entry : superMap.entrySet()) {
            if(entry.getValue().getClass() == FastSetList.class) continue; // done

            String name = entry.getKey();

            recursionLibSupers(superMap, l, entry.getValue());
            for (int i = l.size() - 1; i >= 0; i--) {
                if(!classMap.containsKey(l.get(i))) {
                    l.remove(i); // 删除不存在映射的爹
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

    protected final void generateSelfSuperMap() {
        Map<String, Collection<String>> selfSuperMap = new MyHashMap<>(this.librarySupers);
        selfSuperMap.putAll(this.selfSuperMap); // replace lib class
        this.selfSuperMap = selfSuperMap;

        makeInheritMap(selfSuperMap);
    }

    @SuppressWarnings("unchecked")
    public final void prepareEnv(@Nonnull Object map, @Nullable Object libPath, @Nullable File cacheFile, boolean reverse, boolean save) throws IOException {
        clear();

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
                saveCache(cacheFile);
        } else {
            try {
                readCache(cacheFile);
            } catch (Throwable e) {
                if(!(e instanceof FileNotFoundException)) {
                    CmdUtil.warning("缓存读取失败!");
                    e.printStackTrace();
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
                saveCache(cacheFile);
            }
        }
    }

    public void clear() {
        classMap.clear();
        fieldMap.clear();
        methodMap.clear();
        librarySupers.clear();
    }


    /**
     * Transfer data
     */
    public void extend(IRemapper from) {
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
}
