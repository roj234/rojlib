package roj.io.source;

import roj.collect.LRUCache;
import roj.collect.MyHashMap;
import roj.io.buf.BufferPool;
import roj.math.MutableInt;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import java.util.function.BiConsumer;

/**
 * @author Roj233
 * @since 2021/8/18 13:36
 */
public class BufferedSource extends Source implements BiConsumer<MutableInt,ByteList> {
	public static final int PAGE = 4096;
	private long pos, len;
	private int sync;

	private final Source s;
	private final boolean close;

	private final LRUCache<MutableInt, ByteList> buffers;
	private final BufferPool pool;

	public static Source autoClose(Source copy) throws IOException {
		return new BufferedSource(copy, 4096, BufferPool.localPool(), true);
	}
	public static Source wrap(Source copy) throws IOException {
		return new BufferedSource(copy, 4096, BufferPool.localPool(), false);
	}

	public BufferedSource(Source s, int buffer) throws IOException {
		this(s, buffer, BufferPool.localPool(), false);
	}

	public BufferedSource(Source s, int buffer, BufferPool pool, boolean dispatchClose) throws IOException {
		this.s = s;
		this.buffers = new LRUCache<>((buffer + 4095) >>> 12);
		this.buffers.setEvictListener(this);
		this.pool = pool;
		this.pos = s.position();
		this.len = s.length();
		this.close = dispatchClose;
	}

	@Override
	public int read() throws IOException {
		sl();

		sync |= 1;
		return pos >= len ? -1 : buffer(pos).getU(((int)pos++&4095));
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (len < 0) throw new ArrayIndexOutOfBoundsException();
		if (len == 0) return 0;

		sl();

		long p = pos;

		len = (int) Math.min(len, this.len-p);
		if (len <= 0) return -1;

		int plen = (int) p&4095;
		int rLen = Math.min(len, PAGE-plen);
		buffer(p).read(plen, b, off, rLen);
		p += rLen;

		int end = off+len;
		off += rLen;

		while (end-off >= PAGE) {
			buffer(p).read(0, b, off, PAGE);
			off += PAGE;
			p += PAGE;
		}

		if (end>off) {
			buffer(p).read(0, b, off, end-off);
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
	public void sync() throws IOException {
		pos = s.position();
		len = s.length();
		sync = 0;
	}

	@Override
	public void close() throws IOException {
		invalidate();
		if (close) s.close();
	}
	@Override
	public void reopen() throws IOException {
		if (close) s.reopen();
	}

	@Override
	public Source threadSafeCopy() throws IOException {
		return s.threadSafeCopy();
	}

	public void moveSelf(long from, long to, long length) throws IOException {
		invalidate(to, to + length);
		s.moveSelf(from, to, length);
	}

	@Override
	public boolean isBuffered() {
		return true;
	}

	public final void invalidate() {
		for (Iterator<MyHashMap.Entry<MutableInt, ByteList>> it = buffers.entryIterator(); it.hasNext(); ) {
			MyHashMap.Entry<MutableInt, ByteList> next = it.next();
			try {
				pool.reserve(next.v);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		buffers.clear();
	}

	private void invalidate(long from, long to) {
		int f = (int) (from>>>12);
		int t = (int) ((to+4095)>>>12);
		while (f < t) {
			val.setValue(f++);
			DynByteBuf buf = buffers.remove(val);
			if (buf != null) pool.reserve(buf);
		}
	}

	private final MutableInt val = new MutableInt();
	private ByteList buffer(long pos) throws IOException {
		val.setValue((int) (pos>>>12));
		ByteList buf = buffers.get(val);
		if (buf == null) {
			s.seek(pos & ~4095);
			sync |= 1;

			buf = (ByteList) pool.buffer(false, PAGE);
			try {
				int len = s.read(buf.list, buf.arrayOffset(), PAGE);
				buf.wIndex(len);
			} catch (Throwable e) {
				pool.reserve(buf);
				invalidate();
				throw e;
			}

			buffers.put(new MutableInt(val), buf);
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
	public String toString() {
		return "BufferedSource@" + pool;
	}

	@Override
	public void accept(MutableInt key,ByteList buffer) {
		pool.reserve(buffer);
	}
}
