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

	public void setCompress(boolean compress) {
		this.compress = compress;
	}

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
		if (useZFW) {
			all.setComment(comment);
		} else {
			some.getEND().setComment(comment);
		}
	}

	public void set(String name, ByteList data) throws IOException {
		if (useZFW) {
			all.writeNamed(name, data, compress ? ZipEntry.DEFLATED : ZipEntry.STORED);
		} else {
			some.put(name, data).flag |= compress ? 8 : 0;
		}
	}

	public void set(String name, Supplier<ByteList> data) throws IOException {
		if (useZFW) {
			all.writeNamed(name, data.get(), compress ? ZipEntry.DEFLATED : ZipEntry.STORED);
		} else {
			some.put(name, data, compress);
		}
	}

	public void setS(String name, Supplier<InputStream> data) throws IOException {
		if (useZFW) {
			try (InputStream in = data.get()) {
				set(name, in);
			}
		} else {
			some.put(name, Helpers.cast(data), compress);
		}
	}

	public void set(String name, InputStream in) throws IOException {
		if (useZFW) {
			ZEntry ze = new ZEntry(name);
			ze.setMethod(compress ? ZipEntry.DEFLATED : ZipEntry.STORED);
			all.beginEntry(ze);

			try {
				ByteList b = IOUtil.getSharedByteBuf();
				b.ensureCapacity(4096);
				byte[] list = b.list;
				int cnt;
				do {
					cnt = in.read(list);
					if (cnt < 0) break;
					all.write(list, 0, cnt);
				} while (cnt == list.length);
				all.closeEntry();
			} finally {
				in.close();
			}
		} else {
			some.putStream(name, in, compress);
		}
	}

	public void end() throws IOException {
		if (useZFW) {
			if (all != null) {
				all.finish();
				all = null;
			}
		} else if (some != null) {
			some.store();
			some.closeFile();
		}
		work = false;
	}

	public ZipArchive getMZF() throws IOException {
		if (some == null) {
			return new ZipArchive(file, ZipArchive.FLAG_BACKWARD_READ);
		} else {
			if (!some.isOpen()) {
				some.reopen();
			}
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
