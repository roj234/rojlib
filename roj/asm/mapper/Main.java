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
package roj.asm.mapper;

import roj.asm.mapper.util.Context;
import roj.collect.MyHashMap;
import roj.io.ZipFileWriter;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Mapper 'main' entry
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 18:3
 */
public class Main {
    public static void main(String[] args) throws Exception {
        long time = System.currentTimeMillis();
        if(args.length < 2)
            throw new IllegalArgumentException("Usage: Main <input> <output> [config] \n" +
                    "    配置项:\n" +
                    "      mappingPath => 指定映射位置\n" +
                    "      libPath     => 指定库位置\n" +
                    "      cachePath   => 指定缓存保存位置\n" +
                    "      remapClass  => 重映射类名\n" +
                    "      charset     => 文件编码\n" +
                    "      reverse     => 反转映射\n" +
                    "      singleThread=> 单线程");
        File input = new File(args[0]);

        String mappingPath = "srg.srg";
        String libPath = "class/";
        String cachePath = "cache.bin";

        boolean singleThread = false;

        Charset charset = File.separatorChar == '/' ? StandardCharsets.UTF_8 : StandardCharsets.US_ASCII;

        boolean className = false;
        boolean reverse = false;

        ConstMapper remapper = new ConstMapper();

        for(int i = 2; i < args.length; i++) {
            switch(args[i]) {
                case "singleThread":
                    singleThread = true;
                    System.out.println("单线程模式");
                    break;
                case "mappingPath":
                    mappingPath = args[++i];
                    break;
                case "cachePath":
                    cachePath = args[++i];
                    break;
                case "libPath":
                    libPath = args[++i];
                    break;
                case "reverse":
                    reverse = true;
                    break;
                case "remapClass":
                    className = true;
                    break;
                case "charset":
                    charset = Charset.forName(args[++i]);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown " + args[i]);
            }
        }

        String output = args[1];

        remapper.initEnv(new File(mappingPath), new File(libPath), new File(cachePath), reverse);

        List<Context> arr;
        Map<String, byte[]> streams = new MyHashMap<>();

        arr = Util.ctxFromZip(input, charset, streams);

        ZipFileWriter zfw = new ZipFileWriter(new File(output));

        Thread resourceWriter = Util.writeResourceAsync(zfw, streams);

        remapper.remap(singleThread, arr);

        if(className) {
            new CodeMapper(remapper).remap(singleThread, arr);
        }

        resourceWriter.join();

        for (int i = 0; i < arr.size(); i++) {
            Context ctx = arr.get(i);
            zfw.writeNamed(ctx.getFileName(), ctx.get(true));
        }
        zfw.finish();

        System.out.println("Mem: " + (Runtime.getRuntime().totalMemory() >> 20) + " MB");
        System.out.println("Time: " + (System.currentTimeMillis() - time) + "ms");
    }
}