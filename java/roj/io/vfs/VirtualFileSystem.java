package roj.io.vfs;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.http.server.FileInfo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2025/4/4 0004 19:55
 */
public interface VirtualFileSystem {
	static VirtualFileSystem disk(File base) {return new DiskVFS(base);}

	boolean isOpen();
	void close() throws IOException;

	@NotNull VirtualFile getPath(String pathname);
	@NotNull VirtualFile getPath(VirtualFile parent, String child);
	@NotNull FileInfo toFileInfo(VirtualFile virtualFile);
	@NotNull InputStream getInputStream(VirtualFile path) throws IOException;

	@ApiStatus.Experimental
	@Nullable
	Iterable<? extends VirtualFile> listPath(VirtualFile path, Predicate<String> nameFilter);

}
