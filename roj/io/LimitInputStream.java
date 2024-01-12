package roj.io;

import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2023/3/15 12:55
 */
public class LimitInputStream extends FilterInputStream {
	public LimitInputStream(InputStream in, long length) {
		super(in);
		remain = length;
	}
	public LimitInputStream(InputStream in, long length, boolean dispatchClose) {
		super(in);
		remain = length;
		close = dispatchClose;
	}

	public long remain;
	private boolean close;

	@Override
	public int read() throws IOException {
		if (remain <= 0) return -1;

		int v = in.read();

		if (v < 0) {
			remain = 0;
			in.close();
		} else remain--;

		return v;
	}

	@Override
	public int read(@NotNull byte[] b, int off, int len) throws IOException {
		if (remain <= 0) return -1;

		len = Math.min(len, available());
		len = in.read(b, off, len);

		if (len < 0) {
			remain = 0;
			in.close();
		} else remain -= len;

		return len;
	}

	@Override
	public int available() throws IOException {
		return remain > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remain;
	}

	@Override
	public long skip(long n) throws IOException {
		n = Math.min(n, remain);
		if (n <= 0) return 0;

		if (n < (n = in.skip(n)))
			in.close();
		remain -= n;
		return n;
	}

	@Override
	public void close() throws IOException {
		if (close) in.close();
	}
}