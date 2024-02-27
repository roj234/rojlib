package roj.asmx.classpak.loader;

import java.io.*;
import java.nio.charset.StandardCharsets;

final class SDI extends InputStream {
	public final RandomAccessFile in;
	public final InputStream in2;
	public SDI(File in) throws IOException {
		this.in = new RandomAccessFile(in, "r");
		this.in2 = new FileInputStream(this.in.getFD());
	}
	public SDI(InputStream in2) {
		this.in = null;
		this.in2 = in2;
	}

	@Override
	public void close() throws IOException { if (in != null) in.close(); in2.close(); }

	@Override
	public int read() throws IOException {
		try {
			int i = doRead(1);
			return list[i] & 0xFF;
		} catch (EOFException e) {
			return -1;
		}
	}

	public final int read(byte[] b, int off, int len) throws IOException {
		int total = this.lim - this.pos;
		if (len <= total) {
			System.arraycopy(this.list, this.pos, b, off, len);
			this.pos += len;
			return len;
		}
		System.arraycopy(this.list, this.pos, b, off, total);
		this.pos = this.lim = 0;
		int r = in2.read(b, off + total, len - total);
		return r < 0 ? total == 0 ? r : total : total+r;
	}

	public long skip(long n) throws IOException {
		long total = lim - pos;
		if (n <= total) {
			pos += n;
			return n;
		}

		this.pos = this.lim = 0;
		long skip;
		if (in != null) {
			long fp = in.getFilePointer();
			skip = Math.min(n - total, in.length() - fp);
			in.seek(skip+fp);
		} else {
			skip = in2.skip(n - total);
		}
		return skip+total;
	}

	private byte[] list = new byte[512];
	private int pos, lim;
	private int doRead(int count) throws IOException {
		int l = lim;
		int o = pos;
		if (o+count > l) {
			byte[] dst = list;
			if (dst.length < count) dst = new byte[count];
			System.arraycopy(list, pos, dst, 0, l -= o);
			list = dst;

			while (l < dst.length) {
				int r = in2.read(dst, l, dst.length - l);
				if (r < 0) break;
				l += r;
			}

			if (l < count) throw new EOFException();
			pos = count;
			lim = l;
			return 0;
		} else {
			pos = o + count;
			return o;
		}
	}

	public final int readUnsignedByte() throws IOException {
		int i = doRead(1);
		return list[i]&0xFF;
	}
	public final int readUShortLE() throws IOException {
		int i = doRead(2);
		return (list[i] & 0xFF) | ((list[i+1] & 0xFF) << 8);
	}
	public final int readMediumLE() throws IOException {
		int i = doRead(3);
		byte[] l = list;
		return (l[i++] & 0xFF)| (l[i++] & 0xFF) << 8 | (l[i] & 0xFF) << 16;
	}
	public final int readInt() throws IOException {
		int i = doRead(4);
		byte[] l = this.list;
		return (l[i++] & 0xFF) << 24 | (l[i++] & 0xFF) << 16 | (l[i++] & 0xFF) << 8 | (l[i] & 0xFF);
	}
	public final int readIntLE() throws IOException {
		int i = doRead(4);
		byte[] l = this.list;
		return (l[i++] & 0xFF) | (l[i++] & 0xFF) << 8 | (l[i++] & 0xFF) << 16 | (l[i] & 0xFF) << 24;
	}
	public final long readLong() throws IOException {
		int i = doRead(8);
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

	public final String readUTF(int len) throws IOException {
		if (len > 0) {
			int i = doRead(len);
			return new String(list, i, len, StandardCharsets.UTF_8);
		}
		return "";
	}

	public void seek(long pos) throws IOException {
		in.seek(pos);
		this.pos = lim = 0;
	}
	public long position() throws IOException { return in.getFilePointer() - lim + pos; }
}