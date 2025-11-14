package roj.io.source;

import org.jetbrains.annotations.NotNull;
import roj.optimizer.FastVarHandle;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.VarHandle;

/**
 * @author Roj234
 * @since 2022/12/7 14:27
 */
public sealed class SourceInputStream extends InputStream {
	public static InputStream nullInputStream() {return new SourceInputStream(null, 0);}

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
		if (remain == 0) return -1;

		int v = src.read();
		if (remain > 0 && v >= 0) remain--;
		return v;
	}

	@Override
	public int read(@NotNull byte[] b, int off, int len) throws IOException {
		if (len == 0) return 0;
		if (remain < 0) return src.read(b, off, len);
		if (remain == 0) return -1;

		len = (int) Math.min(len, remain);
		len = src.read(b, off, len);
		if (len > 0) remain -= len;
		return len;
	}

	@Override
	public int available() throws IOException {
		return remain > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max((int)remain, 0);
	}

	@Override
	public long skip(long n) throws IOException {
		if (remain >= 0) n = Math.min(n, remain);
		if (n <= 0) return 0;

		long end = Math.min(src.position()+n, src.length());
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
	@FastVarHandle
	public static final class Shared extends SourceInputStream {
		private Object ref;
		private final VarHandle off;

		public Shared(Source in, long len, Object ref, VarHandle off) {
			super(in, len);
			this.ref = ref;
			this.off = off;
		}

		@Override
		public synchronized void close() throws IOException {
			if (ref != null && !off.compareAndSet(ref, null, src)) {
				super.close();
			}

			ref = null;
			remain = 0;
		}
	}
}