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
import roj.asm.mapper.Mapping;
import roj.asm.mapper.Util;
import roj.asm.mapper.util.Context;
import roj.collect.MyHashMap;
import roj.collect.TrieTreeSet;
import roj.concurrent.task.CalculateTask;
import roj.concurrent.task.ExecutionTask;
import roj.mod.FMDMain;
import roj.mod.remap.ClassMerger;
import roj.mod.util.MappingHelper;
import roj.mod.util.Patcher;
import roj.text.SimpleLineReader;
import roj.ui.CmdUtil;
import roj.util.ByteList;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static roj.mod.Shared.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/30 11:31
 */
public final class Proc1_12 extends Processor {
    File mcpPackFile;

    Map<String, Map<String, List<String>>> paramMap = new MyHashMap<>(1000);

    public Proc1_12(File mcServer, File mcJar, File mcpPackFile) {
        super(mcJar, mcServer);
        this.mcpPackFile = mcpPackFile;
    }

    int done = 0;

    @Override
    public void accept(Integer id, File file) {
        if (id == 0) {
            forgeJar = file;
        } else if(id == 1) {
            try {
                Class.forName("LZMA.LzmaInputStream");
            } catch (ClassNotFoundException ignored) {
                try {
                    ClassLoader ldr = getClass().getClassLoader();
                    addURL.invoke(ldr, file.toURI().toURL());
                } catch (IllegalAccessException | InvocationTargetException | MalformedURLException e) {
                    CmdUtil.error("LZMA加载失败...", e);
                    CmdUtil.error("您可以手动将LZMA的文件加入classpath");
                    System.exit(-1);
                }
            }
        }

        done |= id + 1;
        if(done == 3) {
            parallel.pushTask(this);
        }
    }

    public int run0() {
        InputStream n2sIn;
        Patcher patcher = new Patcher();
        try {
            n2sIn = Helper1_12.forgeInit(forgeJar);

            ZipFile zipFileForge = new ZipFile(forgeJar);
            patcher.setup112(zipFileForge.getInputStream(zipFileForge.getEntry("binpatches.pack.lzma")));
            zipFileForge.close();

            ZipFile zff = new ZipFile(mcServer);
            zff.close();

            zff = new ZipFile(mcJar);
            zff.close();

        } catch (Throwable e) {
            StackTraceElement[] elements = e.getStackTrace();
            for(StackTraceElement element : elements) {
                if(element.getClassName().equals("roj.mod.processor.Proc1_12")) {
                    switch (element.getLineNumber()) {
                        case 105:
                            CmdUtil.error("forgeHelper加载失败", e);
                            return -1;
                        case 108:
                            CmdUtil.error("补丁失败!", e);
                            return -1;
                        case 111:
                        case 112:
                            CmdUtil.error("服务端文件 '" + mcServer.getAbsolutePath() + "' 不是zip");
                            return -1;
                        case 107:
                        case 109:
                            CmdUtil.error("forge '" + forgeJar.getAbsolutePath() + "' 不是zip");
                            return -1;
                        case 114:
                        case 115:
                            CmdUtil.error("客户端文件 '" + mcJar.getAbsolutePath() + "' 不是zip");
                            return -1;
                    }
                }
            }
            CmdUtil.error("IO异常! 无法加载forge 或者 补丁加载失败", e);
            return -1;
        }

        CalculateTask<ConstMapper> prepareRemapper = new CalculateTask<>(() -> {
            ConstMapper n2sRemapper = new ConstMapper();
            n2sRemapper.initEnv(n2sIn, Arrays.asList(mcServer, mcJar, forgeJar), null, false, false); // 映射 notch 到 srg

            File mcpSrgPath = new File(BASE, "/util/mcp-srg.srg");
            if(mcpPackFile != null)
                generateMcpSrg(n2sRemapper, mcpPackFile, mcpSrgPath, paramMap); // 准备MCP映射

            ConstMapper s2mRemapper = new ConstMapper();
            s2mRemapper.initEnv(mcpSrgPath, null, null, true, false); // srg -> mcp

            n2sRemapper.extend(s2mRemapper); // 转换为 notch -> mcp 的映射器 : 直接映射mc本体

            CmdUtil.info("[异步操作] 映射器已加载");

            if (ConstMapper.DEBUG) {
                CmdUtil.warning("检测到DEBUG开启, 转接STDOUT到remap.log");
                System.setOut(new PrintStream(new FileOutputStream(new File(BASE, "remap.log"))));
            }

            return n2sRemapper;
        });

        CalculateTask<List<Context>> applyClientBinPatch = new CalculateTask<>(() -> {
            List<Context> contexts = Util.prepareContextFromZip(mcJar, StandardCharsets.UTF_8);

            if (DEBUG)
                CmdUtil.info("[异步操作] 客户端文件数量: " + contexts.size());

            for (Context context : contexts) {
                ByteList result = patcher.patchClient(context.getName(), context.get());
                if (result != null)
                    context.set(result);
            }

            if (DEBUG)
                CmdUtil.success("[异步操作] 应用了 " + patcher.clientSuccessCount + " 个客户端补丁！");

            return contexts;
        });

        CalculateTask<List<Context>> appleServerBinPatch = new CalculateTask<>(() -> {
            MyHashMap<String, InputStream> streams = new MyHashMap<>(1000);

            ZipFile zf = Util.prepareInputFromZip(mcServer, StandardCharsets.UTF_8, streams);

            TrieTreeSet set = new TrieTreeSet();
            FMDMain.readTextList(set::add, "FMD配置.忽略服务端jar中以以下文件名开头的文件");

            streams.removeIf((s) -> !s.endsWith(".class") || set.startsWith(s));

            if (DEBUG)
                CmdUtil.info("[异步操作] 服务端文件数量: " + streams.size());

            List<Context> contexts = Util.prepareContexts(streams);

            for (Context context : contexts) {
                ByteList result = patcher.patchServer(context.getName(), context.get());
                if (result != null)
                    context.set(result);
            }

            zf.close();

            if (DEBUG)
                CmdUtil.success("[异步操作] 应用了 " + patcher.serverSuccessCount + " 个服务端补丁！");

            return contexts;
        });

        threadWait(prepareRemapper, applyClientBinPatch, appleServerBinPatch);

        List<Context> serverCtx;
        List<Context> clientCtx;
        ConstMapper rmp;
        try {
            clientCtx = applyClientBinPatch.get();
            serverCtx = appleServerBinPatch.get();
            rmp = prepareRemapper.get();
        } catch (InterruptedException | ExecutionException e) {
            CmdUtil.error("出现致命错误", e);
            return -1;
        }

        CodeMapper nameRmp = new CodeMapper(rmp);
        nameRmp.setParamRemappingV2(paramMap);

        CmdUtil.info("开始使用自制映射器(去用1.16和SpecialSource比下么？)");

        double start = System.currentTimeMillis();

        ExecutionTask remapClient = new ExecutionTask(() -> {
            rmp.remap(true, clientCtx);
            nameRmp.remap(true, clientCtx);
            if (DEBUG)
                CmdUtil.success("客户端处理完毕");
        });

        ConstMapper rmp2 = new ConstMapper(rmp);

        ExecutionTask remapServer = new ExecutionTask(() -> {
            rmp2.remap(true, serverCtx);
            nameRmp.remap(true, serverCtx);
            if (DEBUG)
                CmdUtil.success("服务端处理完毕");
        });

        ConstMapper rmp3 = new ConstMapper(rmp);

        CalculateTask<List<Context>> remapForge = new CalculateTask<>(() -> {
            List<Context> contexts = Util.prepareContextFromZip(forgeJar, StandardCharsets.UTF_8);

            rmp3.remap(true, contexts);
            nameRmp.remap(true, contexts);

            if (DEBUG)
                CmdUtil.success("Forge处理完毕");

            return contexts;
        });

        threadWait(remapClient, remapServer, remapForge);

        double last = System.currentTimeMillis();

        CmdUtil.success("自制映射器映射成功! 用时: " + (last = ((last - start) / 1000d)) + "s");

        int fc = serverCtx.size() + clientCtx.size();
        try {
            fc += remapForge.get().size();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        CmdUtil.info("文件数: " + fc + " 平均速度: " + (fc / last) + " 文件/s");

        if(patcher.errorCount != 0)
            CmdUtil.warning("补丁失败数量: " + patcher.errorCount);
        else
            CmdUtil.success("补丁全部成功!");
        patcher.reset();

        ClassMerger merger = new ClassMerger();
        Collection<Context> tmp = merger.process(clientCtx, serverCtx);

        CmdUtil.info("服务端专属 " + merger.serverOnly  + ", 客户端专属 " + merger.clientOnly + ", 共用 " + merger.both);
        CmdUtil.info("合并了" + merger.mergedField + "个字段, " + merger.mergedMethod + "个方法, 覆盖了" + merger.replaceMethod + "个方法");

        List<Context> fgResult;
        try {
            fgResult = remapForge.get();
        } catch (InterruptedException | ExecutionException e) {
            CmdUtil.error("出现致命错误", e);
            return -1;
        }

        if(DEBUG) {
            CmdUtil.info("验证class完整性");
            int err = 0;
            for (Context ctx : tmp) {
                try {
                    ctx.validateSelf();
                } catch (Throwable e) {
                    CmdUtil.warning(ctx.getName() + " 验证失败", e);
                    err++;
                }
            }
            for (Context ctx : fgResult) {
                try {
                    ctx.validateSelf();
                } catch (Throwable e) {
                    CmdUtil.warning(ctx.getName() + " 验证失败", e);
                    err++;
                }
            }
            if (err != 0)
                CmdUtil.warning("验证失败数量: " + err);
            else
                CmdUtil.success("class全部正常!");
        }

        File merged = new File(BASE, "class/" + MERGED_FILE_NAME + ".jar");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(merged))) {
            Util.write(tmp, zos, false);
            Util.write(fgResult, zos, true);

            CmdUtil.success("文件IO完毕");
        } catch (IOException e) {
            CmdUtil.error("IO异常", e);
            return -1;
        }
        return 0;
    }

    private static void generateMcpSrg(Mapping mapping, File mcpPack, File mcpSrgPath, Map<String, Map<String, List<String>>> paramMap) throws IOException {
        MappingHelper helper = new MappingHelper(mapping);

        File log = new File(BASE, "parse_mcp.log");
        FMDMain.redirectOutput(log, () -> {
            try {
                Map<String, String> origPM = new MyHashMap<>(1000);
                helper.parseMCP(mcpPack, origPM);
                helper.MCP_optimizeParamMap(origPM, paramMap);
                helper.extractMcp2Srg_MCP(mcpSrgPath);
            } catch (IOException e) {
                CmdUtil.warning("IO异常 ", e);
            }
        });

        int warnings = 1;
        try (FileInputStream fis = new FileInputStream(log)) {
            warnings = new SimpleLineReader(fis).size();
        } catch (IOException ignored) {}

        if (warnings > 2)
            CmdUtil.warning("忽略了 " + (warnings - 2) + " 个警告, 详情查看 " + log.getAbsolutePath());
    }
}
