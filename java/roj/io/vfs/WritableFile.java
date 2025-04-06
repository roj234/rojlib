package roj.io.vfs;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/4/5 2:00
 */
public interface WritableFile extends VirtualFile {
	WritableFileSystem filesystem();

	public WritableFile child(String path);
	WritableFile parent();

	boolean canWrite();

	boolean createFile() throws IOException;
	boolean delete() throws IOException;

	boolean mkdir() throws IOException;
	boolean mkdirs() throws IOException;

	boolean renameTo(VirtualFile path) throws IOException;
	boolean setLastModified(long time) throws IOException;
}
