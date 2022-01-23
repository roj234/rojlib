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
public final class ATHelper {
    static final class Library {
        final MutableZipFile source;
        final long lastModify;

        Library(File file) throws IOException {
            this.source = new MutableZipFile(file, MutableZipFile.FLAG_READ_ATTR);
            lastModify = file.lastModified();
        }
    }

    private static final List<Library> libraries = new ArrayList<>();

    private File binJar;
    private ByteList atCfg;

    public void init(String name, ByteList atCfg, Map<String, Collection<String>> map) throws IOException {
        Shared.load_S2M_Map();
        if (libraries.isEmpty()) {
            for (File path : new File(BASE, "class").listFiles()) {
                String fn = path.getName().trim().toLowerCase();
                if (!fn.startsWith("[noread]") && (fn.endsWith(".jar") || fn.endsWith(".zip")))
                    libraries.add(new Library(path));
            }
        }

        this.atCfg = atCfg;
        File binJar = this.binJar = new File(TMP_DIR, "at-" + name.hashCode() + ".jar");

        MyHashSet<String> tmp = new MyHashSet<>();
        MutableZipFile mzf = new MutableZipFile(binJar);
        mzf.clear();
        for (Map.Entry<String, Collection<String>> entry : map.entrySet()) {
            name = entry.getKey().replace('.', '/') + ".class";

            tmp.clear();
            for (String s : entry.getValue()) {
                tmp.add(Shared.srg2mcp.getOrDefault(s, s));
            }
            byte[] transform = transform(name, tmp);
            if(transform == null) {
                CmdUtil.warning("无法转换 " + entry.getKey());
            } else
                mzf.setFileData(name, new ByteList(transform));
        }
        mzf.store();
        mzf.close();
    }

    public ByteList getAtCfgBytes() {
        return atCfg;
    }

    public String getFakeJarPath() {
        return binJar.getAbsolutePath();
    }

    public static void gc() throws IOException {
        for (int i = 0; i < libraries.size(); i++) {
            libraries.get(i).source.close();
        }
        libraries.clear();
    }

    static byte[] transform(String name, Set<String> names) throws IOException {
        byte[] is = null;
        IOException ex = null;
        for (int i = 0; i < libraries.size(); i++) {
            Library library = libraries.get(i);
            MutableZipFile zf = library.source;
            if (zf.file.lastModified() != library.lastModify) {
                try {
                    zf.close();
                } catch (IOException e) {
                    if (ex == null)
                        ex = e;
                    else
                        ex.addSuppressed(e);
                }

                if (zf.file.isFile()) {
                    try {
                        libraries.set(i, new Library(zf.file));
                    } catch (IOException e) {
                        libraries.remove(i--);
                        if (ex == null)
                            ex = e;
                        else
                            ex.addSuppressed(e);
                    }
                } else {
                    libraries.remove(i--);
                }
            }

            try {
                is = zf.getFileData(name);
                if (is != null)
                    break;
            } catch (IOException e) {
                if (ex != null)
                    e.addSuppressed(ex);
                throw e;
            }
        }
        if (ex != null)
            ex.printStackTrace();
        if(is != null) {
            is = AccessTransformer.openSome(is, names);
        }
        return is;
    }
}