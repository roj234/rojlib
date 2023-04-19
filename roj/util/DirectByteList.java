package roj.util;

import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.text.CharList;
import roj.text.TextUtil;
import sun.misc.Unsafe;
import sun.nio.ch.DirectBuffer;

import java.io.IOException;
import java.nio.*;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static roj.reflect.FieldAccessor.u;

/**
 * @author Roj234
 * @since 2021/5/29 20:45
 */
public class DirectByteList extends DynByteBuf {
	static final boolean BE_CPU = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;

	NativeMemory nm;
	volatile long address;
	int length;

	public static DirectByteList allocateDirect() {
		return new DirectByteList();
	}
	public static DirectByteList allocateDirect(int capacity) {
		return new DirectByteList(capacity);
	}
	public static DirectByteList allocateDirect(int capacity, int maxCapacity) {
		return new DirectByteList(capacity) {
			@Override
			public void ensureCapacity(int required) {
				if (required > maxCapacity) throw new IllegalArgumentException("Exceeds max capacity " + maxCapacity);
				super.ensureCapacity(required);
			}

			@Override
			public int maxCapacity() {
				return maxCapacity;
			}
		};
	}
	public static DirectByteList wrap(long address, int length) {
		return new DirectByteList.Slice(address, length);
	}

	DirectByteList() {
		nm = new NativeMemory();
	}

	DirectByteList(int cap) {
		nm = new NativeMemory();
		address = nm.allocate(cap);
		length = cap;
	}

	DirectByteList(boolean unused) {}

	public void _free() {
		clear();
		length = 0;

		address = 0;
		nm.release();
	}

	@Override
	public int capacity() {
		return length;
	}
	@Override
	public int maxCapacity() {
		return Integer.MAX_VALUE;
	}
	@Override
	public boolean immutableCapacity() {
		return nm == null;
	}
	@Override
	public final boolean isDirect() {
		return true;
	}
	@Override
	public final long address() {
		return address;
	}
	@Override
	public final boolean hasArray() {
		return false;
	}

	@Override
	public final void copyTo(long addr, int len) {
		copyToArray(addr, null, moveRI(len), address, length);
	}

	public final void clear() {
		wIndex = rIndex = 0;
	}

	public void ensureCapacity(int required) {
		if (length < required) {
			if (nm == null) throw new BufferOverflowException();

			required = length == 0 ? Math.max(required, 1024) : ((required * 3) >>> 1) + 1;
			if (!nm.expandInline(required)) {
				int len = wIndex;
				wIndex = 0;
				long prevAddr = this.address;
				address = 0;

				NativeMemory mem = new NativeMemory();
				long addr = mem.allocate(required);
				copyToArray(prevAddr, null, 0, addr, Math.min(required, len));

				if (wIndex != 0) throw new ConcurrentModificationException();
				wIndex = len;

				address = addr;
				nm = mem;
			}
			length = required;
		}
	}

	// region PUTxxx

	public final DirectByteList put(int e) {
		u.putByte(moveWI(1)+address, (byte) e);
		return this;
	}
	public final DirectByteList put(int i, int e) {
		u.putByte(testWI(i, 1)+address, (byte) e);
		return this;
	}

	public final DirectByteList put(byte[] b, int off, int len) {
		if (len < 0 || off < 0 || len > b.length - off) throw new ArrayIndexOutOfBoundsException();
		if (len > 0) {
			int off1 = moveWI(len);
			copyFromArray(b, Unsafe.ARRAY_BYTE_BASE_OFFSET, off, address + off1, len);
		}
		return this;
	}

	@Override
	public final DirectByteList put(DynByteBuf b, int off, int len) {
		if (off+len > b.wIndex) throw new IndexOutOfBoundsException();

		if (b.hasArray()) {
			put(b.array(), b.arrayOffset()+off, len);
		} else if (b.isDirect()) {
			if ((off|len) < 0) throw new IndexOutOfBoundsException();
			if (len == 0) return this;

			int wi = moveWI(len);
			copyToArray(b.address()+off, null, address, wi, len);
		} else {
			while (len-- > 0) put(b.get(off++));
		}

		return this;
	}

	public final DirectByteList putInt(int wi, int i) {
		long addr = testWI(wi, 4)+address;
		if (BE_CPU) {
			u.putInt(addr, i);
		} else {
			u.putByte(addr++, (byte) (i >>> 24));
			u.putByte(addr++, (byte) (i >>> 16));
			u.putByte(addr++, (byte) (i >>> 8));
			u.putByte(addr, (byte) i);
		}
		return this;
	}
	public final DirectByteList putIntLE(int wi, int i) {
		long addr = testWI(wi, 4)+address;
		if (!BE_CPU) {
			u.putInt(addr, i);
		} else {
			u.putByte(addr++, (byte) i);
			u.putByte(addr++, (byte) (i >>> 8));
			u.putByte(addr++, (byte) (i >>> 16));
			u.putByte(addr, (byte) (i >>> 24));
		}
		return this;
	}

	public final DirectByteList putLong(int wi, long l) {
		long addr = testWI(wi, 8)+address;
		if (BE_CPU) {
			u.putLong(addr, l);
		} else {
			u.putByte(addr++, (byte) (l >>> 56));
			u.putByte(addr++, (byte) (l >>> 48));
			u.putByte(addr++, (byte) (l >>> 40));
			u.putByte(addr++, (byte) (l >>> 32));
			u.putByte(addr++, (byte) (l >>> 24));
			u.putByte(addr++, (byte) (l >>> 16));
			u.putByte(addr++, (byte) (l >>> 8));
			u.putByte(addr, (byte) l);
		}
		return this;
	}
	public final DirectByteList putLongLE(int wi, long l) {
		long addr = testWI(wi, 8)+address;
		if (!BE_CPU) {
			u.putLong(addr, l);
		} else {
			u.putByte(addr++, (byte) l);
			u.putByte(addr++, (byte) (l >>> 8));
			u.putByte(addr++, (byte) (l >>> 16));
			u.putByte(addr++, (byte) (l >>> 24));
			u.putByte(addr++, (byte) (l >>> 32));
			u.putByte(addr++, (byte) (l >>> 40));
			u.putByte(addr++, (byte) (l >>> 48));
			u.putByte(addr, (byte) (l >>> 56));
		}
		return this;
	}

	public final DirectByteList putShort(int wi, int s) {
		long addr = testWI(wi, 2)+address;
		u.putByte(addr++, (byte) (s >>> 8));
		u.putByte(addr, (byte) s);
		return this;
	}
	public final DirectByteList putShortLE(int wi, int s) {
		long addr = testWI(wi, 2)+address;
		u.putByte(addr++, (byte) s);
		u.putByte(addr, (byte) (s >>> 8));
		return this;
	}

	public final DirectByteList putMedium(int wi, int m) {
		long addr = testWI(wi, 3)+address;
		u.putByte(addr++, (byte) (m >>> 16));
		u.putByte(addr++, (byte) (m >>> 8));
		u.putByte(addr, (byte) m);
		return this;
	}
	public final DirectByteList putMediumLE(int wi, int m) {
		long addr = testWI(wi, 3)+address;
		u.putByte(addr++, (byte) m);
		u.putByte(addr++, (byte) (m >>> 8));
		u.putByte(addr, (byte) (m >>> 16));
		return this;
	}

	public final DirectByteList putChars(int wi, CharSequence s) {
		long addr = testWI(wi, (s.length() << 1))+address;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			u.putByte(addr, (byte) (c >>> 8));
			u.putByte(addr, (byte) c);
			addr += 2;
		}
		return this;
	}
	public final DirectByteList putAscii(int wi, CharSequence s) {
		long addr = testWI(wi, s.length())+address;
		for (int i = 0; i < s.length(); i++) {
			u.putByte(addr++, (byte) s.charAt(i));
		}
		return this;
	}

	public final void _writeDioUTF(String s, int byteLen) {
		long addr = moveWI(byteLen)+address;
		ByteList.jutf8_encode_all(s, null, addr, byteLen);
	}

	public final DirectByteList putUTFData0(CharSequence s, int len) {
		long addr = moveWI(len)+address;
		ByteList.utf8mb4_encode_all(s, null, addr, len);
		return this;
	}

	public final DirectByteList putVStrData0(CharSequence s, int len) {
		long addr = moveWI(len)+address;

		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= 0x8080) {
				u.putByte(addr++, (byte) 0);
				u.putByte(addr++, (byte) (c >>> 8));
				u.putByte(addr++, (byte) c);
			} else if (c == 0 || c >= 0x80) {
				c -= 0x80;
				u.putByte(addr++, (byte) ((c >>> 8) | 0x80));
				u.putByte(addr++, (byte) c);
			} else {
				u.putByte(addr++, (byte) c);
			}
		}
		return this;
	}

	public final DirectByteList put(ByteBuffer buf) {
		int rem = buf.remaining();
		if (buf.isDirect()) {
			DirectBuffer db = (DirectBuffer) buf;
			u.copyMemory(db.address()+buf.position(), moveWI(rem)+address, rem);
		} else if (buf.hasArray()) {
			copyFromArray(buf.array(), Unsafe.ARRAY_BYTE_BASE_OFFSET, buf.arrayOffset() + buf.position(), moveWI(rem)+address, rem);
		} else {
			while (rem-- > 0) put(buf.get());
		}
		return this;
	}

	public final byte[] toByteArray() {
		byte[] b = new byte[wIndex-rIndex];
		copyToArray(address+rIndex, b, Unsafe.ARRAY_BYTE_BASE_OFFSET, 0, b.length);
		return b;
	}

	@Override
	public final void preInsert(int off, int len) {
		long tmp = address;
		NativeMemory nm1;
		if (length < wIndex + len) {
			if (immutableCapacity()) throw new BufferOverflowException();
			nm1 = new NativeMemory();
			tmp = nm1.allocate(length = wIndex + len);
		} else nm1 = null;

		if (wIndex != off) {
			if (address != tmp) u.copyMemory(address, tmp, off);
			u.copyMemory(address+off, tmp+off+len, wIndex-off);
		}
		wIndex += len;

		if (tmp != address) {
			//this.nm.release();
			this.nm = nm1;
			address = tmp;
		}
	}

	// endregion
	// region GETxxx

	public final void read(byte[] b, int off, int len) {
		if (len < 0 || off < 0 || len > b.length - off) throw new ArrayIndexOutOfBoundsException();
		if (len > 0) {
			copyToArray(address+moveRI(len), b, Unsafe.ARRAY_BYTE_BASE_OFFSET, off, len);
		}
	}
	public final void read(int i, byte[] b, int off, int len) {
		if (len < 0 || off < 0 || len > b.length - off) throw new ArrayIndexOutOfBoundsException();
		if (len > 0) {
			copyToArray(address+testWI(i, len), b, Unsafe.ARRAY_BYTE_BASE_OFFSET, off, len);
		}
	}

	public final byte get(int i) {
		return u.getByte(testWI(i, 1)+address);
	}
	public final byte readByte() {
		return u.getByte(moveRI(1)+address);
	}

	public final int readUnsignedShort(int i) {
		long addr = testWI(i, 2)+address;
		return ((u.getByte(addr++) & 0xFF) << 8) | (u.getByte(addr) & 0xFF);
	}
	public final int readUShortLE(int i) {
		long addr = testWI(i, 2)+address;
		return (u.getByte(addr++) & 0xFF) | (u.getByte(addr) & 0xFF) << 8;
	}

	public final int readMedium(int i) {
		long addr = testWI(i, 3)+address;
		return (u.getByte(addr++) & 0xFF) << 16 | (u.getByte(addr++) & 0xFF) << 8 | (u.getByte(addr) & 0xFF);
	}
	public final int readMediumLE(int i) {
		long addr = testWI(i, 3)+address;
		return (u.getByte(addr++) & 0xFF) | (u.getByte(addr++) & 0xFF) << 8 | (u.getByte(addr) & 0xFF) << 16;
	}

	public final int readVarInt(boolean mayNeg) {
		int value = 0;
		int i = 0;

		long off = address+rIndex;
		int r = wIndex-rIndex;

		while (i <= 28) {
			if (r-- <= 0) throw new BufferUnderflowException();

			int chunk = u.getByte(off++);
			value |= (chunk & 0x7F) << i;
			i += 7;
			if ((chunk & 0x80) == 0) {
				rIndex = (int) (off - address);
				if (mayNeg) return zag(value);
				if (value < 0) break;
				return value;
			}
		}

		throw new RuntimeException("VarInt format error near " + rIndex);
	}

	public final int readInt(int i) {
		long addr = testWI(i, 4)+address;
		if (BE_CPU) return u.getInt(addr);
		return (u.getByte(addr++) & 0xFF) << 24 | (u.getByte(addr++) & 0xFF) << 16 | (u.getByte(addr++) & 0xFF) << 8 | (u.getByte(addr) & 0xFF);
	}
	public final int readIntLE(int i) {
		long addr = testWI(i, 4)+address;
		if (!BE_CPU) return u.getInt(addr);
		return (u.getByte(addr++) & 0xFF) | (u.getByte(addr++) & 0xFF) << 8 | (u.getByte(addr++) & 0xFF) << 16 | (u.getByte(addr) & 0xFF) << 24;
	}

	public final long readLong(int i) {
		long addr = testWI(i, 8)+address;
		if (BE_CPU) return u.getLong(addr);
		return (u.getByte(addr++) & 0xFFL) << 56 |
			(u.getByte(addr++) & 0xFFL) << 48 |
			(u.getByte(addr++) & 0xFFL) << 40 |
			(u.getByte(addr++) & 0xFFL) << 32 |
			(u.getByte(addr++) & 0xFFL) << 24 |
			(u.getByte(addr++) & 0xFFL) << 16 |
			(u.getByte(addr++) & 0xFFL) << 8 |
			u.getByte(addr) & 0xFFL;
	}
	public final long readLongLE(int i) {
		long addr = testWI(i, 8)+address;
		if (!BE_CPU) return u.getLong(addr);
		return (u.getByte(addr++) & 0xFFL) |
			(u.getByte(addr++) & 0xFFL) << 8 |
			(u.getByte(addr++) & 0xFFL) << 16 |
			(u.getByte(addr++) & 0xFFL) << 24 |
			(u.getByte(addr++) & 0xFFL) << 32 |
			(u.getByte(addr++) & 0xFFL) << 40 |
			(u.getByte(addr++) & 0xFFL) << 48 |
			(u.getByte(addr) & 0xFFL) << 56;
	}

	public final String readAscii(int i, int len) {
		if (len <= 0) return "";

		long addr = testWI(i, len)+address;

		ArrayCache cache = ArrayCache.getDefaultCache();
		char[] ob = cache.getCharArray(Math.max(len/2,128), false);
		int j = 0;
		while (len-- > 0) ob[j++] = (char)u.getByte(addr++);

		String s = new String(ob, 0, j);
		cache.putArray(ob);
		return s;
	}
	public final String readUTF(int len) {
		if (len <= 0) return "";

		int ri = moveRI(len);

		CharList out = IOUtil.getSharedCharBuf();
		out.ensureCapacity(len);
		try {
			ByteList.utf8mb4_decode(null, address, ri, len, out, -1, false);
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return out.toString();
	}
	public final String readVStr(int len) {
		if (len <= 0) return "";

		long addr = moveRI(len)+address;

		ArrayCache cache = ArrayCache.getDefaultCache();
		char[] ob = cache.getCharArray(Math.max(len/2,128), false);
		int j = 0;

		while (len > 0) {
			if (j == ob.length) {
				char[] ob1 = cache.getCharArray(MathUtils.getMin2PowerOf(ob.length+3), false);
				System.arraycopy(ob, 0, ob1, 0, j);
				cache.putArray(ob);
				ob = ob1;
			}

			byte b = u.getByte(addr++);

			if ((b & 0x80) != 0) {
				ob[j++] = (char) ((((b & 0x7F) << 8) | (u.getByte(addr++) & 0xFF)) + 0x80);
				len -= 2;
			} else if (b == 0) {
				ob[j++] = (char) ((u.getByte(addr++) & 0xFF) << 8 | (u.getByte(addr++) & 0xFF));
				len -= 3;
			} else {
				ob[j++] = (char) b;
				len -= 1;
			}
		}

		if (len < 0) throw new IllegalArgumentException("在结尾处被截断");

		String s = new String(ob, 0, j);
		cache.putArray(ob);
		return s;
	}

	@Override
	@SuppressWarnings("deprecation")
	public final String readLine() {
		long l = address+rIndex;
		int r = wIndex-rIndex;
		if (r <= 0) return null;

		while (true) {
			if (r-- == 0) break;
			byte b = u.getByte(l++);
			if (b == '\r' || b == '\n') {
				if (b == '\r' && r>0 && u.getByte(l) == '\n') l++;
				break;
			}
		}
		byte[] tmp = new byte[(int)(l-address)-rIndex];
		copyToArray(address + rIndex, tmp, Unsafe.ARRAY_BYTE_BASE_OFFSET, 0, tmp.length);
		rIndex += tmp.length;
		return new String(tmp, 0);
	}

	public final int readZeroTerminate() {
		long i = address+rIndex;
		int r = wIndex-rIndex;
		while (r-- > 0) {
			if (u.getByte(i) == 0) return (int) (i-address)-rIndex;
			i++;
		}
		return -1;
	}

	public final void forEachByte(IntUnaryOperator operator) {
		long i = address+rIndex;
		long e = address+wIndex;
		while (i < e) {
			int v = operator.applyAsInt(u.getByte(i));
			u.putByte(i, (byte) v);

			i++;
		}
	}

	// endregion
	// region Buffer Ops

	public final DirectByteList slice(int len) {
		return slice(moveRI(len), len);
	}
	public final DirectByteList slice(int off, int len) {
		return new Slice(testWI(off, len)+address, len);
	}

	@Override
	public final DirectByteList compact() {
		if (rIndex > 0) {
			long addr = address;
			if (addr != 0) {
				if (wIndex < rIndex) {
					rIndex = 0;
					System.out.println("data = " + TextUtil.dumpBytes(toByteArray()));
					new Throwable("windex < rindex").printStackTrace();
					wIndex = rIndex = 0;
					return this;
				}
				u.copyMemory(addr+rIndex, addr, wIndex-rIndex);
			}
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
		return (ByteBuffer) NativeMemory.newDirectBuffer(address, length, nm).limit(wIndex).position(rIndex);
	}

	@Override
	public final void nioBuffers(List<ByteBuffer> buffers) {
		buffers.add(nioBuffer());
	}

	@Override
	public String dump() {
		return "DirectBuffer:"+TextUtil.dumpBytes(toByteArray(), 0, readableBytes());
	}

	// endregion

	static void copyFromArray(Object src, long srcBaseOffset, long srcPos, long dstAddr, long length) {
		if ((srcPos|srcBaseOffset|dstAddr) < 0) throw new IllegalArgumentException("Some param(s) < 0");

		long offset = srcBaseOffset + srcPos;
		while (length > 0) {
			long size = (length > 1048576) ? 1048576 : length;
			u.copyMemory(src, offset, null, dstAddr, size);
			length -= size;
			offset += size;
			dstAddr += size;
		}
	}
	static void copyToArray(long srcAddr, Object dst, long dstBaseOffset, long dstPos, long length) {
		if ((srcAddr|dstBaseOffset|dstPos) < 0) throw new IllegalArgumentException("Some param(s) < 0");

		long offset = dstBaseOffset + dstPos;
		while (length > 0) {
			long size = (length > 1048576) ? 1048576 : length;
			u.copyMemory(null, srcAddr, dst, offset, size);
			length -= size;
			srcAddr += size;
			offset += size;
		}
	}

	@Override
	public final boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DirectByteList list = (DirectByteList) o;

		if (address != list.address) return false;
		return length == list.length;
	}

	@Override
	public int hashCode() {
		int result = (int) (address ^ (address >>> 32));
		result = 31 * result + length;
		return result;
	}

	@Override
	public String toString() {
		byte[] tmp = new byte[readableBytes()];
		copyToArray(address+rIndex, tmp, Unsafe.ARRAY_BYTE_BASE_OFFSET, 0, tmp.length);
		return "DBL{" + TextUtil.dumpBytes(tmp) + "}";
	}

	public static final class Slice extends DirectByteList {
		public Slice() {
			super(false);
		}

		public Slice(long addr, int len) {
			super(false);
			this.wIndex = len;
			address = addr;
			length = len;
		}

		public DirectByteList set(NativeMemory mem, long addr, int len) {
			rIndex = wIndex = 0;
			address = addr;
			length = len;
			nm = mem;
			return this;
		}

		public void update(long addr, int len) {
			address = addr;
			length = len;
		}

		public DirectByteList copy(DynByteBuf buf) {
			address = buf.address();
			rIndex = buf.rIndex;
			wIndex = buf.wIndex;
			length = buf.wIndex;

			if (buf instanceof DirectByteList) nm = ((DirectByteList) buf).nm;
			return this;
		}

		@Override
		public int capacity() {
			return length;
		}

		@Override
		public int maxCapacity() {
			return length;
		}

		@Override
		public void ensureCapacity(int required) {
			if (required > length) throw new ReadOnlyBufferException();
		}

		@Override
		public boolean immutableCapacity() {
			return true;
		}
	}
}