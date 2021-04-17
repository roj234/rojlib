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

import roj.asm.Parser;
import roj.asm.tree.AccessData;
import roj.io.BoxFile;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.crypt.Base64;
import roj.ui.UIUtil;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.*;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.Set;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/30 19:59
 */
public final class HotReload {
    public static void main(String[] args) throws IOException {
        if(args.length == 0) {
            System.out.println("重载测试工具: 参数缺失: path");
            return;
        }

        File path = new File(TextUtil.concat(args, ' '));

        System.out.println("Path: " + Base64.encode(ByteWriter.encodeUTF(path.getAbsolutePath()), new CharList()));

        BoxFile aoc = new BoxFile(new File(path, "modified.bin"));
        aoc.clear();
        while (true) {
            String ques = UIUtil.userInput("class路径, 留空转换 or CLR 清除");

            if(ques.equals("CLR")) {
                aoc.clear();
            } else if(ques.length() == 0) {
                File lck = new File(path, "mod.lck");
                try(FileOutputStream fos = new FileOutputStream(lck)) {
                   fos.write(0x23);
                }
                System.out.println("重载请求已发送...");
            } else {
                File clz = new File(ques);
                if (!clz.isFile()) {
                    System.out.println("路径不存在");
                    continue;
                }

                byte[] buf = IOUtil.read(new FileInputStream(clz));
                AccessData ad;
                try {
                    ad = Parser.parseAccessDirect(buf);
                } catch (Throwable e) {
                    System.out.println("不是class文件");
                    e.printStackTrace();
                    continue;
                }

                aoc.append(ad.name, new ByteList(buf));
            }
        }
    }

    public static void doEvent(Instrumentation inst, Path path) throws IOException {
        BoxFile aoc = new BoxFile(new File(path.toString(), "modified.bin"));
        aoc.load();

        ByteList rl = new ByteList();
        final Set<String> keys = aoc.keys();

        if(keys.size() == 0) {
            System.err.println("没有数据(0) !");
            return;
        }

        ClassDefinition[] classes = new ClassDefinition[keys.size()];
        try {
            int i = 0;
            for(String k : keys) {
                try {
                    Class<?> t = Class.forName(k.replace('/', '.'), false, HotReload.class.getClassLoader());
                    classes[i++] = new ClassDefinition(t, aoc.get(k, rl).getByteArray());
                    rl.clear();

                } catch (Throwable ignored) {
                    System.err.println("无法找到 " + k);
                }
            }

            if(i != classes.length) {
                if(i == 0) {
                    System.err.println("没有数据(1) !");
                    return;
                }

                ClassDefinition[] clz = new ClassDefinition[i];
                System.arraycopy(classes, 0, clz, 0, i);
                classes = clz;
            }

            inst.redefineClasses(classes);
            System.out.println("对以下对象的操作成功: " + java.util.Arrays.toString(classes));
        } catch (Throwable e) {
            System.err.println("Failed to update class: ");
            e.printStackTrace();
        }

        aoc.close();
    }

    public static void premain(String agentArgs, Instrumentation inst) throws UTFDataFormatException {
        agentArgs = ByteReader.readUTF(Base64.decode(agentArgs, new ByteList()));

        final Path path = Paths.get(agentArgs);

        try {
            WatchService watcher = FileSystems.getDefault().newWatchService();
            path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
            final Thread th = new Thread(new Runner(watcher, inst, path), "FMD.HRcv");
            th.setDaemon(true);
            th.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    static final class Runner implements Runnable {
        final WatchService watcher;
        final Instrumentation inst;
        final Path path;

         Runner(WatchService watcher, Instrumentation inst, Path path) {
            this.watcher = watcher;
            this.inst = inst;
            this.path = path;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    final WatchKey key = watcher.take();

                    for (WatchEvent<?> event : key.pollEvents()) {
                        final WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        final WatchEvent<Path> path1 = (WatchEvent<Path>) event;

                        File flg = new File(path.toString(), path1.context().toString());
                        
                        //文件名不匹配
                        if (!flg.getName().endsWith(".lck") || flg.length() == 0)
                            continue;

                        doEvent(inst, path);
                        
                        try(RandomAccessFile raf = new RandomAccessFile(flg, "rw")) {
                            raf.setLength(0);
                        }
                    }
                    
                    // exit loop if the key is not valid (if the directory was deleted
                    if (!key.reset()) {
                        break;
                    }
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                watcher.close();
            } catch (IOException ignored) {}
        }
    }
}