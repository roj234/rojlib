package roj;

import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.io.down.DownloadTask;
import roj.io.down.ProgressGroupedMulti;
import roj.io.down.ProgressMulti;
import roj.net.NetworkUtil;
import roj.text.LineReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Roj234
 * @since 2020/10/3 16:50
 */
public final class MultiFileDownloader {
	private static final SimpleList<DownloadTask> maxConcurrent = new SimpleList<>();
	private static final MyHashSet<String> diedUrls = new MyHashSet<>();

	private static int MAX_CONCURRENT_REQUEST = 16;
	private static ProgressGroupedMulti progress;

	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			System.out.println("MultiFileDownloader [--thread=N, --ua=UA, --singleBar] <savePath> <urlFile0> [urlFile1 ...]");
			return;
		}

		int i = 0;
		int thread = 32;
		String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36";
		boolean mergedBar = false;
		for (; i < args.length; i++) {
			if (!args[i].startsWith("-")) break;
			switch (args[i]) {
				case "--thread": thread = Integer.parseInt(args[++i]); break;
				case "--ua": ua = args[++i]; break;
				case "--singleBar": mergedBar = true; break;
			}
		}

		MAX_CONCURRENT_REQUEST = thread;
		DownloadTask.userAgent = ua;
		if (mergedBar) progress = new ProgressGroupedMulti();
		//		DownloadTask.defHeaders = Helpers.cast(new Headers("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9\n" +
		//			"Accept-Encoding: gzip, deflate\n" +
		//			"Accept-Language: zh-CN,zh;q=0.9,zh-TW;q=0.8\n" +
		//			"Cache-Control: max-age=0\n" +
		//			"Connection: keep-alive\n" +
		//			"Sec-Fetch-Dest: document\n" +
		//			"Sec-Fetch-Mode: navigate\n" +
		//			"Sec-Fetch-Site: none\n" +
		//			"Sec-Fetch-User: ?1\n" +
		//			"Upgrade-Insecure-Requests: 1\n" +
		//			"User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36"));
		NetworkUtil.setHostCachePolicy(true, 3600);
		NetworkUtil.setHostCachePolicy(false, 120);

		File base = new File(args[i++]);
		if (!base.isDirectory() && !base.mkdirs()) throw new FileNotFoundException("无法创建保存目录 " + args[0]);

		for (;i < args.length; i++) {
			parseFileNames(base, args[i]);
		}
	}

	private static void parseFileNames(File base, String name) throws IOException {
		File file = new File(name);
		LineReader slr = new LineReader(new FileInputStream(file), StandardCharsets.UTF_8, false);

		if (progress != null) {
			progress.bar().setName(file.getName());
			progress.setTotal(slr.size());
		}

		for (String s : slr) {
			s = s.trim();
			if (!s.isEmpty() && !s.startsWith("#")) {
				if (!s.startsWith("http")) {
					throw new IllegalArgumentException(name + "第" + slr.lineNumber() + "行不是有效的链接!");
				}
				download(base, s);
			}
		}
	}

	private static void download(File id, String url) throws IOException {
		URL url1 = new URL(url);
		if (diedUrls.contains(url1.getHost())) {
			System.out.println("skip " + url1.getHost());
			return;
		}

		File file = new File(id, IOUtil.fileName(url1.getPath()));
		if (file.isFile()) {
			progress.finished++;
			return;
		}

		try {
			InetAddress.getByName(url1.getHost());
		} catch (UnknownHostException e) {
			diedUrls.add(url1.getHost());
			System.out.println("skip " + url1.getHost());
			return;
		}

		DownloadTask task = DownloadTask.createTask(url, file, progress == null ? new ProgressMulti() {
			{
				bar.setName(file.getName());
			}
		} : progress.subProgress());

		findSpace:
		while (maxConcurrent.size() >= MAX_CONCURRENT_REQUEST) {
			for (int i = maxConcurrent.size() - 1; i >= 0; i--) {
				DownloadTask task1 = maxConcurrent.get(i);
				if (task1.isDone()) {
					maxConcurrent.remove(i);
					break findSpace;
				}
			}
			LockSupport.parkNanos(1000000);
		}
		maxConcurrent.add(task);
		DownloadTask.QUERY.pushTask(task);
	}
}
