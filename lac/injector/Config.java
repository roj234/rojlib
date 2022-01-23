/*
 * This file is a part of MoreItems
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
package lac.injector;

import roj.collect.MyHashSet;
import roj.config.JSONConfiguration;
import roj.config.data.CMapping;

import java.io.File;

/**
 * Config
 *
 * @author Roj233
 * @since 2021/10/18 1:22
 */
public final class Config extends JSONConfiguration {
    static {
        new Config();
    }

    public static boolean rotateStaticMethods, plusPrivateMethods, makeBackup, useDigest, obfMC, srgMC;
    public static String randomSeed, fuckMiddleMan, classObfMode, methodObfMode, fieldObfMode,
            stackTraceDeobfPath, watermark;
    public static MyHashSet<String> obfKeepClasses;
    public static int obfFlags;

    private Config() {
        super(new File("lac_injector.json"));
    }

    @Override
    protected void readConfig(CMapping map) {
        map.put("提示", "本工具占用内存较大, 建议分配3~4G");
        map.put("这里是", "客户端混淆配置");
        map.put("其他配置", "请安装后在服务端看");
        obfMC = map.putIfAbsent("混淆MC核心", true);
        srgMC = map.putIfAbsent("映射MC核心至SRG名称(兼容较差)", false);
        rotateStaticMethods = map.putIfAbsent("乱放静态方法(成功率看脸,但是成功后可以极大提高防御力)", false);
        plusPrivateMethods = map.putIfAbsent("上一个选项为true时同时也乱放private的非静态方法", true);
        makeBackup = map.putIfAbsent("备份(不备份无法卸载！)", true);
        randomSeed = map.putIfAbsent("随机数种子, 留空使用当前时间", "");
        useDigest = map.putIfAbsent("使用MD5验证,虽然没啥用", true);
        fuckMiddleMan = map.putIfAbsent("验证密钥(限制能加入的服务器,留空任意,需要与服务端配置相同)", "123456");
        classObfMode = map.putIfAbsent("class混淆方式(SRG/ABC/III/W_RESERVED/KEYWORD/NONE)", "NONE");
        methodObfMode = map.putIfAbsent("method混淆方式", "SRG");
        fieldObfMode = map.putIfAbsent("field混淆方式", "SRG");
        obfKeepClasses = map.getOrCreateList("保留(不混淆)的class").asStringSet();
        obfFlags = map.putIfAbsent("附加混淆参数", 0);
        stackTraceDeobfPath = map.putIfAbsent("保存StackTrace反混淆至", "lac-stack-deobf.bin");
        watermark = map.putIfAbsent("水印，你可以当成作者，或者一种注释", "Using Roj234's LAC mod!");
    }
}
