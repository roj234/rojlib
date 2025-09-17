package roj.io.source;

import roj.io.BufferPool;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * @author Roj233
 * @since 2021/8/18 13:36
 */
public class BufferedSource extends Source {
	private static final int PAGE = 4096;

	private long pos, len;
	private int sync;

	private final Source s;
	private final boolean close;

	private int bufPos;
	private final ByteList buf;

	public static Source autoClose(Source copy) throws IOException {return new BufferedSource(copy, 4096, BufferPool.localPool(), true);}
	public static Source wrap(Source copy) throws IOException {return new BufferedSource(copy, 4096, BufferPool.localPool(), false);}

	public BufferedSource(Source s, int buf, BufferPool pool, boolean dispatchClose) throws IOException {
		this.s = s;
		this.bufPos = -1;
		this.buf = (ByteList) pool.allocate(false, buf, 0);
		this.pos = s.position();
		this.len = s.length();
		this.close = dispatchClose;
	}

	@Override
	public int read() throws IOException {
		sl();

		sync |= 1;
		return pos >= len ? -1 : buffer(pos).getUnsignedByte(((int)pos++&(PAGE-1)));
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (len < 0) throw new ArrayIndexOutOfBoundsException();
		if (len == 0) return 0;

		sl();

		long p = pos;

		len = (int) Math.min(len, this.len-p);
		if (len <= 0) return -1;

		int plen = (int) p&(PAGE-1);
		int rLen = Math.min(len, PAGE-plen);
		buffer(p).readFully(plen, b, off, rLen);
		p += rLen;

		int end = off+len;
		off += rLen;

		while (end-off >= PAGE) {
			buffer(p).readFully(0, b, off, PAGE);
			off += PAGE;
			p += PAGE;
		}

		if (end>off) {
			buffer(p).readFully(0, b, off, end-off);
			p += end-off;
		}

		pos = p;
		sync |= 1;

		return len;
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		sp();

		s.write(b, off, len);

		invalidate(pos, pos += len);
	}

	@Override
	public void write(DynByteBuf data) throws IOException {
		sp();
		int len = data.readableBytes();

		s.write(data);

		invalidate(pos, pos += len);
	}

	@Override
	public void seek(long p) {
		pos = p;
		sync |= 1;
	}
	@Override
	public long position() {
		return pos;
	}

	@Override
	public void setLength(long length) throws IOException {
		s.setLength(length);
		len = length;
		sync &= ~2;
	}
	@Override
	public long length() throws IOException {
		sl();
		return len;
	}

	@Override
	public FileChannel channel() {
		return s.channel();
	}

	@Override
	public void close() throws IOException {
		BufferPool.reserve(buf);
		if (close) s.close();
	}
	@Override
	public void reopen() throws IOException {
		if (close) s.reopen();
	}

	@Override
	public Source copy() throws IOException { return new BufferedSource(s.copy(), 4096, BufferPool.localPool(), close); }

	public void moveSelf(long from, long to, long length) throws IOException {
		bufPos = -1;
		s.moveSelf(from, to, length);
	}

	private void invalidate(long from, long to) {
		int f = (int) (from>>>12);
		int t = (int) ((to+(PAGE-1))>>>12);
		if (bufPos >= f && bufPos < t) {
			bufPos = -1;
		}
	}

	@Override public boolean isBuffered() {return true;}
	@Override public boolean isWritable() {return s.isWritable();}

	private ByteList buffer(long pos) throws IOException {
		if (bufPos != (int) (pos>>>12)) {
			bufPos = (int) (pos>>>12);

			s.seek(pos & -PAGE);
			sync |= 1;

			int len = s.read(buf.list, buf.arrayOffset(), PAGE);
			buf.rIndex = 0;
			buf.wIndex(len);
		}
		return buf;
	}
	private void sp() throws IOException {
		if ((sync & 1) != 0) {
			s.seek(pos);
			sync ^= 1;
		}
	}
	private void sl() throws IOException {
		if ((sync & 2) != 0) {
			len = s.length();
			sync ^= 2;
		}
	}

	@Override
	public String toString() { return "BufferedSource@"+s; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		BufferedSource that = (BufferedSource) o;
		return s.equals(that.s);
	}

	@Override public int hashCode() {return s.hashCode()+1;}
}