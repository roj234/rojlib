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

package roj.mod;

import roj.asm.AccessTransformer;
import roj.collect.MyHashSet;
import roj.io.MutableZipFile;
import roj.io.ZipFileWriter;
import roj.ui.CmdUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static roj.mod.Shared.BASE;
import static roj.mod.Shared.TMP_DIR;

/**
 * Text AT Processor
 *
 * @author Roj234
 * @since 2021/5/30 19:59
 */
final class ATHelper {
    static final class Library {
        final MutableZipFile source;
        long lastModify;

        Library(File file) throws IOException {
            source = new MutableZipFile(file, MutableZipFile.FLAG_READ_ATTR);
            lastModify = file.lastModified();
        }
    }

    private static final List<Library> libraries = new ArrayList<>();

    public static void init(String name, Map<String, Collection<String>> map) throws IOException {
        Shared.loadSrg2Mcp();

        if (libraries.isEmpty()) {
            for (File path : new File(BASE, "class").listFiles()) {
                String fn = path.getName().trim().toLowerCase();
                if (!fn.startsWith("[noread]") && (fn.endsWith(".jar") || fn.endsWith(".zip")))
                    libraries.add(new Library(path));
            }
        }
        reopen();

        File binJar = getJar(name);

        MyHashSet<String> tmp = new MyHashSet<>();
        ZipFileWriter zfw = new ZipFileWriter(binJar);
        for (Map.Entry<String, Collection<String>> entry : map.entrySet()) {
            name = entry.getKey().replace('.', '/') + ".class";

            tmp.clear();
            for (String s : entry.getValue()) {
                tmp.add(Shared.srg2mcp.getOrDefault(s, s));
            }
            byte[] transform = transform(name, tmp);
            if(transform == null) {
                CmdUtil.warning("无法转换 " + name);
            } else
                zfw.writeNamed(name, new ByteList(transform));
        }
        zfw.close();

        close();
    }

    public static File getJar(String name) {
        return new File(TMP_DIR, "at-" + name.hashCode() + ".jar");
    }

    public static void gc() throws IOException {
        for (int i = 0; i < libraries.size(); i++) {
            libraries.get(i).source.close();
        }
        libraries.clear();
    }

    private static void reopen() {
        for (int i = libraries.size() - 1; i >= 0; i--) {
            Library lib = libraries.get(i);
            MutableZipFile zf = lib.source;

            try {
                zf.reopen();
                if (zf.file.lastModified() != lib.lastModify) {
                    if (zf.file.isFile()) {
                        zf.read();
                        lib.lastModify = zf.file.lastModified();
                    } else {
                        zf.close();
                        libraries.remove(i);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void close() {
        for (int i = 0; i < libraries.size(); i++) {
            try {
                libraries.get(i).source.tClose();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static byte[] transform(String name, Set<String> names) {
        for (int i = libraries.size() - 1; i >= 0; i--) {
            Library lib = libraries.get(i);
            MutableZipFile zf = lib.source;

            try {
                byte[] is = zf.get(name);
                if (is != null) {
                    return AccessTransformer.openSome(is, names);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        return null;
    }
}