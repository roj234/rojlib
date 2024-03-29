package roj.archive.zip;

import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;
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

	public void begin(boolean allModifyMode) throws IOException {
		if (work) end();

		useZFW = allModifyMode;
		if (allModifyMode) {
			all = new ZipFileWriter(file, false);
			if (some != null) {
				some.close();
				some = null;
			}
		} else if (some == null) {
			some = new ZipArchive(file);
		} else {
			some.reopen();
		}
		work = true;
	}

	public void setComment(String comment) {
		if (useZFW) all.setComment(comment);
		else some.getEND().setComment(comment);
	}

	public void set(String name, ByteList data) throws IOException {
		if (useZFW) all.writeNamed(name, data, compress ? ZipEntry.DEFLATED : ZipEntry.STORED);
		else some.put(name, data).flag |= compress ? 8 : 0;
	}

	public void set(String name, Supplier<ByteList> data) throws IOException {
		if (useZFW) {
			all.writeNamed(name, data.get(), compress ? ZipEntry.DEFLATED : ZipEntry.STORED);
		} else {
			some.put(name, data, compress);
		}
	}

	public void set(String name, InputStream in) throws IOException {
		if (useZFW) {
			ZEntry ze = new ZEntry(name);
			ze.setMethod(compress ? ZipEntry.DEFLATED : ZipEntry.STORED);
			all.beginEntry(ze);

			byte[] b = IOUtil.getSharedByteBuf().list;
			int r;
			try {
				do {
					r = in.read(b);
					if (r < 0) break;
					all.write(b, 0, r);
				} while (r == b.length);
			} finally {
				in.close();
				all.closeEntry();
			}
		} else {
			some.putStream(name, in, compress);
		}
	}

	public void end() throws IOException {
		Exception e3 = null;
		try {
			if (useZFW) {
				if (all != null) {
					all.finish();
					all = null;
				}
			} else if (some != null) {
				some.store();
				some.closeFile();
			}
		} catch (Exception e) {
			e3 = e;
			try {
				if (some != null) some.close();
				if (all != null) all.close();
			} catch (Exception ignored) {}
		}
		work = false;
		if (e3 != null) Helpers.athrow(e3);
	}

	public ZipArchive getMZF() throws IOException {
		if (some == null) {
			return new ZipArchive(file, ZipArchive.FLAG_BACKWARD_READ);
		} else {
			if (!some.isOpen())
				some.reopen();
		}
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