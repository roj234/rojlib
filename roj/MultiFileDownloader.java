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

import roj.concurrent.PrefixFactory;
import roj.concurrent.TaskPool;
import roj.io.FileUtil;
import roj.io.down.Downloader;
import roj.io.down.IDown;
import roj.io.down.MTDProgress;
import roj.text.SimpleLineReader;
import roj.ui.UIUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since  2020/10/3 16:50
 */
public final class MultiFileDownloader {
    static List<String> urls = new ArrayList<>();

    public static class MyProgress extends MTDProgress {
        @Override
        public void onJoin(IDown dn) {
            synchronized (workers) {
                workers.add(dn);
            }
        }

        @Override
        public void onFinish() {}

        public void myOnReturn() {
            super.onFinish();
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("MultiFileDownloader [-noinput] <savePath> <urlFile0> [urlFile1 ...]");
            return;
        }

        boolean flag = args[0].equals("-noinput");
        for (int i = 1; i < args.length; i++) {
            parseFileNames(args[i]);
        }

        FileUtil.userAgent = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36";
        int threadCount;
        if (!flag) {
            String ua = UIUtil.userInput("UA(可选): ");
            if (!ua.equals("")) {
                FileUtil.userAgent = ua;
            }

            System.out.print("线程数[1, 500]: ");
            threadCount = UIUtil.getNumberInRange(1, 501);
        } else {
            threadCount = Math.min(urls.size(), 500);
        }

        TaskPool parallel = new TaskPool(0, threadCount, 1, 512,
                      new PrefixFactory("MFD并行IO", 20000));

        File base = new File(args[0]);
        if (!base.isDirectory() && !base.mkdirs()) {
            throw new FileNotFoundException("无法创建保存目录 " + args[0]);
        }

        urls.sort(null);

        MyProgress handler = new MyProgress();

        for (int k = 0; k < urls.size(); k++) {
            String s = urls.get(k);
            parallel.pushRunnable(() -> {
                File saveTo = new File(base, s.substring(s.lastIndexOf('/') + 1));
                try {
                    Downloader.download(s, saveTo, handler).waitFor();
                } catch (IOException e) {
                    Helpers.athrow(e);
                }
            });
        }

        System.out.println("已启动 " + threadCount + "个线程");

        parallel.waitUntilFinish();

        handler.myOnReturn();
    }

    private static void parseFileNames(String name) throws IOException {
        File file = new File(name);
        try (SimpleLineReader slr = new SimpleLineReader(new FileInputStream(file), false)) {
            for (String s : slr) {
                s = s.trim();
                if (!s.isEmpty() && !s.startsWith("#")) {
                    if (!s.startsWith("http")) {
                        throw new IllegalArgumentException(name + "第" + slr.lineNumber() + "行不是有效的链接!");
                    }
                    urls.add(s);
                }
            }
        }
    }
}
