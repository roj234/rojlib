package roj.archive;

import java.nio.file.attribute.FileTime;

/**
 * @author Roj234
 * @since 2023/3/14 0:58
 */
public interface ArchiveEntry {
	String getName();
	boolean isDirectory();

	long getSize();
	long getCompressedSize();

	boolean isEncrypted();

	// in milliseconds
	long getAccessTime();
	long getCreationTime();
	long getModificationTime();

	// (maybe) in nanoseconds
	FileTime getPrecisionAccessTime();
	FileTime getPrecisionCreationTime();
	FileTime getPrecisionModificationTime();

	boolean hasAccessTime();
	boolean hasCreationTime();
	boolean hasModificationTime();
}