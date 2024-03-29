package roj.io.source;

import roj.RequireTest;
import roj.reflect.FieldAccessor;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj233
 * @since 2021/8/18 13:36
 */
public class MemorySource extends Source {
	private final DynByteBuf list;
	private int cap;

	public MemorySource() { list = new ByteList(); }
	public MemorySource(byte[] arr) { list = ByteList.wrap(arr); cap = arr.length; }
	public MemorySource(DynByteBuf bytes) { list = bytes; cap = bytes.readableBytes(); }

	public int read() { return list.isReadable() ? list.readUnsignedByte() : -1; }
	public int read(byte[] b, int off, int len) {
		len = Math.min(len, list.readableBytes());
		list.read(b, off, len);
		return len;
	}

	public void write(byte[] b, int off, int len) throws IOException {
		list.wIndex(list.rIndex);
		list.put(b, off, len);
		cap = Math.max(list.rIndex = list.wIndex(), cap);
		list.wIndex(cap);
	}
	public void write(DynByteBuf data) throws IOException {
		list.wIndex(list.rIndex);
		list.put(data);
		cap = Math.max(list.rIndex = list.wIndex(), cap);
		list.wIndex(cap);
	}

	public void seek(long pos) { list.rIndex = (int) pos; }
	public long position() { return list.rIndex; }

	public void setLength(long length) throws IOException {
		if (length < 0 || length > Integer.MAX_VALUE - 16) throw new IOException();
		list.ensureCapacity(cap = (int) length);
		list.wIndex(cap);
	}
	public long length() { return cap; }

	public void reopen() {}

	public DataInput asDataInput() { return list; }
	public InputStream asInputStream() { return list.asInputStream(); }

	public Source threadSafeCopy() { return new MemorySource(asReadonly()); }

	@Override
	@RequireTest("direct memory copying maybe not stable if region collisions")
	public void moveSelf(long from, long to, long length) {
		if ((from | to | length) < 0) throw new IllegalArgumentException();
		FieldAccessor.u.copyMemory(list.array(), list._unsafeAddr()+from, list.array(), list._unsafeAddr()+to, length);
	}

	@Override
	public boolean isBuffered() { return true; }

	public DynByteBuf asReadonly() { return list.slice(0, cap); }
	public DynByteBuf buffer() { list.wIndex(cap); list.rIndex = 0; return list; }

	@Override
	public String toString() {
		return "MemorySource@" + System.identityHashCode(list);
	}
}
