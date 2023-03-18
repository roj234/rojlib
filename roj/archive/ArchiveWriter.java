package roj.archive;

import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2023/3/15 0015 8:34
 */
public interface ArchiveWriter extends Closeable {
	ArchiveEntry createEntry(String fileName);

	void copy(ArchiveFile owner, ArchiveEntry entry) throws IOException;

	default void write(ArchiveEntry entry, DynByteBuf data) throws IOException {
		beginEntry(entry);
		if (!data.hasArray()) {
			BufferPool pool = BufferPool.localPool();
			ByteList arrayBuf = (ByteList) pool.buffer(false,Math.min(data.readableBytes(),1024));
			try {
				while (data.isReadable()) {
					arrayBuf.put(data, Math.min(data.readableBytes(),1024));
					write(arrayBuf.array(),arrayBuf.relativeArrayOffset(),arrayBuf.readableBytes());
					arrayBuf.clear();
				}
			} finally {
				pool.reserve(arrayBuf);
			}
		} else {
			write(data.array(),data.relativeArrayOffset(),data.readableBytes());
			data.rIndex = data.wIndex();
		}
		closeEntry();
	}
	default void write(ArchiveEntry entry, File data) throws IOException {
		beginEntry(entry);

		ByteList buf = IOUtil.getSharedByteBuf();
		buf.ensureCapacity(1024);
		byte[] b = buf.list;
		try (FileInputStream in = new FileInputStream(data)) {
			while (true) {
				int r = in.read(b);
				if (r > 0) write(b, 0, r);
				else break;
			}
		} finally {
			closeEntry();
		}
	}

	void beginEntry(ArchiveEntry entry) throws IOException;
	void write(byte[] b) throws IOException;
	void write(byte[] b, int off, int len) throws IOException;
	void closeEntry() throws IOException;

	void finish() throws IOException;
}
