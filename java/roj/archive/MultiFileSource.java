package roj.archive;

import org.jetbrains.annotations.Nullable;
import roj.io.IOUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2024/7/24 0024 1:16
 */
public interface MultiFileSource {
	@Nullable InputStream getStream(String pathname) throws IOException;
	static MultiFileSource disk(String base) {return pathname -> {
		File file = IOUtil.safePath2(base, pathname);
		return file.isFile() ? new FileInputStream(file) : null;
	};}
}