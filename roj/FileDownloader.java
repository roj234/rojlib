package roj;

import roj.io.down.DownloadTask;
import roj.ui.CLIUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2020/10/3 16:50
 */
public final class FileDownloader {
	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			System.out.println("FileDownloader <saveTo> <url>");
			return;
		}

		DownloadTask.defETag = false;
		DownloadTask.userAgent = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36";

		int threadCount;
		if (args.length < 3) {
			System.out.print("线程数: ");
			threadCount = CLIUtil.getNumberInRange(0, 256);
			if (threadCount == 0) {
				threadCount = Runtime.getRuntime().availableProcessors() << 2;
			}
		} else {
			threadCount = Integer.parseInt(args[2]);
		}
		DownloadTask.defMaxChunks = threadCount;
		DownloadTask.defChunkStart = 0;

		File saveTo = new File(args[0]);

		int retry = 2;
		do {
			try {
				DownloadTask.downloadMTD(args[1], saveTo).waitFor();
				break;
			} catch (Throwable e) {
				if (e.getMessage() == null) e.printStackTrace();
				else CLIUtil.warning("下载失败 " + saveTo.getName() + " - " + e.getLocalizedMessage());
				CLIUtil.warning("重试 " + (3 - retry) + "/3");
			}
		} while (retry-- > 0);
	}
}
