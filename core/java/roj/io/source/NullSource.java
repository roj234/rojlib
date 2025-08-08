package roj.io.source;

import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2021/8/18 13:36
 */
public class NullSource extends Source {
	private long pos, len;

	public int read() throws IOException { return -1; }

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (len == 0) return 0;
		return -1;
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		pos += len;
		if (this.len < pos) this.len = pos;
	}

	@Override
	public void write(DynByteBuf data) throws IOException {
		int len = data.readableBytes();
		pos += len;
		if (this.len < pos) this.len = pos;
	}

	public void seek(long p) { pos = p; }
	public long position() { return pos; }

	public void setLength(long length) throws IOException { len = length; }
	public long length() throws IOException { return len; }

	public Source copy() throws IOException { return new NullSource(); }

	public void moveSelf(long from, long to, long length) {}

	public boolean isBuffered() { return true; }
}
