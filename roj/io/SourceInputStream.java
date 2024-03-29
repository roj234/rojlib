package roj.io;

import roj.io.source.Source;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipException;

/**
 * @author Roj234
 * @since 2022/12/7 0007 14:27
 */
public class SourceInputStream extends InputStream {
	public SourceInputStream(Source src, long length) {
		this.src = src;
		this.doClose = src != null;
		this.remain = length;
	}
	public SourceInputStream(Source src, long length, boolean doClose) {
		this.src = src;
		this.doClose = doClose;
		this.remain = length;
	}

	public final Source src;
	public long remain;
	public boolean doClose;

	@Override
	public int read() throws IOException {
		if (remain <= 0) return -1;

		int v = src.read();
		if (v < 0) remain = 0;
		else remain--;
		return v;
	}

	@Override
	public int read(@Nonnull byte[] b, int off, int len) throws IOException {
		if (remain <= 0) return -1;

		len = Math.min(len, available());
		len = src.read(b, off, len);
		if (len < 0) remain = 0;
		else remain -= len;
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

		long end = src.position()+n;
		if (end > src.length()) throw new ZipException("File size externally changed.");
		src.seek(end);
		remain -= n;
		return n;
	}

	@Override
	public void close() throws IOException {
		if (doClose) src.close();
		remain = 0;
	}
}
