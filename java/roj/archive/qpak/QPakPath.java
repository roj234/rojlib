package roj.archive.qpak;

import roj.archive.qz.QZEntry;
import roj.io.IOUtil;
import roj.util.Helpers;

import java.io.*;
import java.util.List;
import java.util.function.Predicate;

public class QPakPath implements Comparable<QPakPath> {
	private final QPakFileSystem fs;
	private final String path;
	Object ref;
	short archiveIndex;

	QPakPath(QPakFileSystem fs, String path) {
		this.fs = fs;
		this.path = path;
	}
	QPakPath(QPakFileSystem fs, String pathname, Object ref) {
		this.fs = fs;
		this.path = pathname;
		this.ref = ref;
	}

	public QPakPath child(String path) { return fs.getPath(this.path+"/"+path); }

	public String getName() { return path.substring(path.lastIndexOf('/')+1); }

	public String getParent() {
		int index = path.lastIndexOf('/');
		return index < 0 ? null : path.substring(0, index);
	}

	public QPakPath getParentFile() {
		String p = getParent();
		if (p == null) return null;
		return new QPakPath(fs, path);
	}

	public String getPath() { return path; }


	/* -- Path operations -- */

	public boolean isAbsolute() { return true; }
	public String getAbsolutePath() { return path; }
	public QPakPath getAbsoluteFile() { return this; }

	/* -- Attribute accessors -- */

	public boolean canRead() { return exists(); }
	public boolean canWrite() { return fs.canWrite(); }
	public boolean exists() { return ref != null; }

	public boolean isDirectory() { return ref != null && (ref.getClass() == File.class ? ((File) ref).isDirectory() : ((QZEntry) ref).isDirectory()); }
	public boolean isFile() { return ref != null && (ref.getClass() == File.class ? ((File) ref).isFile() : !((QZEntry) ref).isDirectory()); }
	public long lastModified() { return ref == null ? 0 : ref.getClass() == File.class ? ((File) ref).lastModified() : ((QZEntry) ref).getModificationTime(); }

	public long length() { return ref == null ? 0 : ref.getClass() == File.class ? ((File) ref).length() : ((QZEntry) ref).getSize(); }

	/* -- File operations -- */

	public boolean createNewFile() throws IOException {
		return fs.createPath(this, false);
	}

	public boolean delete() {
		try {
			return fs.delete(this);
		} catch (IOException e) {
			return false;
		}
	}

	public String[] list() { return list(Helpers.alwaysTrue()); }
	public String[] list(Predicate<String> filter) {
		List<QPakPath> files = fs.queryPath(this, filter);
		if (files == null) return null;

		String[] names = new String[files.size()];
		for (int i = 0; i < files.size(); i++)
			names[i] = files.get(i).path;
		return names;
	}

	public QPakPath[] listFiles() { return listFiles(Helpers.alwaysTrue()); }
	public QPakPath[] listFiles(Predicate<String> filter) {
		List<QPakPath> files = fs.queryPath(this, filter);
		return files == null ? null : files.toArray(new QPakPath[files.size()]);
	}

	public boolean mkdir() {
		if (ref != null) return false;
		if (!fs.canWrite()) return false;
		if (!getParentFile().isDirectory()) return false;

		try {
			fs.createPath(this, true);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean mkdirs() {
		if (ref != null) return false;
		if (!fs.canWrite()) return false;

		try {
			fs.createPath(this, true);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean renameTo(QPakPath dest) {
		if (ref == null) return false;

		boolean fileTarget = ref.getClass() == File.class;
		if (fileTarget) {
			if (dest.ref == null) {
				try {
					fs.createPath(dest, isDirectory());
				} catch (IOException e) {
					e.printStackTrace();
					return false;
				}
			}

			File file = (File) ref;
			if (dest.ref.getClass() == File.class) {
				return file.renameTo((File) dest.ref);
			} else {
				try (FileInputStream in = new FileInputStream(file)) {
					try (OutputStream out = fs.getOutputStream(this, false)) {
						IOUtil.copyStream(in, out);
					}
					fs.delete(this);
				} catch (IOException e) {
					e.printStackTrace();
					return false;
				}
			}
			return true;
		}

		QZEntry entry = (QZEntry) ref;
		if (dest.ref instanceof File) {
			File file = (File) dest.ref;
			if (!entry.isDirectory()) {
				try (FileOutputStream out = new FileOutputStream(file)) {
					try (InputStream in = fs.getInputStream(this)) {
						IOUtil.copyStream(in, out);
					}
					fs.delete(this);
				} catch (IOException e) {
					e.printStackTrace();
					return false;
				}
			} else {
				return file.mkdir();
			}
		} else {
			if (dest.ref != null) dest.delete();
			else dest.ref = ref;

			entry.setName(dest.path);
			fs.markMetadataDirty(entry, archiveIndex);
			try {
				fs.delete(this);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}

	public boolean setLastModified(long time) {
		if (time < 0) throw new IllegalArgumentException("Negative time");
		if (ref == null) return false;

		if (ref.getClass() == File.class) return ((File) ref).setLastModified(time);

		QZEntry entry = (QZEntry) ref;
		entry.setModificationTime(time);
		fs.markMetadataDirty(entry, archiveIndex);
		return true;
	}

	/* -- Basic infrastructure -- */

	public int compareTo(QPakPath path) { return this.path.compareTo(path.path); }

	public boolean equals(Object obj) { return obj instanceof QPakPath && compareTo((QPakPath) obj) == 0; }
	public int hashCode() { return path.hashCode(); }
	public String toString() { return path; }
}