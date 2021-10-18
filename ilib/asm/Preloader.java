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

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.common.discovery.ASMDataTable.ASMData;
import roj.collect.MyHashMap;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

import static ilib.asm.Loader.logger;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class Preloader {
    private static final MyHashMap<String, Function<byte[], byte[]>> fileProcessors = new MyHashMap<>();

    static {
        Thread ldr = new Thread("Class Preloader") {
            @Override
            public void run() {
                logger.info("Preloading minecraft classes...");
                LockSupport.parkNanos(1_000_000_000L);
                if (Thread.interrupted()) return;
                char[] chars = new char[3];
                int count = 0;
                try {
                    LaunchClassLoader cl = Launch.classLoader;
                    for (int i = 0; i < 26; i++) {
                        chars[2] = (char) TextUtil.digits[i + 10];
                        cl.loadClass(new String(chars, 0, 1));
                        LockSupport.parkNanos(100_000L);
                        count++;
                        for (int j = 0; j < 26; j++) {
                            chars[1] = (char) TextUtil.digits[i + 10];
                            cl.loadClass(new String(chars, 0, 2));
                            LockSupport.parkNanos(100_000L);
                            count++;
                            for (int k = 0; k < 26; k++) {
                                chars[0] = (char) TextUtil.digits[i + 10];
                                cl.loadClass(new String(chars, 0, 3));
                                LockSupport.parkNanos(100_000L);
                                count++;
                            }
                        }
                    }
                } catch (ClassNotFoundException nfe) {
                    System.out.println("Preloaded: " + count);
                    nfe.printStackTrace();
                }
            }
        };
        ldr.setDaemon(true);
        ldr.start();
    }

    public static void preload() {
        Set<ASMData> all = Loader.ASMTable.getAll("ilib.asm.Preloaded");
        for (ASMData data : all) {
            try {
                Class.forName(data.getClassName(), true, Launch.classLoader);
            } catch (ClassNotFoundException e) {
                Helpers.throwAny(e);
            }
        }
        all.clear();
    }

    public static void registerFileProcessor(String name, Function<byte[], byte[]> processor) {
        Function<byte[], byte[]> fn = fileProcessors.putIfAbsent(name, processor);
        if (fn != null) {
            fileProcessors.put(name, fn.andThen(processor));
        }
    }

    public static Function<byte[], byte[]> getFileProcessor(String entry) {
        return fileProcessors.get(entry);
    }
}
