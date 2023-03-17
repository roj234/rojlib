package roj.util;

import roj.io.IOUtil;
import roj.lavac.api.PreCompile;
import roj.text.CharList;
import roj.text.TextUtil;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/29 20:45
 */
public class ByteList extends DynByteBuf implements CharSequence, Appendable {
	public static final ByteList EMPTY = new Slice(EmptyArrays.BYTES, 0, 0);

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
		list = EmptyArrays.BYTES;
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
			byte[] newList = new byte[list.length == 0 ? Math.max(required, 256) : ((required * 3) >>> 1) + 1];

			if (wIndex > 0) System.arraycopy(list, 0, newList, 0, wIndex);
			list = newList;
		}
	}

	public ByteList setArray(byte[] array) {
		if (array == null) array = EmptyArrays.BYTES;

		list = array;
		clear();
		wIndex = array.length;
		return this;
	}

	@Override
	final int testWI(int i, int req) {
		if (i + req > wIndex) throw new ArrayIndexOutOfBoundsException("req=" + i + ",wIdx=" + wIndex);
		return i + arrayOffset();
	}

	// region Byte Streams

	public final ByteList readStreamFully(InputStream in) throws IOException {
		return readStreamFully(in, true);
	}

	public final ByteList readStreamFully(InputStream in, boolean close) throws IOException {
		int i = in.available();
		// todo remove this line?
		if (i <= 1) i = 127;
		ensureCapacity(wIndex + i + 1);

		int real;
		do {
			real = in.read(this.list, arrayOffset() + wIndex, list.length - wIndex);
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

	public final ByteList put(byte e) {
		int i = arrayOffset() + moveWI(1);
		list[i] = e;
		return this;
	}

	public final ByteList put(int i, byte e) {
		list[testWI(i, 1)] = e;
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

	public final ByteList putUTFData(CharSequence s) {
		return putUTFData0(s, byteCountUTF8(s));
	}

	public final ByteList putUTFData0(CharSequence s, int len) {
		int wi = moveWI(len) + arrayOffset();
		byte[] list = this.list;

		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if ((c > 0) && (c <= 0x007F)) {
				list[wi++] = (byte) c;
			} else if (c > 0x07FF) {
				list[wi++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
				list[wi++] = (byte) (0x80 | ((c >> 6) & 0x3F));
				list[wi++] = (byte) (0x80 | (c & 0x3F));
			} else {
				list[wi++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
				list[wi++] = (byte) (0x80 | (c & 0x3F));
			}
		}
		return this;
	}

	public final ByteList putVICData0(CharSequence s, int len) {
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
		testWI(rIndex, len);

		CharList rct = IOUtil.getSharedCharBuf();
		rct.ensureCapacity(len);
		try {
			decodeUTF0(this, rIndex, len + rIndex, rct, 0);
		} catch (IOException e) {
			throw new IllegalArgumentException(e.getMessage());
		}
		rIndex += len;
		return rct.toString();
	}

	public final String readVIC(int len) {
		int off = moveRI(len) + arrayOffset();

		CharList tmp = IOUtil.getSharedCharBuf();
		tmp.clear();
		tmp.ensureCapacity(len / 2);

		byte[] list = this.list;

		while (len > 0) {
			byte b = list[off++];

			if (b == 0) {
				tmp.append((char) ((list[off++] & 0xFF) << 8 | (list[off++] & 0xFF)));
				len -= 3;
			} else if ((b & 0x80) != 0) {
				tmp.append((char) ((((b & 0x7F) << 8) | (list[off++] & 0xFF)) + 0x80));
				len -= 2;
			} else {
				tmp.append((char) b);
				len -= 1;
			}
		}

		if (len < 0) throw new IllegalStateException("Partial character at end / " + tmp);

		return tmp.toString();
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
		return getClass().getSimpleName()+TextUtil.dumpBytes(list, arrayOffset()+rIndex, wIndex-rIndex)+'\n';
	}

	// endregion

	@PreCompile
	public static int FOURCC(CharSequence x) {
		return ((x.charAt(0) & 0xFF) << 24) | ((x.charAt(1) & 0xFF) << 16) | ((x.charAt(2) & 0xFF) << 8) | ((x.charAt(3) & 0xFF));
	}

	public static ByteList encodeUTF(CharSequence s) {
		int len = byteCountUTF8(s);
		return new ByteList(len).putUTFData0(s, len);
	}

	public static String readUTF(ByteList list) throws IOException {
		CharList cl = IOUtil.getSharedCharBuf();
		decodeUTF0(list, 0, list.wIndex(), cl, 0);
		return cl.toString();
	}

	public static void decodeUTF(int cnt, Appendable out, DynByteBuf in) throws IOException {
		if (cnt <= 0) cnt = in.wIndex();
		else in.testWI(in.rIndex, cnt);

		if (out instanceof CharList) ((CharList) out).ensureCapacity(cnt);

		if (in.isDirect()) {
			in.rIndex = DirectByteList.decodeUTF0(in.address(), in.rIndex, cnt, out);
		} else {
			in.rIndex = decodeUTF0(in, in.rIndex, cnt, out, 0);
		}
	}

	public static int decodeUTFPartial(int off, int max, Appendable out, ByteList in) throws IOException {
		if (max <= 0) max = in.wIndex();

		return decodeUTF0(in, off, max, out, FLAG_PARTIAL) - off;
	}

	public static final int FLAG_PARTIAL = 1;
	@SuppressWarnings("fallthrough")
	static int decodeUTF0(DynByteBuf in, int i, int max, Appendable out, int flag) throws IOException {
		i += in.arrayOffset();
		max += in.arrayOffset();
		byte[] inn = in.array();

		int c;
		while (i < max) {
			c = inn[i] & 0xFF;
			if (c > 127) break;
			i++;
			out.append((char) c);
		}

		int c2, c3, c4;
		cyl:
		while (i < max) {
			c = inn[i] & 0xFF;
			switch (c >> 4) {
				case 0: case 1: case 2: case 3:
				case 4: case 5: case 6: case 7:
					/* 0xxxxxxx*/
					i++;
					out.append((char) c);
					break;
				case 12: case 13:
					/* 110xxxxx   10xxxxxx*/
					if (i+2 > max) {
						if ((flag & 1) != 0) break cyl;
						throw new UTFDataFormatException("malformed input: partial character at end");
					}

					i++;
					c2 = inn[i++];
					if ((c2 & 0xC0) != 0x80) {
						i -= in.arrayOffset();
						throw new UTFDataFormatException("malformed input around byte " + i);
					}

					out.append((char) (((c & 0x1F) << 6) | (c2 & 0x3F)));
					break;
				case 14:
					/* 1110xxxx  10xxxxxx  10xxxxxx */
					if (i+3 > max) {
						if ((flag & 1) != 0) break cyl;
						throw new UTFDataFormatException("malformed input: partial character at end");
					}

					i++;
					c2 = inn[i++];
					c3 = inn[i++];
					if (((c2^c3) & 0xC0) != 0) {
						i -= in.arrayOffset();
						throw new UTFDataFormatException("malformed input around byte " + i);
					}

					out.append((char) (((c & 0x0F) << 12) | ((c2 & 0x3F) << 6) | c3 & 0x3F));
					break;
				default:
				case 15:
					/* 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx */
					if (i+4 > max) {
						if ((flag & 1) != 0) break cyl;
						throw new UTFDataFormatException("malformed input: partial character at end");
					}

					i++;
					c2 = inn[i++];
					c3 = inn[i++];
					c4 = inn[i++];
					if (((c2^c3^c4) & 0xC0) != 0x80) {
						i -= in.arrayOffset();
						throw new UTFDataFormatException("malformed input around byte " + i);
					}

					c4 = ((c & 7) << 18) | ((c2 & 0x3F) << 12) | ((c3 & 0x3F) << 6) | c4 & 0x3F;
					if (Character.charCount(c4) == 1) {
						out.append((char) c4);
					} else {
						out.append(Character.highSurrogate(c4)).append(Character.lowSurrogate(c4));
					}
					break;
			}
		}

		return i-in.arrayOffset();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ByteList)) return false;
		ByteList ot = (ByteList) o;
		return ArrayUtil.rangedEquals(list, arrayOffset() + rIndex, wIndex, ot.list, ot.arrayOffset() + ot.rIndex, ot.wIndex);
	}

	@Override
	public int hashCode() {
		return ArrayUtil.rangedHashCode(list, arrayOffset() + rIndex, wIndex);
	}

	@Override
	public String toString() {
		return new String(list, arrayOffset()+rIndex, wIndex-rIndex, StandardCharsets.US_ASCII);
	}

	public static class Streamed extends ByteList {
		OutputStream out;
		int fakeWriteIndex;

		public Streamed() {
			super(12);
		}

		public Streamed(OutputStream out) {
			super(1024);
			this.out = out;
		}

		protected Streamed(OutputStream out, boolean unused) {
			super();
			this.out = out;
		}

		public final void setOut(OutputStream out) {
			this.out = out;
		}

		@Override
		public int wIndex() {
			flush();
			return fakeWriteIndex;
		}

		protected int realWIndex() {
			return wIndex;
		}

		@Override
		public void wIndex(int w) {
			flush();
			this.fakeWriteIndex = w;
		}

		@Override
		public boolean isBuffer() {
			return false;
		}

		int moveWI(int i) {
			ensureCapacity(wIndex+i);
			int j = wIndex;
			wIndex = j+i;
			return j;
		}

		@Override
		public void ensureCapacity(int required) {
			if (required > list.length) {
				required -= wIndex;
				flush();
				super.ensureCapacity(required);
			}
		}

		public void flush() {
			if (wIndex > 0) {
				if (out != null) {
					try {
						out.write(list, 0, wIndex);
					} catch (IOException e) {
						Helpers.athrow(e);
					}
				}
				fakeWriteIndex += wIndex;
				wIndex = 0;
			}
		}

		public boolean hasArray() { return false; }
		public byte[] array() { throw a(); }
		public ByteList setArray(byte[] array) { throw a(); }
		public synchronized void close() throws IOException {
			if (out != null) {
				try {
					flush();
				} finally {
					out.close();
					out = null;
				}
			}
		}
		int moveRI(int i) { throw a(); }

		private static UnsupportedOperationException a() { return new UnsupportedOperationException("stream buffer is not readable"); }
	}

	public static final class Slice extends ByteList {
		private int off, len;

		public Slice() {}

		public Slice(byte[] array, int start, int len) {
			super(array);
			this.wIndex = len;
			this.len = len;
			this.off = start;
		}

		public ByteList set(byte[] array, int start, int len) {
			wIndex = rIndex = 0;

			list = array;
			off = start;
			this.len = len;
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