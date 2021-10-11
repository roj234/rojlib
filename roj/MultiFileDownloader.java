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
import roj.io.down.IProgressHandler;
import roj.io.down.MTDProgress;
import roj.text.SimpleLineReader;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/3 16:50
 */
public final class MultiFileDownloader {
    static List<String> urls = new ArrayList<>();

    public static class MyProgressHandler extends MTDProgress {
        @Override
        public void onReturn() {
        }

        public void myOnReturn() {
            super.onReturn();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length == 0) {
            System.out.println("MultiFileDownloader <savePath> <urlFile0> [urlFile1 ...]");
            return;
        }

        boolean flag = args[0].equals("-noinput");
        for (int i = 1; i < args.length; i++) {
            parseFileNames(args[i]);
        }

        FileUtil.ENABLE_ENDPOINT_RECOVERY = true;
        FileUtil.USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36";

        int threadCount;
        if (!flag) {
            String ua = UIUtil.userInput("UA(可选): ");
            if (!ua.equals("")) {
                FileUtil.USER_AGENT = ua;
            }

            System.out.print("线程数[1, 500]: ");
            threadCount = UIUtil.getNumberInRange(1, 501);
        } else {
            threadCount = Math.min(urls.size(), 500);
        }

        File base = new File(args[0]);
        if (!base.isDirectory() && !base.mkdirs()) {
            throw new FileNotFoundException("无法创建保存目录 " + args[0]);
        }

        urls.sort(null);

        int delim = urls.size() / threadCount;
        if (delim < 1) {
            delim = 1;
        }

        MyProgressHandler handler = new MyProgressHandler();

        List<Thread> threads = new ArrayList<>(threadCount);

        File downloadInfo = new File(base, "RojMFD.nfo");

        List<String> list = new ArrayList<>(500);
        int i = 0, j = 0;

        handler.onInitial(urls.size());
        for (String s : urls) {
            list.add(s);
            if (++i > delim) {
                threads.add(openThreadToDownload(list.toArray(new String[list.size()]), base, handler, j + j++ * delim, downloadInfo));
                list.clear();
                i = 0;
            }
        }
        if (!list.isEmpty()) {
            threads.add(openThreadToDownload(list.toArray(new String[list.size()]), base, handler, j + j * delim, downloadInfo));
        }

        for (Thread thread1 : threads) {
            thread1.start();
        }

        System.out.println("已启动 " + j + "个线程");
        int delay = 3;
        do {
            System.out.println(delay + "秒后开始下载");
            Thread.sleep(1000);
        } while (--delay > 0);

        for (Thread thread1 : threads) {
            thread1.join();
        }

        handler.myOnReturn();
    }

    static int i = 1;

    private static Thread openThreadToDownload(String[] arr, File base, IProgressHandler handler, int offset, File downloadInfo) {
        if (arr.length == 0) return null;
        return new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return;
            }
            int offset1 = offset;
            for (String s : arr) {
                File saveTo = new File(base, s.substring(s.lastIndexOf('/') + 1));
                if (!saveTo.exists() || saveTo.length() == 0) {
                    int retry = 2;
                    do {
                        try {
                            FileUtil.downloadFile(s, saveTo, downloadInfo, handler, offset1, false);
                            break;
                        } catch (Throwable e) {
                            CmdUtil.warning("Failure downloading " + saveTo.getName() + " - " + e.getLocalizedMessage());
                            CmdUtil.warning("Retry " + (3 - retry) + "/3");
                        }
                    } while (retry-- > 0);
                }
                offset1++;
            }
        }, "Downloader_" + i++);
    }

    private static void parseFileNames(String name) throws IOException {
        File file = new File(name);
        try (SimpleLineReader slr = new SimpleLineReader(new FileInputStream(file), false)) {
            for (String s : slr) {
                s = s.trim();
                if (!s.isEmpty() && !s.startsWith("#")) {
                    if (!s.startsWith("http")) {
                        throw new IllegalArgumentException(name + "第" + slr.index() + "行不是有效的链接!");
                    }
                    urls.add(s);
                }
            }
        }
    }
}
