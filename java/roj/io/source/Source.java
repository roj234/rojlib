package roj.io.source;

import roj.io.IOUtil;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.*;
import java.nio.channels.FileChannel;

/**
 * @author Roj233
 * @since 2021/8/18 13:36
 */
public abstract class Source extends DataOutputStream {
	public Source() {
		super(null);
		out = this;
	}

	public int read() throws IOException {
		byte[] b1 = IOUtil.SharedBuf.get().singleByteBuffer;
		return read(b1, 0, 1) > 0 ? b1[0] & 0xFF : -1;
	}
	public final int read(byte[] b) throws IOException { return read(b, 0, b.length); }
	public void read(ByteList buf, int len) throws IOException {
		buf.ensureWritable(len);
		int count = read(buf.array(), buf.arrayOffset() + buf.wIndex(), len);
		if (count > 0) buf.wIndex(buf.wIndex()+count);
	}
	public abstract int read(byte[] b, int off, int len) throws IOException;

	// 目前所有的实现除非EOF了否则不会少读
	public void readFully(ByteList buf, int len) throws IOException {read__(buf, len);}
	protected void read__(DynByteBuf buf, int len) throws IOException {
		if (!buf.hasArray()) throw new IllegalStateException("Not implemented!");
		buf.ensureCapacity(buf.wIndex()+len);
		readFully(buf.array(), buf.arrayOffset()+buf.wIndex(), len);
		buf.wIndex(buf.wIndex()+len);
	}
	public final void readFully(byte[] b) throws IOException {readFully(b, 0, b.length);}
	public final void readFully(byte[] b, int off, int len) throws IOException {
		do {
			int n = read(b, off, len);
			if (n < len) throw new EOFException();
			off += n;
			len -= n;
		} while (len > 0);
	}

	public void write(int b) throws IOException {IOUtil.writeSingleByteHelper(this, b);}
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
	/**
	 * 和isBuffered没关系
	 */
	public DynByteBuf buffer() { return null; }
	public void put(Source src) throws IOException {put(src, 0, src.length());}
	public void put(Source src, long offset, long len) throws IOException {
		var buf = src.buffer();
		if (buf != null) {
			write(buf.slice((int) offset, (int) len));
		} else if (hasChannel()&src.hasChannel()) {
			src.channel().transferTo(offset, len, channel());
		} else {
			var pos = src.position();
			src.seek(offset);

			byte[] bb = ArrayCache.getByteArray(4096, false);
			try {
				while (len > 0) {
					int l = (int) Math.min(bb.length, len);
					src.readFully(bb, 0, l);
					write(bb, 0, l);
					len -= l;
				}
			} finally {
				ArrayCache.putArray(bb);
				src.seek(pos);
			}
		}
	}

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

	public boolean isBuffered() {return false;}
	public boolean isWritable() {return true;}
}