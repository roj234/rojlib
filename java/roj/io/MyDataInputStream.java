package roj.io;

import org.jetbrains.annotations.NotNull;
import roj.text.GB18030;
import roj.text.UTF8MB4;
import roj.util.ArrayUtil;
import roj.util.DynByteBuf;
import sun.misc.Unsafe;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static roj.reflect.ReflectionUtils.BIG_ENDIAN;
import static roj.reflect.ReflectionUtils.u;

/**
 * 不会有MyDataOutput，因为有ByteList.WriteOut了，我承认它很难懂，但是我不想再复制同样的代码了
 * 解决上述问题请：
 *  1. 接口允许final的非静态方法 (or
 *  2. 多继承 (or
 *  3. 模板生成代码
 *
 * @author Roj234
 * @since 2024/3/10 0010 3:10
 */
public class MyDataInputStream extends FilterInputStream implements MyDataInput {
	public MyDataInputStream(InputStream in) {
		super(in);
	}

	@Override
	public final int read(byte[] b) throws IOException { return in.read(b, 0, b.length); }
	@Override
	public final int read(byte[] b, int off, int len) throws IOException { return in.read(b, off, len); }
	@Override
	public final void readFully(byte[] b) throws IOException { readFully(b, 0, b.length); }
	@Override
	public final void readFully(byte[] b, int off, int len) throws IOException {
		ArrayUtil.checkRange(b,off,len);
		int n = 0;
		while (n < len) {
			int r = in.read(b, off + n, len - n);
			if (r < 0) throw new EOFException();
			n += r;
		}
	}

	@Override
	public final int skipBytes(int n) throws IOException {
		int total = len - off;
		if (n < total) {
			off += n;
			return n;
		}

		int cur;
		while ((total<n) && ((cur = (int) in.skip(n-total)) > 0)) {
			total += cur;
		}

		return total;
	}

	public long skip(long n) throws IOException {
		long total = len - off;
		if (n < total) {
			off += n;
			return n;
		}
		return super.skip(n-total)+total;
	}

	@Override
	public int available() throws IOException { return len-off + in.available(); }

	private byte[] list;
	private int off, len;
	private int doRead(int count) throws IOException {
		int l = len;
		int o = off;
		if (l - o < count) {
			byte[] dst = list;
			if (dst.length < count) dst = list = new byte[count];
			System.arraycopy(list, off, dst, 0, l -= o);

			while (l < dst.length) {
				int r = in.read(dst, l, dst.length - l);
				if (r < 0) break;
				l += r;
			}

			if (l < count) throw new EOFException();
			off = 0;
			len = l;
			return 0;
		} else {
			off = o+count;
			return o;
		}
	}

	@Override
	public final boolean readBoolean() throws IOException {
		int i = doRead(1);
		return list[i] != 0;
	}
	@Override
	public final byte readByte() throws IOException {
		int i = doRead(1);
		return list[i];
	}
	@Override
	public final int readUnsignedByte() throws IOException {
		int i = doRead(1);
		return list[i]&0xFF;
	}
	@Override
	public final short readShort() throws IOException { return (short) readUnsignedShort(); }
	@Override
	public final char readChar() throws IOException { return (char) readUnsignedShort(); }
	@Override
	public final int readUnsignedShort() throws IOException {
		int i = doRead(2);
		return ((list[i]&0xFF) << 8) | (list[i+1]&0xFF);
	}
	@Override
	public final int readUShortLE() throws IOException {
		int i = doRead(2);
		return (list[i] & 0xFF) | ((list[i+1] & 0xFF) << 8);
	}
	@Override
	public final int readMedium() throws IOException {
		int i = doRead(3);
		byte[] l = list;
		return (l[i++] & 0xFF) << 16 | (l[i++] & 0xFF) << 8 | (l[i] & 0xFF);
	}
	@Override
	public final int readMediumLE() throws IOException {
		int i = doRead(3);
		byte[] l = list;
		return (l[i++] & 0xFF)| (l[i++] & 0xFF) << 8 | (l[i] & 0xFF) << 16;
	}
	@Override
	public final int readInt() throws IOException {
		int i = doRead(4);
		byte[] l = this.list;
		return BIG_ENDIAN ? u.getInt(l, Unsafe.ARRAY_BYTE_BASE_OFFSET+i) : (l[i++] & 0xFF) << 24 | (l[i++] & 0xFF) << 16 | (l[i++] & 0xFF) << 8 | (l[i] & 0xFF);
	}
	@Override
	public final int readIntLE() throws IOException {
		int i = doRead(4);
		byte[] l = this.list;
		return !BIG_ENDIAN ? u.getInt(l, Unsafe.ARRAY_BYTE_BASE_OFFSET+i) : (l[i++] & 0xFF) | (l[i++] & 0xFF) << 8 | (l[i++] & 0xFF) << 16 | (l[i] & 0xFF) << 24;
	}
	@Override
	public final long readUInt() throws IOException { return readInt() & 0xFFFFFFFFL; }
	@Override
	public final long readUIntLE() throws IOException { return readIntLE() & 0xFFFFFFFFL; }
	@Override
	public final long readLong() throws IOException {
		int i = doRead(8);
		byte[] l = this.list;
		if (BIG_ENDIAN) return u.getLong(l, Unsafe.ARRAY_BYTE_BASE_OFFSET+i);
		return (l[i++] & 0xFFL) << 56 |
			(l[i++] & 0xFFL) << 48 |
			(l[i++] & 0xFFL) << 40 |
			(l[i++] & 0xFFL) << 32 |
			(l[i++] & 0xFFL) << 24 |
			(l[i++] & 0xFFL) << 16 |
			(l[i++] & 0xFFL) << 8 |
			l[i] & 0xFFL;
	}
	@Override
	public final long readLongLE() throws IOException {
		int i = doRead(8);
		byte[] l = this.list;
		if (!BIG_ENDIAN) return u.getLong(l, Unsafe.ARRAY_BYTE_BASE_OFFSET+i);
		return (l[i++] & 0xFFL) |
			(l[i++] & 0xFFL) << 8 |
			(l[i++] & 0xFFL) << 16 |
			(l[i++] & 0xFFL) << 24 |
			(l[i++] & 0xFFL) << 32 |
			(l[i++] & 0xFFL) << 40 |
			(l[i++] & 0xFFL) << 48 |
			(l[i] & 0xFFL) << 56;
	}
	@Override
	public final float readFloat() throws IOException { return Float.intBitsToFloat(readInt()); }
	@Override
	public final double readDouble() throws IOException { return Double.longBitsToDouble(readLong()); }


	@Override
	public final int readVarInt() throws IOException { return readVarInt(true); }
	@Override
	public final int readVarInt(boolean zag) throws IOException {
		int value = 0;
		int i = 0;

		while (i <= 28) {
			int chunk = readByte();
			value |= (chunk & 0x7F) << i;
			i += 7;
			if ((chunk & 0x80) == 0) {
				if (zag) return MyDataInput.zag(value);
				if (value < 0) break;
				return value;
			}
		}
		throw new RuntimeException("VarInt太长");
	}


	@Override
	public final long readVarLong() throws IOException { return readVarLong(true); }
	@Override
	public final long readVarLong(boolean zag) throws IOException {
		long value = 0;
		int i = 0;

		while (i <= 63) {
			int chunk = readByte();
			value |= (chunk & 0x7F) << i;
			i += 7;
			if ((chunk & 0x80) == 0) {
				if (zag) return MyDataInput.zag(value);
				if (value < 0) break;
				return value;
			}
		}

		throw new RuntimeException("VarLong太长");
	}

	@Override
	public final int readVUInt() throws IOException {
		int b = readUnsignedByte();
		if ((b&0x80) == 0) return b;
		if ((b&0x40) == 0) return ((b&0x3F)<< 8) | readUnsignedByte();
		if ((b&0x20) == 0) return ((b&0x1F)<<16) | readUShortLE();
		if ((b&0x10) == 0) return ((b&0x0F)<<24) | readMediumLE();
		if ((b&0x08) == 0) {
			if ((b&7) == 0)
				return readIntLE();
		}
		throw new RuntimeException("VUInt太长");
	}
	@Override
	public final long readVULong() throws IOException {
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
	}

	@Override
	public final String readAscii(int len) throws IOException {
		int i = doRead(len);
		return new String(list, i, i+len, StandardCharsets.ISO_8859_1);
	}

	@Override
	@NotNull
	public final String readUTF() throws IOException { return readUTF(readUnsignedShort()); }
	@Override
	public final String readVUIUTF() throws IOException { return readVUIUTF(DEFAULT_MAX_STRING_LEN); }
	@Override
	public final String readVUIUTF(int max) throws IOException {
		int len = readVUInt();
		if (len > max) throw new IllegalArgumentException("字符串长度不正确: "+len+" > "+max);
		return readUTF(len);
	}
	@Override
	public final String readUTF(int len) throws IOException { return readUTF(len, IOUtil.getSharedCharBuf()).toString(); }
	@Override
	public final <T extends Appendable> T readUTF(int len, T target) throws IOException {
		if (len < 0) throw new IllegalArgumentException("length="+len);
		if (len > 0) {
			int i = doRead(len);
			UTF8MB4.CODER.decodeFixedIn(DynByteBuf.wrap(list, i, len),len,target);
		}
		return target;
	}

	@Override
	public final String readVUIGB() throws IOException { return readGB(DEFAULT_MAX_STRING_LEN); }
	@Override
	public final String readVUIGB(int max) throws IOException {
		int len = readVUInt();
		if (len > max) throw new IllegalArgumentException("字符串长度不正确: "+len+" > "+max);
		return readGB(len);
	}
	@Override
	public final String readGB(int len) throws IOException { return readGB(len, IOUtil.getSharedCharBuf()).toString(); }
	@Override
	public final <T extends Appendable> T readGB(int len, T target) throws IOException {
		if (len < 0) throw new IllegalArgumentException("length="+len);
		if (len > 0) {
			int i = doRead(len);
			GB18030.CODER.decodeFixedIn(DynByteBuf.wrap(list, i, len),len,target);
		}
		return target;
	}

	@Override
	public final String readLine() throws IOException {
		throw new UnsupportedOperationException("未实现...");
	}
}