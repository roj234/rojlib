package roj.archive;

import roj.collect.MyHashSet;
import roj.io.source.CompositeSource;
import roj.io.source.FileSource;
import roj.io.source.Source;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2024/1/3 0003 15:43
 */
public class ArchiveUtils {
	// 有意的不包含zip格式
	public static final MyHashSet<String> INCOMPRESSIBLE_FILE_EXT = new MyHashSet<>("7z", "rar", "xz", "txz", "lzma", "lzma2", "bz2", "bzip2", "tbz", "tbz2", "gz", "gzip", "esd", "wim");
	public static final Pattern SPLIT_ARCHIVE_PATTERN = Pattern.compile("\\.[0zZ]01$");

	public static Source tryOpenSplitArchive(File file, boolean readonly) throws IOException {
		Matcher m = SPLIT_ARCHIVE_PATTERN.matcher(file.getName());
		if (m.find()) {
			String s = file.getAbsolutePath();
			return CompositeSource.dynamic(new File(s.substring(0, s.length() + m.start() - m.end())), !readonly);
		} else {
			return new FileSource(file, !readonly);
		}
	}
}