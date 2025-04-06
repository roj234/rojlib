package roj.io.vfs;

import roj.io.source.Source;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Roj234
 * @since 2025/4/5 2:03
 */
public interface WritableFileSystem extends VirtualFileSystem {
	default boolean canWrite() {return true;}

	boolean create(VirtualFile path, boolean directory) throws IOException;
	boolean delete(VirtualFile path) throws IOException;

	OutputStream getOutputStream(VirtualFile path, boolean append) throws IOException;
	Source getRandomAccess(VirtualFile path, boolean readOnly) throws IOException;
}
