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

import roj.asm.annotation.AnnotationProcessor;
import roj.asm.annotation.OpenAny;
import roj.collect.MyHashSet;
import roj.collect.TrieTreeSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * FMD @OpenAny Processor
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/30 19:59
 */
public final class FMDOAProc extends AnnotationProcessor {
    public FMDOAProc() {
        super();
        Shared.load_S2M_Map();
    }

    @Override
    public boolean hook() {
        return false;
    }

    public void internalInit(String atPath, File cp, StringBuilder initial) {
        this.atPath = new File(atPath);
        File dir = this.atPath.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalArgumentException("无法创建AT保存路径");
        }

        if(classData.isEmpty()) {
            try {
                if (cp.isDirectory()) {
                    List<String> prefixes = Shared.MAIN_CONFIG.getDot("FMD配置.需要进行AccessTransform的前缀").asList().asStringList();
                    TrieTreeSet set = new TrieTreeSet();
                    set.addAll(prefixes);

                    // check if is default
                    if(prefixes.size() == 2 && prefixes.get(0).equals("net/minecraft/") && prefixes.get(1).equals("net/minecraftforge/")) {
                        initZip(new File(cp, Shared.MERGED_FILE_NAME + ".jar"), set);
                    } else {
                        for (String path : cp.list()) {
                            if (path.startsWith("[noread]") || !(path.endsWith(".jar") || path.endsWith(".zip")))
                                continue;
                            initZip(new File(cp, path), set);
                        }
                    }
                }
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }

            if(Shared.DEBUG) {
                System.out.println("CTransform.size(): " + classData.size());
                System.out.println("CLoadedFiles.size(): " + openStreams.size());
            }
        }

        FMDMain.runAtMainThread(this);

        if (initial != null) {
            atData = initial;
        }
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        filer = env.getFiler();
        reporter = env.getMessager();
    }

    private void initZip(File file, TrieTreeSet set) throws IOException {
        if (!file.exists() || file.isDirectory() || file.length() == 0) return;
        ZipFile zf;
        try {
            zf = new ZipFile(file);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        Enumeration<? extends ZipEntry> en = zf.entries();
        ZipEntry zn = null;
        boolean added = false;
        while (en.hasMoreElements()) {
            try {
                zn = en.nextElement();
            } catch (IllegalArgumentException e) {
                on(Diagnostic.Kind.WARNING, "非UTF-8编码的ZIP文件 " + file + " 中出现了非ASCII字符, 上一个节点: " + (zn == null ? "~NULL~" : zn.getName()));
                return;
            }
            if (zn.isDirectory()) continue;
            if (!zn.getName().endsWith(".class")) continue;
            if(set.startsWith(zn.getName())) {
                if (classData.put(zn.getName(), zf.getInputStream(zn)) != null) {
                    on(Diagnostic.Kind.NOTE, "重复的类文件 " + zn.getName());
                }
                added = true;
            }
        }

        if(added)
            openStreams.add(zf);
        else
            zf.close();
    }

    @Override
    public void processAClass(List<OpenAny> list, Map<String, Set<String>> collectedAT) {
        for (int i = 0; i < list.size(); i++) {
            OpenAny oa = list.get(i);

            String classQualifiedName = oa.value().replace('.', '/').replace(':', '/');
            Set<String> data = collectedAT.computeIfAbsent(classQualifiedName, (key) -> new MyHashSet<>());

            for (String s : oa.names()) {
                data.add(Shared.srg2mcp.getOrDefault(s, s));
            }
        }
    }
}