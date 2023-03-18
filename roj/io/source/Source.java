package roj.io.source;

import roj.io.SourceInputStream;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.*;
import java.nio.channels.FileChannel;

/**
 * @author Roj233
 * @since 2021/8/18 13:36
 */
public abstract class Source extends DataOutputStream implements Closeable {
	private byte[] b1;

	public Source() {
		super(null);
		out = this;
	}

	public int read() throws IOException {
		if (b1 == null) b1 = new byte[1];
		int r = read(b1,0,1);
		return r <= 0 ? -1 : b1[0]&0xFF;
	}
	public final int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}
	public abstract int read(byte[] b, int off, int len) throws IOException;
	public final void read(ByteList buf, int len) throws IOException {
		buf.ensureCapacity(buf.wIndex()+len);
		readFully(buf.list, buf.arrayOffset()+buf.wIndex(), len);
		buf.wIndex(buf.wIndex()+len);
	}

	// 目前所有的实现除非EOF了否则不会少读
	public final void readFully(byte[] b) throws IOException {
		readFully(b, 0, b.length);
	}
	public final void readFully(byte[] b, int off, int len) throws IOException {
		do {
			int n = read(b, off, len);
			if (n < len) throw new EOFException();
			off += n;
			len -= n;
		} while (len > 0);
	}

	public void write(int b) throws IOException {
		if (b1 == null) b1 = new byte[1];
		b1[0] = (byte) b;
		write(b1,0,1);
	}
	public abstract void write(byte[] b, int off, int len) throws IOException;
	public abstract void write(DynByteBuf data) throws IOException;

	public void flush() throws IOException {}

	public abstract void seek(long pos) throws IOException;
	public abstract long position() throws IOException;
	public long skip(long amount) throws IOException {
		long position = position();
		long pos = Math.min(position + amount, length());
		seek(pos);
		return pos - position;
	}

	public abstract void setLength(long length) throws IOException;
	public abstract long length() throws IOException;

	public boolean hasChannel() { return false; }
	public FileChannel channel() { return null; }

	public void close() throws IOException {}
	public void reopen() throws IOException, UnsupportedOperationException {
		throw new UnsupportedOperationException();
	}

	private DataInputStream dis;
	public DataInput asDataInput() {
		if (dis == null) dis = new DataInputStream(asInputStream());
		return dis;
	}
	public InputStream asInputStream() {
		long len;
		try {
			len = length()-position();
		} catch (IOException e) {
			len = 0;
		}
		return new SourceInputStream(this, len);
	}

	/**
	 * Did not ensure return is writable
	 */
	public abstract Source threadSafeCopy() throws IOException;
	public abstract void moveSelf(long from, long to, long length) throws IOException;

	public boolean isBuffered() {
		return false;
	}
}
