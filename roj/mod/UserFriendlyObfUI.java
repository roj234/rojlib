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

import roj.asm.util.Context;
import roj.config.JSONParser;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.io.MutableZipFile;
import roj.io.MutableZipFile.EFile;
import roj.io.ZipFileWriter;
import roj.mapper.SimpleObfuscator;
import roj.mapper.Util;
import roj.util.ByteList;
import roj.util.ComboRandom;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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

        File src = new File(BASE, project.name + '-' + project.version + ".jar");
        if (!src.isFile()) {
            System.err.println("构建输出不存在");
            return;
        }
        System.out.println("!! 注意，本程序仅为FMD提供简易混淆功能 尚未完工");

        CMapping json = JSONParser.parse(IOUtil.readUTF(new File(BASE, "obfuscator.json"))).asMap();
        CMapping set = json.containsKey(args[0]) ? json.getOrCreateMap(args[0]) : json.get("*").asMap();

        SimpleObfuscator obf = new SimpleObfuscator();
        obf.setFlags(set.getInteger("flags"));

        if (!set.getString("随机种子").isEmpty()) {
            byte[] b = set.getString("随机种子").getBytes(StandardCharsets.UTF_8);
            long[] rnd = new long[b.length >> 3];
            ByteList r = new ByteList(b);
            for (int i = 0; i < rnd.length; i++) {
                rnd[i] = r.readLong() + b[rnd.length << 3 + (i & 7)];
            }
            obf.rand = new ComboRandom(rnd);
        }





        CList list = set.getOrCreateList("保留的包");
        for (int i = 0; i < list.size(); i++) {
            obf.packageExclusions.add(list.get(i).asString());
        }
        json = set.getOrCreateMap("保留的类及方法/字段");
        for (Map.Entry<String, CEntry> entry : json.entrySet()) {
            obf.classExclusions.add(entry.getKey());
        }
        // todo 混淆方式
        json = set.getOrCreateMap("混淆方式");
        // todo 过滤器, 不要上面的了

        // 混淆
        List<Context> contexts = Util.ctxFromZip(src, StandardCharsets.UTF_8);
        obf.reset(FileUtil.findAllFiles(new File(BASE, "class")));
        obf.obfuscate(contexts);

        // 保存
        MutableZipFile mzf = new MutableZipFile(src);
        if (set.getString("覆盖现有文件").equals("true")) {
            for (int i = 0; i < contexts.size(); i++) {
                Context ctx = contexts.get(i);
                mzf.setFileData(ctx.getFileName(), () -> ctx.get(true), true);
            }
            mzf.store();
        } else {
            File dest = new File(set.getString("覆盖现有文件"));
            ZipFileWriter zfw = new ZipFileWriter(dest);
            for (int i = 0; i < contexts.size(); i++) {
                Context ctx = contexts.get(i);
                zfw.writeNamed(ctx.getFileName(), ctx.get(true));
            }
            for(EFile eFile : mzf.getEntries().values()) {
                if (eFile.getName().endsWith("/") || eFile.getName().endsWith(".class"))
                    continue;
                zfw.write(mzf, eFile);
            }
            zfw.close();
        }
        mzf.close();

        // 保存映射
        if (!set.getString("混淆映射表保存位置").isEmpty()) {
            obf.writeObfuscationMap(new File(set.getString("混淆映射表保存位置")));
        }
    }
}
