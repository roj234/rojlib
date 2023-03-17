package roj.mod;

import roj.collect.MyHashSet;

import java.io.IOException;

/**
 * Abstract Project Watcher
 *
 * @author solo6975
 * @since 2021/7/28 20:34
 */
class IFileWatcher {
	public void removeAll() {}

	public static final int ID_RES = 0, ID_SRC = 1;

	public MyHashSet<String> getModified(Project proj, int id) {
		MyHashSet<String> set = new MyHashSet<>(2);
		set.add(null);
		return set;
	}

	public void register(Project proj) throws IOException {}

	public void terminate() {}
}
