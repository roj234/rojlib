package roj.archive;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * @author Roj234
 * @since 2023/3/15 8:37
 */
public interface ArchiveFile extends Closeable {
	void close() throws IOException;
	/**
	 * Reload this archive file from disk, discarding any unsaved changes
	 */
	void reload() throws IOException;

	/**
	 * Looks up an entry by name.
	 */
	@Nullable ArchiveEntry getEntry(String name);
	/**
	 * Returns an unmodifiable list of all entries in the archive.
	 */
	@UnmodifiableView Collection<? extends ArchiveEntry> entries();

	default InputStream getInputStream(String name) throws IOException {
		var entry = getEntry(name);
		return entry != null ? getInputStream(entry) : null;
	}
	default InputStream getInputStream(ArchiveEntry entry) throws IOException { return getInputStream(entry, null); }
	InputStream getInputStream(ArchiveEntry entry, byte[] password) throws IOException;
}