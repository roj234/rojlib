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
package roj;

import roj.io.FileUtil;
import roj.io.down.Downloader;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author Roj234
 * @since  2020/10/3 16:50
 */
public final class FileDownloader {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("FileDownloader <saveTo> <url>");
            return;
        }

        Downloader.checkETag = false;
        FileUtil.userAgent = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36";

        int threadCount;
        if (args.length < 3) {
            System.out.print("线程数: ");
            threadCount = UIUtil.getNumberInRange(0, 256);
            if (threadCount == 0) {
                threadCount = Runtime.getRuntime().availableProcessors() << 2;
            }
        } else {
            threadCount = Integer.parseInt(args[2]);
        }
        Downloader.chunkCount = threadCount;
        Downloader.chunkStartSize = 0;

        File saveTo = new File(args[0]);

        int retry = 2;
        do {
            try {
                Downloader.downloadMTD(args[1], saveTo).waitFor();
                break;
            } catch (Throwable e) {
                if (e.getMessage() == null) e.printStackTrace();
                else CmdUtil.warning("下载失败 " + saveTo.getName() + " - " + e.getLocalizedMessage());
                CmdUtil.warning("重试 " + (3 - retry) + "/3");
            }
        } while (retry-- > 0);
    }
}
