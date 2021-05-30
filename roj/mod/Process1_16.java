package roj.mod;

import roj.asm.remapper.IRemapper;
import roj.asm.remapper.RemapperV2;
import roj.asm.remapper.Renamer;
import roj.asm.remapper.Util;
import roj.asm.remapper.util.Context;
import roj.collect.MyHashMap;
import roj.collect.TrieTreeSet;
import roj.concurrent.OperationDone;
import roj.concurrent.task.CalculateTask;
import roj.concurrent.task.ExecutionTask;
import roj.io.FileUtil;
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
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/30 11:31
 */
class Process1_16 extends IProcess {
    private static File mcpConfigFile;

    File forgeInstallerPath, forgeUniv;

    Map<String, Map<String, List<String>>> paramMap;

    protected static Object[] find116Files(File librariesPath, Collection<String> values) {
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
    public Process1_16(File mcServer, @Nullable Map<String, Map<String, List<String>>> paramMap, Object[] files) {
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

        thread = new Thread(this, "MCBinPrepare_New");
        thread.start();
    }

    public static void handleMCPConfig(File mcpConfigFile) {
        Process1_16.mcpConfigFile = mcpConfigFile;
    }

    public int run0() {
        InputStream serverLzmaInput;
        try {
            ZipFile zf = new ZipFile(mcServer);
            zf.close();

            zf = new ZipFile(forgeInstallerPath); // 顺便read一下
            ZipEntry ze = zf.getEntry("data/server.lzma");

            if (ze == null)
                throw OperationDone.INSTANCE;
            serverLzmaInput = new ByteList().readStreamArrayFully(zf.getInputStream(ze)).asInputStream();
            zf.close();

            zf = new ZipFile(mcJar);
            zf.close();
        } catch (IOException e) {
            StackTraceElement[] elements = e.getStackTrace();
            for(StackTraceElement ste : elements) {
                if(ste.getClassName().equals("roj.mod.Process1_16")) {
                    switch (ste.getLineNumber()) {
                        case 142:
                        case 143:
                            CmdUtil.error("服务端文件 '" + mcServer.getAbsolutePath() + "' 不是zip");
                            break;
                        case 145:
                        case 146:
                        case 150:
                        case 151:
                            CmdUtil.error("forge安装器 '" + forgeInstallerPath.getAbsolutePath() + "' 不是zip");
                            break;
                        case 153:
                        case 154:
                            CmdUtil.error("客户端文件 '" + mcJar.getAbsolutePath() + "' 不是zip");
                            break;
                        default:
                            CmdUtil.error("未知错误 (老A你是不是又忘了更新行号？", e);
                    }
                    return -1;
                }
            }
            CmdUtil.error("未知错误", e);
            return -1;
        } catch (OperationDone e) {
            CmdUtil.error("安装器文件中没有找到 data/server.lzma!!!!");
            return -1;
        }

        File tmp = new File(TMP_DIR, mcServer.getName() + "_Rmp.server");
        tmp.deleteOnExit();

        TrieTreeSet set = new TrieTreeSet();
        ModDevelopment.readTextList(set::add, "FMD配置.忽略服务端jar中以以下文件名开头的文件");

        try {
            Helper1.remap116_SC(tmp, mcServer, mcpConfigFile, set);
        } catch (Throwable e) {
            CmdUtil.error("SpecialSource 错误", e);
            return -1;
        }

        final List<File> libPath = Arrays.asList(mcJar, tmp);

        Patcher patcher = new Patcher();

        CalculateTask<RemapperV2> prepRmp = new CalculateTask<>(() -> {
            try {
                RemapperV2 s2mRemapper = new RemapperV2();
                s2mRemapper.prepareEnv(new File(BASE, "/util/mcp-srg.srg"), libPath, null, true, false); // srg 转换为 mcp

                // s2mRemapper.clearLibs(); // 这个忘了干啥的，先注释掉

                CmdUtil.info("映射表已加载");

                patcher.setup113(serverLzmaInput, Collections.emptyMap()); // 读取服务端补丁，客户端打了

                CmdUtil.info("补丁已加载");

                if (IRemapper.DEBUG) {
                    CmdUtil.warning("检测到DEBUG开启, 转接STDOUT到remap.log");
                    System.setOut(new PrintStream(new FileOutputStream(new File(BASE, "remap.log"))));
                }

                return s2mRemapper;
            } catch (IOException e) {
                CmdUtil.error("[异步操作] IO异常: 映射准备失败!", e);
            }
            return null;
        });

        CalculateTask<List<Context>[]> prepCtx = Helpers.cast(new CalculateTask<>(() -> {
            List<Context> clientCtxs = Util.prepareContextFromZip(mcJar, StandardCharsets.UTF_8);

            if (DEBUG)
                CmdUtil.info("客户端文件数量: " + clientCtxs.size());


            MyHashMap<String, InputStream> streams = new MyHashMap<>(1000);

            ZipFile zf = Util.prepareInputFromZip(tmp, StandardCharsets.UTF_8, streams);

            if (DEBUG)
                CmdUtil.info("服务端文件数量: " + streams.size());

            List<Context> servCtxs = Util.prepareContexts(streams);
            for (Context ctx : servCtxs) {
                ByteList result = patcher.patchServer(ctx.getName(), ctx.get()); // 服务端的文件已经映射，接下来是打补丁
                if (result != null)
                    ctx.set(result);
            }

            zf.close();

            if(patcher.errorCount != 0)
                CmdUtil.warning("补丁失败数量: " + patcher.errorCount);
            else
                CmdUtil.success("补丁全部成功!");
            patcher.reset();

            //if (DEBUG)
            //    CmdUtil.info("服务端应用了: " + Patcher.serverSuccessCount);

            List<Context> fgCtxs = Util.prepareContextFromZip(forgeJar, StandardCharsets.UTF_8);

            if(forgeUniv != null) { // forge-universal
                fgCtxs.addAll(Util.prepareContextFromZip(forgeUniv, StandardCharsets.UTF_8)); // 理论上不会有重复
            }

            // 客户端/forge 高版本都是 srg
            return new List<?>[]{clientCtxs, servCtxs, fgCtxs};
        }));

        threadWait(prepRmp, prepCtx);

        List<Context>[] ctxs;
        RemapperV2 clientRmp;
        try {
            ctxs = prepCtx.get();
            clientRmp = prepRmp.get();
        } catch (InterruptedException | ExecutionException e) {
            CmdUtil.error("出现致命错误 " + e.getClass().getSimpleName(), e.getCause());
            return -1;
        }

        Renamer nameRmp = new Renamer(clientRmp);
        nameRmp.setParamRemappingV2(paramMap);
        nameRmp.rewrite = true;

        ExecutionTask remapClient = new ExecutionTask(reuseLambda(clientRmp, nameRmp, 0, ctxs));
        ExecutionTask remapServer = new ExecutionTask(reuseLambda(new RemapperV2(clientRmp), nameRmp, 1, ctxs));
        ExecutionTask remapForge = new ExecutionTask(reuseLambda(new RemapperV2(clientRmp), nameRmp, 2, ctxs));

        threadWait(remapClient, remapServer, remapForge);

        ClassMerger merger = new ClassMerger();
        Collection<Context> mergedCtx = merger.process(ctxs[0], ctxs[1]);

        CmdUtil.info("服务端专属 " + merger.serverOnly  + ", 客户端专属 " + merger.clientOnly + ", 共用 " + merger.both);
        CmdUtil.info("合并了" + merger.mergedField + "个字段, " + merger.mergedMethod + "个方法, 覆盖了" + merger.replaceMethod + "个方法");

        if(DEBUG) {
            CmdUtil.info("验证class完整性");
            int err = 0;
            for (Context ctx : mergedCtx) {
                try {
                    ctx.validateSelf();
                } catch (Throwable e) {
                    CmdUtil.warning(ctx.getName() + " 验证失败", e);
                    err++;
                }
            }
            for (Context ctx : ctxs[2]) {
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
            Util.write(mergedCtx, zos, false);
            Util.write(ctxs[2], zos, true); // forge

            CmdUtil.success("文件写入完毕");

        } catch (IOException e) {
            CmdUtil.error("文件写入失败", e);
            return -1;
        }

        return 0;
    }

    private static Runnable reuseLambda(RemapperV2 mainRmp, Renamer nameRmp, int i, List<Context>[] arr) {
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
