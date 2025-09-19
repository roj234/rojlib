package roj.asmx.launcher;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * @author Roj234
 * @since 2025/09/21 21:06
 */
final class DataIn extends InputStream {
	public final File file;
	public final RandomAccessFile in;
	boolean ignoreClose;

	public DataIn(File file) throws IOException {
		this.file = file;
		this.in = new RandomAccessFile(file, "r");
	}

	@Override
	public void close() throws IOException {
		if (ignoreClose) return;
		in.close();
	}

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
		int r = in.read(b, off + total, len - total);
		return r < 0 ? total == 0 ? r : total : total + r;
	}

	public long skip(long n) throws IOException {
		long total = lim - pos;
		if (n <= total) {
			pos += n;
			return n;
		}

		this.pos = this.lim = 0;
		long skip;
		long fp = in.getFilePointer();
		skip = Math.min(n - total, in.length() - fp);
		in.seek(skip + fp);
		return skip + total;
	}

	private byte[] list = new byte[512];
	private int pos, lim;

	private int doRead(int count) throws IOException {
		int l = lim;
		int o = pos;
		if (o + count > l) {
			byte[] dst = list;
			if (dst.length < count) dst = new byte[count];
			System.arraycopy(list, pos, dst, 0, l -= o);
			list = dst;

			while (l < dst.length) {
				int r = in.read(dst, l, dst.length - l);
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

	public int readUnsignedShort() throws IOException {
		int i = doRead(2);
		return ((list[i] & 0xFF) << 8) | (list[i + 1] & 0xFF);
	}

	public final int readUShortLE() throws IOException {
		int i = doRead(2);
		return (list[i] & 0xFF) | ((list[i + 1] & 0xFF) << 8);
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
}
