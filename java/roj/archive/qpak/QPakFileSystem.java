package roj.archive.qpak;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import roj.archive.MultiFileSource;
import roj.archive.qz.*;
import roj.archive.qz.xz.LZMA2Options;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.io.IOUtil;

import java.io.*;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2023/11/4 0004 20:15
 */
public class QPakFileSystem implements MultiFileSource {
	private List<QZArchive> archives;
	private final IntMap<QZFileWriter> metadataOverride = new IntMap<>();
	private final MyHashMap<String, QPakPath> files = new MyHashMap<>();

	private final File alsoReadFrom;
	private final QZFileWriter patch;
	private final boolean writeToPatch;

	public QPakFileSystem(List<QZArchive> archives, File alsoReadFrom, QZFileWriter patch, boolean writeToPatch) {
		this.archives = archives;
		this.alsoReadFrom = alsoReadFrom;
		this.patch = patch;
		this.writeToPatch = writeToPatch;
	}

	public boolean isOpen() {return archives != Collections.EMPTY_LIST;}
	public void close() throws IOException {
		for (var writer : metadataOverride.values()) writer.close();
		patch.close();

		for (var archive : archives) archive.close();
		archives = Collections.emptyList();
		for (var value : files.values()) value.ref = null;
		files.clear();
	}

	public boolean canWrite() {return writeToPatch || alsoReadFrom != null;}

	public QPakPath getPath(String pathname) {
		var path = files.get(pathname);
		if (path != null) return path;

		// 2024/07/24 将文件存在检查移动到这里
		if (alsoReadFrom != null) {
			String safePath = IOUtil.safePath(pathname);
			if (safePath.isEmpty()) safePath = "/";
			File file = safePath.equals("/") ? alsoReadFrom : new File(alsoReadFrom, safePath);
			if (file.exists()) {
				files.put(safePath, path = new QPakPath(this, safePath, file));
				return path;
			}
		}

		path = new QPakPath(this, pathname);
		for (int i = 0; i < archives.size(); i++) {
			var entry = archives.get(i).getEntry(pathname);
			if (entry == null) continue;
			if (entry.isAntiItem()) {
				path.ref = null;
				path.archiveIndex = 0;
			} else {
				path.ref = entry;
				path.archiveIndex = (short) i;
			}
		}
		if (path.ref != null) files.put(pathname, path);
		return path;
	}

	void markMetadataDirty(QZEntry entry, short archiveIndex) {
		var qzfw = metadataOverride.get(archiveIndex);
		if (qzfw == null) {
			try {
				metadataOverride.putInt(archiveIndex, qzfw = archives.get(archiveIndex).append());
			} catch (IOException e) {
				return;
			}
			qzfw.setCodec(new LZMA2(new LZMA2Options(6).setDictSize(262144)));
			qzfw.setCompressHeader(1);
		}
	}

	public boolean createPath(QPakPath dest, boolean directory) throws IOException {
		if (dest.ref != null) return false;

		if (writeToPatch) {
			QZEntry entry = QZEntry.of(dest.getName());
			entry.setModificationTime(System.currentTimeMillis());
			entry.setIsDirectory(directory);
			dest.ref = entry;
			dest.archiveIndex = -1;

			files.put(dest.getPath(), dest);
			return true;
		}

		if (alsoReadFrom == null) throw new IOException("this PakFileSystem is not writable");

		File file = new File(alsoReadFrom, dest.getPath());
		dest.ref = file;

		if (directory) Files.createDirectory(file.toPath());
		else Files.createFile(file.toPath());

		files.put(dest.getPath(), dest);
		return true;
	}

	public boolean delete(QPakPath path) throws IOException {
		if (path.ref == null || !path.isFile()) return false;

		if (path.ref.getClass() == File.class) {
			if (!((File) path.ref).delete()) return false;
		} else {
			QZEntry entry = (QZEntry) path.ref;

			QZEntry entry1 = QZEntry.of(entry.getName());
			entry1.setModificationTime(System.currentTimeMillis());
			entry1.setAntiItem(true);
			patch.beginEntry(entry1);
			patch.closeEntry();
		}

		files.remove(path.getPath());
		path.ref = null;
		return true;
	}

	public InputStream getInputStream(QPakPath path) throws IOException {
		if (path.ref == null || !path.isFile()) throw new FileNotFoundException();
		if (path.ref.getClass() == File.class) return new FileInputStream((File) path.ref);

		return archives.get(path.archiveIndex).getInputUncached((QZEntry) path.ref);
	}

	public OutputStream getOutputStream(QPakPath path, boolean append) throws IOException {
		if (path.archiveIndex != -1 && path.ref instanceof QZEntry) delete(path);
		if (path.ref == null) createPath(path, false);
		if (path.ref.getClass() == File.class) return new FileOutputStream((File) path.ref, append);

		QZWriter out = patch.parallel();
		out.beginEntry(((QZEntry) path.ref));
		return out;
	}

	@ApiStatus.Experimental
	public List<QPakPath> queryPath(QPakPath path, Predicate<String> filter) {
		if (!path.isDirectory()) return null;

		String pathname = path.getPath();
		if (!pathname.startsWith("/")) return Collections.emptyList();
		int len = pathname.length();

		SimpleList<QPakPath> paths = new SimpleList<>();
		// Not implemented yet!!
		for (Map.Entry<String, QPakPath> entry : files.entrySet()) {
			String name = entry.getKey();
			if (name.length() > len && name.startsWith(pathname) && name.indexOf('/', len) == -1 && filter.test(entry.getValue().getPath()))
				paths.add(entry.getValue());
		}

		File file = new File(alsoReadFrom, path.getPath());
		if (file.isDirectory()) {
			int len1 = alsoReadFrom.getAbsolutePath().length();
			file.listFiles(f -> {
				String pa = f.getAbsolutePath().substring(len1).replace(File.separatorChar, '/');
				if (!filter.test(pa)) return false;

				QPakPath path1 = new QPakPath(this, pa, f);
				QPakPath prev = files.put(pa, path1);

				if (prev != null) paths.set(paths.lastIndexOf(prev), path1);
				else paths.add(path1);
				return false;
			});
		}
		return paths;
	}

	@Override
	public @Nullable InputStream getStream(String pathname) throws IOException {
		QPakPath path = getPath(pathname);
		return path.exists() ? getInputStream(path) : null;
	}
}