package roj.archive;

import roj.collect.MyHashSet;
import roj.io.source.FileSource;
import roj.io.source.FragmentSource;
import roj.io.source.Source;
import roj.text.TextUtil;

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
		return m.find() ? FragmentSource.dynamic(new File(TextUtil.substr(file.getAbsolutePath(), -(m.end() - m.start()))), !readonly) : new FileSource(file, !readonly);
	}
}