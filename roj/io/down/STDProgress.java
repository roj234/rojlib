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

import roj.text.TextUtil;
import roj.ui.CmdUtil;

import java.io.PrintStream;

/**
 * @author Roj234
 * @since  2020/9/13 12:33
 */
public class STDProgress implements IProgress {
    public static boolean noAvgSpeed    = false;
    public static int     printInterval = 1000;

    // 进度条粒度
    private static final int PROGRESS_SIZE = 25;
    private static final int BITE = 100 / PROGRESS_SIZE;

    static void print(double percent, long speed) {
        PrintStream out = System.out;
        synchronized (out) {
            CmdUtil.clearLine();
            CmdUtil.fg(CmdUtil.Color.CYAN, false);

            int tx = (int) percent / BITE;

            out.print(TextUtil.toFixed(percent));
            out.print("%├");

            out.print(TextUtil.repeat(tx, '█'));
            out.print(TextUtil.repeat(PROGRESS_SIZE - tx, '─'));
            out.println("┤");

            if(!noAvgSpeed) {
                CmdUtil.clearLine();
                CmdUtil.fg(CmdUtil.Color.GREEN, false);

                out.println("Avg: " + TextUtil.scaledNumber(speed).toUpperCase() + "B/S");
            }

            CmdUtil.reset();

            CmdUtil.cursorLeft(999);
            CmdUtil.cursorUp(2);
        }
    }

    public void onFinish() {
        synchronized (System.out) {
            CmdUtil.clearLine();
            CmdUtil.cursorUp(1);
            CmdUtil.clearLine();
            CmdUtil.success("下载完成");
        }
    }

    boolean kill;

    @Override
    public boolean wasShutdown() {
        return kill;
    }

    @Override
    public void shutdown() {
        kill = true;
    }

    long last;

    @Override
    public void onChange(IDown dn) {
        long t = System.currentTimeMillis();
        if(t - last < printInterval) return;
        last = t;

        double temp = (100d * (double) dn.getDownloaded() / (double) dn.getTotal());

        print(temp, dn.getAverageSpeed());
    }
}
