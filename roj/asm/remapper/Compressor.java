/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 *
 * File version : 不知道...
 * Author: R__
 * Filename: RemapperV2.java
 */
package roj.asm.remapper;

import roj.asm.Parser;
import roj.asm.remapper.util.Context;
import roj.ui.CmdUtil;
import roj.util.ByteList;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;

public class Compressor {
    public static final Map<String, byte[]> result = new HashMap<>();

    public static void main(String[] args) throws IOException {
        long time = System.currentTimeMillis();
        if(args.length != 2)
            throw new IllegalArgumentException("Usage: compress <inputName> <outputName>");

        System.err.println("没做好");

        System.out.println("Compress Done(" + result.size() + "), took " + (System.currentTimeMillis() - time) + "ms");
    }

    public static void compress(boolean singleThread, List<Context> classes) {
        if(singleThread) {

            Context holder = null;

            try {
                for(Context entry : classes) {
                    holder = entry;
                    processOne(entry);
                }
            } catch (Throwable e) {
                throw new RuntimeException("At parsing " + holder, e);
            }
        } else {
            List<List<Context>> splitedContexts = new ArrayList<>();
            List<Context> tmp = new ArrayList<>();

            int splitThreshold = (classes.size() / Runtime.getRuntime().availableProcessors()) + 1;

            int i = 0;
            for (Context entry : classes) {
                if(i >= splitThreshold) {
                    splitedContexts.add(tmp);
                    tmp = new ArrayList<>(splitThreshold);
                    i = 0;
                }
                tmp.add(entry);
                i++;
            }
            splitedContexts.add(tmp);

            i = 0;

            List<Thread> thread = new ArrayList<>();

            for(int e = splitedContexts.size();i < e; i++) {
                Thread t;
                final List<Context> sp = splitedContexts.get(i);
                thread.add(t = new Thread(() -> sp.forEach(Compressor::processOne), "RenameWorker_" + i));
                t.start();
            }

            try {
                for(Thread t : thread) {
                    t.join();
                }
            } catch (InterruptedException ignored) {}

            thread.clear();
        }
    }

    private static void processOne(Context context) {
        try {
            context.set(Parser.parse(context.get(), 0).getBytes());
        } catch (Throwable e) {
            CmdUtil.warning("File " + context.getName() + " couldn't be compress due to ", e);
        }
    }

    @Deprecated
    public static void compress(InputStream is, OutputStream os) throws IOException {
        ByteList byteList = new ByteList();

        try (JarInputStream jis = new JarInputStream(is)) {
            Pack200.newPacker().pack(jis, byteList.asOutputStream());

            byteList.pos(0);

            try(JarOutputStream jos = new JarOutputStream(os)) {
                Pack200.newUnpacker().unpack(byteList.asInputStream(), jos);
            }
        }
    }
}