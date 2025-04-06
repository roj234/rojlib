package roj.archive.qpak;

import roj.archive.qz.QZEntry;
import roj.io.IOUtil;
import roj.io.vfs.VirtualFile;
import roj.io.vfs.WritableFile;
import roj.io.vfs.WritableFileSystem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

final class QFile implements Comparable<QFile>, WritableFile {
	private final QPakFileSystem fs;
	private final String path;
	Object ref;
	short archiveIndex;

	QFile(QPakFileSystem fs, String path) {
		this.fs = fs;
		this.path = path;
	}
	QFile(QPakFileSystem fs, String pathname, Object ref) {
		this.fs = fs;
		this.path = pathname;
		this.ref = ref;
	}

	public WritableFileSystem filesystem() {return fs;}

	public QFile child(String child) { return fs.getPath_(path+"/"+child); }
	public QFile parent() {
		String p = getParent();
		if (p == null) return null;
		return fs.getPath_(path);
	}

	public String getParent() {
		int index = path.lastIndexOf('/');
		return index < 0 ? null : path.substring(0, index);
	}

	public String getName() { return path.substring(path.lastIndexOf('/')+1); }
	public String getPath() { return path; }

	/* -- Attribute accessors -- */

	public boolean canRead() { return exists(); }
	public boolean canWrite() { return fs.canWrite(); }
	public boolean exists() { return ref != null; }

	public boolean isDirectory() { return ref != null && (ref.getClass() == File.class ? ((File) ref).isDirectory() : ((QZEntry) ref).isDirectory()); }
	public boolean isFile() { return ref != null && (ref.getClass() == File.class ? ((File) ref).isFile() : !((QZEntry) ref).isDirectory()); }
	public long lastModified() { return ref == null ? 0 : ref.getClass() == File.class ? ((File) ref).lastModified() : ((QZEntry) ref).getModificationTime(); }

	public long length() { return ref == null ? 0 : ref.getClass() == File.class ? ((File) ref).length() : ((QZEntry) ref).getSize(); }

	/* -- File operations -- */

	public boolean createFile() throws IOException {return fs.create(this, false);}
	public boolean delete() throws IOException {return fs.delete(this);}

	public boolean mkdir() throws IOException {
		if (ref != null) return false;
		if (!fs.canWrite()) return false;
		var parent = parent();
		if (parent != null && !parent.isDirectory()) return false;
		return fs.create(this, true);
	}

	public boolean mkdirs() throws IOException {
		if (ref != null) return false;
		if (!fs.canWrite()) return false;
		return fs.create(this, true);
	}

	public boolean renameTo(VirtualFile path) throws IOException {return renameTo(((QFile) path));}
	public boolean renameTo(QFile dest) throws IOException {
		if (ref == null) return false;

		boolean fileTarget = ref.getClass() == File.class;
		if (fileTarget) {
			if (dest.ref == null) {
				fs.create(dest, isDirectory());
			}

			File file = (File) ref;
			if (dest.ref.getClass() == File.class) {
				return file.renameTo((File) dest.ref);
			} else {
				try (var in = new FileInputStream(file)) {
					try (var out = fs.getOutputStream(this, false)) {
						IOUtil.copyStream(in, out);
					}
					fs.delete(this);
				}
			}
			return true;
		}

		QZEntry entry = (QZEntry) ref;
		if (dest.ref instanceof File) {
			File file = (File) dest.ref;
			if (!entry.isDirectory()) {
				try (var out = new FileOutputStream(file)) {
					try (var in = fs.getInputStream(this)) {
						IOUtil.copyStream(in, out);
					}
					fs.delete(this);
				}
			} else {
				return file.mkdir();
			}
		} else {
			if (dest.ref != null) dest.delete();
			else dest.ref = ref;

			entry.setName(dest.path);
			fs.markMetadataDirty(entry, archiveIndex);
			fs.delete(this);
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

	public int compareTo(QFile path) { return this.path.compareTo(path.path); }

	public boolean equals(Object obj) { return obj instanceof QFile && compareTo((QFile) obj) == 0; }
	public int hashCode() { return path.hashCode(); }
	public String toString() { return path; }
}