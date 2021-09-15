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
package ilib.asm;

import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.text.CharList;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class Preloader {
    private static final List<Function<String, UnaryOperator<byte[]>>> fileProcessors = new ArrayList<>();

    public static void doPreload() {
        for (String clazz : findPreloadClasses()) {
            try {
                Launch.classLoader.addTransformerExclusion(clazz);
                Class.forName(clazz, true, Launch.classLoader);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public static void registerTransformer(IClassTransformer transformer) {
        Loader.last(transformer);
    }

    /**
     * 不推荐使用
     */
    public static void registerFileProcessor(Function<String, UnaryOperator<byte[]>> processor) {
        fileProcessors.add(processor);
    }

    public static Set<String> visitedFiles = new MyHashSet<>();

    private static Collection<String> findPreloadClasses() {
        File file = new File("mods/");
        if (!file.isDirectory()) return Collections.emptySet();
        File[] files = file.listFiles();
        if (files == null) {
            System.err.println("Failed to load mod classes");
            return Collections.emptySet();
        }

        Collection<String> list = new ArrayList<>();

        for (File file1 : files) {
            if (file1.isFile() && (file1.getName().endsWith(".jar") || file1.getName().endsWith(".zip"))) {
                try(ZipFile jar = new ZipFile(file1)) {
                    ZipEntry entry = jar.getEntry("META-INF/Preload.ini");
                    if (entry != null) {
                        Loader.logger().info("Found Preload ASM config in " + file1.getName());
                        Loader.logger().info("Adding classpath... ");
                        Launch.classLoader.addURL(file1.toURI().toURL());
                        visitedFiles.add(file1.getAbsolutePath());

                        String text = new String(IOUtil.read(jar.getInputStream(entry)), StandardCharsets.UTF_8);
                        CharList cl = new CharList();
                        for (int i = 0; i < text.length(); i++) {
                            char c = text.charAt(i);
                            switch (c) {
                                case ' ':
                                    continue;
                                case ';':
                                    if (cl.length() > 0) {
                                        list.add(cl.toString());
                                        cl.clear();
                                    }
                                    break;
                                default:
                                    cl.append(c);
                            }
                        }
                        if (cl.length() > 0) {
                            list.add(cl.toString());
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return list;
    }

    public static List<Function<String, UnaryOperator<byte[]>>> getFileProcessors() {
        return fileProcessors;
    }
}
