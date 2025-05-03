package roj.plugins.web.share;

import roj.config.auto.Name;
import roj.config.auto.Optional;
import roj.http.server.MimeType;
import roj.io.IOUtil;
import roj.io.vfs.VirtualFile;

import java.io.File;

/**
 * @author Roj234
 * @since 2025/4/4 21:27
 */
final class ShareFile implements VirtualFile {
	String name;
	@Name("type") String mime;
	@Optional String path;
	long size, lastModified;
	//@Optional transient int download;

	// Either<Integer, File> => uploadPath / file
	@Optional transient int id;
	@Optional transient File file;

	public ShareFile() {}
	public ShareFile(File file, int prefixLength) {
		this.name = file.getName();
		this.mime = MimeType.getMimeType(IOUtil.extensionName(name));
		this.size = file.length();
		this.lastModified = file.lastModified();
		String path = file.getAbsolutePath();
		if (path.length() > prefixLength + name.length())
			this.path = path.substring(prefixLength, path.length() - name.length() - 1).replace(File.separatorChar, '/');
		this.file = file;
	}

	// VirtualFile
	@Override public boolean isDirectory() {return id == 0 && file == null;}
	@Override public String getName() {return name;}
	@Override public String getPath() {return path == null ? "" : path;}
	@Override public long length() {return size;}
	@Override public long lastModified() {return lastModified;}
	@Override public String toString() {return path == null ? name : path + "/" + name;}
}
