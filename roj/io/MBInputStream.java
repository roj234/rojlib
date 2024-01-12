package roj.io;

import org.jetbrains.annotations.NotNull;
import roj.util.ArrayCache;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2024/1/3 0003 10:10
 */
public abstract class MBInputStream extends InputStream {
	private byte[] b1;

	@Override
	public final int read() throws IOException {
		if (b1 == null) b1 = new byte[1];
		return read(b1, 0, 1) < 0 ? -1 : b1[0]&0xFF;
	}

	@Override
	public abstract int read(@NotNull byte[] b, int off, int len) throws IOException;

	@Override
	public long skip(long n) throws IOException {
		if (n <= 0) return 0;

		long remaining = n;

		int size = (int)Math.min(4096, remaining);
		byte[] skipBuffer = ArrayCache.getByteArray(size, false);
		try {
			while (remaining > 0) {
				int r = read(skipBuffer, 0, (int)Math.min(size, remaining));
				if (r < 0) break;
				remaining -= r;
			}
		} finally {
			ArrayCache.putArray(skipBuffer);
		}

		return n - remaining;
	}
}