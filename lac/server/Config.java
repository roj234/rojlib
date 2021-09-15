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
package lac.server;

import roj.config.JSONConfiguration;
import roj.config.data.CMapping;
import roj.io.BoxFile;

import java.io.File;
import java.io.IOException;

/**
 * LAC Configuration
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/7/8 18:53
 */
public final class Config extends JSONConfiguration {
    public static final Config instance;

    public static BoxFile mod_info;
    public static boolean enableLoginModule, kickWrong;
    public static int loginDelay;

    static {
        instance = new Config();
    }

    private Config() {
        super(new File("LAC.json"));
    }

    public static int noPass1Type;

    public static String getInInfo(String key) {
        return null;
    }

    @Override
    protected void readConfig(CMapping map) {
        map.dotMode(true);

        File f = new File(map.putIfAbsent("mod描述文件", "mod.info"));
        if(!f.isFile())
            throw new RuntimeException("未找到mod描述文件, 请先配置LAC！");
        try {
            mod_info = new BoxFile(f);
        } catch (IOException e) {
            throw new RuntimeException("mod描述文件无法读取", e);
        }

        // do sth
    }
}
