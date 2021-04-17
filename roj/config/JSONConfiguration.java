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
package roj.config;

/**
 * JSON格式配置文件包装
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */

import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.util.ByteWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class JSONConfiguration {
    final File config;
    CMapping map;

    public JSONConfiguration(File config, boolean instantInit) {
        this.config = config;
        if(instantInit)
            init();
    }

    public JSONConfiguration(File config) {
        this.config = config;
        init();
    }

    public final void init() {
        if (!config.isFile()) {
            resetConfig(this.map = new CMapping(), config);
        } else {
            reload();
        }
    }

    public void reload() {
        try (FileInputStream fis = new FileInputStream(config)) {
            CMapping map = this.map = JSONParser.parse(new String(IOUtil.read(fis), StandardCharsets.UTF_8), 2).asMap();
            readConfig(map);
        } catch (IOException | ParseException | ClassCastException e) {
            e.printStackTrace();
            config.renameTo(new File(config.getPath() + ".broken." + System.currentTimeMillis()));
            System.err.println("配置文件 " + config + " 读取失败! 重新生成配置!");
            resetConfig(this.map = new CMapping(), config);
        }
    }

    protected abstract void readConfig(CMapping map);

    private void resetConfig(CMapping map, File config) {
        readConfig(map);
        try (FileOutputStream fos = new FileOutputStream(config)) {
            ByteWriter.encodeUTF(map.toJSON()).writeToStream(fos);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public final void save() {
        saveConfig(getConfig());
        try (FileOutputStream fos = new FileOutputStream(config)) {
            fos.write(map.toJSON().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void saveConfig(CMapping map) {
        readConfig(map);
    }

    public final CMapping getConfig() {
        CMapping map = this.map;
        if(map == null)
            map = this.map = new CMapping();
        return map;
    }

    public final File getFile() {
        return config;
    }
}
