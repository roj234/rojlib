package roj.ci;

import org.jetbrains.annotations.Nullable;
import roj.archive.ArchiveUtils;
import roj.archive.zip.ZipEditor;
import roj.archive.zip.ZipEntry;
import roj.archive.zip.ZipPacker;
import roj.archive.zip.ZipUpdate;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.function.ExceptionalSupplier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Deflater;

/**
 * @author Roj233
 * @since 2022/2/22 13:14
 */
public final class ZipOutput implements AutoCloseable {
	public final File file;
	private ZipEditor archive;
	private ZipPacker writer;
	private boolean incremental, isOpen;
	private int compressionLevel = Deflater.DEFAULT_COMPRESSION;

	public ZipOutput(File file) {this.file = file;}

	public void setCompressionLevel(int compressionLevel) {
		this.compressionLevel = compressionLevel;
	}

	public ZipPacker getWriter() {return writer;}

	public void begin(boolean incremental) throws IOException {
		if (isOpen) end();

		this.incremental = incremental;
		if (incremental) {
			getArchive();
		} else {
			writer = new ZipPacker(file);
			writer.setCompressionLevel(compressionLevel);
			if (archive != null) {
				archive.close();
				archive = null;
			}
		}
		isOpen = true;
	}

	public void setComment(String comment) {
		if (!incremental) writer.setComment(comment);
		else archive.setComment(comment);
	}

	private boolean shouldCompress(String name) {return compressionLevel != 0 && !ArchiveUtils.INCOMPRESSIBLE_FILE_EXT.contains(IOUtil.getExtension(name));}

	public void set(String name, @Nullable ByteList data) throws IOException {
		if (data == null && (!incremental || archive.getEntry(name) == null)) return;

		if (incremental) {
			ZipUpdate mod = archive.put(name, data);
			mod.setMethod(shouldCompress(name) ? ZipEntry.DEFLATED : ZipEntry.STORED);
		} else {
			writer.writeNamed(name, data, shouldCompress(name) ? ZipEntry.DEFLATED : ZipEntry.STORED);
		}
	}

	public void set(String name, ExceptionalSupplier<DynByteBuf, IOException> data) throws IOException {set(name, data, System.currentTimeMillis());}
	public void set(String name, ExceptionalSupplier<DynByteBuf, IOException> data, long modTime) throws IOException {
		if (incremental) {
			ZipUpdate mod = archive.put(name, data, shouldCompress(name));
			mod.modificationTime = modTime;
		} else {
			writer.writeNamed(name, data.get(), shouldCompress(name) ? ZipEntry.DEFLATED : ZipEntry.STORED, modTime);
		}
	}

	public void setStream(String name, ExceptionalSupplier<InputStream, IOException> data, long modTime) throws IOException {
		if (incremental) {
			archive.putStream(name, data, shouldCompress(name));
		} else {
			ZipEntry entry = new ZipEntry(name);
			entry.setMethod(shouldCompress(name) ? ZipEntry.DEFLATED : ZipEntry.STORED);
			entry.setModificationTime(modTime);
			writer.beginEntry(entry);

			byte[] b = IOUtil.getSharedByteBuf().list;
			int r;
			try (var in = data.get()) {
				do {
					r = in.read(b);
					if (r < 0) break;
					writer.write(b, 0, r);
				} while (r == b.length);
			} finally {
				writer.closeEntry();
			}
		}
	}

	public void end() throws IOException {
		isOpen = false;
		try {
			if (incremental && archive != null) {
				archive.ensureOpen();
				archive.save(compressionLevel);
			}
		} finally {
			IOUtil.closeSilently(archive);
			IOUtil.closeSilently(writer);
			writer = null;
		}
	}

	public ZipEditor getArchive() throws IOException {
		if (archive == null) return archive = new ZipEditor(file);
		archive.ensureOpen();
		return archive;
	}
	@Nullable
	public ZipEditor getArchiveIfPresent() {return archive;}

	public void close() throws IOException {
		end();
		archive = null;
	}
}