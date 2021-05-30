package roj.mod;

import roj.asm.remapper.IRemapper;
import roj.asm.remapper.RemapperV2;
import roj.concurrent.task.ITask;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CMapping;
import roj.io.BOMInputStream;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.ui.CmdUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/29 11:38
 */
public final class Shared {
    public static final boolean DEBUG;
    static final boolean ENABLE_CONCURRENT = false;

    public static final String VERSION = "1.4.4";

    public static final File BASE, TMP_DIR, PROJ_CONF_DIR;

    static Config config;
    static boolean isForgeMap;

    static RemapperV2 reverseRemapper;
    static final RemapperV2 remapper = new RemapperV2();

    static {
        File base;
        try {
            base = new File(ModDevelopment.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsoluteFile().getParentFile().getParentFile();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            base = new File("").getAbsoluteFile();
        }
        BASE = base;

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
    }

    public static final File CONF_INDEX = new File(BASE, "config/index.json"),
            MC_CONF = new File(BASE, "config/mc.json");

    public static final CMapping MAIN_CONFIG;

    static {
        File file = new File(BASE, "config.json");
        try {
            final BOMInputStream bom = new BOMInputStream(new FileInputStream(file), "UTF8");
            if(!bom.getEncoding().equals("UTF8")) { // 检测到了则是 UTF-8
                CmdUtil.warning("文件的编码中含有BOM(推荐使用UTF-8无BOM格式!), 识别的编码: " + bom.getEncoding());
            }

            MAIN_CONFIG = JSONParser.parse(IOUtil.readAs(bom, bom.getEncoding()), 64).asMap();

            DEBUG = MAIN_CONFIG.getBoolean("调试模式");

            CMapping cfgGen = MAIN_CONFIG.get("通用").asMap();
            // 4KB
            FileUtil.MIN_ASYNC_SIZE = cfgGen.getBoolean("使用多线程下载") ? 1024 * 4 : Integer.MAX_VALUE;
            FileUtil.ENABLE_ENDPOINT_RECOVERY = cfgGen.getBoolean("开启断点续传");
            FileUtil.USER_AGENT = cfgGen.getString("UserAgent");

        } catch (ParseException | ClassCastException e) {
            CmdUtil.error(file.getAbsolutePath() + " 有语法错误! 请修正!", e);
            System.exit(-2);
            throw new RuntimeException();
        } catch (IOException e) {
            CmdUtil.error(file.getAbsolutePath() + " 读取失败!", e);
            System.exit(-2);
            throw new RuntimeException();
        }
    }

    public static final String MERGED_FILE_NAME = "forgeMcBin";

    public static boolean loadConfig(boolean forced) {
        if(config == null) {
            if(!CONF_INDEX.isFile())
                return false;
            String cf;
            try {
                CMapping map = JSONParser.parse(IOUtil.readAsUTF(new FileInputStream(CONF_INDEX))).asMap();
                cf = map.getString("config");
                isForgeMap = map.getBoolean("forgeMapping");
            } catch (ParseException | ClassCastException e) {
                CmdUtil.warning("配置索引(config/index.json)解析失败, 使用默认配置", e);
                cf = BASE.getAbsolutePath() + "/config/default.json";
            } catch (IOException e) {
                throw new RuntimeException("配置索引读取失败", e);
            }

            File file = new File(cf);
            if(forced || file.isFile()) {
                config = new Config(file);
                return true;
            } else
                return false;
        }
        return false;
    }

    public static void initRemapper() {
        if(remapper.getClassMap().isEmpty()) {
            try {
                remapper.prepareEnv(new File(BASE, "/util/mcp-srg.srg"), new File(BASE, "/class/"), new File(BASE, "/util/remapCache.bin"), false, true);
                CmdUtil.success("映射表已加载");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static IRemapper getReverseRemapper() {
        if(reverseRemapper == null) {
            reverseRemapper = new RemapperV2();
            try {
                reverseRemapper.prepareEnv(new File(BASE, "/util/mcp-srg.srg"), new File(BASE, "/class/"), null, true, false);
                CmdUtil.success("反向映射表已加载");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return reverseRemapper;
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
            MCLauncher.parallel.pushTask(task);
        }

        try {
            MCLauncher.parallel.waitUntilFinish();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void setCurrentConfig(String path) {
        try(FileOutputStream fos = new FileOutputStream(CONF_INDEX)) {
            CMapping map = new CMapping();
            map.put("config", path);
            map.put("forgeMapping", isForgeMap);
            fos.write(map.toJSON().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            CmdUtil.error("", e);
        }
    }

    public static void updateConfig() {
        if(config != null)
            setCurrentConfig(config.getFile().getAbsolutePath());
    }
}
