package roj.io.source;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipException;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2022/12/7 14:27
 */
public sealed class SourceInputStream extends InputStream {
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
	public int read(@NotNull byte[] b, int off, int len) throws IOException {
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

	/**
	 * @author Roj234
	 * @since 2023/9/14 14:01
	 */
	public static final class Shared extends SourceInputStream {
		private Object ref;
		private final long off;

		public Shared(Source in, long len, Object ref, long off) {
			super(in, len);
			this.ref = ref;
			this.off = off;
		}

		@Override
		public synchronized void close() throws IOException {
			if (ref != null && !U.compareAndSetReference(ref, off, null, src)) {
				super.close();
			}

			ref = null;
			remain = 0;
		}
	}
}