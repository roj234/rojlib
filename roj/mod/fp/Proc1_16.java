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
import roj.collect.TrieTreeSet;
import roj.concurrent.OperationDone;
import roj.concurrent.task.CalculateTask;
import roj.concurrent.task.ExecutionTask;
import roj.io.FileUtil;
import roj.mod.FMDMain;
import roj.mod.MCLauncher;
import roj.mod.remap.ClassMerger;
import roj.mod.util.Patcher;
import roj.text.CharList;
import roj.ui.CmdUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static roj.mod.Shared.*;

/**
 * 1.12 or upper File Processor
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/30 11:31
 */
public final class Proc1_16 extends Processor {
    private static File mcpConfigFile;

    File forgeInstallerPath, forgeUniv;

    Map<String, Map<String, List<String>>> paramMap;

    public static Object[] find116Files(File librariesPath, Collection<String> values) {
        File srgCli, specialSource;
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

        srgCli = new File(librariesPath, forge.append("-client.jar").toString());

        forge.setIndex(len);
        File forgeNormal = new File(librariesPath, forge.append(".jar").toString());
        forge.setIndex(len);
        String forgeInstPath = forge.append("-installer.jar").toString();
        forge.setIndex(len);
        File forgeUniversal = new File(librariesPath, forge.append("-universal.jar").toString());

        return new Object[]{
                srgCli,
                files,
                forgeNormal,
                forgeInstPath,
                forgeUniversal
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

    @SuppressWarnings("unchecked")
    public Proc1_16(File mcServer, @Nullable Map<String, Map<String, List<String>>> paramMap, Object[] files) {
        super((File) files[0], mcServer);
        this.forgeJar = (File) files[2]; // normal
        this.forgeUniv = (File) files[4]; // univer
        this.paramMap = paramMap;

        try {
            final String f3 = files[3].toString();
            String instUrl = MAIN_CONFIG.get("FMD配置").asMap().getString("ForgeMaven仓库地址") + f3;
            MCLauncher.downloadAndVerifyMD5(instUrl, this.forgeInstallerPath = new File(TMP_DIR, f3.substring(f3.lastIndexOf('/') + 1)), true);
        } catch (IOException e) {
            CmdUtil.warning("文件下载失败, 请重试，断点已保存", e);
            System.exit(-4);
        }

        try {
            Class.forName("net.md_5.specialsource.SpecialSource");
        } catch (Throwable ignored) {
            try {
                List<File> libs = (List<File>)files[1];
                ClassLoader ldr = getClass().getClassLoader();
                for(File lib : libs) {
                    addURL.invoke(ldr, lib.toURI().toURL());
                }
                Class.forName("net.md_5.specialsource.SpecialSource");
            } catch (Throwable e) {
                CmdUtil.error("SpecialSource的依赖加载失败...", e);
                CmdUtil.error("您可以手动将SpecialSource的文件加入classpath");
                System.exit(-1);
            }
        }

        parallel.pushRunnable(this);
    }

    public static void handleMCPConfig(File mcpConfigFile) {
        Proc1_16.mcpConfigFile = mcpConfigFile;
    }

    public int run0() {
        InputStream server_lzma;
        try {
            ZipFile zf = new ZipFile(forgeInstallerPath); // 顺便read一下
            ZipEntry ze = zf.getEntry("data/server.lzma");

            if (ze == null)
                throw OperationDone.INSTANCE;
            server_lzma = new ByteList().readStreamArrayFully(zf.getInputStream(ze)).asInputStream();
            zf.close();
        } catch (IOException e) {
            CmdUtil.error("forge安装器 '" + forgeInstallerPath.getAbsolutePath() + "'", e);
            return -1;
        } catch (OperationDone e) {
            CmdUtil.error("forge安装器 '" + forgeInstallerPath.getAbsolutePath() + "' 中没有找到 data/server.lzma");
            return -1;
        }

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

        List<File> libPath = Arrays.asList(mcJar, tmp);

        Patcher patcher = new Patcher();
        patcher.setup113(server_lzma, Collections.emptyMap()); // 读取服务端补丁，客户端打了
        CmdUtil.info("补丁已加载");

        CalculateTask<ConstMapper> prepRmp = new CalculateTask<>(() -> {
            if (ConstMapper.DEBUG) {
                CmdUtil.warning("检测到DEBUG开启, 转接STDOUT到remap.log");
                System.setOut(new PrintStream(new FileOutputStream(new File(BASE, "remap.log"))));
            }

            ConstMapper s2mRemapper = new ConstMapper();
            s2mRemapper.initEnv(new File(BASE, "/util/mcp-srg.srg"), libPath, null, true); // srg 转换为 mcp
            // s2mRemapper.clearLibs(); // 这个忘了干啥的，先注释掉
            CmdUtil.info("映射表已加载");
            return s2mRemapper;
        });

        CalculateTask<List<Context>[]> prepCtx = Helpers.cast(new CalculateTask<>(() -> {
            List<Context> servCtxs = Util.ctxFromZip(tmp, StandardCharsets.UTF_8);

            for (int i = 0; i < servCtxs.size(); i++) {
                Context ctx = servCtxs.get(i);
                ByteList result = patcher.patchServer(ctx.getName(), ctx.get()); // 服务端的文件已经映射，接下来是打补丁
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

            List<Context> clientCtxs = Util.ctxFromZip(mcJar, StandardCharsets.UTF_8);

            if (DEBUG) {
                CmdUtil.info("客户端文件数量: " + clientCtxs.size());
                CmdUtil.info("服务端文件数量: " + servCtxs.size());
                CmdUtil.info("Forge文件数量: " + fgCtxs.size());
            }

            // 客户端/forge 高版本都是 srg
            return new List<?>[]{clientCtxs, servCtxs, fgCtxs};
        }));

        threadWait(prepRmp, prepCtx);

        List<Context>[] ctxs;
        ConstMapper rmp;
        try {
            ctxs = prepCtx.get();
            rmp = prepRmp.get();
        } catch (InterruptedException | ExecutionException e) {
            CmdUtil.error("出现致命错误 " + e.getClass().getSimpleName(), e.getCause());
            return -1;
        }

        CodeMapper nameRmp = new CodeMapper(rmp);
        nameRmp.setParamRemappingV2(paramMap);
        nameRmp.rewrite = true;

        ExecutionTask remapClient = new ExecutionTask(com(rmp, nameRmp, 0, ctxs));
        ExecutionTask remapServer = new ExecutionTask(com(new ConstMapper(rmp), nameRmp, 1, ctxs));
        ExecutionTask remapForge = new ExecutionTask(com(new ConstMapper(rmp), nameRmp, 2, ctxs));

        threadWait(remapClient, remapServer, remapForge);

        ClassMerger merger = new ClassMerger();
        Collection<Context> mergedCtx = merger.process(ctxs[0], ctxs[1]);

        CmdUtil.info("服务端专属 " + merger.serverOnly  + ", 客户端专属 " + merger.clientOnly + ", 共用 " + merger.both);
        CmdUtil.info("合并了" + merger.mergedField + "个字段, " + merger.mergedMethod + "个方法, 覆盖了" + merger.replaceMethod + "个方法");

        if(DEBUG) {
            CmdUtil.info("验证class完整性");
        }
        for (Context ctx : mergedCtx) {
            try {
                ctx.validateSelf();
            } catch (Throwable e) {
                CmdUtil.warning(ctx.getName() + " 验证失败", e);
            }
        }
        for (Context ctx : ctxs[2]) {
            try {
                ctx.validateSelf();
            } catch (Throwable e) {
                CmdUtil.warning(ctx.getName() + " 验证失败", e);
            }
        }

        File merged = new File(BASE, "class/" + MERGED_FILE_NAME + ".jar");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(merged))) {
            Util.write(mergedCtx, zos, false);
            Util.write(ctxs[2], zos, true); // forge

            if(DEBUG)
                CmdUtil.success("文件写入完毕");
        } catch (IOException e) {
            CmdUtil.error("文件写入失败", e);
            return -1;
        }

        return 0;
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
