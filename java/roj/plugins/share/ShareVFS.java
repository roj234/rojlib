package roj.plugins.share;

import org.jetbrains.annotations.NotNull;
import roj.collect.SimpleList;
import roj.collect.TrieTree;
import roj.http.server.DiskFileInfo;
import roj.io.vfs.VirtualFile;
import roj.io.vfs.VirtualFileSystem;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2025/4/4 0004 21:26
 */
final class ShareVFS implements VirtualFileSystem {
	private final FileShare owner;
	private final Share info;

	private final TrieTree<ShareFile> files;

	public ShareVFS(FileShare owner, Share info) {
		this.owner = owner;
		this.info = info;
		this.files = new TrieTree<>();

		for (ShareFile file : info.files) {
			if (file.getPath().isEmpty()) files.put(file.getName(), file);
			else {
				String path = file.getPath();
				files.put(path+"/"+file.getName(), file);

				int i = path.lastIndexOf('/');
				while (true) {
					var prev = files.get(path);
					if (prev != null) break;

					var directory = createDirectory(path);
					files.put(path, directory);

					if (i < 0) break;
					path = path.substring(0, i);
					i = path.lastIndexOf('/');
				}
			}
		}
	}

	@Override public boolean isOpen() {return true;}
	@Override public void close() {}

	@Override
	public @NotNull VirtualFile getPath(String pathname) {
		ShareFile file = files.get(pathname);
		if (file == null) file = createDirectory(pathname.isEmpty() ? "/" : pathname);
		return file;
	}

	@NotNull
	private static ShareFile createDirectory(String pathname) {
		ShareFile file;
		file = new ShareFile();
		int i = pathname.lastIndexOf('/');
		if (i >= 0) {
			file.path = pathname.substring(0, i);
			file.name = pathname.substring(i + 1);
		} else {
			file.name = pathname;
		}
		return file;
	}

	@Override
	public @NotNull VirtualFile getPath(VirtualFile parent, String child) {return getPath(parent.toString()+"/"+child);}
	@Override
	public @NotNull DiskFileInfo toDiskInfo(VirtualFile virtualFile) {return new DiskFileInfo(getFile((ShareFile) virtualFile));}
	@Override
	public @NotNull InputStream getInputStream(VirtualFile path) throws IOException {return new FileInputStream(getFile((ShareFile) path));}

	@NotNull
	private File getFile(ShareFile file) {
		var realFile = file.file;
		if (realFile == null) {
			realFile = file.id != 0
					? new File(owner.uploadPath, Integer.toString(file.id, 36))
					: new File(info.base, file.path + '/' + file.name);
			file.file = realFile;
		}
		return realFile;
	}

	@Override
	public Iterable<? extends VirtualFile> listPath(VirtualFile path, Predicate<String> filter) {
		if (!path.isDirectory()) return null;

		var subPath = path.toString();
		if (subPath.equals("/")) subPath = "";

		List<ShareFile> list = new SimpleList<>();
		files.forEachSince(subPath, (name, file) -> {
			list.add(file);
			return FileVisitResult.SKIP_SUBTREE;
		});
		return list;
	}
}
