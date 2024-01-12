package roj.util;

import org.jetbrains.annotations.NotNull;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.GB18030;
import roj.text.UTF8MB4;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * @author Roj233
 * @since 2022/5/19 1:44
 */
public abstract class DynByteBuf extends OutputStream implements CharSequence, DataInput, DataOutput {
	public final class BufferInputStream extends InputStream {
		public DynByteBuf buffer() { return DynByteBuf.this; }

		public int read() {
			if (!isReadable()) return -1;
			return get()&0xFF;
		}

		public int read(@NotNull byte[] arr, int off, int len) {
			if (!isReadable()) return -1;
			len = Math.min(readableBytes(), len);
			DynByteBuf.this.read(arr, off, len);
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
			b = new DirectByteList.Slice(NativeMemory.getAddress(buf), capacity);
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

	public final byte get() { return readByte(); }

	public abstract int capacity();
	public abstract int maxCapacity();

	int wIndex;
	public int rIndex;

	public int wIndex() { return wIndex; }
	public void wIndex(int w) {
		ensureCapacity(w);
		this.wIndex = w;
	}

	public final void rIndex(int r) {
		if (rIndex > wIndex) throw new IllegalArgumentException();
		this.rIndex = r;
	}

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

	public boolean immutableCapacity() { return false; }
	public boolean hasBuffer() { return true; }

	public long address() { throw new UnsupportedOperationException(); }
	public abstract long _unsafeAddr();

	public abstract boolean hasArray();
	public byte[] array() { return null; }
	public int arrayOffset() { throw new UnsupportedOperationException(); }
	public final int relativeArrayOffset() { return arrayOffset()+rIndex; }

	public abstract void copyTo(long address, int bytes);

	public abstract void clear();

	public abstract void ensureCapacity(int capacity);

	public final ArrayRef byteRangeR(int len) { return byteRange(moveRI(len), len); }
	public final ArrayRef byteRangeW(int len) { return byteRange(moveWI(len), len); }
	public final ArrayRef byteRange(int off, int len) {
		if (off<0 || len < 0 || off+len < 0 || off+len > wIndex) throw new IndexOutOfBoundsException("pos="+off+",len="+len+",cap="+wIndex);
		return ArrayRef.create(array(), _unsafeAddr()+off, 1, len);
	}

	int moveWI(int i) {
		int t = wIndex;
		int e = t+i;
		ensureCapacity(e);
		wIndex = e;
		return t;
	}

	int moveRI(int i) {
		int t = rIndex;
		int e = t+i;
		if (e > wIndex) throw new IndexOutOfBoundsException("pos="+rIndex+",len="+i+",cap="+wIndex);
		rIndex = e;
		return t;
	}

	int testWI(int i, int req) {
		if (i<0||i+req>wIndex) throw new IndexOutOfBoundsException("pos="+i+",len="+req+",cap="+wIndex);
		return i;
	}

	public final InputStream asInputStream() { return new BufferInputStream(); }

	// region DataInput
	@Override
	public final void readFully(@NotNull byte[] b) {
		read(b, 0, b.length);
	}

	@Override
	public final void readFully(@NotNull byte[] b, int off, int len) {
		read(b, off, len);
	}
	// endregion
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

	public final DynByteBuf putBool(boolean b) { return put(b?1:0); }

	public abstract DynByteBuf put(int e);
	public abstract DynByteBuf put(int i, int e);

	public final DynByteBuf put(byte[] b) {
		return put(b, 0, b.length);
	}
	public abstract DynByteBuf put(byte[] b, int off, int len);

	public final DynByteBuf put(DynByteBuf b) {
		return put(b, b.readableBytes());
	}
	public DynByteBuf put(DynByteBuf b, int len) {
		return put(b, b.rIndex, len);
	}
	public abstract DynByteBuf put(DynByteBuf b, int off, int len);

	public static int zig(int i) {
		// (~i << 1) + 1
		// (-i << 1) - 1
		return (i & Integer.MIN_VALUE) == 0 ? i << 1 : ((-i << 1) - 1);
	}
	public static long zig(long i) {
		return (i & Long.MIN_VALUE) == 0 ? i << 1 : ((-i << 1) - 1);
	}

	public final DynByteBuf putVarInt(int i) {
		putVarLong(this, zig(i));
		return this;
	}
	public DynByteBuf putVarInt(int i, boolean canBeNegative) {
		putVarLong(this, canBeNegative ? zig(i) : i);
		return this;
	}

	public final DynByteBuf putVarLong(long i) {
		putVarLong(this, zig(i));
		return this;
	}
	public final DynByteBuf putVarLong(long i, boolean canBeNegative) {
		putVarLong(this, canBeNegative ? zig(i) : i);
		return this;
	}

	public static void putVarLong(DynByteBuf list, long i) {
		do {
			if (i < 0x80) {
				list.put((byte) i);
				return;
			} else {
				list.put((byte) ((i & 0x7F) | 0x80));
				i >>>= 7;
			}
		} while (true);
	}

	// fastpath for int
	public final DynByteBuf putVUInt(int i) {
		negative:
		if (i <= 0x3FFF) {
			if (i < 0) break negative;
			// 7
			if (i <= 0x7F) return put((byte) i);
			// 14: 0b10xxxxxx B
			return putShort(0x8000 | i);
		} else if (i <= 0xFFFFFFF) {
			// 21: 0b110xxxxx B B
			if (i <= 0x1FFFFF) return put((byte) (0xC0 | (i>>>16))).putShortLE(i);
			// 28: 0b1110xxxx B B B
			return put((byte) (0xE0 | (i>>>24))).putMediumLE(i);
		}

		// 35: 0b11110xxx B B B B
		return put((byte) 0xF0).putIntLE(i);
	}
	public final DynByteBuf putVULong(long l) {
		if ((l & 0xFFFFFFFF00000000L) == 0) return putVUInt((int) l);

		int firstByte = 0;
		int mask = 0x80;
		long max = 0x80;
		int i;

		for (i = 0; i < 8; i++) {
			if (l < max) {
				firstByte |= (l >>> (i<<3));
				break;
			}
			firstByte |= mask;
			mask >>>= 1;
			max <<= 7;
		}

		put((byte) firstByte);
		for (; i > 0; i--) {
			put((byte) l);
			l >>>= 8;
		}
		return this;
	}

	public final DynByteBuf putIntLE(int i) { return putIntLE(moveWI(4), i); }
	public abstract DynByteBuf putIntLE(int wi, int i);

	public DynByteBuf putInt(int i) { return putInt(moveWI(4), i); }
	public abstract DynByteBuf putInt(int wi, int i);

	public final DynByteBuf putLongLE(long l) { return putLongLE(moveWI(8), l); }
	public abstract DynByteBuf putLongLE(int wi, long l);

	public final DynByteBuf putLong(long l) { return putLong(moveWI(8), l); }
	public abstract DynByteBuf putLong(int wi, long l);

	public final DynByteBuf putFloat(float f) { return putInt(moveWI(4), Float.floatToRawIntBits(f)); }
	public final DynByteBuf putFloat(int wi, float f) { return putInt(wi, Float.floatToRawIntBits(f)); }

	public final DynByteBuf putDouble(double d) { return putLong(moveWI(8), Double.doubleToRawLongBits(d)); }
	public final DynByteBuf putDouble(int wi, double d) { return putLong(wi, Double.doubleToRawLongBits(d)); }

	public DynByteBuf putShort(int s) { return putShort(moveWI(2), s); }
	public abstract DynByteBuf putShort(int wi, int s);

	public final DynByteBuf putShortLE(int s) { return putShortLE(moveWI(2), s); }
	public abstract DynByteBuf putShortLE(int wi, int s);

	public final DynByteBuf putMedium(int m) { return putMedium(moveWI(3), m); }
	public abstract DynByteBuf putMedium(int wi, int m);
	public final DynByteBuf putMediumLE(int m) { return putMediumLE(moveWI(3), m); }
	public abstract DynByteBuf putMediumLE(int wi, int m);

	public final DynByteBuf putChars(CharSequence s) { return putChars(moveWI(s.length() << 1), s); }
	public abstract DynByteBuf putChars(int wi, CharSequence s);

	public DynByteBuf putAscii(CharSequence s) { return putAscii(moveWI(s.length()), s); }
	public abstract DynByteBuf putAscii(int wi, CharSequence s);

	public static int byteCountUTF8(CharSequence s) { return UTF8MB4.CODER.byteCount(s); }
	public final DynByteBuf putUTF(CharSequence s) {
		if (s.length() > 0xFFFF) throw new ArrayIndexOutOfBoundsException("UTF too long: " + s.length());
		int len = UTF8MB4.CODER.byteCount(s);
		if (len > 0xFFFF) throw new ArrayIndexOutOfBoundsException("UTF too long: " + len);
		return putShort(len).putUTFData0(s, len);
	}
	public final DynByteBuf putVarIntUTF(CharSequence s) { int len = byteCountUTF8(s); putVarLong(this, len); return putUTFData0(s, len); }
	public final DynByteBuf putVUIUTF(CharSequence s) { int len = byteCountUTF8(s); return putVUInt(len).putUTFData0(s, len); }
	public DynByteBuf putUTFData(CharSequence s) { UTF8MB4.CODER.encodeFixedIn(s, this); return this; }
	public final DynByteBuf putUTFData0(CharSequence s, int len) { ensureWritable(len); UTF8MB4.CODER.encodePreAlloc(s, this, len); return this; }

	public static int byteCountGB(CharSequence s) { return GB18030.CODER.byteCount(s); }
	public final DynByteBuf putVUIGB(CharSequence s) { int len = byteCountGB(s); return putVUInt(len).putGBData0(s, len); }
	public final DynByteBuf putGBData(CharSequence s) { GB18030.CODER.encodeFixedIn(s, this); return this; }
	public final DynByteBuf putGBData0(CharSequence s, int len) { ensureWritable(len); GB18030.CODER.encodePreAlloc(s, this, len); return this; }

	public abstract DynByteBuf put(ByteBuffer buf);

	public abstract byte[] toByteArray();

	public abstract void preInsert(int off, int len);
	public final void remove(int from, int to) {
		if (from >= to) throw new IllegalArgumentException("from >= to");
		preInsert(from, from-to);
	}

	// endregion
	// region GETxxx

	public final byte[] readBytes(int len) {
		byte[] result = new byte[len];
		read(result, 0, len);
		return result;
	}

	public final void read(byte[] b) { read(b, 0, b.length); }
	public abstract void read(byte[] b, int off, int len);
	public final void read(int i, byte[] b) { read(i, b, 0, b.length); }
	public abstract void read(int i, byte[] b, int off, int len);

	public final boolean readBoolean(int i) { return get(i) != 0; }
	public final boolean readBoolean() { return get() != 0; }

	public abstract byte get(int i);
	public abstract byte readByte();

	public final int getU(int i) { return get(i)&0xFF; }
	public final int readUnsignedByte() { return get()&0xFF; }

	public final int readUnsignedShort() { return readUnsignedShort(moveRI(2)); }
	public abstract int readUnsignedShort(int i);

	public final int readUShortLE() { return readUShortLE(moveRI(2)); }
	public abstract int readUShortLE(int i);

	@Override
	public final short readShort() { return (short) readUnsignedShort(); }
	public final short readShort(int i) { return (short) readUnsignedShort(i); }

	@Override
	public final char readChar() { return (char) readUnsignedShort(); }
	public final char readChar(int i) { return (char) readUnsignedShort(i); }

	public final int readMedium() { return readMedium(moveRI(3)); }
	public abstract int readMedium(int i);

	public final int readMediumLE() { return readMediumLE(moveRI(3)); }
	public abstract int readMediumLE(int i);

	public static int zag(int i) { return (i >> 1) & ~(1 << 31) ^ -(i & 1); }
	public static long zag(long i) { return (i >> 1) & ~(1L << 63) ^ -(i & 1); }

	public final int readVarInt() { return readVarInt(true); }
	public abstract int readVarInt(boolean mayNeg);

	public final long readVarLong() { return readVarLong(true); }
	public final long readVarLong(boolean mayNeg) {
		long value = 0;
		int i = 0;

		while (i <= 63) {
			int chunk = readByte();
			value |= (chunk & 0x7F) << i;
			i += 7;
			if ((chunk & 0x80) == 0) {
				if (mayNeg) return zag(value);
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

	public final int readInt() { return readInt(moveRI(4)); }
	public abstract int readInt(int i);

	public final int readIntLE() { return readIntLE(moveRI(4)); }
	public abstract int readIntLE(int i);

	public final long readUInt(int i) { return readInt(i) & 0xFFFFFFFFL; }
	public final long readUInt() { return readInt() & 0xFFFFFFFFL; }

	public final long readUIntLE() { return readIntLE() & 0xFFFFFFFFL; }
	public final long readUIntLE(int i) { return readIntLE(i) & 0xFFFFFFFFL; }

	public final long readLong() { return readLong(moveRI(8)); }
	public abstract long readLong(int i);

	public final long readLongLE() { return readLongLE(moveRI(8)); }
	public abstract long readLongLE(int i);

	public final float readFloat() { return readFloat(moveRI(4)); }
	public final float readFloat(int i) { return Float.intBitsToFloat(readInt(i)); }

	public final double readDouble() { return readDouble(moveRI(8)); }
	public final double readDouble(int i) { return Double.longBitsToDouble(readLong(i)); }

	public final String readAscii(int len) { return readAscii(moveRI(len), len); }
	public abstract String readAscii(int pos, int len);

	@NotNull
	public final String readUTF() { return readUTF(readUnsignedShort()); }
	public final String readVarIntUTF() { return readVarIntUTF(1000000); }
	public final String readVarIntUTF(int max) {
		int l = readVarInt(false);
		if (l > max) throw new IllegalArgumentException("Maximum " + max + " got " + l);
		return readUTF(l);
	}
	public final String readVUIUTF() { return readVUIUTF(1048575); }
	public final String readVUIUTF(int max) {
		int l = readVUInt();
		if (l > max) throw new IllegalArgumentException("Maximum " + max + " got " + l);
		return readUTF(l);
	}
	public final String readUTF(int len) {
		if (len < 0) throw new IllegalArgumentException("length="+len);
		if (len == 0) return "";
		testWI(rIndex,len);
		CharList sb = IOUtil.getSharedCharBuf();
		UTF8MB4.CODER.decodeFixedIn(this,len,sb);
		return sb.toString();
	}
	public final <T extends Appendable> T readUTF(int len, T target) {
		if (len < 0) throw new IllegalArgumentException("length="+len);
		if (len > 0) {
			testWI(rIndex,len);
			UTF8MB4.CODER.decodeFixedIn(this,len,target);
		}
		return target;
	}

	public final String readVUIGB() { return readGB(readVUInt()); }
	public final String readVUIGB(int max) {
		int l = readVUInt();
		if (l > max) throw new IllegalArgumentException("Maximum " + max + " got " + l);
		return readGB(l);
	}
	public final String readGB(int len) {
		if (len < 0) throw new IllegalArgumentException("length="+len);
		if (len == 0) return "";
		testWI(rIndex,len);
		CharList sb = IOUtil.getSharedCharBuf();
		GB18030.CODER.decodeFixedIn(this,len,sb);
		return sb.toString();
	}
	public final <T extends Appendable> T readGB(int len, T target) {
		if (len < 0) throw new IllegalArgumentException("length="+len);
		if (len > 0) {
			testWI(rIndex,len);
			GB18030.CODER.decodeFixedIn(this,len,target);
		}
		return target;
	}

	public abstract String readLine();

	public abstract int readZeroTerminate(int b);

	public abstract void forEachByte(IntUnaryOperator operator);

	// region ASCII sequence

	public final int length() { return readableBytes(); }
	public final char charAt(int i) { return (char) get(rIndex+i); }
	public final CharSequence subSequence(int start, int end) { return slice(rIndex+start, end-start); }

	// endregion
	// endregion
	// region Buffer Ops

	public abstract DynByteBuf slice(int length);
	public abstract DynByteBuf slice(int off, int len);

	public final DynByteBuf duplicate() {
		return slice(0, capacity());
	}
	public abstract DynByteBuf compact();

	public abstract int nioBufferCount();
	public abstract ByteBuffer nioBuffer();
	public abstract void nioBuffers(List<ByteBuffer> buffers);

	public final String info() {
		return getClass().getSimpleName()+"[rp="+rIndex+",wp="+wIndex+",cap="+capacity()+"=>"+maxCapacity()+"]";
	}
	public abstract String dump();
	// endregion
}