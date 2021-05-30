package roj.asm.remapper;
/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 *
 * File version : 不知道...
 * Author: R__
 * Filename: RemapperV1.java
 */

import roj.asm.remapper.util.Context;
import roj.io.FileUtil;
import roj.util.Helpers;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

public class Main {
    public static void main(String[] args) throws Exception {
        long time = System.currentTimeMillis();
        if(args.length < 2)
            throw new IllegalArgumentException("Usage: " + Main.class.getName() + " <fileList/zip> <outputName> [config] \n" +
                    "    available config:\n" +
                    "      mappingPath => 指定映射表保存位置\n" +
                    "      libPath => 指定用户类保存位置\n" +
                    "      cachePath => 指定缓存保存位置\n" +
                    "      remapClassName => 重映射类名\n" +
                    "      zipped => 指定fileList为zip文件\n" +
                    "      charset => 文件编码 (UTF_8 or US_ASCII)\n" +
                    "      reverse => 反转srg\n" +
                    "      singleThread => 单线程模式");
        File input = new File(args[0]);

        String mappingPath = "mcp-srg.srg";
        String libPath = "../class/";
        String cachePath = "util/cache.bin";
        String workPath = null;

        boolean singleThread = false;

        boolean zipped = false;
        Charset charset = File.separatorChar == '/' ? StandardCharsets.UTF_8 : StandardCharsets.US_ASCII;

        boolean remapClassName = false;
        boolean reverse = false;

        RemapperV2 remapper = new RemapperV2();

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
                case "remapClassName":
                    remapClassName = true;
                    break;
                case "zipped":
                    zipped = true;
                    break;
                case "charset":
                    charset = Charset.forName(args[++i]);
                    break;
                case "workPath":
                    workPath = args[++i];
                    break;
                default:
                    throw new IllegalArgumentException("Unknown config entry " + args[i]);
            }
        }

        String output = args[1];


        remapper.prepareEnv(new File(mappingPath), new File(libPath), new File(cachePath), reverse, true);

        Map<String, InputStream> streams = new HashMap<>();
        Map<String, InputStream> classes = new HashMap<>();
        Closeable closeable = null;

        if(!zipped) {
            if(workPath == null) {
                workPath = new File("").getAbsolutePath() + File.separatorChar;
            }
            FileUtil.prepareInputFromTxt(input, workPath, charset, streams);
        } else {
            closeable = Util.prepareInputFromZip(input, charset, streams);
        }

        Helpers.filter(streams, classes, (key) -> key.endsWith(".class"));

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File(output)));

        Thread resourceWriter = Util.createResourceWriter(zos, streams);

        List<Context> arr = remapper.remap(classes, singleThread);

        resourceWriter.join();
        if(closeable != null)
            closeable.close();

        if(remapClassName) {
            new Renamer(remapper).remap(singleThread, arr);
        }

        Util.write(arr, zos, true);

        System.out.println("Memory: " + (Runtime.getRuntime().totalMemory() >> 20) + " MB");
        System.out.println("Total: " + (System.currentTimeMillis() - time) + "ms");
    }
}