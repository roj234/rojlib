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
import roj.io.DummyOutputStream;
import roj.mod.FMDMain;
import roj.mod.remap.ClassMerger;
import roj.mod.util.MappingHelper;
import roj.mod.util.Patcher;
import roj.text.SimpleLineReader;
import roj.ui.CmdUtil;
import roj.util.ByteList;
import roj.util.Executable;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
            parallel.pushRunnable(this);
        }
    }

    public int run0() {
        ByteList n2sIn;
        Patcher patcher = new Patcher();
        try {
            n2sIn = Helper1_12.forgeInit(forgeJar);

            ZipFile zf = new ZipFile(forgeJar);
            patcher.setup112(zf.getInputStream(zf.getEntry("binpatches.pack.lzma")));
            zf.close();
        } catch (Throwable e) {
            StackTraceElement[] elements = e.getStackTrace();
            for(StackTraceElement element : elements) {
                if(element.getClassName().equals("roj.mod.processor.Proc1_12")) {
                    switch (element.getLineNumber()) {
                        case 106:
                            CmdUtil.error("forgeHelper加载失败", e);
                            return -1;
                        case 109:
                            CmdUtil.error("补丁失败!", e);
                            return -1;
                        case 108:
                        case 110:
                            CmdUtil.error("forge '" + forgeJar.getAbsolutePath() + "' 不是zip");
                            return -1;
                    }
                }
            }
            CmdUtil.error("I/O异常! 无法加载forgeHelper 或者 补丁加载失败", e);
            return -1;
        }

        CalculateTask<List<Context>> applyClientBinPatch = new CalculateTask<>(() -> {
            List<Context> contexts = Util.ctxFromZip(mcJar, StandardCharsets.UTF_8);

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
            TrieTreeSet set = new TrieTreeSet();
            FMDMain.readTextList(set::add, "FMD配置.忽略服务端jar中以以下文件名开头的文件");

            List<Context> contexts = Util.ctxFromZip(mcServer, StandardCharsets.UTF_8, name -> !set.startsWith(name));

            if (DEBUG)
                CmdUtil.info("[异步操作] 服务端文件数量: " + contexts.size());

            for (Context context : contexts) {
                ByteList result = patcher.patchServer(context.getName(), context.get());
                if (result != null)
                    context.set(result);
            }

            if (DEBUG)
                CmdUtil.success("[异步操作] 应用了 " + patcher.serverSuccessCount + " 个服务端补丁！");

            return contexts;
        });

        threadWait(applyClientBinPatch, appleServerBinPatch);

        List<Context> serverCtx;
        List<Context> clientCtx;
        try {
            clientCtx = applyClientBinPatch.get();
            serverCtx = appleServerBinPatch.get();
        } catch (InterruptedException | ExecutionException e) {
            CmdUtil.error("读取server/client文件时出现错误", e.getCause());
            return -1;
        }

        ConstMapper rmp = new ConstMapper();

        ClassMerger merger = new ClassMerger();
        List<Context> merged = new ArrayList<>(merger.process(clientCtx, serverCtx));

        CmdUtil.info("服务端专属 " + merger.serverOnly  + ", 客户端专属 " + merger.clientOnly + ", 共用 " + merger.both);
        CmdUtil.info("合并了" + merger.mergedField + "个字段, " + merger.mergedMethod + "个方法, 覆盖了" + merger.replaceMethod + "个方法");

        try {
            rmp.initEnv(n2sIn, Arrays.asList(mcJar, mcServer, forgeJar), null, false);
            // 映射 notch 到 srg

            File mcpSrgPath = new File(BASE, "/util/mcp-srg.srg");
            if (mcpPackFile != null)
                generateMcpSrg(rmp, mcpPackFile, mcpSrgPath, paramMap); // 准备MCP映射

            ConstMapper s2mRemapper = new ConstMapper();
            s2mRemapper.initEnv(mcpSrgPath, null, null, true); // srg -> mcp

            rmp.extend(s2mRemapper); // 转换为 notch -> mcp 的映射器 : 直接映射mc本体

            if(DEBUG)
                CmdUtil.info("[异步操作] 映射器已加载");

            if (ConstMapper.DEBUG) {
                CmdUtil.warning("检测到DEBUG开启, 转接STDOUT到remap.log");
                System.setOut(new PrintStream(new FileOutputStream(new File(BASE, "remap.log"))));
            }
        } catch (IOException e) {
            CmdUtil.error("准备映射时出现I/O错误", e);
            return -1;
        }

        CodeMapper nameRmp = new CodeMapper(rmp);
        nameRmp.setParamRemappingV2(paramMap);

        if(DEBUG)
            CmdUtil.info("开始映射");

        double start = System.currentTimeMillis();

        ExecutionTask remapMerged = new ExecutionTask(() -> {
            rmp.remap(true, merged);
            nameRmp.remap(true, merged);
            if (DEBUG)
                CmdUtil.success("合并cls处理完毕");
        });

        CalculateTask<List<Context>> remapForge = new CalculateTask<>(() -> {
            List<Context> contexts = Util.ctxFromZip(forgeJar, StandardCharsets.UTF_8);

            new ConstMapper(rmp).remap(true, contexts);
            nameRmp.remap(true, contexts);

            if (DEBUG)
                CmdUtil.success("Forge处理完毕");

            return contexts;
        });

        threadWait(remapMerged, remapForge);

        double last = System.currentTimeMillis();

        List<Context> fgResult;
        try {
            fgResult = remapForge.get();
        } catch (InterruptedException | ExecutionException e) {
            CmdUtil.error("forge文件映射失败", e);
            return -1;
        }

        int fc = serverCtx.size() + clientCtx.size() + fgResult.size();

        CmdUtil.success("映射成功, " + (last = ((last - start) / 1000d)) + "s");
        CmdUtil.info("文件数: " + fc + " 平均: " + (int)(fc / last) + " 文件/s");

        if(patcher.errorCount != 0)
            CmdUtil.warning("补丁失败数量: " + patcher.errorCount);

        patcher.reset();

        //if(DEBUG) {
            CmdUtil.info("验证class完整性");
            for (Context ctx : merged) {
                try {
                    ctx.validateSelf();
                } catch (Throwable e) {
                    CmdUtil.warning(ctx.getName() + " 验证失败", e);
                }
            }
            for (Context ctx : fgResult) {
                try {
                    ctx.validateSelf();
                } catch (Throwable e) {
                    CmdUtil.warning(ctx.getName() + " 验证失败", e);
                }
            }
            CmdUtil.info("验证完毕");
        //}

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File(BASE, "class/" + MERGED_FILE_NAME + ".jar")))) {
            Util.write(merged, zos, false);
            Util.write(fgResult, zos, true);

            if (DEBUG)
                CmdUtil.success("I/O完毕");
        } catch (IOException e) {
            CmdUtil.error("IO异常", e);
            return -1;
        }
        return 0;
    }

    private static void generateMcpSrg(Mapping mapping, File mcpPack, File mcpSrgPath, Map<String, Map<String, List<String>>> paramMap) throws IOException {
        Executable action = () -> {
            try {
                MappingHelper helper = new MappingHelper(mapping);
                Map<String, String> origPM = new MyHashMap<>(1000);
                helper.parseMCP(mcpPack, origPM);
                helper.MCP_optimizeParamMap(origPM, paramMap);
                helper.extractMcp2Srg_MCP(mcpSrgPath);
            } catch (IOException e) {
                CmdUtil.warning("IO异常 ", e);
            }
        };

        if(DEBUG) {
            File log = new File(BASE, "parse_mcp.log");
            FMDMain.redirectOutput(log, action);

            int warnings = 1;
            try (FileInputStream fis = new FileInputStream(log)) {
                warnings = new SimpleLineReader(fis).size();
            } catch (IOException ignored) {}

            if (warnings > 2)
                CmdUtil.warning("忽略了 " + (warnings - 2) + " 个警告, 详情查看 " + log.getAbsolutePath());
        } else {
            PrintStream out = System.out;
            DummyOutputStream w = new DummyOutputStream();
            System.setOut(new PrintStream(w));

            action.execute();

            if(w.wrote > 10)
                CmdUtil.warning("解析MCP时有一些警告 (打开debug查看详细)");

            System.setOut(out);
        }
    }
}
