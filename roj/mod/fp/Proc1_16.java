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
package roj.mod.fp;

import roj.asm.mapper.CodeMapper;
import roj.asm.mapper.ConstMapper;
import roj.asm.mapper.Util;
import roj.asm.mapper.util.Context;
import roj.collect.MyHashSet;
import roj.collect.TrieTreeSet;
import roj.concurrent.OperationDone;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CMapping;
import roj.io.FileUtil;
import roj.io.ZipFileWriter;
import roj.mod.FMDMain;
import roj.mod.MCLauncher;
import roj.mod.remap.ClassMerger;
import roj.mod.util.Patcher;
import roj.text.CharList;
import roj.ui.CmdUtil;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static roj.mod.Shared.*;

/**
 * 1.13 or upper File Processor
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/30 11:31
 */
public final class Proc1_16 extends Processor {
    private static File mcpConfigFile;

    File forgeInstallerPath, forgeUniv, mcClearSrg;
    InputStream serverLzmaInput;

    Map<String, Map<String, List<String>>> paramMap;

    public static Object[] find116Files(File librariesPath, Collection<String> values) {
        File specialSource;
        CharList forge = new CharList();

        for (String s : values) {
            if (s.startsWith("net/minecraftforge/forge/")) {
                if(s.endsWith("-universal.jar") || s.endsWith("-client.jar")) {
                    continue;
                }

                forge.append(s);
                forge.setIndex(forge.length() - 4);
                break;
            }
        }

        List<File> files = getSpecialSource(librariesPath);
        if(files.isEmpty()) {
            throw new IllegalArgumentException("未找到net/md-5/SpecialSource/*.jar");
        }

        int len = forge.length();

        if (len == 0)
            throw new IllegalArgumentException("未找到net/minecraftforge/forge/xxx/forge-xxx.jar");

        File srgCli = new File(librariesPath, forge.append("-client.jar").toString());

        File forgeInstaller;
        try {
            forge.setIndex(len);
            String forgeInst = forge.append("-installer.jar").toString();
            String instUrl = MAIN_CONFIG.get("FMD配置").asMap().getString("ForgeMaven仓库地址") + forgeInst;
            MCLauncher.downloadAndVerifyMD5(instUrl, forgeInstaller = new File(TMP_DIR, forgeInst.substring(
                    forgeInst.lastIndexOf('/') + 1)), true);
        } catch (IOException e) {
            CmdUtil.warning("文件下载失败, 请检查网络", e);
            System.exit(-4);
            return null;
        }

        forge.setIndex(len);
        File forgeNormal = new File(librariesPath, forge.append(".jar").toString());
        forge.setIndex(len);
        File forgeUniversal = new File(librariesPath, forge.append("-universal.jar").toString());

        InputStream server_lzma;
        File mcClear;
        try {
            ZipFile zf = new ZipFile(forgeInstaller); // 顺便read一下
            ZipEntry ze = zf.getEntry("data/server.lzma");
            if (ze == null)
                throw OperationDone.INSTANCE;
            server_lzma = new ByteList().readStreamArrayFully(zf.getInputStream(ze)).asInputStream();

            ze = zf.getEntry("install_profile.json");
            if (ze == null)
                throw OperationDone.INSTANCE;
            CMapping installconf = JSONParser.parse(ByteReader.readUTF(new ByteList().readStreamArrayFully(zf.getInputStream(ze)))).asMap();

            zf.close();

            String mcSrgClient = installconf.getDot("data.MC_SRG.client").asString();
            mcClear = new File(librariesPath, MCLauncher.mavenPath(mcSrgClient.substring(1, mcSrgClient.length() - 1)).toString());
        } catch (IOException e) {
            throw new IllegalArgumentException("IO eror, forge安装器 '" + forgeInstaller.getAbsolutePath() + "'", e);
        } catch (OperationDone | ParseException e) {
            throw new IllegalArgumentException("forge安装器 '" + forgeInstaller.getAbsolutePath() + "' 有错误", e);
        }


        try {
            Class.forName("net.md_5.specialsource.SpecialSource");
        } catch (Throwable ignored) {
            try {
                ClassLoader ldr = Proc1_16.class.getClassLoader();
                for(File lib : files) {
                    addURL.invoke(ldr, lib.toURI().toURL());
                }
                Class.forName("net.md_5.specialsource.SpecialSource");
            } catch (Throwable e) {
                CmdUtil.error("SpecialSource的依赖加载失败...", e);
                CmdUtil.error("您可以手动将SpecialSource的文件加入classpath");
                System.exit(-1);
            }
        }

        return new Object[]{
                srgCli,
                forgeNormal,
                forgeUniversal,
                server_lzma,
                mcClear
        };
    }

    @Nonnull
    private static List<File> getSpecialSource(File librariesPath) {
        return FileUtil.findAllFiles(librariesPath, (file -> {
            String n = file.getName();
            // f@@k them ALL
            return n.startsWith("SpecialSource") || n.startsWith("asm-") || n.startsWith("guava-") || n.startsWith("jopt-simple-");
        }));
    }

    public Proc1_16(File mcServer, @Nullable Map<String, Map<String, List<String>>> paramMap, Object[] files) {
        super((File) files[0], mcServer);
        this.forgeJar = (File) files[1]; // normal
        this.forgeUniv = (File) files[2]; // univer
        this.paramMap = paramMap;
        this.serverLzmaInput = (InputStream) files[3];
        this.mcClearSrg = (File) files[4];

        parallel.pushRunnable(this);
    }

    public static void handleMCPConfig(File mcpConfigFile) {
        Proc1_16.mcpConfigFile = mcpConfigFile;
    }

    public int run0() {
        File tmp = new File(TMP_DIR, mcServer.getName() + "_Rmp.server");
        tmp.deleteOnExit();

        TrieTreeSet set = new TrieTreeSet();
        FMDMain.readTextList(set::add, "FMD配置.忽略服务端jar中以以下文件名开头的文件");

        try {
            Helper1_16.remap116_SC(tmp, mcServer, mcpConfigFile, set);
        } catch (Throwable e) {
            if(e instanceof IOException)
                CmdUtil.warning("读取失败, " + mcServer.getAbsolutePath() + " or " + mcpConfigFile.getAbsolutePath(), e);
            else
                CmdUtil.error("SpecialSource 错误", e);
            return -1;
        }

        Patcher patcher = new Patcher();
        patcher.setup113(serverLzmaInput, Collections.emptyMap()); // 读取服务端补丁，客户端打了
        CmdUtil.info("补丁已加载");

        ConstMapper rmp = new ConstMapper();
        rmp.loadMap(new File(BASE, "/util/mcp-srg.srg"), true);
        rmp.loadLibraries(Arrays.asList(mcJar, tmp));
        CmdUtil.info("映射表已加载");

        List<Context>[] ctxs;
        try {
            List<Context> servCtxs = Util.ctxFromZip(tmp, StandardCharsets.UTF_8);

            for (int i = 0; i < servCtxs.size(); i++) {
                Context ctx = servCtxs.get(i);
                ByteList result = patcher.patchServer(ctx.getFileName(), ctx.get()); // 服务端的文件已经映射，接下来是打补丁
                if (result != null) ctx.set(result);
            }

            if(patcher.errorCount != 0)
                CmdUtil.warning("补丁失败数量: " + patcher.errorCount);
            else
                CmdUtil.success("补丁全部成功!");
            patcher.reset();

            List<Context> fgCtxs = Util.ctxFromZip(forgeJar, StandardCharsets.UTF_8);

            if(forgeUniv != null) { // forge-universal
                fgCtxs.addAll(Util.ctxFromZip(forgeUniv, StandardCharsets.UTF_8)); // 理论上不会有重复
            }

            List<Context> clientCtxs = readZipReplaced(StandardCharsets.UTF_8, mcJar, mcClearSrg);

            if (DEBUG) {
                CmdUtil.info("客户端文件数量: " + clientCtxs.size());
                CmdUtil.info("服务端文件数量: " + servCtxs.size());
                CmdUtil.info("Forge文件数量: " + fgCtxs.size());
            }
            ctxs = Helpers.cast(new List<?>[] {
                    clientCtxs, servCtxs, fgCtxs
            });
        } catch (IOException e) {
            CmdUtil.error("IO", e);
            return -1;
        }

        tmp.delete();

        ClassMerger merger = new ClassMerger();
        ctxs[1] = new ArrayList<>(merger.process(ctxs[0], ctxs[1]));
        ctxs[0].clear();
        ctxs[0] = null;

        CmdUtil.info("服务端专属 " + merger.serverOnly  + ", 客户端专属 " + merger.clientOnly + ", 共用 " + merger.both);
        CmdUtil.info("合并了" + merger.mergedField + "个字段, " + merger.mergedMethod + "个方法, 覆盖了" + merger.replaceMethod + "个方法");

        CodeMapper nameRmp = new CodeMapper(rmp);
        nameRmp.setParamRemappingV2(paramMap);

        threadWait(com(rmp, nameRmp, 1, ctxs),
                   com(new ConstMapper(rmp), nameRmp, 2, ctxs));

        try (ZipFileWriter zfw = new ZipFileWriter(new File(BASE, "class/" + MERGED_FILE_NAME + ".jar"))) {
            FileTime time = FileTime.from(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            List<Context> merged = ctxs[1];
            for (int i = 0; i < merged.size(); i++) {
                Context ctx = merged.get(i);
                ByteList list;
                try {
                    list = ctx.getCompressedShared();
                } catch (Throwable e) {
                    CmdUtil.warning(ctx.getFileName() + " 验证失败", e);
                    continue;
                }
                zfw.writeNamed(ctx.getFileName(), list);
            }
            List<Context> fgResult = ctxs[2];
            merged.clear();
            for (int i = 0; i < fgResult.size(); i++) {
                Context ctx = fgResult.get(i);
                ByteList list;
                try {
                    list = ctx.getCompressedShared();
                } catch (Throwable e) {
                    CmdUtil.warning(ctx.getFileName() + " 验证失败", e);
                    continue;
                }
                zfw.writeNamed(ctx.getFileName(), list);
            }
            fgResult.clear();

            if (DEBUG)
                CmdUtil.success("I/O完毕");
        } catch (IOException e) {
            CmdUtil.error("文件写入失败", e);
            return -1;
        }

        return 0;
    }

    private static List<Context> readZipReplaced(Charset charset, File mcJar, File forgeReplaced) throws IOException {
        MyHashSet<String> map = new MyHashSet<>();
        List<Context> ctx = new ArrayList<>();
        ByteList bl = new ByteList();

        ZipFile zf = new ZipFile(forgeReplaced, charset);
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry zn;
            try {
                zn = en.nextElement();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("可能是编码错误! 请指定编码", e);
            }
            if (zn.isDirectory()) continue;
            if (zn.getName().endsWith(".class")) {
                map.add(zn.getName());
                InputStream in = zf.getInputStream(zn);
                Context c = new Context(zn.getName().replace('\\', '/'), bl.readStreamArrayFully(in).toByteArray());
                in.close();
                bl.clear();
                ctx.add(c);
            }
        }

        zf.close();
        zf = new ZipFile(mcJar, charset);
        en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry zn;
            try {
                zn = en.nextElement();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("可能是编码错误! 请指定编码", e);
            }
            if (zn.isDirectory()) continue;
            if (zn.getName().endsWith(".class") && map.add(zn.getName())) {
                InputStream in = zf.getInputStream(zn);
                Context c = new Context(zn.getName().replace('\\', '/'), bl.readStreamArrayFully(in).toByteArray());
                in.close();
                bl.clear();
                ctx.add(c);
            }
        }

        zf.close();

        return ctx;
    }

    private static Runnable com(ConstMapper mainRmp, CodeMapper nameRmp, int i, List<Context>[] arr) {
        List<Context> ctxs = arr[i];
        return () -> {
            mainRmp.remap(true, ctxs);
            nameRmp.remap(true, ctxs);

            if (DEBUG)
                CmdUtil.success(i + "处理完毕");
        };
    }

    @Override
    public void accept(Integer integer, File file) {
    }
}
