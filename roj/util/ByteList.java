package roj.util;

import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.lavac.api.PreCompile;
import roj.math.MathUtils;
import roj.text.CharList;
import roj.text.TextUtil;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.List;

import static java.lang.Character.MAX_HIGH_SURROGATE;
import static java.lang.Character.MIN_HIGH_SURROGATE;
import static roj.reflect.FieldAccessor.u;

/**
 * @author Roj234
 * @since 2021/5/29 20:45
 */
public class ByteList extends DynByteBuf implements CharSequence, Appendable {
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

	public static ByteList allocate(int cap) {
		return new ByteList(cap);
	}
	public static ByteList allocate(int capacity, int maxCapacity) {
		return new ByteList(capacity) {
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

	public ByteList() {
		list = ArrayCache.BYTES;
	}

	public ByteList(int len) {
		list = new byte[len];
	}

	public ByteList(byte[] array) {
		list = array;
		wIndex = array.length;
	}

	@Override
	public int capacity() {
		return list.length;
	}
	@Override
	public int maxCapacity() {
		return 2147000000;
	}

	@Override
	public final boolean isDirect() {
		return false;
	}

	@Override
	public boolean hasArray() {
		return true;
	}
	@Override
	public byte[] array() {
		return list;
	}
	public int arrayOffset() {
		return 0;
	}
	public final int relativeArrayOffset() {
		return arrayOffset()+rIndex;
	}

	@Override
	public final void copyTo(long address, int len) {
		DirectByteList.copyFromArray(list, Unsafe.ARRAY_BYTE_BASE_OFFSET, arrayOffset() + moveRI(len), address, len);
	}

	public void clear() {
		wIndex = rIndex = 0;
		if (br != null) br.reset(this);
	}

	public void ensureCapacity(int required) {
		if (required > list.length) {
			int newLen = list.length == 0 ? Math.max(required, 256) : ((required * 3) >>> 1) + 1;

			ArrayCache cache = ArrayCache.getDefaultCache();
			cache.putArray(list);
			byte[] newList = cache.getByteArray(newLen, false);

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
		if (i + req > wIndex) throw new ArrayIndexOutOfBoundsException("pos="+i+",len="+req+",cap="+wIndex);
		return i + arrayOffset();
	}

	// region Byte Streams

	public final ByteList readStreamFully(InputStream in) throws IOException {
		return readStreamFully(in, true);
	}

	public final ByteList readStreamFully(InputStream in, boolean close) throws IOException {
		int i = in.available();
		if (i <= 1) i = 127;
		ensureCapacity(wIndex + i + 1);

		int real;
		do {
			real = in.read(this.list, arrayOffset() + wIndex, capacity() - wIndex);
			if (real < 0) break;
			wIndex += real;
			ensureCapacity(wIndex + 1);
		} while (true);
		if (close) in.close();
		return this;
	}

	public final int readStream(InputStream in, int max) throws IOException {
		ensureCapacity(wIndex + max);
		int read = wIndex;
		while (true) {
			int r = in.read(list, arrayOffset() + wIndex, max);
			if (r <= 0) break;

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

	public final ByteList putUTFData(CharSequence s) { return putUTFData0(s, byteCountUTF8(s)); }
	public final ByteList putUTFData0(CharSequence s, int len) {
		int wi = moveWI(len);
		jutf8_encode_all(s, list, Unsafe.ARRAY_BYTE_BASE_OFFSET+wi+arrayOffset(), len);
		return this;
	}

	public final DynByteBuf putUtf8mb4Data(CharSequence s, int len) {
		int wi = moveWI(len);
		utf8mb4_encode_all(s, list, Unsafe.ARRAY_BYTE_BASE_OFFSET+wi+arrayOffset(), len);
		return this;
	}

	public final ByteList putVStrData0(CharSequence s, int len) {
		int wi = moveWI(len) + arrayOffset();
		byte[] list = this.list;

		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= 0x8080) {
				list[wi++] = 0;
				list[wi++] = (byte) (c >>> 8);
				list[wi++] = (byte) c;
			} else if (c == 0 || c >= 0x80) {
				c -= 0x80;
				list[wi++] = (byte) ((c >>> 8) | 0x80);
				list[wi++] = (byte) c;
			} else {
				list[wi++] = (byte) c;
			}
		}

		wi = wIndex-wi-arrayOffset();
		if (wi != 0) throw new IllegalArgumentException("长度断言失败,多了="+wi);

		return this;
	}

	public final ByteList putAscii(CharSequence s) {
		return putAscii(moveWI(s.length()), s);
	}
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

	public Appendable append(CharSequence s) throws IOException {
		return append(s,0, s.length());
	}
	@SuppressWarnings("deprecation")
	public Appendable append(CharSequence s, int start, int end) throws IOException {
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
	public Appendable append(char c) throws IOException {
		return put((byte) c);
	}

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
		if (tmp.length < wIndex + len) {
			if (immutableCapacity()) throw new BufferOverflowException();
			tmp = new byte[wIndex + len];
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
		if (len < 0 || off < 0 || len > b.length - off) throw new ArrayIndexOutOfBoundsException();
		if (len > 0) {
			System.arraycopy(list, moveRI(len) + arrayOffset(), b, off, len);
		}
	}

	public final void read(int i, byte[] b, int off, int len) {
		if (len < 0 || off < 0 || len > b.length - off) throw new ArrayIndexOutOfBoundsException();
		if (len > 0) {
			System.arraycopy(list, testWI(i, len), b, off, len);
		}
	}

	public final byte get(int i) {
		return list[testWI(i, 1)];
	}
	@Override
	public final byte readByte() {
		return list[moveRI(1) + arrayOffset()];
	}

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

	public final String readUTF(int len) {
		if (len <= 0) return "";

		int off = moveRI(len);

		CharList sb = IOUtil.getSharedCharBuf();
		sb.ensureCapacity(len);
		try {
			utf8mb4_decode(list, Unsafe.ARRAY_BYTE_BASE_OFFSET+arrayOffset(), off, len, sb, -1, false);
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return sb.toString();
	}

	public final String readVStr(int len) {
		if (len <= 0) return "";

		int off = moveRI(len) + arrayOffset();

		byte[] list = this.list;

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

			byte b = list[off++];
			if (b == 0) {
				ob[j++] = (char) ((list[off++] & 0xFF) << 8 | (list[off++] & 0xFF));
				len -= 3;
			} else if ((b & 0x80) != 0) {
				ob[j++] = (char) ((((b & 0x7F) << 8) | (list[off++] & 0xFF)) + 0x80);
				len -= 2;
			} else {
				ob[j++] = (char) b;
				len -= 1;
			}
		}

		if (len < 0) throw new IllegalStateException("Partial character at end");

		String s = new String(ob, 0, j);
		cache.putArray(ob);
		return s;
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

	public final int readZeroTerminate() {
		int i = rIndex + arrayOffset();
		int end = wIndex + arrayOffset();
		byte[] l = list;
		while (i < end) {
			if (l[i] == 0) return i - arrayOffset() - rIndex;
			i++;
		}
		return -1;
	}

	public final ByteList slice(int length) {
		if (length == 0) return new ByteList();
		ByteList list = slice(rIndex, length);
		rIndex += length;
		return list;
	}
	public final ByteList slice(int off, int len) {
		return new Slice(list, off + arrayOffset(), len);
	}

	// region Old BitReader
	private BitWriter br;

	public final int readBit1() {
		if (br == null) br = new BitWriter(this);
		return br.readBit1();
	}

	public final int readBit(int numBits) {
		if (br == null) br = new BitWriter(this);
		return br.readBit(numBits);
	}

	public void skipBits(int i) {
		if (br == null) br = new BitWriter(this);
		br.skipBits(i);
	}

	public void endBitRead() {
		if (br == null) br = new BitWriter(this);
		br.endBitRead();
	}

	// endregion
	// region ASCII sequence

	@Override
	public int length() {
		return wIndex;
	}

	@Override
	public char charAt(int i) {
		return (char) list[testWI(i, 1) + arrayOffset()];
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		return new Slice(list, start, end - start);
	}

	// endregion
	// endregion
	// region Buffer Ops

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

	@PreCompile
	public static int FOURCC(CharSequence x) {
		return ((x.charAt(0) & 0xFF) << 24) | ((x.charAt(1) & 0xFF) << 16) | ((x.charAt(2) & 0xFF) << 8) | ((x.charAt(3) & 0xFF));
	}

	public static void decodeUTF(int len, Appendable out, DynByteBuf in) throws IOException {
		if (len <= 0) {
			len = in.readableBytes();
			if (len <= 0) return;
		}
		int ri = in.moveRI(len);

		if (out instanceof CharList) ((CharList) out).ensureCapacity(len);

		if (in.isDirect()) {
			utf8mb4_decode(null, in.address(), ri, len, out, -1, false);
		} else {
			utf8mb4_decode(in.array(), Unsafe.ARRAY_BYTE_BASE_OFFSET+in.arrayOffset(), ri, len, out, -1, false);
		}
	}

	public static int utf8mb4_decode(Object ref, long base, int pos, int len, Appendable out, int outMax, boolean partial) throws IOException {
		if (pos < 0) throw new IllegalArgumentException("pos="+pos);

		long i = base+pos;
		long max = i+len;

		int c;
		while (i < max) {
			if (outMax == 0) return (int) (i-base);

			c = u.getByte(ref, i);
			if (c < 0) break;
			i++;
			outMax--;
			out.append((char) c);
		}

		truncate: {
		malformed: {
		int c2, c3, c4;
		while (i < max) {
			if (outMax-- == 0) break;

			c = u.getByte(ref, i++) & 0xFF;
			switch (c >> 4) {
				case 0: case 1: case 2: case 3:
				case 4: case 5: case 6: case 7:
					/* 0xxxxxxx*/
					out.append((char) c);
					break;
				case 12: case 13:
					/* 110xxxxx   10xxxxxx*/
					if (i >= max) break truncate;

					c2 = u.getByte(ref, i++);
					if ((c2 & 0xC0) != 0x80) {
						i -= 1;
						break malformed;
					}

					out.append((char) (((c & 0x1F) << 6) | (c2 & 0x3F)));
					break;
				case 14:
					/* 1110xxxx  10xxxxxx  10xxxxxx */
					if (i+1 >= max) break truncate;

					c2 = u.getByte(ref, i++);
					c3 = u.getByte(ref, i++);
					if (((c2^c3) & 0xC0) != 0) {
						i -= 2;
						break malformed;
					}

					out.append((char) (((c & 0x0F) << 12) | ((c2 & 0x3F) << 6) | c3 & 0x3F));
					break;
				default:
				case 15:
					/* 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx */
					if (i+2 >= max) break truncate;

					c2 = u.getByte(ref, i++);
					c3 = u.getByte(ref, i++);
					c4 = u.getByte(ref, i++);
					if (((c2^c3^c4) & 0xC0) != 0x80) {
						i -= 3;
						break malformed;
					}

					c4 = ((c & 7) << 18) | ((c2 & 0x3F) << 12) | ((c3 & 0x3F) << 6) | c4 & 0x3F;
					if (Character.charCount(c4) == 1) {
						out.append((char) c4);
					} else {
						if (outMax-- == 0) { i -= 4; break;}

						out.append(Character.highSurrogate(c4)).append(Character.lowSurrogate(c4));
					}
					break;
			}
		}

		return (int) (i-base);}
		throw new IllegalArgumentException((int) (i-base) + " 附近解码错误");}
		if (partial) return (int) (i-base);
		throw new IllegalArgumentException("被截断");
	}
	public static void jutf8_encode_all(CharSequence s, Object ref, long addr, int byte_len) {
		int i = 0;
		int len = s.length();
		overflow:{
			while (i < len) {
				int c = s.charAt(i);
				if (c > 0x7F) break;
				i++;

				if ((byte_len -= 1) < 0) break overflow;
				u.putByte(ref, addr++, (byte) c);
			}

			while (i < len) {
				int c = s.charAt(i++);

				if (c > 0x7F) {
					if (c <= 0x7FFF) {
						if ((byte_len -= 2) < 0) break overflow;
						u.putByte(ref, addr++, (byte) (0xC0 | ((c >> 6) & 0x1F)));
						u.putByte(ref, addr++, (byte) (0x80 | (c & 0x3F)));
					} else {
						if ((byte_len -= 3) < 0) break overflow;
						u.putByte(ref, addr++, (byte) (0xE0 | ((c >> 12) & 0x0F)));
						u.putByte(ref, addr++, (byte) (0x80 | ((c >> 6) & 0x3F)));
						u.putByte(ref, addr++, (byte) (0x80 | (c & 0x3F)));
					}
				} else {
					if ((byte_len -= 1) < 0) break overflow;
					u.putByte(ref, addr++, (byte) c);
				}
			}
		}
		if (byte_len < 0) throw new IllegalArgumentException("长度断言失败,少了至少"+ -byte_len +"字节");
	}
	public static void utf8mb4_encode_all(CharSequence s, Object ref, long addr, int byte_len) {
		int i = 0;
		int len = s.length();
		overflow:{
		while (i < len) {
			int c = s.charAt(i);
			if (c > 0x7F) break;
			i++;

			if ((byte_len -= 1) < 0) break overflow;
			u.putByte(ref, addr++, (byte) c);
		}

		while (i < len) {
			int c = s.charAt(i++);
			if (c >= MIN_HIGH_SURROGATE && c <= MAX_HIGH_SURROGATE) {
				c = TextUtil.codepoint(c,s.charAt(i++));
			}

			if (c <= 0x7FF) {
				if (c > 0x7F) {
					if ((byte_len -= 2) < 0) break overflow;
					u.putByte(ref, addr++, (byte) (0xC0 | ((c >> 6) & 0x1F)));
					u.putByte(ref, addr++, (byte) (0x80 | (c & 0x3F)));
				} else {
					if ((byte_len -= 1) < 0) break overflow;
					u.putByte(ref, addr++, (byte) c);
				}
			} else {
				if (c > 0xFFFF) {
					if ((byte_len -= 4) < 0) break overflow;
					u.putByte(ref, addr++, (byte) (0xF0 | ((c >> 18) & 0x07)));
					u.putByte(ref, addr++, (byte) (0x80 | ((c >> 12) & 0x3F)));
				} else {
					if ((byte_len -= 3) < 0) break overflow;
					u.putByte(ref, addr++, (byte) (0xE0 | ((c >> 12) & 0x0F)));
				}
				u.putByte(ref, addr++, (byte) (0x80 | ((c >> 6) & 0x3F)));
				u.putByte(ref, addr++, (byte) (0x80 | (c & 0x3F)));
			}
		}
		}
		if (byte_len < 0) throw new IllegalArgumentException("长度断言失败,少了至少"+ -byte_len +"字节");
	}
	public static long utf8mb4_encode(CharSequence s, Object ref, long addr, int max_len) {
		long base = addr;
		long max = addr+max_len;

		int i = 0;
		int len = s.length();
		while (i < len) {
			if (addr == max) break;

			int c = s.charAt(i);
			if (c > 0x7F) break;
			i++;
			u.putByte(ref, addr++, (byte) c);
		}

		int previ;
		while (i < len) {
			if (addr == max) break;
			previ = i;

			int c = s.charAt(i++);
			if (c >= MIN_HIGH_SURROGATE && c <= MAX_HIGH_SURROGATE) {
				if (i == len) {
					if (i == s.length()) throw new IllegalArgumentException("缺失surrogate pair");

					i--;
					break;
				}
				c = TextUtil.codepoint(c,s.charAt(i++));
			}

			if (c <= 0x7FF) {
				if (c > 0x7F) {
					if (max-addr < 2) { i = previ; break; }
					u.putByte(ref, addr++, (byte) (0xC0 | ((c >> 6) & 0x1F)));
					u.putByte(ref, addr++, (byte) (0x80 | (c & 0x3F)));
				} else {
					u.putByte(ref, addr++, (byte) c);
				}
			} else {
				if (c > 0xFFFF) {
					if (max-addr < 4) { i = previ; break; }
					u.putByte(ref, addr++, (byte) (0xF0 | ((c >> 18) & 0x07)));
					u.putByte(ref, addr++, (byte) (0x80 | ((c >> 12) & 0x3F)));
				} else {
					if (max-addr < 3) { i = previ; break; }
					u.putByte(ref, addr++, (byte) (0xE0 | ((c >> 12) & 0x0F)));
				}
				u.putByte(ref, addr++, (byte) (0x80 | ((c >> 6) & 0x3F)));
				u.putByte(ref, addr++, (byte) (0x80 | (c & 0x3F)));
			}
		}

		return ((addr-base) << 32) | i;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ByteList)) return false;
		ByteList ot = (ByteList) o;
		return ArrayUtil.rangedEquals(list, arrayOffset() + rIndex, wIndex, ot.list, ot.arrayOffset() + ot.rIndex, ot.wIndex);
	}

	@Override
	public int hashCode() { return ArrayUtil.rangedHashCode(list, arrayOffset() + rIndex, wIndex); }

	@Override
	public String toString() { return new String(list, 0, arrayOffset()+rIndex, wIndex-rIndex); }

	public static class WriteOut extends ByteList {
		private OutputStream out;
		private int fakeWriteIndex;

		private BufferPool pool;
		private DynByteBuf buf;

		public WriteOut(OutputStream out) { this(out, 1024, BufferPool.localPool()); }
		public WriteOut(OutputStream out, int buffer, BufferPool pool) {
			super();
			this.out = out;
			this.pool = pool;
			this.buf = pool.buffer(false, buffer);
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
					buf = pool.expand(buf, cap);
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
				if (pool != null) {
					pool.reserve(buf);
					pool = null;
					buf = null;
				}
			}

		}

		private static UnsupportedOperationException a() { return new UnsupportedOperationException("stream buffer is not readable"); }
	}

	public static final class Slice extends ByteList {
		private int off, len;

		public Slice() {}
		public Slice(byte[] b, int off, int len) { setR(b, off, len); }

		public ByteList setW(byte[] b, int off, int len) {
			wIndex = rIndex = 0;

			list = b;
			this.off = off;
			this.len = len;

			if (off+len > list.length || list.length-off < len || (off|len)<0)
				throw new ArrayIndexOutOfBoundsException("pos="+off+",len="+len+",cap="+b.length);
			return this;
		}

		public ByteList setR(byte[] array, int off, int len) {
			setW(array,off,len).wIndex = len;
			return this;
		}

		public void update(int offset, int length) {
			off = offset;
			len = length;
		}

		@Override
		public int capacity() {
			return len;
		}

		@Override
		public int maxCapacity() {
			return len;
		}

		@Override
		public void ensureCapacity(int required) {
			if (required > len) throw new ReadOnlyBufferException();
		}

		@Override
		public int arrayOffset() {
			return off;
		}

		@Override
		public ByteList setArray(byte[] array) {
			throw new ReadOnlyBufferException();
		}

		@Override
		public void preInsert(int off, int len) {
			throw new ReadOnlyBufferException();
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
		public boolean immutableCapacity() {
			return true;
		}
	}
}