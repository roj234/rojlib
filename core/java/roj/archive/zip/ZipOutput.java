package roj.archive.zip;

import org.jetbrains.annotations.Nullable;
import roj.archive.ArchiveUtils;
import roj.crypt.CRC32;
import roj.io.IOUtil;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.function.ExceptionalSupplier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;

/**
 * @author Roj233
 * @since 2022/2/22 13:14
 */
public final class ZipOutput implements AutoCloseable {
	public final File file;
	private ZipArchive archive;
	private ZipFileWriter writer;
	private boolean incremental, compress = true, isOpen, checkCRC;

	public ZipOutput(File file) {this.file = file;}

	public boolean isCompress() { return compress; }
	public void setCompress(boolean compress) { this.compress = compress; }

	public void setCheckCRC(boolean checkCRC) {this.checkCRC = checkCRC;}

	public boolean isIncremental() {return incremental;}

	public ZipFileWriter getWriter() {return writer;}

	public void begin(boolean incremental) throws IOException {
		if (isOpen) end();

		this.incremental = incremental;
		if (incremental) {
			if (archive == null) {
				archive = new ZipArchive(file);
			} else {
				archive.reopen();
			}
		} else {
			writer = new ZipFileWriter(file);
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

	private boolean shouldCompress(String name) {return compress && !ArchiveUtils.INCOMPRESSIBLE_FILE_EXT.contains(IOUtil.extensionName(name));}

	public void set(String name, @Nullable ByteList data) throws IOException {
		if (data == null && (!incremental || archive.getEntry(name) == null)) return;

		if (incremental) {
			noMatch:
			if (data != null && checkCRC) {
				ZEntry entry = archive.getEntry(name);
				if (entry != null && entry.getCrc32() == CRC32.crc32(data)) {
					var arr = ArrayCache.getIOBuffer();
					int offset = data.rIndex;
					try (var in = archive.getInputStream(name)) {
						while (true) {
							int r = in.read(arr);
							if (r < 0) {
								if (offset == data.wIndex()) return;
								break noMatch;
							}
							for (int i = 0; i < r; i++) {
								if (data.getByte(offset++) != arr[i])
									break noMatch;
							}
						}
					}
				}
			}

			EntryMod mod = archive.put(name, data);
			mod.flag = shouldCompress(name) ? EntryMod.COMPRESS : 0;
		} else {
			writer.writeNamed(name, data, shouldCompress(name) ? ZipEntry.DEFLATED : ZipEntry.STORED);
		}
	}

	public void set(String name, ExceptionalSupplier<ByteList, IOException> data) throws IOException {set(name, data, System.currentTimeMillis());}
	public void set(String name, ExceptionalSupplier<ByteList, IOException> data, long modTime) throws IOException {
		if (incremental) {
			EntryMod mod = archive.put(name, data, shouldCompress(name));
			mod.modificationTime = modTime;
		} else {
			writer.writeNamed(name, data.get(), shouldCompress(name) ? ZipEntry.DEFLATED : ZipEntry.STORED, modTime);
		}
	}

	public void setStream(String name, ExceptionalSupplier<InputStream, IOException> data, long modTime) throws IOException {
		if (incremental) {
			archive.putStream(name, data, shouldCompress(name));
		} else {
			ZEntry entry = new ZEntry(name);
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
				archive.save();
			}
		} finally {
			IOUtil.closeSilently(archive);
			IOUtil.closeSilently(writer);
			writer = null;
		}
	}

	public ZipArchive getArchive() throws IOException {
		if (archive == null) return archive = new ZipArchive(file);

		archive.reopen();
		return archive;
	}

	public void close() throws IOException {
		end();
		archive = null;
	}
}