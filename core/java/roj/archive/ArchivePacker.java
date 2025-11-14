package roj.archive;

import roj.io.Finishable;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2023/3/15 8:34
 */
public interface ArchivePacker<ArchiveType extends ArchiveFile<EntryType>, EntryType extends ArchiveEntry> extends Closeable, Finishable {
	void copy(ArchiveType owner, EntryType entry) throws IOException;

	default void write(EntryType entry, DynByteBuf data) throws IOException {
		beginEntry(entry);

		if (!data.hasArray()) {
			byte[] b = ArrayCache.getIOBuffer();
			try {
				int len;
				while ((len = data.readableBytes()) > 0) {
					len = Math.min(len, b.length);
					data.readFully(b, 0, len);
					write(b, 0, len);
				}
			} finally {
				ArrayCache.putArray(b);
			}
		} else {
			write(data.array(),data.relativeArrayOffset(),data.readableBytes());
			data.rIndex = data.wIndex();
		}

		closeEntry();
	}
	default void write(EntryType entry, File data) throws IOException {
		beginEntry(entry);

		byte[] b = ArrayCache.getIOBuffer();
		try (var in = new FileInputStream(data)) {
			while (true) {
				int r = in.read(b);
				if (r > 0) write(b, 0, r);
				else break;
			}
		} finally {
			ArrayCache.putArray(b);
		}

		closeEntry();
	}

	void beginEntry(EntryType entry) throws IOException;
	void write(byte[] b) throws IOException;
	void write(byte[] b, int off, int len) throws IOException;
	void closeEntry() throws IOException;

	void finish() throws IOException;
}