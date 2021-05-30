/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 *
 * File version : 不知道...
 * Author: R__
 * Filename: HotReload.java
 */
package roj.mod;

import roj.asm.Parser;
import roj.asm.struct.AccessData;
import roj.io.AppendOnlyCache;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.ui.UIUtil;
import roj.util.Base64;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.*;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.Set;

public final class HotReload {
    public static void main(String[] args) throws IOException {
        System.out.println("热重载class文件测试工具 1.0 beta");
        if(args.length == 0) {
            System.out.println("参数缺失: path");
            return;
        }

        File path = new File(TextUtil.concat(args, ' '));


        System.out.println("B64Encode: " + Base64.encode(ByteWriter.encodeUTF(path.getAbsolutePath()), new CharList()));

        AppendOnlyCache aoc = new AppendOnlyCache(new File(path, "modified.bin"));
        aoc.clear();
        while (true) {
            String ques = UIUtil.userInput("class路径... or 留空转换 or CLR 清除");

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

                byte[] buf = IOUtil.readFully(new FileInputStream(clz));
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
        AppendOnlyCache aoc = new AppendOnlyCache(new File(path.toString(), "modified.bin"));
        aoc.read();

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

    private static class Runner implements Runnable {
        final WatchService watcher;
        final Instrumentation inst;
        final Path path;

        private Runner(WatchService watcher, Instrumentation inst, Path path) {
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