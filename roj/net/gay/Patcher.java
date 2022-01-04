/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roj234
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
package roj.net.gay;

import roj.io.IOUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author solo6975
 * @version 0.1
 * @since 2022/1/3 17:25
 */
public class Patcher {
    public static void main(String[] args) throws IOException {
        System.out.println("线性版本管理系统 Gay 1.0 补丁更新系统 0.1");
        if (args.length < 3) {
            System.out.println("参数: Patcher <patch> <version> <base path>");
            return;
        }
        ByteList sb = IOUtil.getSharedByteBuf();
        int lv = sb.readStreamFully(new FileInputStream(args[0])).readInt();
        sb.clear();
        sb.readStreamFully(new FileInputStream(args[1]));
        int rv = sb.readInt();
        if (rv != lv) {
            System.out.println("无法更新！此补丁适用于 " + rv + " 版本，您当前的版本是 " + lv);
            return;
        }
        int tv = sb.readInt();
        System.out.println("开始更新，目标版本: " + tv);
        try {
            Ver.applyPatch(sb, new File(args[2]));
            sb.clear();
            sb.putInt(tv).writeToStream(new FileOutputStream(args[1]));
            System.out.println("更新成功！");
        } finally {
            System.out.println("更新失败，请将错误复制并交给管理员！");
        }
    }
}
