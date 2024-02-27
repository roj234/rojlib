package roj.archive;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * @author Roj234
 * @since 2023/3/15 0015 8:37
 */
public interface ArchiveFile extends Closeable {
	void close() throws IOException;
	/**
	 * Reload this archive file from disk, discarding any unsaved changes
	 */
	void reload() throws IOException;

	ArchiveEntry getEntry(String name);
	Collection<? extends ArchiveEntry> entries();

	default InputStream getStream(ArchiveEntry entry) throws IOException { return getStream(entry, null); }
	InputStream getStream(ArchiveEntry entry, byte[] pw) throws IOException;
}