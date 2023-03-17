package roj.io.source;

import roj.RequireTest;
import roj.reflect.FieldAccessor;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj233
 * @since 2021/8/18 13:36
 */
public class MemorySource extends Source {
	private final DynByteBuf list;
	private int cap;

	public MemorySource() {
		list = new ByteList();
	}

	public MemorySource(byte[] arr) {
		list = ByteList.wrap(arr);
	}

	public MemorySource(DynByteBuf bytes) {
		list = bytes;
	}

	public int read() {
		int pos = list.wIndex();
		if (pos < cap) {
			list.wIndex(pos+1);
			return list.getU(pos);
		}
		return -1;
	}
	public int read(byte[] b, int off, int len) {
		if (len < 0) throw new ArrayIndexOutOfBoundsException();
		if (len == 0) return 0;

		int pos = list.wIndex();
		len = Math.min(len, cap-pos);
		if (len <= 0) return -1;

		list.read(pos, b, off, len);
		list.wIndex(pos+len);
		return len;
	}

	public void write(byte[] b, int off, int len) throws IOException {
		list.put(b, off, len);
		cap = Math.max(list.wIndex(), cap);
	}
	public void write(DynByteBuf data) throws IOException {
		list.put(data);
		cap = Math.max(list.wIndex(), cap);
	}

	public void seek(long pos) { list.wIndex((int) pos); }
	public long position() { return list.wIndex(); }

	public void setLength(long length) throws IOException {
		if (length < 0 || length > Integer.MAX_VALUE - 16) throw new IOException();
		list.ensureCapacity(cap = (int) length);
	}
	public long length() {
		return cap;
	}

	public void reopen() {}

	public DataInput asDataInput() { return list; }
	public DataOutput asDataOutput() { return list; }
	public InputStream asInputStream() { return list.asInputStream(); }

	public Source threadSafeCopy() { return new MemorySource(asReadonly()); }

	@Override
	@RequireTest("direct memory copying maybe not stable if region collisions")
	public void moveSelf(long from, long to, long length) {
		if ((from | to | length) < 0) throw new IllegalArgumentException();
		if (list.hasArray()) {
			System.arraycopy(list.array(), (int) (list.arrayOffset()+from), list.array(), (int) (list.arrayOffset()+to), (int) length);
		} else if (list.isDirect()) {
			FieldAccessor.u.copyMemory(list.address()+from, list.address()+to, length);
		} else {
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public boolean isBuffered() {
		return true;
	}

	public DynByteBuf asReadonly() {
		return list.slice(0, list.wIndex());
	}
	public DynByteBuf buffer() {
		return list;
	}

	@Override
	public String toString() {
		return "MemorySource@" + System.identityHashCode(list);
	}
}
