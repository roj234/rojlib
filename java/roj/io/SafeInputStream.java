package roj.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj233
 * @since 2022/2/22 19:35
 */
public class SafeInputStream extends InputStream {
	protected IOException ex;
	protected InputStream in;
	private boolean closed, hideClose;

	public SafeInputStream(InputStream in) {
		this.in = in;
	}

	public void setHideClose(boolean hideClose) {
		this.hideClose = hideClose;
	}

	private void kaboom(IOException e) {
		if (ex == null) ex = e;
		else ex.addSuppressed(e);
		closed = true;
	}

	public boolean isClosed() {
		return closed;
	}

	public IOException getException() {
		return ex;
	}

	public int read() {
		if (closed) return -1;
		try {
			return in.read();
		} catch (IOException e) {
			kaboom(e);
			return -1;
		}
	}

	public int read(byte[] b, int off, int len) {
		if (closed) return -1;
		try {
			return in.read(b, off, len);
		} catch (IOException e) {
			kaboom(e);
			return -1;
		}
	}

	public long skip(long n) {
		if (closed) return -1;
		try {
			return in.skip(n);
		} catch (IOException e) {
			kaboom(e);
			return -1;
		}
	}

	public int available() {
		if (closed) return 0;
		try {
			return in.available();
		} catch (IOException e) {
			kaboom(e);
			return 0;
		}
	}

	public void close() {
		if (hideClose) {
			closed = true;
			return;
		}

		try {
			in.close();
		} catch (IOException e) {
			kaboom(e);
		}
	}

	public void mark(int readlimit) {
		in.mark(readlimit);
	}

	public void reset() {
		try {
			in.reset();
		} catch (IOException e) {
			kaboom(e);
		}
	}

	public boolean markSupported() {
		return in.markSupported();
	}
}
