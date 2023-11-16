package roj.util;

import roj.io.buf.BufferPool;
import roj.lavac.api.Constant;
import roj.text.TextUtil;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2021/5/29 20:45
 */
public class ByteList extends DynByteBuf implements Appendable {
	static final boolean USE_CACHE = true;
	public static final ByteList EMPTY = new Slice(ArrayCache.BYTES, 0, 0);

	public byte[] list;

	public static ByteList wrap(byte[] b) {
		return new ByteList(b);
	}
	public static ByteList wrap(byte[] b, int off, int len) {
		return new ByteList.Slice(b, off, len);
	}

	public static ByteList wrapWrite(byte[] b) {
		return wrapWrite(b, 0, b.length);
	}
	public static ByteList wrapWrite(byte[] b, int off, int len) {
		ByteList bl = new ByteList.Slice(b, off, len);
		bl.wIndex = 0;
		return bl;
	}

	public static ByteList allocate(int cap) { return new ByteList(cap); }
	public static ByteList allocate(int capacity, int maxCapacity) {
		return new ByteList(capacity) {
			@Override
			public void ensureCapacity(int required) {
				if (required > maxCapacity) throw new IllegalArgumentException("Exceeds max capacity " + maxCapacity);
				super.ensureCapacity(required);
			}

			@Override
			public int maxCapacity() { return maxCapacity; }
		};
	}

	public ByteList() { list = ArrayCache.BYTES; }
	public ByteList(int len) { list = new byte[len]; }

	public ByteList(byte[] array) {
		list = array;
		wIndex = array.length;
	}

	public synchronized void _free() {
		clear();
		byte[] b = list;
		list = ArrayCache.BYTES;
		ArrayCache.putArray(b);
	}

	public int capacity() { return list.length; }
	public int maxCapacity() { return 2147000000; }
	public final boolean isDirect() { return false; }
	public long _unsafeAddr() { return Unsafe.ARRAY_BYTE_BASE_OFFSET+arrayOffset(); }
	public boolean hasArray() { return true; }
	public byte[] array() { return list; }
	public int arrayOffset() { return 0; }

	@Override
	public final void copyTo(long address, int len) {
		DirectByteList.copyFromArray(list, Unsafe.ARRAY_BYTE_BASE_OFFSET, arrayOffset() + moveRI(len), address, len);
	}

	public void clear() {
		wIndex = rIndex = 0;
	}

	public void ensureCapacity(int required) {
		if (required > list.length) {
			int newLen = list.length == 0 ? Math.max(required, 256) : ((required * 3) >>> 1) + 1;
			if (newLen == 1073741823 || newLen > maxCapacity()) newLen = maxCapacity();
			if (newLen <= list.length) throw new IndexOutOfBoundsException("cannot hold "+required+"bytes in this buffer("+list.length+")");

			byte[] newList;
			if (USE_CACHE) {
				ArrayCache.putArray(list);
				newList = ArrayCache.getByteArray(newLen, false);
			} else {
				newList = new byte[newLen];
			}

			if (wIndex > 0) System.arraycopy(list, 0, newList, 0, wIndex);
			list = newList;
		}
	}

	public ByteList setArray(byte[] array) {
		if (array == null) array = ArrayCache.BYTES;

		list = array;
		clear();
		wIndex = array.length;
		return this;
	}

	@Override
	final int testWI(int i, int req) {
		if (i < 0 || i + req > wIndex) throw new ArrayIndexOutOfBoundsException("pos="+i+",len="+req+",cap="+wIndex);
		return i + arrayOffset();
	}

	// region Byte Streams

	public final ByteList readStreamFully(InputStream in) throws IOException {
		return readStreamFully(in, true);
	}

	public final ByteList readStreamFully(InputStream in, boolean close) throws IOException {
		while (true) {
			if (wIndex == capacity())
				ensureCapacity(wIndex + Math.max(1, in.available()));

			int r = in.read(list, arrayOffset()+wIndex, capacity()-wIndex);
			if (r < 0) break;
			wIndex += r;
		}

		if (close) in.close();
		return this;
	}

	public final int readStream(InputStream in, int max) throws IOException {
		int read = wIndex;
		while (max > 0) {
			if (wIndex == capacity()) ensureCapacity(wIndex+max);

			int r = in.read(list, arrayOffset()+wIndex, Math.min(capacity()-wIndex, max));
			if (r < 0) break;
			wIndex += r;
			max -= r;
		}
		return wIndex-read;
	}

	public final void writeToStream(OutputStream os) throws IOException {
		int w = readableBytes();
		if (w > 0) os.write(list, arrayOffset()+rIndex, w);
	}

	// endregion
	// region PUTxxx

	public final ByteList put(int e) {
		int i = arrayOffset() + moveWI(1);
		list[i] = (byte) e;
		return this;
	}

	public final ByteList put(int i, int e) {
		list[testWI(i, 1)] = (byte) e;
		return this;
	}

	public final ByteList put(byte[] b, int off, int len) {
		if (len < 0 || off < 0 || len > b.length - off) throw new ArrayIndexOutOfBoundsException();
		if (len > 0) {
			int off1 = arrayOffset() + moveWI(len);
			System.arraycopy(b, off, list, off1, len);
		}
		return this;
	}

	@Override
	public final ByteList put(DynByteBuf b, int len) {
		return put(b, b.rIndex, len);
	}

	@Override
	public final ByteList put(DynByteBuf b, int off, int len) {
		if (off+len > b.wIndex) throw new IndexOutOfBoundsException();
		ensureCapacity(wIndex+len);
		b.read(off, list, wIndex+arrayOffset(), len);
		wIndex += len;
		return this;
	}

	public final ByteList putVarInt(int i, boolean canBeNegative) {
		putVarLong(this, canBeNegative ? zig(i) : i);
		return this;
	}

	public final ByteList putIntLE(int wi, int i) {
		wi = testWI(wi, 4);
		byte[] list = this.list;
		list[wi++] = (byte) i;
		list[wi++] = (byte) (i >>> 8);
		list[wi++] = (byte) (i >>> 16);
		list[wi] = (byte) (i >>> 24);
		return this;
	}

	public final ByteList putInt(int i) {
		return putInt(moveWI(4), i);
	}
	public final ByteList putInt(int wi, int i) {
		wi = testWI(wi, 4);
		byte[] list = this.list;
		list[wi++] = (byte) (i >>> 24);
		list[wi++] = (byte) (i >>> 16);
		list[wi++] = (byte) (i >>> 8);
		list[wi] = (byte) i;
		return this;
	}

	public final ByteList putLongLE(int wi, long l) {
		wi = testWI(wi, 8);
		byte[] list = this.list;
		list[wi++] = (byte) l;
		list[wi++] = (byte) (l >>> 8);
		list[wi++] = (byte) (l >>> 16);
		list[wi++] = (byte) (l >>> 24);
		list[wi++] = (byte) (l >>> 32);
		list[wi++] = (byte) (l >>> 40);
		list[wi++] = (byte) (l >>> 48);
		list[wi] = (byte) (l >>> 56);
		return this;
	}

	public final ByteList putLong(int wi, long l) {
		wi = testWI(wi, 8);
		byte[] list = this.list;
		list[wi++] = (byte) (l >>> 56);
		list[wi++] = (byte) (l >>> 48);
		list[wi++] = (byte) (l >>> 40);
		list[wi++] = (byte) (l >>> 32);
		list[wi++] = (byte) (l >>> 24);
		list[wi++] = (byte) (l >>> 16);
		list[wi++] = (byte) (l >>> 8);
		list[wi] = (byte) l;
		return this;
	}

	public final ByteList putShort(int s) {
		return putShort(moveWI(2), s);
	}
	public final ByteList putShort(int wi, int s) {
		wi = testWI(wi, 2);
		byte[] list = this.list;
		list[wi++] = (byte) (s >>> 8);
		list[wi] = (byte) s;
		return this;
	}

	public final ByteList putShortLE(int wi, int s) {
		wi = testWI(wi, 2);
		byte[] list = this.list;
		list[wi++] = (byte) s;
		list[wi] = (byte) (s >>> 8);
		return this;
	}

	public final ByteList putMedium(int wi, int m) {
		wi = testWI(wi, 3);
		byte[] list = this.list;
		list[wi++] = (byte) (m >>> 16);
		list[wi++] = (byte) (m >>> 8);
		list[wi] = (byte) m;
		return this;
	}

	public final ByteList putMediumLE(int wi, int m) {
		wi = testWI(wi, 3);
		byte[] list = this.list;
		list[wi++] = (byte) m;
		list[wi++] = (byte) (m >>> 8);
		list[wi] = (byte) (m >>> 16);
		return this;
	}

	public final ByteList putChars(int wi, CharSequence s) {
		wi = testWI(wi, s.length() << 1);
		byte[] list = this.list;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			list[wi++] = (byte) (c >>> 8);
			list[wi++] = (byte) c;
		}
		return this;
	}

	@SuppressWarnings("deprecation")
	final void _writeDioUTF(String s, int byteLen) {
		int wi = moveWI(byteLen);

		// better on Java 9 and later
		if (byteLen == s.length()) {
			s.getBytes(0,byteLen,list,wi);
			return;
		}

		jutf8_encode_all(s, list, _unsafeAddr()+wi);
	}

	public final ByteList putUTFData(CharSequence s) { return (ByteList) super.putUTFData(s); }

	public final ByteList putAscii(CharSequence s) { return putAscii(moveWI(s.length()), s); }
	@SuppressWarnings("deprecation")
	public final ByteList putAscii(int wi, CharSequence s) {
		wi = testWI(wi, s.length());
		if (s.getClass() == String.class) {
			s.toString().getBytes(0, s.length(), list, wi);
		} else {
			byte[] list = this.list;
			for (int i = 0; i < s.length(); i++) {
				list[wi++] = (byte) s.charAt(i);
			}
		}
		return this;
	}

	public final Appendable append(CharSequence s) { return append(s,0, s.length()); }
	@SuppressWarnings("deprecation")
	public Appendable append(CharSequence s, int start, int end) {
		int wi = end-start;
		if ((wi|start|(s.length()-end)) < 0) throw new IndexOutOfBoundsException();
		wi = moveWI(wi);
		if (s.getClass() == String.class) {
			s.toString().getBytes(start, end, list, wi);
		} else {
			byte[] list = this.list;
			while (start < end) {
				list[wi++] = (byte) s.charAt(start++);
			}
		}
		return this;
	}
	public Appendable append(char c) { return put((byte) c); }

	public final ByteList put(ByteBuffer buf) {
		int rem = buf.remaining();
		int v = moveWI(rem);
		buf.get(list, v, rem);
		return this;
	}

	public byte[] toByteArray() {
		byte[] b = new byte[wIndex - rIndex];
		System.arraycopy(list, arrayOffset() + rIndex, b, 0, b.length);
		return b;
	}

	@Override
	public void preInsert(int off, int len) {
		byte[] tmp = list;
		if (tmp.length < wIndex+len) {
			if (immutableCapacity()) throw new BufferOverflowException();
			tmp = ArrayCache.getByteArray(wIndex+len, false);
		}

		if (wIndex != off) {
			if (list != tmp) System.arraycopy(list, 0, tmp, 0, off);

			System.arraycopy(list, off, tmp, off+len, wIndex-off);
		}
		wIndex += len;
		list = tmp;
	}

	// endregion
	// region GETxxx

	public final void read(byte[] b, int off, int len) {
		ArrayUtil.checkRange(b, off, len);
		if (len > 0) System.arraycopy(list, moveRI(len) + arrayOffset(), b, off, len);
	}

	public final void read(int i, byte[] b, int off, int len) {
		ArrayUtil.checkRange(b, off, len);
		if (len > 0) System.arraycopy(list, testWI(i, len), b, off, len);
	}

	public final byte get(int i) { return list[testWI(i, 1)]; }
	@Override
	public final byte readByte() { return list[moveRI(1) + arrayOffset()]; }

	public final int readUnsignedShort(int i) {
		i = testWI(i, 2);
		byte[] l = this.list;
		return (l[i++] & 0xFF) << 8 | (l[i] & 0xFF);
	}

	public final int readUShortLE(int i) {
		i = testWI(i, 2);
		byte[] l = this.list;
		return (l[i++] & 0xFF) | (l[i] & 0xFF) << 8;
	}

	public final int readMedium(int i) {
		i = testWI(i, 3);
		byte[] l = this.list;
		return (l[i++] & 0xFF) << 16 | (l[i++] & 0xFF) << 8 | (l[i] & 0xFF);
	}

	public final int readMediumLE(int i) {
		i = testWI(i, 3);
		byte[] l = this.list;
		return (l[i++] & 0xFF)| (l[i++] & 0xFF) << 8 | (l[i] & 0xFF) << 16;
	}

	public final int readVarInt(boolean mayNeg) {
		int value = 0;
		int i = 0;

		byte[] list = this.list;
		int off = arrayOffset() + rIndex;
		int len = arrayOffset() + wIndex;
		while (i <= 28) {
			if (off >= len) throw new ArrayIndexOutOfBoundsException();

			int chunk = list[off++];
			rIndex++;
			value |= (chunk & 0x7F) << i;
			i += 7;
			if ((chunk & 0x80) == 0) {
				if (mayNeg) return zag(value);
				if (value < 0) break;
				return value;
			}
		}

		throw new RuntimeException("VarInt format error near " + rIndex);
	}

	public final int readInt(int i) {
		i = testWI(i, 4);
		byte[] l = this.list;
		return (l[i++] & 0xFF) << 24 | (l[i++] & 0xFF) << 16 | (l[i++] & 0xFF) << 8 | (l[i] & 0xFF);
	}

	public final int readIntLE(int i) {
		i = testWI(i, 4);
		byte[] l = this.list;
		return (l[i++] & 0xFF) | (l[i++] & 0xFF) << 8 | (l[i++] & 0xFF) << 16 | (l[i] & 0xFF) << 24;
	}

	public final long readLong(int i) {
		i = testWI(i, 8);
		byte[] l = this.list;
		return (l[i++] & 0xFFL) << 56 |
			(l[i++] & 0xFFL) << 48 |
			(l[i++] & 0xFFL) << 40 |
			(l[i++] & 0xFFL) << 32 |
			(l[i++] & 0xFFL) << 24 |
			(l[i++] & 0xFFL) << 16 |
			(l[i++] & 0xFFL) << 8 |
			l[i] & 0xFFL;
	}

	public final long readLongLE(int i) {
		i = testWI(i, 8);
		byte[] l = this.list;
		return (l[i++] & 0xFFL) |
			(l[i++] & 0xFFL) << 8 |
			(l[i++] & 0xFFL) << 16 |
			(l[i++] & 0xFFL) << 24 |
			(l[i++] & 0xFFL) << 32 |
			(l[i++] & 0xFFL) << 40 |
			(l[i++] & 0xFFL) << 48 |
			(l[i] & 0xFFL) << 56;
	}

	@SuppressWarnings("deprecation")
	public final String readAscii(int i, int len) {
		return new String(list, 0, testWI(i, len), len);
	}

	@Override
	@SuppressWarnings("deprecation")
	public final String readLine() {
		int i = rIndex + arrayOffset();
		int len = wIndex + arrayOffset();
		byte[] l = list;
		while (true) {
			if (i >= len) throw new ArrayIndexOutOfBoundsException();
			byte b = l[i++];
			if (b == '\r' || b == '\n') {
				if (b == '\r' && i < wIndex && l[i] == '\n') i++;
				break;
			}
		}
		String s = new String(l, 0, rIndex, i - rIndex);
		rIndex = i - arrayOffset();
		return s;
	}

	public final int readZeroTerminate(int b) {
		int i = rIndex + arrayOffset();
		int end = wIndex + arrayOffset();
		byte[] l = list;
		while (i < end) {
			if (l[i] == 0) return i - arrayOffset() - rIndex;
			i++;
		}
		return -1;
	}

	public final void forEachByte(IntUnaryOperator operator) {
		byte[] b = list;
		int i = rIndex + arrayOffset();
		int e = wIndex + arrayOffset();
		while (i < e) {
			int v = operator.applyAsInt(b[i]);
			b[i] = (byte) v;

			i++;
		}
	}

	// endregion
	// region Buffer Ops

	public final ByteList slice(int length) {
		if (length == 0) return EMPTY;
		ByteList list = slice(rIndex, length);
		rIndex += length;
		return list;
	}
	public final ByteList slice(int off, int len) {
		return new Slice(list, off + arrayOffset(), len);
	}

	@Override
	public final ByteList compact() {
		if (rIndex > 0) {
			System.arraycopy(list, arrayOffset() + rIndex, list, arrayOffset(), wIndex - rIndex);
			wIndex -= rIndex;
			rIndex = 0;
		}
		return this;
	}

	@Override
	public final int nioBufferCount() {
		return 1;
	}

	@Override
	public final ByteBuffer nioBuffer() {
		return NativeMemory.newHeapBuffer(list, -1, rIndex, wIndex, capacity(), arrayOffset());
	}

	@Override
	public final void nioBuffers(List<ByteBuffer> buffers) {
		buffers.add(nioBuffer());
	}

	@Override
	public String dump() {
		return "HeapBuffer:"+TextUtil.dumpBytes(list, arrayOffset()+rIndex, wIndex-rIndex);
	}

	// endregion

	@Constant
	public static int FOURCC(CharSequence x) { return ((x.charAt(0) & 0xFF) << 24) | ((x.charAt(1) & 0xFF) << 16) | ((x.charAt(2) & 0xFF) << 8) | ((x.charAt(3) & 0xFF)); }
	@Constant
	public static String UNFOURCC(int fc) { return new String(new char[]{(char) (fc >>> 24), (char) ((fc >>> 16) & 0xFF), (char) ((fc >>> 8) & 0xFF), (char) (fc & 0xFF)}); }

	static void jutf8_encode_all(String s, Object ref, long addr) {
		int i = 0, len = s.length();
		while (i < len) {
			int c = s.charAt(i++);

			if (c == 0 || c > 0x7F) {
				if (c <= 0x7FF) {
					u.putByte(ref, addr++, (byte) (0xC0 | ((c >> 6) & 0x1F)));
					u.putByte(ref, addr++, (byte) (0x80 | (c & 0x3F)));
				} else {
					u.putByte(ref, addr++, (byte) (0xE0 | ((c >> 12) & 0x0F)));
					u.putByte(ref, addr++, (byte) (0x80 | ((c >> 6) & 0x3F)));
					u.putByte(ref, addr++, (byte) (0x80 | (c & 0x3F)));
				}
			} else {
				u.putByte(ref, addr++, (byte) c);
			}
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ByteList)) return false;
		ByteList ot = (ByteList) o;
		return ArrayUtil.rangedEquals(list, arrayOffset() + rIndex, readableBytes(), ot.list, ot.arrayOffset() + ot.rIndex, ot.readableBytes());
	}

	@Override
	public int hashCode() { return ArrayUtil.rangedHashCode(list, arrayOffset() + rIndex, wIndex); }

	@Override
	public String toString() { return new String(list, 0, arrayOffset()+rIndex, wIndex-rIndex); }

	public static class WriteOut extends ByteList {
		private OutputStream out;
		private int fakeWriteIndex;

		private DynByteBuf buf;

		public WriteOut(OutputStream out) { this(out, 1024); }
		public WriteOut(OutputStream out, int buffer) {
			super();
			this.out = out;
			this.buf = BufferPool.buffer(false, buffer);
			this.list = buf.array();
		}

		public final void setOut(OutputStream out) { this.out = out; }

		@Override
		public int wIndex() {
			flush();
			return fakeWriteIndex;
		}
		protected final int realWIndex() { return wIndex; }
		public void wIndex(int w) { throw a(); }

		public final boolean hasBuffer() { return false; }
		public final int arrayOffset() { return buf.arrayOffset(); }
		public final int capacity() { return buf.capacity(); }
		public final int maxCapacity() { return buf.maxCapacity(); }

		@Override
		public void ensureCapacity(int cap) {
			if (cap > buf.capacity()) {
				cap -= wIndex;
				flush();

				if (wIndex+cap > buf.capacity()) {
					buf = BufferPool.expand(buf, cap);
					list = buf.array();
				}
			}
		}

		final int moveWI(int i) {
			ensureCapacity(wIndex+i);
			int j = wIndex;
			wIndex = j+i;
			return j;
		}
		final int moveRI(int i) { throw a(); }

		public final boolean hasArray() { return false; }
		public final byte[] array() { throw a(); }
		public final ByteList setArray(byte[] array) { throw a(); }

		public void flush() {
			if (wIndex > 0) {
				if (out != null) {
					try {
						out.write(list, arrayOffset(), wIndex);
					} catch (IOException e) {
						Helpers.athrow(e);
					}
				}
				fakeWriteIndex += wIndex;
				wIndex = 0;
			}
		}

		public synchronized void close() throws IOException {
			try {
				if (out != null) {
					try {
						flush();
					} finally {
						out.close();
						out = null;
					}
				}
			} finally {
				if (buf != null) {
					BufferPool.reserve(buf);
					buf = null;
				}
			}

		}

		private static UnsupportedOperationException a() { return new UnsupportedOperationException("stream buffer is not readable"); }
	}

	public static class Slice extends ByteList {
		private int off, len;

		public Slice() {}
		public Slice(byte[] b, int off, int len) { setR(b, off, len); }

		public ByteList set(byte[] b, int off, int len) {
			assert this != EMPTY;
			ArrayUtil.checkRange(b, off, len);

			wIndex = rIndex = 0;

			list = b;
			this.off = off;
			this.len = len;

			return this;
		}

		public ByteList setR(byte[] array, int off, int len) {
			set(array,off,len).wIndex = len;
			return this;
		}

		public void _expand(int len, boolean backward) {
			if (backward) {
				off -= len;
				wIndex += len;
			}
			this.len += len;
		}

		public ByteList copy(DynByteBuf src) {
			list = src.array();
			rIndex = src.rIndex;
			wIndex = src.wIndex;
			off = src.arrayOffset();
			len = src.wIndex();
			return this;
		}

		@Override
		public int capacity() { return len; }
		@Override
		public int maxCapacity() { return len; }
		@Override
		public boolean immutableCapacity() { return true; }
		@Override
		public void ensureCapacity(int required) {
			if (required > len) throw new IndexOutOfBoundsException("cannot hold "+required+"bytes in this buffer("+len+")");
		}

		@Override
		public int arrayOffset() { return off; }

		@Override
		public ByteList setArray(byte[] array) { throw new ReadOnlyBufferException(); }
		@Override
		public void preInsert(int off, int len) { throw new ReadOnlyBufferException(); }
	}
}