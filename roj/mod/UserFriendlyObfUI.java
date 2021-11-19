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
package roj.mod;

import roj.asm.mapper.SimpleObfuscator;
import roj.asm.mapper.Util;
import roj.asm.util.Context;
import roj.config.JSONParser;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.FileUtil;
import roj.io.IOUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static roj.mod.Shared.BASE;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/11/30 13:07
 */
public class UserFriendlyObfUI {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("UserFriendlyObfUI <项目名>");
            return;
        }
        File projectFile = new File(BASE, "config/" + args[0] + ".json");
        if (!projectFile.isFile()) {
            System.err.println("项目不存在");
            return;
        }
        Project project = Project.load(args[0]);

        File jarFile = new File(BASE, project.name + '-' + project.version + ".jar");
        if (!jarFile.isFile()) {
            System.err.println("构建输出不存在");
            return;
        }

        CMapping json = JSONParser.parse(IOUtil.readUTF(new File(BASE, "obfuscator.json"))).asMap();
        CMapping projectSpec = json.containsKey(args[0]) ? json.getOrCreateMap(args[0]) : json.get("*").asMap();
        SimpleObfuscator obf = new SimpleObfuscator();
        obf.setFlags(projectSpec.getInteger("flags"));
        if (projectSpec.containsKey("随机种子")) {
            obf.rand.setSeed(projectSpec.getLong("随机种子"));
        }
        CList list = projectSpec.getOrCreateList("保留的包");
        for (int i = 0; i < list.size(); i++) {
            obf.packageExclusions.add(list.get(i).asString());
        }
        json = projectSpec.getOrCreateMap("保留的类及方法/字段");
        // todo 混淆方式

        List<Context> contexts = Util.ctxFromZip(jarFile, StandardCharsets.UTF_8);
        obf.reset(FileUtil.findAllFiles(new File(BASE, "class")));
        obf.obfuscate(contexts);
        // todo 覆盖现有文件


        if (projectSpec.containsKey("混淆映射表保存位置")) {
            obf.writeObfuscationMap(new File(projectSpec.getString("混淆映射表保存位置")));
        }
    }
}
