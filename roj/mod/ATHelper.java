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
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.io.ZipUtil;
import roj.ui.CmdUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static roj.mod.Shared.BASE;
import static roj.mod.Shared.TMP_DIR;

/**
 * Text AT Processor
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/30 19:59
 */
public final class ATHelper {
    private static final List<ZipFile>       libraries = new ArrayList<>();
    private static final Map<String, byte[]> classes   = new MyHashMap<>();

    private File binJar;
    private ByteList atCfg;

    public void init(String name, ByteList atCfg, Map<String, Collection<String>> map) throws IOException {
        if(libraries.isEmpty()) {
            initZip(new File(BASE, "class"));
            Shared.load_S2M_Map();
        }

        this.atCfg = atCfg;
        File binJar = this.binJar = new File(TMP_DIR, "at-" + atCfg.hashCode() + ".jar");
        binJar.deleteOnExit();

        MyHashSet<String> tmp = new MyHashSet<>();
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(binJar));
        for (Map.Entry<String, Collection<String>> entry : map.entrySet()) {
            String name1 = entry.getKey().replace('.', '/') + ".class";
            ZipEntry ze = new ZipEntry(name1);
            zos.putNextEntry(ze);

            tmp.clear();
            for (String s : entry.getValue()) {
                tmp.add(Shared.srg2mcp.getOrDefault(s, s));
            }
            byte[] transform = transform(name1, tmp);
            if(transform == null) {
                CmdUtil.warning("无法转换 " + entry.getKey());
            } else {
                zos.write(transform);
            }
            zos.closeEntry();
        }
        ZipUtil.close(zos);
    }

    public ByteList getAtCfgBytes() {
        return atCfg;
    }

    public String getFakeJarPath() {
        return binJar.getAbsolutePath();
    }

    public static void gc() throws IOException {
        classes.clear();
        for (ZipFile zf : libraries) {
            zf.close();
        }
        libraries.clear();
    }

    static byte[] transform(String name, Set<String> names) {
        byte[] is = classes.get(name);
        if(is == null) {
            for (int i = 0; i < libraries.size(); i++) {
                ZipFile zf = libraries.get(i);
                ZipEntry ze = zf.getEntry(name);
                if(ze != null) {
                    try {
                        classes.put(name, is = IOUtil.read(zf.getInputStream(ze)));
                        break;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if(is != null) {
            is = AccessTransformer.openSome(is, names);
        }
        return is;
    }

    static void initZip(File cp) throws IOException {
        for (File path : cp.listFiles()) {
            String name = path.getName();
            if (name.startsWith("[noread]") || !(name.endsWith(".jar") || name.endsWith(".zip")))
                continue;
            libraries.add(new ZipFile(path));
        }
    }
}