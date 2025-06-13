package roj.archive.zip;

import roj.concurrent.ExceptionalSupplier;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.Helpers;

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
	private ZipArchive some;
	private ZipFileWriter all;
	private boolean useZFW, compress, work;

	public ZipOutput(File file) {
		this.file = file;
	}

	public boolean isCompress() { return compress; }
	public void setCompress(boolean compress) { this.compress = compress; }

	public boolean isUseZFW() {return useZFW;}
	public ZipFileWriter getZFW() {return all;}

	public void begin(boolean incremental) throws IOException {
		if (work) end();

		useZFW = !incremental;
		if (incremental) {
			if (some == null) {
				some = new ZipArchive(file);
			} else {
				some.reopen();
			}
		} else {
			all = new ZipFileWriter(file);
			if (some != null) {
				some.close();
				some = null;
			}
		}
		work = true;
	}

	public void setComment(String comment) {
		if (useZFW) all.setComment(comment);
		else some.setComment(comment);
	}

	public void set(String name, ByteList data) throws IOException {
		if (useZFW) all.writeNamed(name, data, compress ? ZipEntry.DEFLATED : ZipEntry.STORED);
		else some.put(name, data).flag |= compress ? 8 : 0;
	}

	public void set(String name, ExceptionalSupplier<ByteList, IOException> data) throws IOException {
		if (useZFW) {
			all.writeNamed(name, data.get(), compress ? ZipEntry.DEFLATED : ZipEntry.STORED);
		} else {
			some.put(name, data, compress);
		}
	}

	public void set(String name, ExceptionalSupplier<ByteList, IOException> data, long modTime) throws IOException {
		if (useZFW) {
			all.writeNamed(name, data.get(), compress ? ZipEntry.DEFLATED : ZipEntry.STORED, modTime);
		} else {
			some.put(name, data, compress).entry.setModificationTime(modTime);
		}
	}

	public void setStream(String name, ExceptionalSupplier<InputStream, IOException> data, long modTime) throws IOException {
		if (useZFW) {
			ZEntry ze = new ZEntry(name);
			ze.setMethod(compress ? ZipEntry.DEFLATED : ZipEntry.STORED);
			ze.setModificationTime(modTime);
			all.beginEntry(ze);

			byte[] b = IOUtil.getSharedByteBuf().list;
			int r;
			try (var in = data.get()) {
				do {
					r = in.read(b);
					if (r < 0) break;
					all.write(b, 0, r);
				} while (r == b.length);
			} finally {
				all.closeEntry();
			}
		} else {
			some.putStream(name, data, compress);
		}
	}

	public void end() throws IOException {
		Exception e3 = null;
		try {
			if (useZFW) {
				if (all != null) {
					all.close();
					all = null;
				}
			} else if (some != null) {
				some.save();
				some.close();
			}
		} catch (Exception e) {
			e3 = e;
			IOUtil.closeSilently(some);
			IOUtil.closeSilently(all);
		}
		work = false;
		if (e3 != null) Helpers.athrow(e3);
	}

	public ZipArchive getMZF() throws IOException {
		if (some == null) return new ZipArchive(file);

		some.reopen();
		return some;
	}

	public void close() throws IOException {
		end();
		if (some != null) {
			some.close();
			some = null;
		}
	}
}