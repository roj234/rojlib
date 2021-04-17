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
import roj.io.ZipFileWriter;
import roj.mod.FMDMain;
import roj.mod.remap.ClassMerger;
import roj.mod.util.DummyPrintStream;
import roj.mod.util.MappingHelper;
import roj.mod.util.Patcher;
import roj.ui.CmdUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

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
        List<Context>[] arr = Helpers.cast(new List<?>[3]);
        arr[0] = new ArrayList<>();

        Patcher patcher = new Patcher();
        try {
            ZipFile zf = new ZipFile(forgeJar);
            patcher.setup112(zf.getInputStream(zf.getEntry("binpatches.pack.lzma")));
            zf.close();
        } catch (Throwable e) {
            CmdUtil.error("补丁加载失败", e);
            return -1;
        }

        Runnable applyClientBinPatch = () -> {
            List<Context> contexts;
            try {
                contexts = Util.ctxFromZip(mcJar, StandardCharsets.UTF_8);
            } catch (IOException e) {
                CmdUtil.error("mcJar读取失败", e);
                System.exit(-1);
                return;
            }

            if (DEBUG)
                CmdUtil.info("[异步操作] 客户端文件数量: " + contexts.size());

            for (int i = 0; i < contexts.size(); i++) {
                Context context = contexts.get(i);
                ByteList result = patcher.patchClient(context.getFileName(), context.get());
                if (result != null) {
                    arr[0].add(new Context(context.getFileName(), context.get()));
                    context.set(result);
                }
                context.getData();
            }

            if (DEBUG)
                CmdUtil.success("[异步操作] 应用了 " + patcher.clientSuccessCount + " 个客户端补丁！");

            arr[1] = contexts;
        };

        Runnable appleServerBinPatch = () -> {
            TrieTreeSet set = new TrieTreeSet();
            FMDMain.readTextList(set::add, "FMD配置.忽略服务端jar中以以下文件名开头的文件");

            List<Context> contexts;
            try {
                contexts = Util.ctxFromZip(mcServer, StandardCharsets.UTF_8, name -> !set.startsWith(name));
            } catch (IOException e) {
                CmdUtil.error("mcServer读取失败", e);
                System.exit(-1);
                return;
            }

            if (DEBUG)
                CmdUtil.info("[异步操作] 服务端文件数量: " + contexts.size());

            for (int i = 0; i < contexts.size(); i++) {
                Context context = contexts.get(i);
                ByteList result = patcher.patchServer(context.getFileName(), context.get());
                if (result != null) {
                    arr[0].add(new Context(context.getFileName(), context.get()));
                    context.set(result);
                }
                context.getData();
            }

            if (DEBUG)
                CmdUtil.success("[异步操作] 应用了 " + patcher.serverSuccessCount + " 个服务端补丁！");

            arr[2] = contexts;
        };

        ConstMapper rmp = new ConstMapper();
        Runnable genMapping = () -> {
            try {
                // 获取 notch 到 srg
                Helper1_12.forgeInit(forgeJar, rmp);
            } catch (IOException e) {
                CmdUtil.error("Helper1_12.forgeInit()失败", e);
                System.exit(-1);
                return;
            }

            File mcpSrgPath = new File(BASE, "/util/mcp-srg.srg");
            if (mcpPackFile != null) {
                Runnable action = () -> {
                    try {
                        MappingHelper helper = new MappingHelper(rmp);
                        Map<String, String> origPM = new MyHashMap<>(1000);
                        helper.parseMCP(mcpPackFile, origPM);
                        helper.MCP_optimizeParamMap(origPM, paramMap);
                        helper.extractMcp2Srg_MCP(mcpSrgPath);
                    } catch (IOException e) {
                        CmdUtil.error("generateMcpSrg()失败", e);
                        System.exit(-1);
                    }
                };

                if(DEBUG) {
                    File log = new File(BASE, "parse_mcp.log");
                    try {
                        FMDMain.redirectOutput(log, action);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    if (log.length() > 10)
                        CmdUtil.warning("解析MCP时有一些警告, 查看 " + log.getAbsolutePath());
                } else {
                    MappingHelper.OUT = new DummyPrintStream();
                    action.run();
                }
            }

            Mapping s2m = new Mapping();
            s2m.loadMap(mcpSrgPath, true); // srg -> mcp

            rmp.extend(s2m); // 转换为 notch -> mcp 的映射器 : 直接映射mc本体
        };

        threadWait(applyClientBinPatch, appleServerBinPatch, genMapping);

        ClassMerger merger = new ClassMerger();
        List<Context> merged = new ArrayList<>(merger.process(arr[1], arr[2]));

        CmdUtil.info("服务端专属 " + merger.serverOnly  + ", 客户端专属 " + merger.clientOnly + ", 共用 " + merger.both);
        CmdUtil.info("合并了" + merger.mergedField + "个字段, " + merger.mergedMethod + "个方法, 覆盖了" + merger.replaceMethod + "个方法");

        rmp.loadLibraries(Arrays.asList(merged, arr[0], forgeJar));

        Arrays.fill(arr, null);

        if(DEBUG)
            CmdUtil.info("映射器已加载");

        CodeMapper nameRmp = new CodeMapper(rmp);
        nameRmp.setParamRemappingV2(paramMap);

        if(DEBUG)
            CmdUtil.info("开始映射");

        Runnable remapMerged = () -> {
            rmp.remap(true, merged);
            nameRmp.remap(true, merged);
        };

        Runnable remapForge = () -> {
            List<Context> contexts;
            try {
                contexts = Util.ctxFromZip(forgeJar, StandardCharsets.UTF_8);
            } catch (IOException e) {
                CmdUtil.error("Should not happen", e);
                System.exit(-1);
                return;
            }

            new ConstMapper(rmp).remap(true, contexts);
            nameRmp.remap(true, contexts);

            arr[0] = contexts;
        };

        long start = System.currentTimeMillis();

        threadWait(remapMerged, remapForge);

        long last = System.currentTimeMillis();

        List<Context> fgResult = arr[0];

        int fc = merged.size() + fgResult.size();

        float cost;
        CmdUtil.success("映射成功, " + (cost = ((last - start) / 1000f)) + "s");
        CmdUtil.info("文件数: " + fc + " 平均: " + (int)(fc / cost) + " 文件/s");

        if(patcher.errorCount != 0)
            CmdUtil.warning("补丁失败数量: " + patcher.errorCount);

        patcher.reset();

        try (ZipFileWriter zfw = new ZipFileWriter(new File(BASE, "class/" + MERGED_FILE_NAME + ".jar"))) {
            FileTime time = FileTime.from(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
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
            CmdUtil.error("IO异常", e);
            return -1;
        }
        return 0;
    }
}
