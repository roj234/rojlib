package roj.io.down;

import roj.collect.ToLongMap;
import roj.text.TextUtil;
import roj.ui.CmdUtil;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/9/13 12:33
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
    public void handleJoin(Downloader downloader) {
        all += downloader.length;
        downloaded.putLong(downloader, 0);
        downloadBytes.putLong(downloader, 0);
    }

    @Override
    public void handleProgress(Downloader thread, long downloaded, long deltaRead) {
        this.downloadBytes.getEntry(thread).v += deltaRead;

        _progress(thread, downloaded);
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
    public void handleReconnect(Downloader thread, long downloaded) {
        pr("[线程" + thread.id + "]速度太慢, 尝试重建链接", CmdUtil.Color.YELLOW, true);
    }

    @Override
    public void handleDone(Downloader thread) {
        this.downloadBytes.getEntry(thread).v += thread.length - this.downloaded.putLong(thread, thread.length);
        _progress(null, 0);
    }
}
