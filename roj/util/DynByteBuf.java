package roj.util;

import roj.RequireUpgrade;

import javax.annotation.Nonnull;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/5/19 1:44
 */
public abstract class DynByteBuf extends OutputStream implements DataInput, DataOutput {
	public final byte get() {
		return readByte();
	}

	public abstract int capacity();
	public abstract int maxCapacity();

	int wIndex;
	public int rIndex;

	public int wIndex() {
		return wIndex;
	}

	public void wIndex(int w) {
		ensureCapacity(w);
		this.wIndex = w;
	}

	public final void rIndex(int r) {
		if (rIndex > wIndex) throw new IllegalArgumentException();
		this.rIndex = r;
	}

	public final boolean isReadable() {
		return wIndex > rIndex;
	}
	public final int readableBytes() {
		return Math.max(wIndex-rIndex, 0);
	}

	public final boolean isWritable() {
		return wIndex < maxCapacity();
	}
	public final int writableBytes() {
		return maxCapacity() - wIndex;
	}

	public final boolean ensureWritable(int count) {
		if (count > writableBytes()) return false;
		ensureCapacity(count+wIndex);
		return true;
	}

	public abstract boolean isDirect();

	public boolean immutableCapacity() {
		return false;
	}

	public boolean isBuffer() {
		return true;
	}

	public long address() {
		throw new UnsupportedOperationException();
	}

	public abstract boolean hasArray();
	public byte[] array() {
		throw new UnsupportedOperationException();
	}
	public int arrayOffset() {
		throw new UnsupportedOperationException();
	}
	public int relativeArrayOffset() {
		throw new UnsupportedOperationException();
	}

	public abstract void copyTo(long address, int bytes);

	public abstract void clear();

	public abstract void ensureCapacity(int capacity);

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
		if (e > wIndex) throw new ArrayIndexOutOfBoundsException("mov=" + i + ",rIdx=" + rIndex + ",wIdx=" + wIndex);
		rIndex = e;
		return t;
	}

	int testWI(int i, int req) {
		if (i + req > wIndex) throw new ArrayIndexOutOfBoundsException("mov=" + req + ",rIdx=" + i + ",wIdx=" + wIndex);
		return i;
	}

	public final InputStream asInputStream() {
		return new InputStream() {
			@Override
			public int read() {
				if (!isReadable()) return -1;
				return readByte();
			}

			@Override
			public int read(@Nonnull byte[] arr, int off, int len) {
				if (!isReadable()) return -1;
				len = Math.min(readableBytes(), len);
				DynByteBuf.this.read(arr, off, len);
				return len;
			}

			@Override
			public long skip(long len) {
				return skipBytes((int) len);
			}

			@Override
			public int available() {
				return readableBytes();
			}
		};
	}

	// region DataInput
	@Override
	public final void readFully(@Nonnull byte[] b) {
		read(b, 0, b.length);
	}

	@Override
	public final void readFully(@Nonnull byte[] b, int off, int len) {
		read(b, off, len);
	}
	// endregion
	// region DataOutput
	@Override
	public final void write(int b) {
		put((byte) b);
	}

	@Override
	public final void write(@Nonnull byte[] b) {
		put(b, 0, b.length);
	}

	@Override
	public final void write(@Nonnull byte[] b, int off, int len) {
		put(b, off, len);
	}

	@Override
	public final void writeBoolean(boolean v) {
		put((byte) (v ? 1 : 0));
	}

	@Override
	public final void writeByte(int v) {
		put((byte) v);
	}

	@Override
	public final void writeShort(int s) {
		put((byte) (s >>> 8)).put((byte) s);
	}

	@Override
	public final void writeChar(int c) {
		writeShort(c);
	}

	@Override
	public final void writeInt(int i) {
		putInt(i);
	}

	@Override
	public final void writeLong(long l) {
		putLong(l);
	}

	@Override
	public final void writeFloat(float v) {
		putInt(Float.floatToRawIntBits(v));
	}

	@Override
	public final void writeDouble(double v) {
		putLong(Double.doubleToRawLongBits(v));
	}

	@Override
	public void writeBytes(@Nonnull String s) {
		putAscii(s);
	}

	@Override
	public final void writeChars(@Nonnull String s) {
		putChars(s);
	}

	@Override
	public final void writeUTF(@Nonnull String str) {
		putUTF(str);
	}

	@Override
	public final int skipBytes(int i) {
		int skipped = Math.min(wIndex - rIndex, i);
		rIndex += skipped;
		return skipped;
	}
	// endregion
	// region PUTxxx

	public final DynByteBuf putBool(boolean b) {
		return put((byte) (b?1:0));
	}

	public abstract DynByteBuf put(byte e);
	public abstract DynByteBuf put(int i, byte e);

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

	public DynByteBuf putVSInt(int i) {
		return putVSLong(i);
	}

	public final DynByteBuf putVSLong(long l) {
		int firstByte = 0;
		int mask = 0x80;
		int i;

		for (i = 0; i < 8; i++) {
			if (l < ((1L << (7 * (i+1))))) {
				firstByte |= (l >>> (8 * i));
				break;
			}
			firstByte |= mask;
			mask >>>= 1;
		}

		put((byte) firstByte);
		for (; i > 0; i--) {
			put((byte) l);
			l >>>= 8;
		}
		return this;
	}

	public final DynByteBuf putIntLE(int i) {
		return putIntLE(moveWI(4), i);
	}
	public abstract DynByteBuf putIntLE(int wi, int i);

	@RequireUpgrade
	public DynByteBuf putInt(int i) {
		return putInt(moveWI(4), i);
	}
	public abstract DynByteBuf putInt(int wi, int i);

	public final DynByteBuf putLongLE(long l) {
		return putLongLE(moveWI(8), l);
	}
	public abstract DynByteBuf putLongLE(int wi, long l);

	public final DynByteBuf putLong(long l) {
		return putLong(moveWI(8), l);
	}
	public abstract DynByteBuf putLong(int wi, long l);

	public final DynByteBuf putFloat(float f) {
		return putInt(moveWI(4), Float.floatToRawIntBits(f));
	}
	public final DynByteBuf putFloat(int wi, float f) {
		return putInt(wi, Float.floatToRawIntBits(f));
	}

	public final DynByteBuf putDouble(double d) {
		return putLong(moveWI(8), Double.doubleToRawLongBits(d));
	}
	public final DynByteBuf putDouble(int wi, double d) {
		return putLong(wi, Double.doubleToRawLongBits(d));
	}

	@RequireUpgrade
	public DynByteBuf putShort(int s) {
		return putShort(moveWI(2), s);
	}
	public abstract DynByteBuf putShort(int wi, int s);

	public final DynByteBuf putShortLE(int s) {
		return putShortLE(moveWI(2), s);
	}
	public abstract DynByteBuf putShortLE(int wi, int s);

	public final DynByteBuf putMedium(int m) {
		return putMedium(moveWI(3), m);
	}
	public abstract DynByteBuf putMedium(int wi, int m);
	public final DynByteBuf putMediumLE(int m) {
		return putMediumLE(moveWI(3), m);
	}
	public abstract DynByteBuf putMediumLE(int wi, int m);

	public final DynByteBuf putChars(CharSequence s) {
		return putChars(moveWI(s.length() << 1), s);
	}
	public abstract DynByteBuf putChars(int wi, CharSequence s);

	public DynByteBuf putAscii(CharSequence s) {
		return putAscii(moveWI(s.length()), s);
	}
	public abstract DynByteBuf putAscii(int wi, CharSequence s);

	public final DynByteBuf putUTF(CharSequence s) {
		if (s.length() > 0xFFFF) throw new ArrayIndexOutOfBoundsException("UTF too long: " + s.length());
		int len = byteCountUTF8(s);
		if (len > 0xFFFF) throw new ArrayIndexOutOfBoundsException("UTF too long: " + len);
		return putShort(len).putUTFData0(s, len);
	}
	public final DynByteBuf putVarIntUTF(CharSequence s) {
		int len = byteCountUTF8(s);
		putVarLong(this, len);
		return putUTFData0(s, len);
	}
	@RequireUpgrade
	public DynByteBuf putUTFData(CharSequence s) {
		return putUTFData0(s, byteCountUTF8(s));
	}
	public static int byteCountUTF8(CharSequence s) {
		int len = s.length();
		int utfLen = len;

		for (int i = 0; i < len; i++) {
			int c = s.charAt(i);
			if (c < 0x0001) {
				utfLen ++;
			} else if (c > 0x007F) {
				if (c > 0x07FF) {
					utfLen += 2;
				} else {
					utfLen ++;
				}
			}
		}

		return utfLen;
	}
	public abstract DynByteBuf putUTFData0(CharSequence s, int len);

	public final DynByteBuf putVarIntVIC(CharSequence s) {
		int len = byteCountVIC(s);
		putVarLong(this, len);
		return putVICData0(s, len);
	}
	public final DynByteBuf putVICData(CharSequence s) {
		return putVICData0(s, byteCountVIC(s));
	}
	public static int byteCountVIC(CharSequence s) {
		int len = s.length();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == 0 || c >= 0x80) {
				if (c >= 0x8080) {
					len += 2;
				} else {
					len++;
				}
			}
		}
		return len;
	}
	public abstract DynByteBuf putVICData0(CharSequence s, int len);

	public abstract DynByteBuf put(ByteBuffer buf);

	public abstract byte[] toByteArray();

	public abstract void preInsert(int off, int len);

	// endregion
	// region GETxxx

	public final byte[] readBytes(int len) {
		byte[] result = new byte[len];
		read(result, 0, len);
		return result;
	}

	public final void read(byte[] b) {
		read(b, 0, b.length);
	}
	public abstract void read(byte[] b, int off, int len);
	public final void read(int i, byte[] b) {
		read(i, b, 0, b.length);
	}
	public abstract void read(int i, byte[] b, int off, int len);

	public final boolean readBoolean(int i) {
		return get(i) != 0;
	}
	public final boolean readBoolean() {
		return get() != 0;
	}

	public abstract byte get(int i);
	public abstract byte readByte();

	public final int getU(int i) {
		return get(i)&0xFF;
	}
	public final int readUnsignedByte() {
		return get()&0xFF;
	}

	public final int readUnsignedShort() {
		return readUnsignedShort(moveRI(2));
	}
	public abstract int readUnsignedShort(int i);

	public final int readUShortLE() {
		return readUShortLE(moveRI(2));
	}
	public abstract int readUShortLE(int i);

	@Override
	public final short readShort() {
		return (short) readUnsignedShort();
	}
	public final short readShort(int i) {
		return (short) readUnsignedShort(i);
	}

	@Override
	public final char readChar() {
		return (char) readUnsignedShort();
	}
	public final char readChar(int i) {
		return (char) readUnsignedShort(i);
	}

	public final int readMedium() {
		return readMedium(moveRI(3));
	}
	public abstract int readMedium(int i);

	public final int readMediumLE() {
		return readMediumLE(moveRI(3));
	}
	public abstract int readMediumLE(int i);

	public static int zag(int i) {
		return (i >> 1) & ~(1 << 31) ^ -(i & 1);
	}
	public static long zag(long i) {
		return (i >> 1) & ~(1L << 63) ^ -(i & 1);
	}

	public final int readVarInt() {
		return readVarInt(true);
	}
	public abstract int readVarInt(boolean mayNeg);

	public final long readVarLong() {
		return readVarLong(true);
	}
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

	public final int readVSInt() {
		int b = readUnsignedByte();

		int mask = 0x80;
		int value = 0;
		for (int i = 0;; i += 8) {
			if ((b & mask) == 0)
				return value | (b & (mask-1)) << i;

			if (i == 32) break;

			value |= readUnsignedByte() << i;
			mask >>>= 1;
		}

		throw new RuntimeException("VSInt长度超限 at " + rIndex);
	}

	public final long readVSLong() {
		int b = readUnsignedByte();

		int mask = 0x80;
		long value = 0;
		for (int i = 0;; i += 8) {
			if ((b & mask) == 0)
				return value | (b & (mask-1)) << i;

			value |= (long) readUnsignedByte() << i;
			mask >>>= 1;
		}
	}

	public final int readInt() {
		return readInt(moveRI(4));
	}
	public abstract int readInt(int i);

	public final int readIntLE() {
		return readIntLE(moveRI(4));
	}
	public abstract int readIntLE(int i);

	public final long readUInt(int i) {
		return readInt(i) & 0xFFFFFFFFL;
	}
	public final long readUInt() {
		return readInt() & 0xFFFFFFFFL;
	}

	public final long readUIntLE() {
		return readIntLE() & 0xFFFFFFFFL;
	}
	public final long readUIntLE(int i) {
		return readIntLE(i) & 0xFFFFFFFFL;
	}

	public final long readLong() {
		return readLong(moveRI(8));
	}
	public abstract long readLong(int i);

	public final long readLongLE() {
		return readLongLE(moveRI(8));
	}
	public abstract long readLongLE(int i);

	public final float readFloat() {
		return readFloat(moveRI(4));
	}
	public final float readFloat(int i) {
		return Float.intBitsToFloat(readInt(i));
	}

	public final double readDouble() {
		return readDouble(moveRI(8));
	}
	public final double readDouble(int i) {
		return Double.longBitsToDouble(readLong(i));
	}

	public final String readAscii(int len) {
		return readAscii(moveRI(len), len);
	}
	public abstract String readAscii(int pos, int len);

	@Nonnull
	public final String readUTF() {
		return readUTF(readUnsignedShort());
	}
	public abstract String readUTF(int len);

	public final String readVarIntUTF() {
		return readVarIntUTF(1000000);
	}
	public final String readVarIntUTF(int max) {
		int l = readVarInt(false);
		if (l < 0) throw new IllegalArgumentException("Invalid string length " + l);
		if (l == 0) return "";
		if (l > max) throw new IllegalArgumentException("Maximum allowed " + max + " got " + l);
		return readUTF(l);
	}

	public final String readVIVIC() {
		return readVIC(readVarInt(false));
	}
	public abstract String readVIC(int len);

	public abstract String readLine();

	public abstract int readZeroTerminate();

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
		return getClass().getSimpleName()+"[rp="+rIndex+",wp="+wIndex+",cap="+capacity()+",maxCap="+maxCapacity()+"]";
	}
	public abstract String dump();
	// endregion
}
