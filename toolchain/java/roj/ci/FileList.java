package roj.ci;

import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.crypt.CryptoFactory;
import roj.io.IOUtil;
import roj.util.function.Flow;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * 文件列表，用于跟踪文件系统的变化
 * @author Roj234
 * @since 2025/10/14 21:31
 */
final class FileList {
	transient final Map<String, byte[]> changed = new HashMap<>();
	transient final Set<String> removed = new HashSet<>();
	private final Map<String, byte[]> files = new HashMap<>();
	private transient String prefix;
	private transient boolean wasChanged;

	FileList() {}

	public void setPrefix(String prefix) {this.prefix = prefix;}

	public boolean isEmpty() {
		return files.isEmpty();
	}

	public Set<String> getDeletedAlt(String prefix) {
		if (havePendingChanges()) {
			MCMake.log.error("assertion error: havePendingChanges()");
			return null;
		}

		return Flow.of(files.keySet())
				.filter(pathname -> pathname.startsWith(prefix) && !new File(this.prefix+pathname).exists())
				.map(pathname -> this.prefix.concat(pathname))
				.toSet();
	}

	/**
	 * 处理文件变化事件
	 *
	 * <p>当文件被创建、修改或删除时调用此方法。
	 * 如果文件存在，则添加到待添加集合；如果文件不存在且之前已提交，则添加到待删除集合。
	 *
	 * @param pathname 发生变化的文件路径
	 */
	public synchronized boolean fileChanged(String pathname) {
		File file = new File(pathname);
		pathname = pathname.replace(File.separatorChar, '/').substring(prefix.length());

		boolean exist = file.isFile();
		if (exist) {
			MessageDigest digest = CryptoFactory.getSharedDigest("SHA-1");
			try {
				IOUtil.digestFile(file, file.length(), digest);
			} catch (IOException e) {
				MCMake.log.error("Could not digest {}", e, file);
				return false;
			}

			byte[] hash = digest.digest();
			if (Arrays.equals(hash, files.get(pathname))) return false;

			byte[] prevHash = changed.put(pathname, hash);
			if (Arrays.equals(hash, prevHash)) return false;

			removed.remove(pathname);
		} else {
			changed.remove(pathname);
			if (files.containsKey(pathname))
				removed.add(pathname);
		}

		return true;
	}

	/**
	 * 提交所有挂起的变化到稳定文件集合
	 *
	 * <p>将待添加的文件合并到已提交集合，从已提交集合中移除待删除的文件，
	 * 然后清空所有挂起的变更集合。
	 */
	public synchronized void commit() {
		if (!havePendingChanges()) return;

		wasChanged = true;
		files.keySet().removeAll(removed);
		files.putAll(changed);
		removed.clear();
		changed.clear();
	}

	public boolean havePendingChanges() {
		return !removed.isEmpty() | !changed.isEmpty();
	}

	public boolean wasChanged() {return wasChanged;}
}
