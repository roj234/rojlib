package roj.util;

import org.jetbrains.annotations.NotNull;
import roj.io.UnsafeOutputStream;
import roj.math.MathUtils;
import roj.text.CharList;
import roj.text.TextUtil;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ConcurrentModificationException;
import java.util.function.IntUnaryOperator;

import static roj.reflect.ReflectionUtils.u;
import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2021/5/29 20:45
 */
public class DirectByteList extends DynByteBuf {
	private static final boolean SAFER_BUT_NOT_TOTALLY_SAFE_TS_EXPAND = true;

	NativeMemory nm;
	volatile long address;
	int length;

	DirectByteList(boolean unused) {}
	DirectByteList() { nm = new NativeMemory(); }
	DirectByteList(int cap) {
		nm = new NativeMemory();
		address = nm.allocate(cap);
		length = cap;
	}

	public synchronized void _free() {
		clear();
		length = 0;

		address = 0;
		if (nm != null) {
			nm.release();
			nm = null;
		}
	}

	public NativeMemory memory() { return nm; }

	public int capacity() { return length; }
	public int maxCapacity() { return nm == null ? length : Integer.MAX_VALUE; }
	public final boolean isDirect() { return true; }
	public final long address() { return address; }
	public final long _unsafeAddr() { return address; }
	public final boolean hasArray() { return false; }

	@Override
	public final void copyTo(long addr, int len) {
		copyToArray(addr, null, preRead(len), address, length);
	}

	public final void clear() {
		wIndex = rIndex = 0;
	}

	public void ensureCapacity(int required) {
		if (length < required) {
			int newLen = Math.max(MathUtils.getMin2PowerOf(required), 1024);
			if (newLen > 1073741823 || newLen > maxCapacity()) newLen = maxCapacity();
			if (nm == null || newLen <= length) throw new IndexOutOfBoundsException("cannot hold "+required+" bytes in this buffer("+(nm==null&&length==0?"NaN":length)+"/"+newLen+")");

			if (SAFER_BUT_NOT_TOTALLY_SAFE_TS_EXPAND) {
				int len = wIndex;
				wIndex = 0;
				NativeMemory prevNM = nm;
				long prevAddr = address;

				NativeMemory mem = new NativeMemory();
				long addr = mem.allocate(newLen);
				u.copyMemory(prevAddr, addr, Math.min(newLen, len));

				if (wIndex != 0) {
					mem.release();
					throw new ConcurrentModificationException();
				}
				wIndex = len;

				address = addr;
				nm = mem;

				prevNM.release();
			} else {
				address = nm.resize(newLen);
			}
			length = newLen;
		}
	}

	@Override
	public void writeToStream(OutputStream out) throws IOException {
		int len = readableBytes();
		if (len <= 0) return;

		if (out instanceof UnsafeOutputStream) {
			((UnsafeOutputStream) out).write0(null, address, len);
			return;
		}

		byte[] array = ArrayCache.getByteArray(Math.min(len, 4096), false);
		try {
			long addr = address;
			while (len > 0) {
				int w = Math.min(len, array.length);
				u.copyMemory(null, addr, array, Unsafe.ARRAY_BYTE_BASE_OFFSET, w);
				out.write(array, 0, w);

				addr += w;
				len -= w;
			}
		} finally {
			ArrayCache.putArray(array);
		}
	}

	// region PUTxxx
	public final DynByteBuf put(int x) {
		u.putByte(preWrite(1)+address, (byte) x);
		return this;
	}
	public final DynByteBuf put(int i, int x) {
		u.putByte(testWI(i, 1)+address, (byte) x);
		return this;
	}

	public final DynByteBuf put(byte[] b, int off, int len) {
		ArrayUtil.checkRange(b, off, len);
		if (len > 0) {
			int off1 = preWrite(len);
			copyFromArray(b, Unsafe.ARRAY_BYTE_BASE_OFFSET, off, address + off1, len);
		}
		return this;
	}

	@Override
	public final DynByteBuf put(DynByteBuf b, int off, int len) {
		if (off+len > b.wIndex) throw new IndexOutOfBoundsException();

		if (b.hasArray()) {
			put(b.array(), b.arrayOffset()+off, len);
		} else if (b.isDirect()) {
			if ((off|len) < 0) throw new IndexOutOfBoundsException();
			if (len == 0) return this;

			int wi = preWrite(len);
			copyToArray(b.address()+off, null, address, wi, len);
		} else {
			while (len-- > 0) put(b.get(off++));
		}

		return this;
	}

	public final DynByteBuf putChars(int wi, CharSequence s) {
		long addr = testWI(wi, (s.length() << 1))+address;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			U.put16UB(null, addr, c);
			addr += 2;
		}
		return this;
	}
	public final DynByteBuf putAscii(int wi, CharSequence s) {
		long addr = testWI(wi, s.length())+address;
		for (int i = 0; i < s.length(); i++) {
			u.putByte(addr++, (byte) s.charAt(i));
		}
		return this;
	}

	final void _writeDioUTF(String s, int byteLen) {
		long addr = preWrite(byteLen)+address;
		ByteList.jutf8_encode_all(s, null, addr);
	}

	public final DynByteBuf put(ByteBuffer buf) {
		int rem = buf.remaining();
		if (buf.isDirect()) {
			u.copyMemory(NativeMemory.getAddress(buf)+buf.position(), preWrite(rem)+address, rem);
		} else if (buf.hasArray()) {
			copyFromArray(buf.array(), Unsafe.ARRAY_BYTE_BASE_OFFSET, buf.arrayOffset() + buf.position(), preWrite(rem)+address, rem);
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

	public final void readFully(byte[] b, int off, int len) {
		ArrayUtil.checkRange(b, off, len);
		if (len > 0) {
			copyToArray(address+ preRead(len), b, Unsafe.ARRAY_BYTE_BASE_OFFSET, off, len);
		}
	}
	public final void readFully(int i, byte[] b, int off, int len) {
		ArrayUtil.checkRange(b, off, len);
		if (len > 0) {
			copyToArray(address+testWI(i, len), b, Unsafe.ARRAY_BYTE_BASE_OFFSET, off, len);
		}
	}

	public final byte get(int i) {return u.getByte(testWI(i, 1)+address);}
	public final byte readByte() {return u.getByte(preRead(1)+address);}

	public final int readVarInt() {
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
				if (value < 0) break;
				return value;
			}
		}

		throw new RuntimeException("VarInt format error near " + rIndex);
	}

	public final String readAscii(int i, int len) {
		if (len <= 0) return "";

		long addr = testWI(i, len)+address;
		byte[] ob = ArrayCache.getByteArray(len, false);
		u.copyMemory(null,addr,ob,Unsafe.ARRAY_BYTE_BASE_OFFSET, len);

		String s = new String(ob, 0, len, StandardCharsets.ISO_8859_1);
		ArrayCache.putArray(ob);
		return s;
	}

	@Override
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
		return new String(tmp, 0, tmp.length, StandardCharsets.ISO_8859_1);
	}

	public final int readZeroTerminate(int max) {
		long i = address+rIndex;
		int r = Math.min(max, wIndex-rIndex);
		while (r-- > 0) {
			if (u.getByte(i) == 0) return (int) (i-address)-rIndex;
			i++;
		}
		return max;
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

	public final DynByteBuf slice(int off, int len) {
		return new Slice(testWI(off, len)+address, len);
	}

	@Override
	public final DynByteBuf compact() {
		if (rIndex > 0) {
			long addr = address;
			if (addr != 0) {
				int wi = wIndex, ri = rIndex;
				if ((wi -= ri) < 0) throw new AssertionError("Illegal Buffer State:"+info());
				u.copyMemory(addr+ri, addr, wi);
			}
			wIndex -= rIndex;
			rIndex = 0;
		}
		return this;
	}

	@Override
	public final ByteBuffer nioBuffer() {return NativeMemory.newDirectBuffer(address, length, nm).limit(wIndex).position(rIndex);}

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
	@NotNull
	public String toString() {
		int len = wIndex - rIndex;
		assert len <= length : info();

		byte[] tmp = new byte[len];
		copyToArray(address, tmp, Unsafe.ARRAY_BYTE_BASE_OFFSET, 0, len);
		return new String(tmp, StandardCharsets.US_ASCII);
	}

	@Override
	public CharList hex(CharList sb) {
		long off = address+rIndex;
		long len = address+wIndex;
		sb.ensureCapacity((int) (sb.length() + (len-off) << 1));
		char[] tmp = sb.list;
		int j = sb.length();
		while (off < len) {
			int bb = u.getByte(off++);
			tmp[j++] = TextUtil.b2h(bb >>> 4);
			tmp[j++] = TextUtil.b2h(bb & 0xf);
		}
		sb.setLength(j);
		return sb;
	}

	public static class Slice extends DirectByteList {
		public Slice() { super(false); }
		public Slice(long addr, int len) {
			super(false);
			this.wIndex = len;
			address = addr;
			length = len;
		}

		public DynByteBuf set(NativeMemory mem, long addr, int len) {
			rIndex = wIndex = 0;
			address = addr;
			length = len;
			nm = mem;
			return this;
		}
		public void _expand(int len, boolean backward) {
			if (backward) {
				address -= len;
				wIndex += len;
			}
			length += len;
		}

		public DynByteBuf copy(DynByteBuf buf) {
			address = buf.address();
			rIndex = buf.rIndex;
			wIndex = buf.wIndex;
			length = buf.wIndex;

			if (buf instanceof DirectByteList) nm = ((DirectByteList) buf).nm;
			return this;
		}

		@Override public int capacity() {return length;}
		@Override public int maxCapacity() {return length;}
		@Override public void ensureCapacity(int required) {if (required > length) throw new IndexOutOfBoundsException("长度为"+length+"的缓冲区无法放下"+required);}
	}
}