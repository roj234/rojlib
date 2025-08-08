package roj.io;

import org.jetbrains.annotations.NotNull;
import roj.crypt.CRC32;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipException;

/**
 * @author Roj234
 * @since 2023/3/14 0:43
 */
public final class CRC32InputStream extends InputStream {
	private final InputStream in;
	private final int except;
	private int crc = CRC32.initial;

	public CRC32InputStream(InputStream in, int except) {
		this.in = in;
		this.except = except;
	}

	public int available() throws IOException { return in.available(); }
	public void close() throws IOException { in.close(); }

	public int read() throws IOException {
		int b = in.read();
		if (b >= 0) crc = CRC32.update(crc, b);
		else check();
		return b;
	}

	public int read(@NotNull byte[] b, int off, int len) throws IOException {
		len = in.read(b, off, len);
		if (len >= 0) crc = CRC32.update(crc, b, off, len);
		else check();
		return len;
	}

	private void check() throws ZipException {
		crc = CRC32.finish(crc);
		if (except != crc) throw new ZipException("CRC32校验错误: except="+Integer.toHexString(except)+", got="+Integer.toHexString(crc));
	}
}