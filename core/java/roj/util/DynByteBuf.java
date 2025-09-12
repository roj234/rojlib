package roj.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import roj.ci.annotation.ReferenceByGeneratedClass;
import roj.crypt.Base64;
import roj.io.IOUtil;
import roj.io.MyDataInput;
import roj.math.MathUtils;
import roj.reflect.Unaligned;
import roj.text.CharList;
import roj.text.FastCharset;

import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.function.IntUnaryOperator;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj233
 * @since 2022/5/19 1:44
 */
public abstract class DynByteBuf extends OutputStream implements CharSequence, MyDataInput, DataOutput {
	public static ByteList allocate() { return new ByteList(); }
	public static ByteList allocate(int cap) { return new ByteList(cap); }
	public static ByteList allocate(int capacity, int maxCapacity) {
		return new ByteList(capacity) {
			@Override public int maxCapacity() { return maxCapacity; }
			@Override public void ensureCapacity(int required) {
				if (required > list.length) {
					int newLen = Math.max(MathUtils.nextPowerOfTwo(required), 1024);
					if (newLen > 1073741823 || newLen > maxCapacity()) newLen = maxCapacity();
					if (newLen <= list.length) throw new IndexOutOfBoundsException("cannot hold "+required+" bytes in this buffer("+list.length+"/"+newLen+")");

					byte[] newList = ArrayCache.getByteArray(newLen, false);
					if (newList.length > maxCapacity) {
						ArrayCache.putArray(newList);
						newList = (byte[]) Unaligned.U.allocateUninitializedArray(byte.class, maxCapacity);
					}

					if (wIndex > 0) System.arraycopy(list, 0, newList, 0, wIndex);
					ArrayCache.putArray(list);
					list = newList;
				}
			}
		};
	}
	public static ByteList wrap(byte[] b) {return new ByteList(b);}
	public static ByteList wrap(byte[] b, int off, int len) {return new ByteList.Slice(b, off, len);}

	public static DynByteBuf allocateDirect() { return new DirectByteList(); }
	public static DynByteBuf allocateDirect(int capacity) { return new DirectByteList(capacity); }
	public static DynByteBuf allocateDirect(int capacity, int maxCapacity) {
		return new DirectByteList(capacity) {
			@Override public int maxCapacity() { return maxCapacity; }
		};
	}
	public static DynByteBuf wrap(long address, int length) { return new DirectByteList.Slice(null, address, length); }

	public int refCnt() {return -1;}
	public final void retain() {retain(1);}
	public void retain(int count) {}
	public final void release() {release(1);}
	public abstract void release(int count);

	public final class BufferInputStream extends InputStream {
		public DynByteBuf buffer() { return DynByteBuf.this; }

		public int read() {
			if (!isReadable()) return -1;
			return readByte()&0xFF;
		}

		public int read(@NotNull byte[] arr, int off, int len) {
			if (!isReadable()) return -1;
			len = Math.min(readableBytes(), len);
			DynByteBuf.this.readFully(arr, off, len);
			return len;
		}

		public long skip(long len) { return skipBytes((int) len); }
		public int available() { return readableBytes(); }
	}

	public static DynByteBuf fromNio(ByteBuffer buf, int capacity) {
		byte[] array = NativeMemory.getArray(buf);
		DynByteBuf b;
		if (array != null) {
			b = new ByteList.Slice(array, NativeMemory.getOffset(buf), capacity);
		} else if (buf.isDirect()) {
			b = new DirectByteList.Slice(null, NativeMemory.getAddress(buf), capacity);
		} else {
			return null;
		}
		return b;
	}
	public static DynByteBuf nioRead(ByteBuffer buf) {
		DynByteBuf b = fromNio(buf, buf.capacity());
		if (b == null) return null;

		b.rIndex = buf.position();
		b.wIndex = buf.limit();
		return b;
	}
	public static DynByteBuf nioWrite(ByteBuffer buf) {
		DynByteBuf b = fromNio(buf, buf.limit());
		if (b == null) return null;

		b.wIndex = buf.position();
		return b;
	}

	public abstract int capacity();
	public abstract int maxCapacity();

	protected int wIndex;
	public int rIndex;

	public int wIndex() { return wIndex; }
	public void wIndex(int w) {
		if (w < 0) throw new IllegalArgumentException("negative size:"+w);
		ensureCapacity(w);
		this.wIndex = w;
	}

	public final void rIndex(int r) {
		if (rIndex > wIndex) throw new IllegalArgumentException();
		this.rIndex = r;
	}
	@Override
	public final long position() { return rIndex; }

	public final boolean isReadable() { return wIndex > rIndex; }
	public final int readableBytes() { return Math.max(wIndex-rIndex, 0); }

	public final boolean isWritable() { return wIndex < maxCapacity(); }
	public final int writableBytes() { return maxCapacity() - wIndex; }
	public final int unsafeWritableBytes() { return capacity() - wIndex; }

	public final boolean ensureWritable(int count) {
		if (count > writableBytes()) return false;
		ensureCapacity(count+wIndex);
		return true;
	}

	public abstract boolean isDirect();

	public boolean immutableCapacity() { return capacity() == maxCapacity(); }
	/**
	 * 这是否是一个真实的内存缓冲区
	 * 如果不是，那么它可能只能顺序读取或顺序写入
	 * @see ByteList.ToStream
	 */
	public boolean isReal() { return true; }

	public long address() { throw new UnsupportedOperationException(); }
	public abstract long _unsafeAddr();

	public abstract boolean hasArray();
	public byte[] array() { return null; }
	public int arrayOffset() { throw new UnsupportedOperationException(); }
	public final int relativeArrayOffset() { return arrayOffset()+rIndex; }

	public abstract void copyTo(long address, int bytes);

	public abstract void clear();

	public abstract void ensureCapacity(int capacity);

	public final NativeArray byteRangeR(int len) { return byteRange(preRead(len), len); }
	public final NativeArray byteRangeW(int len) { return byteRange(preWrite(len), len); }
	/**
	 * Create NativeArray to avoid boundary checking
	 */
	public final NativeArray byteRange(int off, int len) {
		if (off<0 || len < 0 || off+len < 0 || off+len > wIndex) throw new IndexOutOfBoundsException("pos="+off+",len="+len+",cap="+wIndex);
		return NativeArray.create(array(), _unsafeAddr()+off, 1, len);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof DynByteBuf b)) return false;

		int len = readableBytes();
		if (len != b.readableBytes()) return false;
		assert len <= capacity() : info();

		return ArrayUtil.compare(array(), _unsafeAddr()+rIndex, b.array(), b._unsafeAddr()+b.rIndex, len, ArrayUtil.LOG2_ARRAY_BYTE_INDEX_SCALE) < 0;
	}
	@Override
	public int hashCode() {
		int len = readableBytes();
		assert len <= capacity() : info();
		return ArrayUtil.byteHashCode(array(), _unsafeAddr()+rIndex, len);
	}

	int preWrite(int i) {
		int t = wIndex;
		int e = t+i;
		// overflow or < 0
		if (e < t) throw new IndexOutOfBoundsException("pos="+t+",len="+i);
		ensureCapacity(e);
		wIndex = e;
		return t;
	}

	int preRead(int i) {
		int t = rIndex;
		int e = t+i;
		if (e > wIndex) throw new IndexOutOfBoundsException("pos="+rIndex+",len="+i+",cap="+wIndex);
		rIndex = e;
		return t;
	}

	final int testWI(int i, int req) {
		if (i<0||i+req>wIndex) throw new IndexOutOfBoundsException("pos="+i+",len="+req+",cap="+wIndex);
		return i;
	}

	public final InputStream asInputStream() { return new BufferInputStream(); }

	// MyDataInput misc
	public void mark(int limit) {}
	public void unread(int bytes) {rIndex -= bytes;}

	// region DataOutput
	public final void write(int b) { put((byte) b); }
	public final void write(@NotNull byte[] b) { put(b, 0, b.length); }
	public final void write(@NotNull byte[] b, int off, int len) { put(b, off, len); }
	public final void writeBoolean(boolean v) { put((byte) (v ? 1 : 0)); }
	public final void writeByte(int v) { put((byte) v); }
	public final void writeShort(int s) { putShort(s); }
	public final void writeChar(int c) { writeShort(c); }
	public final void writeInt(int i) { putInt(i); }
	public final void writeLong(long l) { putLong(l); }
	public final void writeFloat(float v) { putInt(Float.floatToRawIntBits(v)); }
	public final void writeDouble(double v) { putLong(Double.doubleToRawLongBits(v)); }
	public void writeBytes(@NotNull String s) { putAscii(s); }
	public final void writeChars(@NotNull String s) { putChars(s); }
	public final void writeUTF(@NotNull String str) {
		int byteLen = byteCountDioUTF(str);
		putShort(byteLen)._writeDioUTF(str, byteLen);
	}
	public static int byteCountDioUTF(@NotNull String str) {
		int len = str.length();
		int byteLen = len;

		for (int i = 0; i < len; i++) {
			int c = str.charAt(i);
			if (c == 0 || c > 0x007F) {
				if (c > 0x07FF) {
					byteLen += 2;
				} else {
					byteLen ++;
				}
			}
		}
		return byteLen;
	}
	abstract void _writeDioUTF(String s, int byteLen);

	@Override
	public final int skipBytes(int i) {
		int skipped = Math.min(wIndex - rIndex, i);
		rIndex += skipped;
		return skipped;
	}
	// endregion
	public abstract void writeToStream(OutputStream out) throws IOException;
	// region PUTxxx

	public final DynByteBuf putZero(int off, int count) {
		long offset = _unsafeAddr() + testWI(off, count);
		U.setMemory(array(), offset, count, (byte)0);
		return this;
	}
	public final DynByteBuf putZero(int count) {
		long offset = _unsafeAddr() + preWrite(count);
		U.setMemory(array(), offset, count, (byte)0);
		return this;
	}

	public final DynByteBuf put(byte[] b) {return put(b, 0, b.length);}
	public abstract DynByteBuf put(byte[] b, int off, int len);

	public final DynByteBuf put(DynByteBuf b) {return put(b, b.readableBytes());}
	public DynByteBuf put(DynByteBuf b, int len) {return put(b, b.rIndex, len);}
	public abstract DynByteBuf put(DynByteBuf b, int off, int len);

	public final DynByteBuf put(int offset, DynByteBuf b) {return put(offset, b, b.rIndex, b.readableBytes());}
	public final DynByteBuf put(int offset, DynByteBuf b, int len) {return put(offset, b, b.rIndex, len);}
	public abstract DynByteBuf put(int offset, DynByteBuf b, int off, int len);

	//region 基本类型
	public final DynByteBuf putBool(boolean n) {return put(n?1:0);}
	@ReferenceByGeneratedClass
	public final DynByteBuf putByte(byte n) {return put(n);}

	public abstract DynByteBuf put(@Range(from = -128, to = 0xFF) int n);
	public abstract DynByteBuf put(int i, @Range(from = -128, to = 0xFF) int n);

	public final DynByteBuf putShort(@Range(from = -32768, to = 0xFFFF) int n) {return put16UB(preWrite(2), n);}
	public final DynByteBuf putShort(int wi, @Range(from = -32768, to = 0xFFFF) int n) {return put16UB(testWI(wi, 2), n);}
	private DynByteBuf put16UB(int offset,@Range(from = -32768, to = 0xFFFF) int n) {U.put16UB(array(), _unsafeAddr()+offset, n);return this;}

	public final DynByteBuf putShortLE(@Range(from = -32768, to = 0xFFFF) int n) {return put16UL(preWrite(2), n);}
	public final DynByteBuf putShortLE(int wi, @Range(from = -32768, to = 0xFFFF) int n) {return put16UL(testWI(wi, 2), n);}
	private DynByteBuf put16UL(int offset, @Range(from = -32768, to = 0xFFFF) int n) {U.put16UL(array(), _unsafeAddr()+offset, n);return this;}

	public final DynByteBuf putMedium(@Range(from = 0, to = 0xFFFFFF) int n) {return put24UB(preWrite(3), n);}
	public final DynByteBuf putMedium(int wi, @Range(from = 0, to = 0xFFFFFF) int n) {return put24UB(testWI(wi, 3), n);}
	private DynByteBuf put24UB(int offset, @Range(from = 0, to = 0xFFFFFF) int n) {U.put24UB(array(), _unsafeAddr()+offset, n);return this;}

	public final DynByteBuf putMediumLE(@Range(from = 0, to = 0xFFFFFF) int n) {return put24UL(preWrite(3), n);}
	public final DynByteBuf putMediumLE(int wi, @Range(from = 0, to = 0xFFFFFF) int n) {return put24UL(testWI(wi, 3), n);}
	private DynByteBuf put24UL(int offset, @Range(from = 0, to = 0xFFFFFF) int n) {U.put24UL(array(), _unsafeAddr()+offset, n);return this;}

	public final DynByteBuf putInt(int n) {return put32UB(preWrite(4), n);}
	public final DynByteBuf putInt(int wi, int n) {return put32UB(testWI(wi, 4), n);}
	private DynByteBuf put32UB(int offset, int n) {U.put32UB(array(), _unsafeAddr()+offset, n);return this;}

	public final DynByteBuf putIntLE(int n) {return put32UL(preWrite(4), n);}
	public final DynByteBuf putIntLE(int wi, int n) {return put32UL(testWI(wi, 4), n);}
	private DynByteBuf put32UL(int offset, int n) {U.put32UL(array(), _unsafeAddr()+offset, n);return this;}

	public final DynByteBuf putLong(long n) {return put64UB(preWrite(8), n);}
	public final DynByteBuf putLong(int wi, long n) {return put64UB(testWI(wi, 8), n);}
	private DynByteBuf put64UB(int offset, long n) {U.put64UB(array(), _unsafeAddr()+offset, n);return this;}

	public final DynByteBuf putLongLE(long n) {return put64UL(preWrite(8), n);}
	public final DynByteBuf putLongLE(int wi, long n) {return put64UL(testWI(wi, 8), n);}
	private DynByteBuf put64UL(int offset, long n) {U.put64UL(array(), _unsafeAddr()+offset, n);return this;}

	public final DynByteBuf putFloat(float n) {return putInt(preWrite(4), Float.floatToRawIntBits(n));}
	public final DynByteBuf putFloat(int wi, float n) {return putInt(wi, Float.floatToRawIntBits(n));}

	public final DynByteBuf putDouble(double n) {return putLong(preWrite(8), Double.doubleToRawLongBits(n));}
	public final DynByteBuf putDouble(int wi, double n) {return putLong(wi, Double.doubleToRawLongBits(n));}
	//endregion
	//region varint
	public static int zig(int i) {return (i << 1) ^ (i >> 31);}
	public static long zig(long i) {return (i << 1) ^ (i >> 63);}

	public final DynByteBuf putVarInt(int x) {
		while (true) {
			if (Integer.compareUnsigned(x, 0x80) < 0) {
				put(x);
				return this;
			} else {
				put((x & 0x7F) | 0x80);
				x >>>= 7;
			}
		}
	}
	public final DynByteBuf putVarLong(long x) {
		while (true) {
			if (Long.compareUnsigned(x, 0x80) < 0) {
				put((byte) x);
				return this;
			} else {
				put((byte) ((x & 0x7F) | 0x80));
				x >>>= 7;
			}
		}
	}

	// fastpath for int
	public final DynByteBuf putVUInt(int x) {
		negative:
		if (x <= 0x3FFF) {
			if (x < 0) break negative;
			// 7
			if (x <= 0x7F) return put(x);
			// 14: 0b10xxxxxx B
			return putShort(0x8000 | x);
		} else if (x <= 0xFFFFFFF) {
			// 21: 0b110xxxxx B B
			if (x <= 0x1FFFFF) return put(0b11000000 | (x>>>16)).putShortLE(x);
			// 28: 0b1110xxxx B B B
			return put(0b11100000 | (x>>>24)).putMediumLE(x);
		}

		// 35: 0b11110xxx B B B B
		return put(0xF0).putIntLE(x);
	}
	public final DynByteBuf putVULong(long x) {
		if ((x & 0xFFFFFFFF00000000L) == 0) return putVUInt((int) x);

		int firstByte = 0;
		int mask = 0x80;
		long max = 0x80;
		int i;

		for (i = 0; i < 8; i++) {
			if (Long.compareUnsigned(x, max) < 0) {
				firstByte |= (x >>> (i<<3));
				break;
			}
			firstByte |= mask;
			mask >>>= 1;
			max <<= 7;
		}

		put(firstByte);
		for (; i > 0; i--) {
			put((byte)x);
			x >>>= 8;
		}
		return this;
	}
	//endregion

	public final DynByteBuf putChars(CharSequence s) {return putChars(preWrite(s.length() << 1), s);}
	public abstract DynByteBuf putChars(int wi, CharSequence s);

	public DynByteBuf putAscii(CharSequence s) {return putAscii(preWrite(s.length()), s);}
	public abstract DynByteBuf putAscii(int wi, CharSequence s);

	public static int byteCountUTF8(CharSequence s) { return FastCharset.UTF8().byteCount(s); }
	public final DynByteBuf putUTF(CharSequence s) {
		if (s.length() > 0xFFFF) throw new ArrayIndexOutOfBoundsException("UTF too long: " + s.length());
		int len = FastCharset.UTF8().byteCount(s);
		if (len > 0xFFFF) throw new ArrayIndexOutOfBoundsException("UTF too long: " + len);
		return putShort(len).putUTFData0(s, len);
	}
	public final DynByteBuf putVarIntUTF(CharSequence s) { int len = byteCountUTF8(s); return putVarInt(len).putUTFData0(s, len); }
	public final DynByteBuf putVUIUTF(CharSequence s) { int len = byteCountUTF8(s); return putVUInt(len).putUTFData0(s, len); }
	public DynByteBuf putUTFData(CharSequence s) { return putStrData(s, FastCharset.UTF8()); }
	public final DynByteBuf putUTFData0(CharSequence s, int len) { return putStrData(s, len, FastCharset.UTF8()); }

	public final DynByteBuf putVUIGB(CharSequence s) { return putVUIStr(s, FastCharset.GB18030()); }
	public final DynByteBuf putGBData(CharSequence s) { return putStrData(s, FastCharset.GB18030()); }

	public final DynByteBuf putVUIStr(CharSequence str, FastCharset charset) { int len = charset.byteCount(str); return putVUInt(len).putStrData(str, len, charset); }
	public final DynByteBuf putStrData(CharSequence str, FastCharset charset) { charset.encodeFixedIn(str, this); return this; }
	public final DynByteBuf putStrData(CharSequence str, int len, FastCharset charset) { ensureWritable(len); charset.encodePreAlloc(str, this, len); return this; }

	public abstract DynByteBuf put(ByteBuffer buf);

	public abstract byte[] toByteArray();

	public abstract void preInsert(int off, int len);
	public final void remove(int from, int to) {
		if (from >= to) throw new IllegalArgumentException("from >= to");
		preInsert(to, from-to);
	}

	// endregion
	// region GETxxx

	public final byte[] readBytes(int len) {
		byte[] result = new byte[len];
		this.readFully(result, 0, len);
		return result;
	}
	public final void readFully(byte[] b) { this.readFully(b, 0, b.length); }
	public abstract void readFully(byte[] b, int off, int len);
	public final void readFully(int i, byte[] b) { readFully(i, b, 0, b.length); }
	public abstract void readFully(int i, byte[] b, int off, int len);

	//region 基本类型
	public final boolean readBoolean(int i) {return get(i) != 0;}
	public final boolean readBoolean() {return readByte() != 0;}

	public abstract byte get(int i);
	public abstract byte readByte();

	@Range(from = 0, to = 255)
	public final int getU(int i) {return get(i)&0xFF;}
	public final int readUnsignedByte() {return readByte()&0xFF;}

	public final int readUnsignedShort() {return readUnsignedShort(preRead(2));}
	@Range(from = 0, to = 65535)
	public final int readUnsignedShort(int i) {long addr = testWI(i, 2)+_unsafeAddr();return U.get16UB(array(), addr);}

	public final int readUShortLE() {return readUShortLE(preRead(2));}
	@Range(from = 0, to = 65535)
	public final int readUShortLE(int i) {long addr = testWI(i, 2)+_unsafeAddr();return U.get16UL(array(), addr);}

	@Override public final short readShort() {return (short) readUnsignedShort();}
	public final short readShort(int i) {return (short) readUnsignedShort(i); }

	@Override public final char readChar() {return (char) readUnsignedShort();}
	public final char readChar(int i) {return (char) readUnsignedShort(i);}

	@Range(from = 0, to = 16777215) public final int readMedium() {return readMedium(preRead(3));}
	@Range(from = 0, to = 16777215)
	public final int readMedium(int i) {
		long addr = testWI(i, 3)+_unsafeAddr();
		byte[] array = array();
		return (U.getByte(array, addr++) & 0xFF) << 16
			| (U.getByte(array, addr++) & 0xFF) << 8
			| (U.getByte(array, addr) & 0xFF);
	}

	@Range(from = 0, to = 16777215) public final int readMediumLE() {return readMediumLE(preRead(3));}
	@Range(from = 0, to = 16777215)
	public final int readMediumLE(int i) {
		long addr = testWI(i, 3)+_unsafeAddr();
		byte[] array = array();
		return (U.getByte(array, addr++) & 0xFF)
			| (U.getByte(array, addr++) & 0xFF) << 8
			| (U.getByte(array, addr) & 0xFF) << 16;
	}

	public final int readInt() {return readInt(preRead(4));}
	public final int readInt(int i) {long addr = testWI(i, 4)+_unsafeAddr();return U.get32UB(array(), addr);}

	public final int readIntLE() {return readIntLE(preRead(4));}
	public final int readIntLE(int i) {long addr = testWI(i, 4)+_unsafeAddr();return U.get32UL(array(), addr);}

	@Range(from = 0, to = 0xFFFFFFFFL) public final long readUInt(int i) {return readInt(i) & 0xFFFFFFFFL;}
	@Range(from = 0, to = 0xFFFFFFFFL) public final long readUInt() {return readInt() & 0xFFFFFFFFL;}

	@Range(from = 0, to = 0xFFFFFFFFL) public final long readUIntLE() {return readIntLE() & 0xFFFFFFFFL;}
	@Range(from = 0, to = 0xFFFFFFFFL) public final long readUIntLE(int i) {return readIntLE(i) & 0xFFFFFFFFL;}

	public final long readLong() {return readLong(preRead(8));}
	public final long readLong(int i) {long addr = testWI(i, 8)+_unsafeAddr();return U.get64UB(array(), addr);}

	public final long readLongLE() {return readLongLE(preRead(8));}
	public final long readLongLE(int i) {long addr = testWI(i, 8)+_unsafeAddr();return U.get64UL(array(), addr);}

	public final float readFloat() {return readFloat(preRead(4)); }
	public final float readFloat(int i) {return Float.intBitsToFloat(readInt(i));}

	public final double readDouble() {return readDouble(preRead(8));}
	public final double readDouble(int i) {return Double.longBitsToDouble(readLong(i));}
	//endregion
	//region varint
	public final int readVarInt(int max) {
		int v = readVarInt();
		if (v > max) throw new IllegalArgumentException("varint太大:"+v+",要求:"+max);
		return v;
	}
	public abstract int readVarInt();
	public final long readVarLong() {
		long value = 0;
		int i = 0;

		while (i <= 63) {
			int chunk = readByte();
			value |= (long) (chunk & 0x7F) << i;
			i += 7;
			if ((chunk & 0x80) == 0) {
				if (value < 0) break;
				return value;
			}
		}

		throw new RuntimeException("VarLong长度超限 at " + rIndex);
	}

	public final int readVUInt() {
		int b = readUnsignedByte();
		if ((b&0x80) == 0) return b;
		if ((b&0x40) == 0) return ((b&0x3F)<< 8) | readUnsignedByte();
		if ((b&0x20) == 0) return ((b&0x1F)<<16) | readUShortLE();
		if ((b&0x10) == 0) return ((b&0x0F)<<24) | readMediumLE();
		if ((b&0x08) == 0) {
			if ((b&7) == 0)
				return readIntLE();
		}
		throw new RuntimeException("VUInt长度超限 at " + rIndex);
	}
	public final long readVULong() {
		int b = readUnsignedByte();

		if ((b&0x80) == 0) return b;
		if ((b&0x40) == 0) return ((b&0x3FL)<< 8) | readUnsignedByte();
		if ((b&0x20) == 0) return ((b&0x1FL)<<16) | readUShortLE();
		if ((b&0x10) == 0) return ((b&0x0FL)<<24) | readMediumLE();
		if ((b&0x08) == 0) return ((b&0x07L)<<32) | readUIntLE();
		if ((b&0x04) == 0) return ((b&0x03L)<<40) | readUIntLE() | (long) readUnsignedByte() << 32;
		if ((b&0x02) == 0) return ((b&0x01L)<<48) | readUIntLE() | (long) readUShortLE()     << 40;
		if ((b&0x01) == 0) return 					readUIntLE() | (long) readMediumLE()     << 48;
		return readLongLE();

		//int mask = 0x80;
		//long value = 0;
		//for (int i = 0;; i += 8) {
		//	if ((b & mask) == 0)
		//		return value | (b & (mask-1)) << i;

		//	value |= (get()&0xFFL) << i;
		//	mask >>>= 1;
		//}
	}
	//endregion

	public final String readAscii(int len) {return readAscii(preRead(len), len);}
	public abstract String readAscii(int pos, int len);

	public final String readVarIntUTF() {return readVarIntUTF(DEFAULT_MAX_STRING_LEN);}
	public final String readVarIntUTF(int max) {
		int len = readVarInt();
		if (len > max) throw new IllegalArgumentException("字符串长度不正确: "+len+" > "+max);
		return readUTF(len);
	}
	@NotNull
	public final String readUTF() { return readUTF(readUnsignedShort()); }
	public final String readVUIUTF() { return readVUIUTF(DEFAULT_MAX_STRING_LEN); }
	public final String readVUIUTF(int max) {return readVUIStr(max, FastCharset.UTF8());}
	public final String readUTF(int len) {return readStr(len, IOUtil.getSharedCharBuf(), FastCharset.UTF8()).toString();}

	public final String readVUIGB() {return readVUIGB(DEFAULT_MAX_STRING_LEN);}
	public final String readVUIGB(int max) {return readVUIStr(max, FastCharset.GB18030());}
	public final String readGB(int len) {return readStr(len, IOUtil.getSharedCharBuf(), FastCharset.GB18030()).toString();}

	public final String readVUIStr(FastCharset charset) { return readVUIStr(DEFAULT_MAX_STRING_LEN, charset); }
	public final String readVUIStr(int max, FastCharset charset) {
		int len = readVUInt();
		if (len > max) throw new IllegalArgumentException("字符串长度不正确: "+len+" > "+max);
		return readStr(len, charset);
	}
	public final String readStr(int len, FastCharset charset) { return readStr(len, IOUtil.getSharedCharBuf(), charset).toString(); }
	public final <T extends Appendable> T readStr(int len, T target, FastCharset charset) {
		if (len > 0) {
			testWI(rIndex, len);
			charset.decodeFixedIn(this,len,target);
		}
		return target;
	}


	public abstract String readLine();

	public abstract int readZeroTerminate(int max);

	public abstract void forEachByte(IntUnaryOperator operator);

	// region ASCII sequence

	public final int length() { return readableBytes(); }
	public final char charAt(int i) { return (char) getU(rIndex+i); }
	public final CharSequence subSequence(int start, int end) { return slice(rIndex+start, end-start); }

	// endregion
	// endregion
	// region Buffer Ops

	public final DynByteBuf slice() { return slice(rIndex, readableBytes()); }
	public final DynByteBuf slice(int len) { return slice(preRead(len), len); }
	public abstract DynByteBuf slice(int off, int len);
	public abstract DynByteBuf copySlice();

	public abstract DynByteBuf compact();

	public abstract ByteBuffer nioBuffer();

	public final String info() {
		return getClass().getSimpleName()+"[rp="+rIndex+",wp="+wIndex+",cap="+capacity()+"=>"+maxCapacity()+"]";
	}
	public abstract String dump();
	// endregion

	public final String base64() {return base64(IOUtil.getSharedCharBuf()).toString();}
	public final CharList base64(CharList sb) {return Base64.encode(slice(), sb);}

	public final String base64UrlSafe() {return base64UrlSafe(IOUtil.getSharedCharBuf()).toString();}
	public final CharList base64UrlSafe(CharList sb) {return Base64.encode(slice(), sb, Base64.B64_URL_SAFE);}

	public final String hex() {return hex(IOUtil.getSharedCharBuf()).toString();}
	public abstract CharList hex(CharList sb);
}