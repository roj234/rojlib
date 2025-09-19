package roj.archive;

import org.intellij.lang.annotations.MagicConstant;

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

	default boolean isEncrypted() { return false; }

	// in milliseconds
	default long getAccessTime() { return 0; }
	default long getCreationTime() { return 0; }
	default long getModificationTime() { return 0; }
	@MagicConstant(flagsFromClass = WinAttributes.class)
	default int getWinAttributes() { return 0; }

	// (maybe) in nanoseconds
	default FileTime getPrecisionAccessTime() { return null; }
	default FileTime getPrecisionCreationTime() { return null; }
	default FileTime getPrecisionModificationTime() { return null; }

	default boolean hasAccessTime() {return false;}
	default boolean hasCreationTime() {return false;}
	default boolean hasModificationTime() {return false;}
}