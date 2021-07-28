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

import roj.asm.mapper.ConstMapper;
import roj.asm.mapper.util.FlDesc;
import roj.asm.mapper.util.MtDesc;
import roj.collect.MyHashMap;
import roj.concurrent.pool.PrefixFactory;
import roj.concurrent.pool.TaskPool;
import roj.concurrent.task.ITask;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CMapping;
import roj.io.BOMInputStream;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.ui.CmdUtil;
import roj.util.ByteWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.StandardWatchEventKinds;
import java.util.Map;

/**
 * FMD Shared Data / Utility Methods
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/29 11:38
 */
public final class Shared {
    public static final boolean DEBUG;
    public static final Map<String, String> srg2mcp = new MyHashMap<>(1000, 1.5f);
    static final boolean ENABLE_CONCURRENT = false;

    public static final String VERSION = "1.5.4";

    public static final File BASE, TMP_DIR, PROJ_CONF_DIR;

    static Project currentProject;
    static boolean isForgeMap;

    static final ConstMapper mapperFwd = new ConstMapper();
    static ConstMapper mapperRev;

    static FileWatcher watcher;

    public static TaskPool parallel = new TaskPool(1, Runtime.getRuntime().availableProcessors() * 2, 16, 1024, new PrefixFactory("AsyncTasker", 5000));

    public static final CMapping MAIN_CONFIG;

    static {
        File base;
        try {
            base = new File(FMDMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsoluteFile().getParentFile().getParentFile();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            base = new File("").getAbsoluteFile();
        }
        BASE = base;

        Compiler.BASE_PATH = BASE.getAbsolutePath();
        if(File.separatorChar != '/') {
            Compiler.BASE_PATH = Compiler.BASE_PATH.replace(File.separatorChar, '/');
        }

        TMP_DIR = new File(BASE, "tmp/");

        if(!TMP_DIR.isDirectory() && !TMP_DIR.mkdir()) {
            CmdUtil.error("无法创建临时文件夹: " + TMP_DIR.getAbsolutePath());
            System.exit(-2);
        }

        PROJ_CONF_DIR = new File(BASE, "/config/");
        if(!PROJ_CONF_DIR.isDirectory() && !PROJ_CONF_DIR.mkdirs()) {
            CmdUtil.error("无法创建配置保存文件夹: " + PROJ_CONF_DIR.getAbsolutePath());
            System.exit(-2);
        }

        File file = new File(BASE, "config.json");
        try {
            final BOMInputStream bom = new BOMInputStream(new FileInputStream(file), "UTF8");
            if(!bom.getEncoding().equals("UTF8")) { // 检测到了则是 UTF-8
                CmdUtil.warning("文件的编码中含有BOM(推荐使用UTF-8无BOM格式!), 识别的编码: " + bom.getEncoding());
            }

            MAIN_CONFIG = JSONParser.parse(IOUtil.readAs(bom, bom.getEncoding()), 2).asMap();

            MAIN_CONFIG.dotMode(true);

            DEBUG = MAIN_CONFIG.getBool("调试模式");

            CMapping cfgGen = MAIN_CONFIG.get("通用").asMap();
            // 4KB
            FileUtil.MIN_ASYNC_SIZE = cfgGen.getBool("使用多线程下载") ? 1024 * 4 : Integer.MAX_VALUE;
            FileUtil.ENABLE_ENDPOINT_RECOVERY = cfgGen.getBool("开启断点续传");
            FileUtil.USER_AGENT = cfgGen.getString("UserAgent");
            FileUtil.TIMEOUT = cfgGen.getInteger("下载超时");

        } catch (ParseException | ClassCastException e) {
            CmdUtil.error(file.getAbsolutePath() + " 有语法错误! 请修正!", e);
            System.exit(-2);
            throw new RuntimeException();
        } catch (IOException e) {
            CmdUtil.error(file.getAbsolutePath() + " 读取失败!", e);
            System.exit(-2);
            throw new RuntimeException();
        }

        if(MAIN_CONFIG.getDot("FMD配置.文件修改监控").asBool()) {
            try {
                watcher = new FileWatcher();

                watcher.register(new File(BASE, "/class/").toPath(), path -> {
                    CmdUtil.warning("库文件已被修改,重新加载映射表...");
                    mapperFwd.clear();
                    initForwardMapper();
                    mapperRev = null;
                }, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                watcher.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static final File CONF_INDEX = new File(BASE, "config/index.json");

    public static final String MERGED_FILE_NAME = "forgeMcBin";

    public static void setConfig(String path) {
        try(FileOutputStream fos = new FileOutputStream(CONF_INDEX)) {
            CMapping map = new CMapping();
            map.put("config", path);
            map.put("forgeMapping", isForgeMap);
            ByteWriter.encodeUTF(map.toJSON()).writeToStream(fos);
        } catch (IOException e) {
            CmdUtil.error("配置保存", e);
        }
        currentProject = Project.load(path);
    }

    public static boolean loadConfig(boolean forced) {
        if(currentProject == null) {
            if(!CONF_INDEX.isFile())
                return false;
            String cf;
            try {
                CMapping map = JSONParser.parse(IOUtil.readAsUTF(new FileInputStream(CONF_INDEX))).asMap();
                cf = map.getString("config");
                isForgeMap = map.getBool("forgeMapping");
            } catch (ParseException | ClassCastException e) {
                CmdUtil.warning("配置索引(config/index.json)解析失败, 使用默认配置", e);
                cf = "default";
            } catch (IOException e) {
                throw new RuntimeException("配置索引读取失败", e);
            }

            File file = new File(BASE, "/config/" + cf + ".json");
            if(forced || file.isFile()) {
                try {
                    currentProject = Project.load(cf);
                } catch (Throwable e) {
                    CmdUtil.warning("配置读取失败, 使用默认配置", e);
                    currentProject = Project.load("default");
                    setConfig("default");
                }
                return true;
            } else
                return false;
        }
        return false;
    }

    public static void initForwardMapper() {
        if(mapperFwd.getClassMap().isEmpty()) {
            synchronized (mapperFwd) {
                if(mapperFwd.getClassMap().isEmpty()) {
                    try {
                        mapperFwd.initEnv(new File(BASE, "/util/mcp-srg.srg"), new File(BASE, "/class/"), new File(BASE, "/util/remapCache.bin"), false);
                        mapperFwd.backupLibSupers();
                        if (DEBUG)
                            CmdUtil.success("正向映射表已加载");
                    } catch (Exception e) {
                        CmdUtil.error("正向映射表加载失败", e);
                    }
                }
            }
        }
    }

    public static ConstMapper getReverseMapper() {
        if(mapperRev == null) {
            initForwardMapper();

            mapperRev = new ConstMapper(mapperFwd);
            mapperRev.reverse();

            if(DEBUG)
                CmdUtil.success("反向映射表已加载");
        }
        return mapperRev;
    }

    public static void threadWait(ITask... tasks) {
        if(!ENABLE_CONCURRENT) {
            for (ITask task : tasks) {
                try {
                    task.calculate(null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return;
        }

        for(ITask task : tasks) {
            parallel.pushTask(task);
        }

        parallel.waitUntilFinish();
    }

    public static void saveForgeMapping() {
        try(FileOutputStream fos = new FileOutputStream(CONF_INDEX)) {
            CMapping map = new CMapping();
            if(currentProject != null)
                map.put("config", currentProject.name);
            map.put("forgeMapping", isForgeMap);
            ByteWriter.encodeUTF(map.toJSON()).writeToStream(fos);
        } catch (IOException e) {
            CmdUtil.error("配置保存", e);
        }
    }

    public static void load_S2M_Map() {
        Map<String, String> srg2mcp = Shared.srg2mcp;
        if(srg2mcp.isEmpty()) {
            Shared.initForwardMapper();

            ConstMapper fwd = mapperFwd;
            for (Map.Entry<FlDesc, String> entry : fwd.getFieldMap().entrySet()) {
                srg2mcp.put(entry.getValue(), entry.getKey().name);
            }

            for (Map.Entry<MtDesc, String> entry : fwd.getMethodMap().entrySet()) {
                srg2mcp.put(entry.getValue(), entry.getKey().name);
            }
        }
    }
}
