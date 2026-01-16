package roj.webui;

import roj.io.IOUtil;
import roj.util.JVM;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * @author Roj234
 * @since 2026/01/22 01:36
 */
final class CacheManager {
	private final Path rootPath, lockPath;

	private FileChannel lockChannel;
	private FileLock fileLock;
	private Thread cleanupThread;

	public CacheManager(String path) {
		this.rootPath = Paths.get(path).toAbsolutePath();
		this.lockPath = rootPath.resolve(".locks");
	}

	/**
	 * 初始化缓存目录并注册当前实例
	 */
	public void acquire() throws IOException {
		if (!Files.exists(lockPath)) {
			Files.createDirectories(lockPath);
		}

		removeDeadLocks();

		Path lockFile = lockPath.resolve(String.valueOf(ProcessHandle.current().pid()));

		lockChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);
		fileLock = lockChannel.lock();

		Runtime.getRuntime().addShutdownHook(cleanupThread = new Thread(this::release));
	}

	/**
	 * 退出时的清理逻辑
	 */
	public void release() {
		if (cleanupThread != null && !JVM.isShutdownInProgress()) {
			Runtime.getRuntime().removeShutdownHook(cleanupThread);
			cleanupThread = null;
		}

		IOUtil.closeSilently(lockChannel);

		if (removeDeadLocks()) {
			IOUtil.deleteRecursively(rootPath);
		}
	}

	/**
	 * 清理残留的、未被其它进程锁定的锁文件
	 */
	private boolean removeDeadLocks() {
		if (!Files.exists(lockPath)) return true;

		int count = 0;
		try (var stream = Files.newDirectoryStream(lockPath)) {
			for (var file : stream) {
				try {
					Files.deleteIfExists(file);
				} catch (IOException e) {
					count ++;
					// 另一个存活的进程持有锁
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return count == 0;
	}

	public Path getRootPath() {return rootPath;}
}