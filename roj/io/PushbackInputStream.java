package roj.io;

import javax.annotation.Nonnull;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author solo6975
 * @since 2021/10/1 21:39
 */
public class PushbackInputStream extends FilterInputStream {
	byte[] buffer;
	int bOff;
	int bLen;

	public PushbackInputStream(InputStream in) {
		super(in);
	}

	public void setBuffer(@Nonnull byte[] b, int off, int len) {
		this.buffer = b;
		this.bOff = off;
		this.bLen = len;
	}

	public void setIn(InputStream in) {
		this.in = in;
	}

	@Override
	public int read(@Nonnull byte[] b, int off, int len) throws IOException {
		int k = bOff;
		if (k < bLen) {
			int rm = bLen - k;
			if (len <= rm) {
				if (len == rm) {
					bOff = bLen = 0;
				} else {
					bOff = k + len;
				}
				System.arraycopy(buffer, k, b, off, len);
				return len;
			} else {
				bOff = bLen = 0;
				System.arraycopy(buffer, k, b, off, rm);
				int read = in.read(b, off + rm, len - rm);
				return (read > 0 ? read : 0) + rm;
			}
		} else {
			return in.read(b, off, len);
		}
	}

	@Override
	public int read() throws IOException {
		if (bOff < bLen) {
			byte b = buffer[bOff++];
			if (bOff == bLen) bOff = bLen = 0;
			return b;
		} else {
			return in.read();
		}
	}

	@Override
	public int available() throws IOException {
		return in.available() + bLen - bOff;
	}

	@Override
	public long skip(long n) throws IOException {
		int k = bOff;
		if (k < bLen) {
			int rm = bLen - k;
			if (n <= rm) {
				if (n == rm) {
					bOff = bLen = 0;
				} else {
					bOff = (int) (k + n);
				}
				return n;
			} else {
				bOff = bLen = 0;
				return rm + in.skip(n-rm);
			}
		}

		return in.skip(n);
	}

	public int getBufferPos() {
		return bOff;
	}
}
