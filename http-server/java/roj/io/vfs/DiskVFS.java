package roj.io.vfs;

import org.jetbrains.annotations.NotNull;
import roj.util.function.Flow;
import roj.http.server.DiskFileInfo;
import roj.io.IOUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2025/4/4 19:58
 */
public class DiskVFS implements VirtualFileSystem {
	private final File base;

	public DiskVFS(File base) {this.base = base;}

	@Override public boolean isOpen() {return true;}
	@Override public void close() throws IOException {}

	@Override
	public @NotNull VirtualFile getPath(String pathname) {return new DiskFile(new File(base, IOUtil.safePath(pathname)));}

	@Override
	public @NotNull VirtualFile getPath(VirtualFile parent, String child) {return new DiskFile(new File(((DiskFile) parent).getFile(), child));}

	@Override
	public @NotNull DiskFileInfo toFileInfo(VirtualFile virtualFile) {return (DiskFile) virtualFile;}

	@Override
	public @NotNull InputStream getInputStream(VirtualFile path) throws IOException {return new FileInputStream(((DiskFile) path).getFile());}

	@Override
	public Iterable<? extends VirtualFile> listPath(VirtualFile path, Predicate<String> filter) {
		if (!path.isDirectory()) return null;
		return Flow.of(((DiskFile) path).getFile().listFiles((dir, name) -> filter.test(name))).map(DiskFile::new).toList();
	}

	static final class DiskFile extends DiskFileInfo implements VirtualFile {
		public DiskFile(File file) {super(file);}

		public File getFile() {return file;}

		@Override public boolean exists() {return file.exists();}
		@Override public boolean isDirectory() {return file.isDirectory();}
		@Override public String getName() {return file.getName();}
		@Override public String getPath() {
			String path = file.getAbsolutePath();
			return path.substring(0, path.length()-file.getName().length()-1);
		}
		@Override public long length() {return length(false);}
	}
}
