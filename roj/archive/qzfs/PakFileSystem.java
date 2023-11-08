package roj.archive.qzfs;

import roj.archive.qz.QZArchive;
import roj.archive.qz.QZEntry;
import roj.archive.qz.QZFileWriter;
import roj.archive.qz.QZWriter;
import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.DynByteBuf;

import java.io.*;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2023/11/4 0004 20:15
 */
public class PakFileSystem {
	private List<QZArchive> archives;
	private List<QZFileWriter> metadataOverride = Collections.emptyList();
	private final Map<String, PakPath> files = new MyHashMap<>();

	private final File alsoReadFrom;
	private final QZFileWriter patch;
	private final boolean writeToPatch;

	private final LinkedMyHashMap<String, DynByteBuf> LRU = new LinkedMyHashMap<>();
	private long cacheRemain;

	public PakFileSystem(List<QZArchive> archives, File alsoReadFrom, QZFileWriter patch, boolean writeToPatch) {
		this.archives = archives;
		this.alsoReadFrom = alsoReadFrom;
		this.patch = patch;
		this.writeToPatch = writeToPatch;

		for (QZArchive archive : archives) {
			for (QZEntry entry : archive.getEntriesByPresentOrder()) {
				if (entry.isAntiItem()) files.remove(entry.getName());
				else {
					PakPath path;
					File file = new File(alsoReadFrom, entry.getName());
					if (file.exists()) path = new PakPath(this, entry.getName(), file);
					else path = new PakPath(this, entry.getName(), entry);

					files.put(entry.getName(), path);
				}
			}
		}
	}

	private static final Pattern PATTERN = Pattern.compile("patch-(\\d+)\\.7z", Pattern.CASE_INSENSITIVE);
	public static PakFileSystem autoPatch(File archiveFolder, File extraFolder) throws IOException {
		List<QZArchive> archives = new SimpleList<>();
		int maxEXTRA = 0;
		for (File file : archiveFolder.listFiles()) {
			if (!file.getName().endsWith(".7z") && !file.getName().endsWith(".7z.001")) continue;
			QZArchive arc = new QZArchive(file);
			Matcher m = PATTERN.matcher(file.getName());
			if (!m.matches()) {
				archives.add(arc);
			} else if (arc.getEntriesByPresentOrder() == null) {
				arc.close();
				file.delete();
			} else {
				maxEXTRA = Math.max(maxEXTRA, Integer.parseInt(m.group(1)));
			}
		}
		QZFileWriter nextPatch = new QZFileWriter(new File(archiveFolder,
			new CharList().append("patch-").padNumber(maxEXTRA+1, 5).append(".7z").toStringAndFree()));

		return new PakFileSystem(archives, extraFolder, nextPatch, true);
	}

	public boolean isOpen() { return archives != Collections.EMPTY_LIST; }
	public void close() throws IOException {
		for (QZFileWriter writer : metadataOverride) writer.close();
		patch.close();

		for (QZArchive archive : archives) archive.close();
		archives = Collections.emptyList();
		for (PakPath value : files.values()) value.ref = null;
		files.clear();
	}

	public boolean canWrite() { return writeToPatch || alsoReadFrom != null; }

	public PakPath getPath(String pathname) {
		PakPath exist = files.get(pathname);
		if (exist != null) return exist;

		if (alsoReadFrom != null) {
			String safePath = IOUtil.safePath(pathname);
			if (safePath.isEmpty()) safePath = "/";
			File file = safePath.equals("/") ? alsoReadFrom : new File(alsoReadFrom, safePath);
			if (file.exists()) {
				files.put(safePath, exist = new PakPath(this, safePath, file));
				return exist;
			}
		}

		return new PakPath(this, pathname);
	}

	void markMetadataDirty(QZEntry entry, short archiveIndex) {
		// not implemented yet
		throw new UnsupportedOperationException();
	}

	public boolean createPath(PakPath dest, boolean directory) throws IOException {
		if (dest.ref != null) return false;

		if (writeToPatch) {
			QZEntry entry = new QZEntry(dest.getName());
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

	public boolean delete(PakPath path) throws IOException {
		if (path.ref == null || !path.isFile()) return false;

		if (path.ref.getClass() == File.class) {
			if (!((File) path.ref).delete()) return false;
		} else {
			QZEntry entry = (QZEntry) path.ref;

			DynByteBuf buf = LRU.remove(entry.getName());
			if (buf != null) {
				cacheRemain += buf.readableBytes();
				try {
					buf.close();
				} catch (IOException ignored) {}
			}

			QZEntry entry1 = new QZEntry(entry.getName());
			entry1.setModificationTime(System.currentTimeMillis());
			entry1.setAntiItem(true);
			patch.beginEntry(entry1);
			patch.closeEntry();
		}

		files.remove(path.getPath());
		path.ref = null;
		return true;
	}

	public InputStream getInputStream(PakPath path) throws IOException {
		if (path.ref == null || !path.isFile()) throw new FileNotFoundException();
		if (path.ref.getClass() == File.class) return new FileInputStream((File) path.ref);

		DynByteBuf buf = LRU.get(path.ref);
		if (buf != null) return buf.slice(0, buf.readableBytes()).asInputStream();

		// TODO parallel
		return archives.get(path.archiveIndex).getInput((QZEntry) path.ref);
	}

	public OutputStream getOutputStream(PakPath path, boolean append) throws IOException {
		if (path.archiveIndex != -1 && path.ref instanceof QZEntry) delete(path);
		if (path.ref == null) createPath(path, false);
		if (path.ref.getClass() == File.class) return new FileOutputStream((File) path.ref, append);

		QZWriter out = patch.parallel();
		out.beginEntry(((QZEntry) path.ref));
		return out;
	}

	public List<PakPath> queryPath(PakPath path, Predicate<String> filter) {
		if (!path.isDirectory()) return null;

		String pathname = path.getPath();
		if (!pathname.startsWith("/")) return Collections.emptyList();
		int len = pathname.length();

		SimpleList<PakPath> paths = new SimpleList<>();
		for (Map.Entry<String, PakPath> entry : files.entrySet()) {
			String name = entry.getKey();
			if (name.length() > len && name.startsWith(pathname) && name.indexOf('/', len) == -1)
				paths.add(entry.getValue());
		}

		File file = new File(alsoReadFrom, path.getPath());
		if (file.isDirectory()) {
			int len1 = alsoReadFrom.getAbsolutePath().length();
			file.listFiles(f -> {
				String pa = f.getAbsolutePath().substring(len1).replace('\\', '/');
				PakPath path1 = new PakPath(this, pa, f);
				PakPath prev = files.put(pa, path1);

				if (prev != null) paths.set(paths.lastIndexOf(prev), path1);
				else paths.add(path1);
				return false;
			});
		}
		return paths;
	}
}
