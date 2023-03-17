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
	void reload() throws IOException;

	Map<String, ? extends ArchiveEntry> getEntries();
	default InputStream getInputStream(ArchiveEntry entry) throws IOException {
		return getInputStream(entry, null);
	}
	InputStream getInputStream(ArchiveEntry entry, byte[] password) throws IOException;
}
