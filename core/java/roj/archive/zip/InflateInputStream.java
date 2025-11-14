package roj.archive.zip;

import roj.collect.ArrayList;
import roj.io.Finishable;
import roj.io.source.SourceInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * @author Roj234
 * @since 2023/3/14 0:42
 */
public final class InflateInputStream extends InflaterInputStream implements Finishable {
	private static final ThreadLocal<ArrayList<InflateInputStream>> CACHE = ThreadLocal.withInitial(ArrayList::new);
	private static final int CACHE_MAX = 10;

	private boolean closed;

	private InflateInputStream(InputStream in) { super(in, new Inflater(true), 4096); }

	public static InputStream getInstance(InputStream in) {
		var inf = CACHE.get().pop();
		return inf == null ? new InflateInputStream(in) : inf.reset(in);
	}
	private InflateInputStream reset(InputStream in) {
		closed = false;
		this.in = in;
		this.inf.reset();
		return this;
	}

	public Inflater getInflater() { return inf; }

	@Override public int available() { return closed ? 0 : 1; }
	@Override public int read(byte[] b, int off, int len) throws IOException {
		len = super.read(b, off, len);
		if (len < 0) close();
		return len;
	}
	@Override public void close() throws IOException {
		if (!closed) {
			closed = true;

			var inflateIns = CACHE.get();
			if (inflateIns.size() < CACHE_MAX) {
				inflateIns.add(this);
				in.close();
			} else {
				super.close();
			}
		}
	}
	@Override public void finish() throws IOException {
		if (!closed) {
			closed = true;

			var inflateIns = CACHE.get();
			if (inflateIns.size() < CACHE_MAX) {
				inflateIns.add(this);
			} else {
				in = SourceInputStream.nullInputStream();
				super.close();
			}
		}
	}
}