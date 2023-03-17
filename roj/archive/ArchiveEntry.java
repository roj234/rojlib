package roj.archive;

/**
 * @author Roj234
 * @since 2023/3/14 0014 0:58
 */
public interface ArchiveEntry {
	String getName();
	long getSize();
	long getCompressedSize();
	long getModificationTime();
}
