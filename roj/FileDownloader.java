package roj;

import roj.io.FileUtil;
import roj.ui.UIUtil;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/10/3 16:50
 */
public final class FileDownloader {
    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        if (args.length == 0) {
            System.out.println("FileDownloader <saveTo> <url>");
            return;
        }

        FileUtil.ENABLE_ENDPOINT_RECOVERY = true;
        FileUtil.USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36";
        String ua = UIUtil.userInput("UA(可选): ");
        if (!ua.equals("")) {
            FileUtil.USER_AGENT = ua;
        }

        System.out.print("线程数[0, 500](0为自动): ");
        int threadCount = UIUtil.getNumberInRange(0, 501);
        if (threadCount == 0) {
            threadCount = Runtime.getRuntime().availableProcessors() << 1;
        }
        FileUtil.MIN_ASYNC_SIZE = 0;

        int delay = 3;
        do {
            System.out.println(delay + "秒后开始下载");
            Thread.sleep(1000);
        } while (--delay > 0);

        File saveTo = new File(args[0]);

        FileUtil.downloadFileAsync(args[1], saveTo, threadCount).waitFor();
        /*int retry = 2;
        do {
            try {
                // xxx
                break;
            } catch (Throwable e) {
                CmdUtil.warning("Failure when downloading " + saveTo.getName() + " - " + e.getLocalizedMessage());
                CmdUtil.warning("Retry " + (3 - retry) + "/3");
            }
        } while (retry-- > 0);*/
    }
}
