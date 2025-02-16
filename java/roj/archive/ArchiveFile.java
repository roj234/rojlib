package roj.archive;

import org.jetbrains.annotations.UnmodifiableView;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * @author Roj234
 * @since 2023/3/15 0015 8:37
 */
public interface ArchiveFile extends Closeable, MultiFileSource {
	void close() throws IOException;
	/**
	 * Reload this archive file from disk, discarding any unsaved changes
	 */
	void reload() throws IOException;

	ArchiveEntry getEntry(String name);
	@UnmodifiableView Collection<? extends ArchiveEntry> entries();

	default InputStream getStream(String name) throws IOException {
		var entry = getEntry(name);
		return entry != null ? getStream(entry) : null;
	}
	default InputStream getStream(ArchiveEntry entry) throws IOException { return getStream(entry, null); }
	InputStream getStream(ArchiveEntry entry, byte[] pw) throws IOException;
}