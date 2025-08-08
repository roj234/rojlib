package roj.compiler.jpp.machine;

import roj.io.source.Source;
import roj.util.ByteList;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/5/30 18:00
 */
public abstract class NativeExecutable implements Closeable {
	protected final Source src;
	protected final ByteList rb;

	public NativeExecutable(Source src) {
		this.src = src;
		this.rb = new ByteList(128);
	}

	public abstract void read() throws IOException;

	public void readPlus(long off, int len) throws IOException {
		if (off >= 0) src.seek(off);
		ByteList rb = this.rb;
		int len1 = rb.wIndex() - rb.rIndex;
		if (len1 > 0) System.arraycopy(rb.list, rb.rIndex, rb.list, 0, len1);
		len -= len1;

		rb.ensureCapacity(len);
		len = src.read(rb.list, len1, len);
		rb.rIndex = 0;
		rb.wIndex(len1 + len);
	}

	public void read(long off, int len) throws IOException {
		if (off >= 0) src.seek(off);
		ByteList rb = this.rb;
		rb.clear();
		rb.ensureCapacity(len);
		src.readFully(rb, len);
	}

	public void write(long off) throws IOException {
		if (off >= 0) src.seek(off);
		src.write(rb);
		rb.rIndex = rb.wIndex();
	}

	@Override
	public void close() throws IOException {
		src.close();
	}

	protected void pad(int pad) throws IOException {
		long more = (pad - src.length() % pad) % pad;
		this.src.setLength(src.length() + more);
	}

	public ByteList getData() {
		return rb;
	}
}