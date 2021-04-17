package roj.io.down;

import roj.text.CharList;
import roj.text.TextUtil;
import roj.ui.CmdUtil;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/9/13 12:33
 */
public class STDProgress implements IProgressHandler {
    public static boolean DECR_LOGS = false, DECR_LOGS_2 = false;

    // 进度条粒度
    private static final int PROGRESS_SIZE = 25;
    private static final int BITE = 100 / PROGRESS_SIZE;

    public static String printProgress(double percent) {
        CharList sb = new CharList();

        int tx = (int) percent / BITE;

        CharList finish = TextUtil.repeat(tx, '█');
        CharList unFinish = TextUtil.repeat(PROGRESS_SIZE - tx, '─');

        sb.append(TextUtil.scaledDouble(percent)).append("%├").append(finish);
        sb.append(unFinish);
        return sb.append('┤').toString();
    }

    public void onReturn() {
        synchronized (System.out) {
            CmdUtil.clearLine();
            if(!DECR_LOGS) {
                CmdUtil.cursorUp(1);
                CmdUtil.clearLine();
            }
            CmdUtil.color("下载完成", CmdUtil.Color.GREEN);
            CmdUtil.cursorLeft(20);
            CmdUtil.cursorDown(1);
        }
    }

    protected boolean errored;

    @Override
    public void errorCaught() {
        errored = true;
    }

    @Override
    public boolean continueDownload() {
        return !errored;
    }

    long lastTime;

    @Override
    public void handleProgress(Downloader thread, long downloaded, long speedByByte) {
        if(DECR_LOGS_2) {
            long t = System.currentTimeMillis();
            if(t - lastTime < 5000) {
                return;
            }
            lastTime = t;
        }

        double temp = (100d * (double) downloaded / (double) thread.length);

        synchronized (System.out) {
            CmdUtil.clearLine();
            CmdUtil.fg(CmdUtil.Color.CYAN, false);

            System.out.println(printProgress(temp));

            if(!DECR_LOGS) {
                CmdUtil.clearLine();
                CmdUtil.fg(CmdUtil.Color.GREEN, false);

                System.out.print("Avg: " + TextUtil.getScaledNumber(speedByByte).toUpperCase() + "B/S");
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
        pr("速度太慢, 尝试重建链接", CmdUtil.Color.YELLOW, true);
    }

    public static void pr(String s) {
        pr(s, null, false);
    }

    public static void pr(String s, CmdUtil.Color color, boolean hl) {
        if(DECR_LOGS_2)
            return;

        synchronized (System.out) {
            CmdUtil.cursorLeft(200);
            CmdUtil.cursorDown(2);

            CmdUtil.clearLine();
            if (color != null) CmdUtil.fg(color, hl);
            System.out.print(s);
            if (color != null) CmdUtil.reset();
            System.out.println();

            CmdUtil.cursorLeft(200);
            CmdUtil.cursorUp(3);
        }
    }
}
