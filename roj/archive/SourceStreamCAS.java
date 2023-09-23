package roj.archive;

import roj.io.SourceInputStream;
import roj.io.source.Source;

import java.io.IOException;

import static roj.reflect.FieldAccessor.u;

/**
 * @author Roj234
 * @since 2023/9/14 0014 14:01
 */
public final class SourceStreamCAS extends SourceInputStream {
	private Object ref;
	private final long off;

	public SourceStreamCAS(Source in, long len, Object ref, long off) {
		super(in, len);
		this.ref = ref;
		this.off = off;
	}

	@Override
	public synchronized void close() throws IOException {
		if (ref != null && !u.compareAndSwapObject(ref, off, null, src)) {
			super.close();
		}

		ref = null;
		remain = 0;
	}
}
