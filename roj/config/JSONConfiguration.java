package roj.config;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: JSONConfiguration.java
 */

import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.util.log.ILogger;
import roj.util.log.LogManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class JSONConfiguration {
    static ILogger logger = LogManager.getLogger("JSONConf");

    final File config;

    public JSONConfiguration(File config) {
        this.config = config;
        if (!config.exists()) {
            resetConfig(new CMapping(), config);
        } else {
            reload();
        }

    }

    public void reload() {
        try (FileInputStream fis = new FileInputStream(config)) {
            CMapping map = JSONParser.parse(new String(IOUtil.readFully(fis), StandardCharsets.UTF_8), 64).asMap();
            //resetConfig(map, config);
            readConfig(map);
        } catch (IOException | ParseException | ClassCastException e) {
            logger.catching(e);
            config.renameTo(new File(config.getPath() + ".broken." + System.currentTimeMillis()));
            logger.warn("配置文件读取失败! 重新生成配置!");
            resetConfig(new CMapping(), config);
        }
    }

    protected abstract void readConfig(CMapping map);

    private void resetConfig(CMapping map, File config) {
        readConfig(map);
        //if(map.isModified()) {
        try (FileOutputStream fos = new FileOutputStream(config)) {
            fos.write(map.toJSON().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.catching(e);
        }
        //}
    }

    public final void save() {
        CMapping map = new CMapping();
        saveConfig(map);
        try (FileOutputStream fos = new FileOutputStream(config)) {
            fos.write(map.toJSON().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.catching(e);
        }
    }

    protected void saveConfig(CMapping map) {
        readConfig(map);
    }

    public final File getFile() {
        return config;
    }
}
