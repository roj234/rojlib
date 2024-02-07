package roj.archive;

import roj.collect.MyHashSet;

/**
 * @author Roj234
 * @since 2024/1/3 0003 15:43
 */
public class ArchiveConstants {
	// 有意的不包含zip格式
	public static final MyHashSet<String> INCOMPRESSIBLE_FILE_EXT = new MyHashSet<>("7z", "rar", "xz", "txz", "lzma", "lzma2", "bz2", "bzip2", "tbz", "tbz2", "gz", "gzip", "esd", "wim");
}