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
package roj.misc;

import roj.io.FileUtil;
import roj.io.MutableZipFile;

import java.io.File;
import java.io.IOException;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/12/11 14:46
 */
public class ZipShrinker {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("ZipShrinker <path>");
            System.out.println("  用途：精简zip, 删除目录项");
            return;
        }

        for (File file : FileUtil.findAllFiles(new File(args[0]), (f) -> f.getName().endsWith(".jar") || f.getName().endsWith(".zip"))) {
            long modTime = file.lastModified();
            try (MutableZipFile zf = new MutableZipFile(file)) {
                for (String entry : zf.getEntries().keySet()) {
                    if (entry.endsWith("/")) {
                        zf.setFileData(entry, null);
                    }
                }
                zf.getEOF().setComment(new byte[0]);
                zf.store();
            } catch (IOException e) {
                e.printStackTrace();
            }
            file.setLastModified(modTime);
        }
        System.out.println("OK");
    }
}
