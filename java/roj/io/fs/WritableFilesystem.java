package roj.io.fs;

import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Roj234
 * @since 2024/7/24 0024 1:16
 */
public interface WritableFilesystem extends Filesystem {
	OutputStream getOutput(String pathname) throws IOException;
	default void write(String pathname, DynByteBuf buf) throws IOException {
		try (var out = getOutput(pathname)) {
			buf.writeToStream(out);
		}
	}
}