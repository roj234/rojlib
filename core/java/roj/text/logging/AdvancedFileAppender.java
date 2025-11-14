package roj.text.logging;

import roj.archive.xz.LZMA2Options;
import roj.archive.xz.LZMAOutputStream;
import roj.concurrent.FastLocalThread;
import roj.config.node.MapValue;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.Formatter;
import roj.text.TextUtil;
import roj.text.TextWriter;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 * @author Roj234
 * @since 2025/11/06 03:50
 */
public class AdvancedFileAppender implements LogAppender, Runnable {
	private final File logPath;
	private final Formatter logName;

	// rotation
	private final int rotationFileSizeKb;
	private final Formatter rotationFilePath;
	private String nextRotationFileName;

	// remove
	private final int maxFileCount, maxFileSizeKb;

	private final BlockingQueue<String> queue;
	private final boolean immediateFlush;

	private final Charset charset;
	private final Thread writer = new FastLocalThread(this, "RojLib 异步日志追加 ");
	private volatile boolean running = true;

	public AdvancedFileAppender(MapValue data) {
		data.dot(true);

		charset = Charset.forName(data.getString("charset", "UTF-8"));
		logPath = new File(data.getString("path", "logs"));
		logName = new Template(LogContext.FMT, data.getString("file_name", "latest.log"));

		if (data.getBool("async.enable", true)) {
			immediateFlush = false;
			queue = new ArrayBlockingQueue<>(data.getInt("async.buffer", 10));
		} else {
			immediateFlush = true;
			queue = new LinkedTransferQueue<>();
		}

		rotationFileSizeKb = (int) (Math.round(TextUtil.unscaledNumber1024(data.getString("rotation.size", "10MB"))) >> 10);
		if (rotationFileSizeKb <= 0) throw new IllegalArgumentException("rotation.size must be greater than 0");
		rotationFilePath = new Template(LogContext.FMT, data.getString("rotation.file_name", "%d{YYYY-MM-DD}.log.gz"));

		maxFileCount = data.getInt("remove.last", 30);
		maxFileSizeKb = (int) (Math.round(TextUtil.unscaledNumber1024(data.getString("remove.max_size", "1GB"))) >> 10);
		if (maxFileSizeKb < 0) throw new IllegalArgumentException("remove.max_size must be non-negative");

		writer.setDaemon(true);
		writer.start();
	}

	@Override
	public void append(CharList sb) throws IOException {
		try {
			String str = sb.toString();
			if (!queue.offer(str, 1000, TimeUnit.MILLISECONDS)) {
				throw new IOException("Log timed out: " + str);
			}
		} catch (InterruptedException e) {
			throw IOUtil.rethrowAsIOException(e);
		}
	}

	public void run() {
		try {
			File logFile = changeFile(null, null);
			var writer = TextWriter.append(logFile, charset);
			int deltaTime = 0;
			long prevTime = System.currentTimeMillis();
			try {
				while (running || !queue.isEmpty()) {
					long time = System.currentTimeMillis();
					deltaTime += time - prevTime;
					prevTime = time;

					if (deltaTime < 0 || deltaTime > 1000) {
						deltaTime = 0;

						var newFile = changeFile(logFile, writer);
						if (newFile != null) {
							writer = TextWriter.append(newFile, charset);
							logFile = newFile;
						}
					}

					try {
						var message = queue.take();
						writer.append(message);
						writer.flush();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			} finally {
				writer.close();
			}
		} catch (Throwable e) {
			System.err.println("AsyncLogger fatal error: "+e.getMessage());
			e.printStackTrace();
		}
	}

	private File changeFile(File currentFile, Closeable handle) throws IOException {
		if (currentFile != null) {
			if (currentFile.length() < ((long) rotationFileSizeKb << 10)) {
				if (getRotateFileName(currentFile).equals(nextRotationFileName)) {
					return null;
				}
			}
			handle.close();
			rotate(currentFile);

			nextRotationFileName = getRotateFileName(currentFile);
		}

		if (!logPath.exists()) {
			logPath.mkdirs();
		}

		block:
		if (maxFileSizeKb > 0) {
			File[] files = logPath.listFiles();
			if (files == null) break block;
			Arrays.sort(files, (o1, o2) -> Long.compare(o2.lastModified(), o1.lastModified()));

			// 删除旧文件
			long sumSize = 0;
			long maxFileSize = (long) maxFileSizeKb << 10;
			int i = 0;
			for (; i < files.length; i++) {
				File file = files[i];
				sumSize += file.length();
				if (sumSize > maxFileSize) break;
			}

			int countLimit = files.length - maxFileCount;
			if (countLimit > 0 && i > countLimit) i = countLimit;

			while (i < files.length) {
				var file = files[i];
				if (!file.delete()) System.err.println("[Async Logger] Failed to delete "+file.getName());
				i++;
			}
		}

		if (currentFile == null) {
			CharList sb = IOUtil.getSharedCharBuf();
			String filename = logName.format(Collections.emptyMap(), sb).toString();
			writer.setName(writer.getName()+IOUtil.fileName(filename));

			currentFile = new File(logPath, filename);

			nextRotationFileName = getRotateFileName(currentFile);
			// 打包旧的log
			if (currentFile.exists()) rotate(currentFile);
		}

		return currentFile;
	}

	private void rotate(File currentFile) throws IOException {
		String filename = nextRotationFileName;

		String newFilename = filename;
		var rotateFile = new File(logPath, newFilename);
		int nth = 1;
		while (rotateFile.exists()) {
			String n = IOUtil.fileName(filename);
			newFilename = IOUtil.fileName(n)+"-"+nth+IOUtil.extensionNameDot(n)+IOUtil.extensionNameDot(filename);
			rotateFile = new File(logPath, newFilename);
			nth++;
		}

		String s = IOUtil.extensionName(filename);
		switch (s) {
			default -> Files.move(currentFile.toPath(), rotateFile.toPath());
			case "gz" -> {
				try (var os = new GZIPOutputStream(new FileOutputStream(rotateFile));
					var is = new FileInputStream(currentFile)) {

					IOUtil.copyStream(is, os);
				}

				Files.delete(currentFile.toPath());
			}
			case "lzma" -> {
				try (var os = new LZMAOutputStream(new FileOutputStream(rotateFile), new LZMA2Options(), currentFile.length());
					 var is = new FileInputStream(currentFile)) {

					IOUtil.copyStream(is, os);
				}

				Files.delete(currentFile.toPath());
			}
		}
	}

	private String getRotateFileName(File currentFile) {
		CharList sb = IOUtil.getSharedCharBuf();
		return rotationFilePath.format(Collections.singletonMap("file", currentFile.getName()), sb).toString();
	}

	public void close() {
		running = false;
		writer.interrupt();
		try {
			writer.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
