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
package roj.mod;

import roj.collect.MyHashMap;
import roj.concurrent.TaskPool;
import roj.concurrent.TaskSequencer;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CMapping;
import roj.dev.HRRemote;
import roj.io.BOMInputStream;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.io.down.Downloader;
import roj.mapper.ConstMapper;
import roj.mapper.Util;
import roj.mapper.util.Desc;
import roj.misc.CpFilter;
import roj.ui.CmdUtil;
import roj.util.ByteList;
import roj.util.FastLocalThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FMD Shared Data / Utility Methods
 *
 * @author Roj234
 * @since  2020/8/29 11:38
 */
public final class Shared {
    public static final boolean DEBUG;
    public static final String MC_BINARY = "forgeMcBin";
    public static final String VERSION = "1.6.4";

    public static final File BASE, TMP_DIR, PROJECTS_DIR;

    static final IFileWatcher watcher;
    static HRRemote hotReload;

    public static void launchHotReload() {
        if (hotReload == null && CONFIG.getBool("???????????????")) {
            int port = 0xFFFF & CONFIG.getInteger("????????????");
            if (port == 0) port = 4485;
            try {
                hotReload = new HRRemote(port);
                hotReload.start();
            } catch (IOException e) {
                CmdUtil.warning("??????????????????????????????", e);
            }
        }
    }

    public static final TaskSequencer PeriodicTask;
    public static final TaskPool      Task = new TaskPool(0, Runtime.getRuntime().availableProcessors(), 1, 5000, "????????????");
    public static void async(Runnable... run) {
        Thread[] t = new Thread[run.length];
        for (int i = 0; i < run.length; i++) {
            Thread o = t[i] = new FastLocalThread(run[i]);
            o.setDaemon(true);
            o.start();
        }
        for (Thread thread : t) {
            try {
                thread.join();
            } catch (InterruptedException ignored) {}
        }
    }

    public static CMapping CONFIG;
    static void loadConfig() {
        File file = new File(BASE, "config.json");
        try {
            final BOMInputStream bom = new BOMInputStream(new FileInputStream(file), "UTF8");
            if(!bom.getEncoding().equals("UTF8")) { // ?????????????????? UTF-8
                CmdUtil.warning("????????????????????????BOM(????????????UTF-8???BOM??????!), ???????????????: " + bom.getEncoding());
            }

            CONFIG = JSONParser.parse(IOUtil.readAs(bom, bom.getEncoding()), 7).asMap();
            CONFIG.dot(true);
        } catch (ParseException | ClassCastException e) {
            CmdUtil.error(file.getAbsolutePath() + " ???????????????! ?????????!", e);
        } catch (IOException e) {
            CmdUtil.error(file.getAbsolutePath() + " ????????????!", e);
        }
    }

    static Project project;
    static boolean isForgeMap;
    public static void setProject(String path) {
        if (path == null) path = project.name;
        try(FileOutputStream fos = new FileOutputStream(new File(BASE, "config/index.json"))) {
            CMapping map = new CMapping();
            map.put("config", path);
            map.put("forgeMapping", isForgeMap);
            ByteList.encodeUTF(map.toJSONb()).writeToStream(fos);
        } catch (IOException e) {
            CmdUtil.error("????????????", e);
        }
        if (project != (project = Project.load(path))) {
            AutoCompile.notifyIt();
        }
    }
    public static boolean loadProject(boolean forced) {
        if(project == null) {
            File projIndex = new File(BASE, "config/index.json");

            String cf;
            if(!projIndex.isFile()) {
                cf = "default";
            } else {
                try {
                    CMapping map = JSONParser.parse(IOUtil.readUTF(new FileInputStream(projIndex))).asMap();
                    cf = map.getString("config");
                    isForgeMap = map.getBool("forgeMapping");
                } catch (ParseException | ClassCastException e) {
                    CmdUtil.warning("????????????(config/index.json)????????????, ??????????????????", e);
                    cf = "default";
                } catch (IOException e) {
                    throw new RuntimeException("????????????????????????", e);
                }
            }

            File proj = new File(BASE, "/config/" + cf + ".json");
            if(forced || proj.isFile()) {
                try {
                    project = Project.load(cf);
                } catch (Throwable e) {
                    CmdUtil.warning("??????????????????, ??????????????????", e);
                    project = Project.load("default");
                    setProject("default");
                }
                return true;
            }
        }
        return false;
    }

    static final ConstMapper mapperFwd = new ConstMapper();
    private static ConstMapper mapperRev;
    public static void loadMapper() {
        if(mapperFwd.getClassMap().isEmpty()) {
            synchronized (mapperFwd) {
                if(mapperFwd.getClassMap().isEmpty()) {
                    File map = new File(BASE, "/util/mcp-srg.srg");
                    if (!map.isFile()) {
                        CmdUtil.error("??????????????? " + map + " ?????????,??????????????????");
                        return;
                    }
                    mapperRev = null;
                    try {
                        mapperFwd.initEnv(map, new File(BASE, "/class/"), new File(BASE, "/util/remapCache.bin"), false);
                        if (DEBUG) CmdUtil.success("????????????????????????");
                    } catch (Exception e) {
                        CmdUtil.error("???????????????????????????", e);
                    }
                }
            }
        }
    }
    public static ConstMapper loadReverseMapper() {
        if(mapperRev == null) {
            loadMapper();

            mapperRev = new ConstMapper(mapperFwd);
            mapperRev.reverse();

            if(DEBUG) CmdUtil.success("????????????????????????");
        }
        return mapperRev;
    }

    public static final Map<String, String> srg2mcp = new MyHashMap<>(1000, 1.5f);
    public static void loadSrg2Mcp() {
        Map<String, String> srg2mcp = Shared.srg2mcp;
        if(srg2mcp.isEmpty()) {
            Shared.loadMapper();

            ConstMapper fwd = mapperFwd;
            for (Map.Entry<Desc, String> entry : fwd.getFieldMap().entrySet()) {
                srg2mcp.put(entry.getValue(), entry.getKey().name);
            }

            for (Map.Entry<Desc, String> entry : fwd.getMethodMap().entrySet()) {
                srg2mcp.put(entry.getValue(), entry.getKey().name);
            }
        }
    }

    private static ServerSocket ProcessLock;
    private static AtomicInteger ThreadLock;
    public static void singletonLock() {
        if (ProcessLock != null) {
            if (!ThreadLock.compareAndSet(0, 1)) {
                throw new IllegalStateException("?????????????????????");
            }
        } else {
            try {
                ProcessLock = new ServerSocket(CONFIG.getInteger("???????????????"));
            } catch (Throwable e) {
                throw new IllegalStateException("?????????????????????", e);
            }
            ThreadLock = new AtomicInteger();
        }
    }
    public static void singletonUnlock() {
        if (ThreadLock != null) {
            ThreadLock.set(0);
        }
    }

    static {
        String basePath = System.getProperty("fmd.base_path", ".");
        BASE = new File(basePath).getAbsoluteFile();

        loadConfig();
        if (CONFIG == null) System.exit(-2);

        boolean launchOnly = System.getProperty("fmd.launch_only") != null || CONFIG.getBool("???????????????");

        TMP_DIR = new File(BASE, "tmp/");

        if (!TMP_DIR.isDirectory() && !TMP_DIR.mkdir()) {
            CmdUtil.error("???????????????????????????: " + TMP_DIR.getAbsolutePath());
            System.exit(-2);
        }

        PROJECTS_DIR = new File(BASE, "/config/");
        if (!launchOnly && !PROJECTS_DIR.isDirectory() && !PROJECTS_DIR.mkdirs()) {
            CmdUtil.error("?????????????????????????????????: " + PROJECTS_DIR.getAbsolutePath());
            System.exit(-2);
        }

        DEBUG = CONFIG.getBool("????????????");
        if(DEBUG) {
            try {
                CpFilter.registerShutdownHook();
            } catch (NoClassDefFoundError ignored) {}
        }

        CMapping cfgGen = CONFIG.get("??????").asMap();
        int threads = cfgGen.getInteger("???????????????");
        Downloader.chunkStartSize = threads > 0 ? 4096 : Integer.MAX_VALUE;
        Downloader.chunkCount = threads;
        FileUtil.userAgent = cfgGen.getString("UserAgent");
        FileUtil.timeout = cfgGen.getInteger("????????????");

        IFileWatcher w = null;
        if(!launchOnly && CONFIG.getBool("??????????????????")) {
            try {
                w = new FileWatcher();
            } catch (IOException e) {
                CmdUtil.warning("????????????????????????", e);
            }

            AutoCompile.Debounce = CONFIG.getInteger("??????????????????");
            if (CONFIG.getBool("????????????")) {
                AutoCompile.setEnabled(true);
            }
        }

        if (CONFIG.getBool("????????????")) {
            mapperFwd.flag |= ConstMapper.FLAG_CHECK_SUB_IMPL;
        }

        if(w == null) w = new IFileWatcher();
        watcher = w;

        if (!launchOnly) {
            Thread seqWorker = new FastLocalThread(PeriodicTask = new TaskSequencer());
            seqWorker.setDaemon(true);
            seqWorker.setName("????????????");
            seqWorker.start();
        } else {
            PeriodicTask = null;
        }
        Util.setAsyncPool(Task);
    }
}
