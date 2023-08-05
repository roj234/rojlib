package roj.archive;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

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

	Map<String, ? extends ArchiveEntry> getEntries();
	default InputStream getInput(ArchiveEntry entry) throws IOException { return getInput(entry, null); }
	InputStream getInput(ArchiveEntry entry, byte[] password) throws IOException;
}
