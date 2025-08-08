package roj.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2024/1/3 10:10
 */
public abstract class MBInputStream extends InputStream {
	private static final byte[] SHARED_SKIP_BUFFER = new byte[4096];

	@Override public final int read() throws IOException {return IOUtil.readSingleByteHelper(this);}
	@Override public abstract int read(@NotNull byte[] b, int off, int len) throws IOException;
	@Override public long skip(long n) throws IOException {
		if (n <= 0) return 0;

		long remaining = n;
		while (remaining > 0) {
			int r = read(SHARED_SKIP_BUFFER, 0, (int)Math.min(4096, remaining));
			if (r < 0) break;
			remaining -= r;
		}

		return n - remaining;
	}
}