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
package roj.io.down;

import roj.collect.ToLongMap;
import roj.text.TextUtil;
import roj.ui.CmdUtil;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/13 12:33
 */
public class MTDProgress extends STDProgress {
    private static final int TIME = 300;
    private static final int TIME_A = 100;

    ToLongMap<Downloader> downloaded, downloadBytes;
    long all, lastTime2;

    String s;

    public MTDProgress() {
        downloaded = new ToLongMap<>();
        downloadBytes = new ToLongMap<>();
    }

    @Override
    public void handleJoin(Downloader dn) {
        all += dn.length;
        downloaded.putLong(dn, 0);
        downloadBytes.putLong(dn, 0);
    }

    @Override
    public void handleProgress(Downloader dn, long downloaded, long deltaRead) {
        this.downloadBytes.getEntry(dn).v += deltaRead;

        _progress(dn, downloaded);
    }

    private void _progress(Downloader thread, long downloaded) {
        long curr = System.currentTimeMillis();
        if (curr - lastTime < (DECR_LOGS_2 ? 5000 : TIME_A)) {
            return;
        }
        lastTime = curr;

        long sum = 0;
        for (ToLongMap.Entry<Downloader> entry : this.downloaded.selfEntrySet()) {
            if (entry.k == thread)
                entry.v = downloaded;
            sum += entry.v;
        }

        //if(sum >= all) {
        //    handleDone(thread);
        //    return;
        //}

        synchronized (System.out) {
            CmdUtil.clearLine();
            CmdUtil.fg(CmdUtil.Color.CYAN, false);

            System.out.println(STDProgress.printProgress(sum * 100d / this.all));

            if(!DECR_LOGS) {
                CmdUtil.clearLine();
                CmdUtil.fg(CmdUtil.Color.GREEN, false);

                long diff = curr - lastTime2;
                if (diff >= TIME) {
                    sum = 0;
                    for (ToLongMap.Entry<Downloader> entry : this.downloadBytes.selfEntrySet()) {
                        sum += entry.v;
                        entry.v = 0;
                    }
                    long lastAvg = (long) (sum * 1000d / diff);

                    System.out.print(s = "Avg: " + TextUtil.getScaledNumber(lastAvg).toUpperCase() + "B/S");

                    lastTime2 = curr;
                } else {
                    System.out.print(s);
                }
            }

            CmdUtil.reset();

            if(!DECR_LOGS) {
                System.out.println();
            }

            CmdUtil.cursorLeft(200);
            CmdUtil.cursorUp(2);
        }
    }

    @Override
    public void handleReconnect(Downloader dn, long downloaded) {
        pr("[线程" + dn.id + "]速度太慢, 尝试重建链接", CmdUtil.Color.YELLOW, true);
    }

    @Override
    public void handleDone(Downloader dn) {
        this.downloadBytes.getEntry(dn).v += dn.length - this.downloaded.putLong(dn, dn.length);
        _progress(null, 0);
    }
}
