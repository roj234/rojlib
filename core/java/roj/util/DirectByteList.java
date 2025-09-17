package roj.util;

import org.jetbrains.annotations.NotNull;
import roj.io.UnsafeOutputStream;
import roj.math.MathUtils;
import roj.reflect.Unsafe;
import roj.text.CharList;
import roj.text.TextUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.ConcurrentModificationException;
import java.util.function.IntUnaryOperator;

import static roj.reflect.Unsafe.U;

/**
 * @author Roj234
 * @since 2021/5/29 20:45
 */
public class DirectByteList extends DynByteBuf {
	private static final boolean SAFER_BUT_NOT_TOTALLY_SAFE_TS_EXPAND = true;

	protected NativeMemory nm;
	protected volatile long address;
	protected int length;

	DirectByteList(boolean unused) {}
	DirectByteList() { nm = new NativeMemory(); }
	DirectByteList(int cap) {
		nm = new NativeMemory();
		address = nm.allocate(cap);
		length = cap;
	}

	public synchronized void release(int count) {
		clear();
		length = 0;

		address = 0;
		if (nm != null) {
			nm.free();
			nm = null;
		}
	}

	public final int capacity() { return length; }
	public int maxCapacity() { return nm == null ? length : Integer.MAX_VALUE; }
	public final boolean isDirect() { return true; }
	public final long address() { return address; }
	public final long _unsafeAddr() { return address; }
	public final boolean hasArray() { return false; }

	@Override
	public final void copyTo(long addr, int len) {
		copyToArray(addr, null, preRead(len), address, length);
	}

	public final void clear() {wIndex = rIndex = 0;}

	public final void ensureCapacity(int required) {
		if (length < required) {
			int newLen = Math.max(MathUtils.nextPowerOfTwo(required), 1024);
			if (newLen > 1073741823 || newLen > maxCapacity()) newLen = maxCapacity();
			if (nm == null || newLen <= length) throw new IndexOutOfBoundsException("长度为("+(nm==null&&length==0?"NaN":length)+" => "+newLen+")的缓冲区无法放下"+required+"字节");

			if (SAFER_BUT_NOT_TOTALLY_SAFE_TS_EXPAND) {
				int len = wIndex;
				wIndex = 0;
				NativeMemory prevNM = nm;
				long prevAddr = address;

				NativeMemory mem = new NativeMemory();
				long addr = mem.allocate(newLen);
				U.copyMemory(prevAddr, addr, Math.min(newLen, len));

				if (wIndex != 0) {
					mem.free();
					throw new ConcurrentModificationException();
				}
				wIndex = len;

				address = addr;
				nm = mem;

				prevNM.free();
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
				U.copyMemory(null, addr, array, Unsafe.ARRAY_BYTE_BASE_OFFSET, w);
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
		U.putByte(preWrite(1)+address, (byte) x);
		return this;
	}
	public final DynByteBuf set(int offset, int x) {
		U.putByte(testWI(offset, 1)+address, (byte) x);
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
		if ((off|len|(off+len)) < 0 || off + len > b.wIndex) throw new IndexOutOfBoundsException("off="+off+",len="+len+",cap="+b.wIndex);

		U.copyMemory(b.array(), b._unsafeAddr()+off, null, preWrite(len) + address, len);
		return this;
	}

	@Override
	public DynByteBuf set(int wi, DynByteBuf b, int off, int len) {
		U.copyMemory(b.array(), b._unsafeAddr()+off, null, testWI(wi, len) + address, len);
		return this;
	}

	public final DynByteBuf setChars(int wi, CharSequence s) {
		long addr = testWI(wi, (s.length() << 1))+address;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			U.put16UB(null, addr, c);
			addr += 2;
		}
		return this;
	}
	public final DynByteBuf setAscii(int wi, CharSequence s) {
		long addr = testWI(wi, s.length())+address;
		for (int i = 0; i < s.length(); i++) {
			U.putByte(addr++, (byte) s.charAt(i));
		}
		return this;
	}

	final void writeJavaUTF(String s, int byteLen) {
		long addr = preWrite(byteLen)+address;
		ByteList.jutf8_encode_all(s, null, addr);
	}

	public final byte[] toByteArray() {
		byte[] b = new byte[wIndex-rIndex];
		copyToArray(address + rIndex, b, Unsafe.ARRAY_BYTE_BASE_OFFSET, 0, b.length);
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
			if (address != tmp) U.copyMemory(address, tmp, off);
			U.copyMemory(address+off, tmp+off+len, wIndex-off);
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
			copyToArray(address + preRead(len), b, Unsafe.ARRAY_BYTE_BASE_OFFSET, off, len);
		}
	}
	public final void readFully(int i, byte[] b, int off, int len) {
		ArrayUtil.checkRange(b, off, len);
		if (len > 0) {
			copyToArray(address + testWI(i, len), b, Unsafe.ARRAY_BYTE_BASE_OFFSET, off, len);
		}
	}

	public final byte getByte(int i) {return U.getByte(testWI(i, 1)+address);}
	public final byte readByte() {return U.getByte(preRead(1)+address);}

	public final int readVarInt() {
		int value = 0;
		int i = 0;

		long off = address+rIndex;
		int r = wIndex-rIndex;

		while (i <= 28) {
			if (r-- <= 0) throw new BufferUnderflowException();

			int chunk = U.getByte(off++);
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

	public final String getAscii(int i, int len) {
		if (len <= 0) return "";

		long addr = testWI(i, len)+address;
		byte[] ob = ArrayCache.getByteArray(len, false);
		U.copyMemory(null, addr, ob, Unsafe.ARRAY_BYTE_BASE_OFFSET, len);

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
			byte b = U.getByte(l++);
			if (b == '\r' || b == '\n') {
				if (b == '\r' && r>0 && U.getByte(l) == '\n') l++;
				break;
			}
		}
		byte[] tmp = new byte[(int)(l-address)-rIndex];
		copyToArray(address + rIndex, tmp, Unsafe.ARRAY_BYTE_BASE_OFFSET, 0, tmp.length);
		rIndex += tmp.length;
		return new String(tmp, 0, tmp.length, StandardCharsets.ISO_8859_1);
	}

	public final int readCString(int max) {
		long i = address+rIndex;
		int r = Math.min(max, wIndex-rIndex);
		while (r-- > 0) {
			if (U.getByte(i) == 0) return (int) (i-address)-rIndex;
			i++;
		}
		return max;
	}

	public final void forEachByte(IntUnaryOperator operator) {
		long i = address+rIndex;
		long e = address+wIndex;
		while (i < e) {
			int v = operator.applyAsInt(U.getByte(i));
			U.putByte(i, (byte) v);

			i++;
		}
	}

	// endregion
	// region Buffer Ops

	public final DynByteBuf slice(int off, int len) {return new Slice(nm, testWI(off, len)+address, len);}
	@Override
	public final DynByteBuf copySlice() {
		int length = wIndex - rIndex;
		var result = new DirectByteList(length);
		result.wIndex = length;
		U.copyMemory(address+rIndex, result.address, length);
		return result;
	}

	@Override
	public final DynByteBuf compact() {
		if (rIndex > 0) {
			long addr = address;
			if (addr != 0) {
				int wi = wIndex, ri = rIndex;
				if ((wi -= ri) < 0) throw new AssertionError("Illegal Buffer State:"+info());
				U.copyMemory(addr+ri, addr, wi);
			}
			wIndex -= rIndex;
			rIndex = 0;
		}
		return this;
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
			U.copyMemory(src, offset, null, dstAddr, size);
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
			U.copyMemory(null, srcAddr, dst, offset, size);
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
			int bb = U.getByte(off++);
			tmp[j++] = TextUtil.b2h(bb >>> 4);
			tmp[j++] = TextUtil.b2h(bb & 0xf);
		}
		sb.setLength(j);
		return sb;
	}

	public static class Slice extends DirectByteList {
		public Slice() { super(false); }
		public Slice(NativeMemory mem, long addr, int len) {
			super(false);
			this.wIndex = len;
			address = addr;
			length = len;
			this.nm = mem;
		}

		public DynByteBuf set(NativeMemory mem, long addr, int len) {
			rIndex = wIndex = 0;
			address = addr;
			length = len;
			nm = mem;
			return this;
		}

		@Override public int maxCapacity() {return length;}
	}
}