package roj.io.fs;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2024/7/24 0024 1:16
 */
public interface Filesystem {
	@Nullable InputStream getStream(String pathname) throws IOException;
	static Filesystem disk(File base) {return pathname -> {
		File file = new File(base, pathname);
		return file.isFile() ? new FileInputStream(file) : null;
	};}
}