package roj.mod;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * @author solo6975
 * @since 2021/7/28 20:34
 */
class IFileWatcher {
	public static final int ID_RES = 0, ID_SRC = 1;

	public Set<String> getModified(Project proj, int id) { return Collections.singleton(null); }
	public void register(Project proj) throws IOException {}

	public void removeAll() {}
	public void terminate() {}
}