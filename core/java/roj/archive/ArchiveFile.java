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
public interface ArchiveFile<EntryType extends ArchiveEntry> extends Closeable {
	void close() throws IOException;
	/**
	 * 从磁盘重新读取压缩包.
	 */
	void reload() throws IOException;

	/**
	 * Lookup an entry by name.
	 */
	@Nullable EntryType getEntry(String name);
	/**
	 * Returns an unmodifiable list of all entries in the archive.
	 */
	@UnmodifiableView Collection<? extends EntryType> entries();

	default InputStream getInputStream(String name) throws IOException {
		var entry = getEntry(name);
		return entry != null ? getInputStream(entry) : null;
	}
	default InputStream getInputStream(EntryType entry) throws IOException { return getInputStream(entry, null); }
	InputStream getInputStream(EntryType entry, byte[] password) throws IOException;
}