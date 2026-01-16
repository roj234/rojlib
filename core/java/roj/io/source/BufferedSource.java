package roj.io.source;

import roj.util.ArrayCache;
import roj.util.ArrayUtil;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2021/8/18 13:36
 * @revised 2026/1/24 23:47
 */
public class BufferedSource extends Source {
	private final Source src;
	private final boolean closeable;
	private long pos, length;

	private byte[] buf;
	private int bufPtr, bufLen;
	private boolean isDirty;

	public static Source autoClose(Source copy) throws IOException {return new BufferedSource(copy, true);}
	public static Source wrap(Source copy) throws IOException {return new BufferedSource(copy, false);}

	public BufferedSource(Source src, boolean dispatchClose) throws IOException {
		this.src = src;
		this.buf = ArrayCache.getIOBuffer();
		this.pos = src.position();
		this.length = src.length();
		this.closeable = dispatchClose;
	}

	private void fill() throws IOException {
		flush();

		pos += bufLen;
		bufLen = src.read(buf);
		if (bufLen == -1) bufLen = 0;
		bufPtr = 0;
	}

	@Override
	public int read() throws IOException {
		if (bufPtr >= bufLen) {
			fill();
			if (bufLen == 0) return -1; // EOF
		}
		return buf[bufPtr++] & 0xff;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		ArrayUtil.checkRange(b, off, len);

		int totalRead = 0;
		while (len > 0) {
			if (bufPtr >= bufLen) {
				flush();

				// 读穿透 (已经在XDataInputStream里写了一遍了)
				// 不过这个版本更好……
				if (len >= buf.length) {
					src.seek(pos + bufPtr);
					int read = src.read(b, off + totalRead, len);
					if (read == -1) return totalRead == 0 ? -1 : totalRead;

					totalRead += read;
					pos += bufPtr + read;
					bufPtr = 0;
					bufLen = 0;
					return totalRead;
				} else {
					fill();
					if (bufLen == 0) return totalRead == 0 ? -1 : totalRead;
				}
			}

			int copy = Math.min(len, bufLen - bufPtr);
			System.arraycopy(buf, bufPtr, b, off + totalRead, copy);

			bufPtr += copy;
			totalRead += copy;
			len -= copy;
		}
		return totalRead;
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if (!src.isWritable()) throw new IOException("Not writable");
		ArrayUtil.checkRange(b, off, len);

		while (len > 0) {
			if (bufPtr >= buf.length) {
				flush();
				pos += buf.length;
				bufPtr = 0;
				bufLen = 0;
			}

			// XDataOutput也许也应该实现一下写穿透
			if (len >= buf.length && bufPtr == 0) {
				//src.seek(bufPos);
				src.write(b, off, len);
				pos += len;
				break;
			}

			int copy = Math.min(len, buf.length - bufPtr);
			System.arraycopy(b, off, buf, bufPtr, copy);

			bufPtr += copy;
			if (bufPtr > bufLen) bufLen = bufPtr;
			isDirty = true;

			off += copy;
			len -= copy;
		}

		if (pos + bufPtr > length) length = pos + bufPtr;
	}

	@Override
	public void write(DynByteBuf data) throws IOException {
		int len = data.readableBytes();
		while (len > 0) {
			if (bufPtr >= buf.length) {
				flush();
				pos += buf.length;
				bufPtr = 0;
				bufLen = 0;
			}

			// 写穿透
			if (len >= buf.length && bufPtr == 0) {
				//src.seek(bufPos);
				src.write(data);
				pos += len;
				break;
			}

			int copy = Math.min(len, buf.length - bufPtr);
			data.read(buf, bufPtr, copy);

			bufPtr += copy;
			if (bufPtr > bufLen) bufLen = bufPtr;
			isDirty = true;

			len -= copy;
		}

		if (pos + bufPtr > length) length = pos + bufPtr;
	}

	@Override
	public void flush() throws IOException {
		if (isDirty) {
			//src.seek(pos);
			src.write(buf, 0, bufLen);
			isDirty = false;
		}
	}

	@Override
	public void seek(long p) throws IOException {
		if (p >= pos && p < pos + bufLen) {
			bufPtr = (int) (p - pos);
		} else {
			flush();
			src.seek(p);
			pos = p;
			bufPtr = 0;
			bufLen = 0;
		}
	}
	@Override
	public long position() {return pos + bufPtr;}

	@Override
	public void setLength(long length) throws IOException {
		src.setLength(length);
		this.length = length;
	}
	@Override
	public long length() throws IOException {return length;}

	@Override
	public void close() throws IOException {
		flush();

		if (buf != null) {
			ArrayCache.putArray(buf);
			buf = null;
		}
		if (closeable) src.close();
	}
	@Override
	public void reopen() throws IOException {
		if (closeable) src.reopen();
	}

	@Override
	public Source copy() throws IOException { return new BufferedSource(src.copy(), closeable); }

	public void moveSelf(long from, long to, long length) throws IOException {
		flush();
		src.moveSelf(from, to, length);
		pos = src.position();
		bufPtr = 0;
		bufLen = 0;
	}

	@Override public boolean isBuffered() {return true;}
	@Override public boolean isWritable() {return src.isWritable();}

	@Override
	public String toString() { return "Buffered "+src; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		BufferedSource that = (BufferedSource) o;
		return src.equals(that.src);
	}

	@Override public int hashCode() {return src.hashCode()+1;}
}