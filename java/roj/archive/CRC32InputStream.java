package roj.archive;

import org.jetbrains.annotations.NotNull;
import roj.crypt.CRC32s;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipException;

/**
 * @author Roj234
 * @since 2023/3/14 0014 0:43
 */
public final class CRC32InputStream extends InputStream {
	private final InputStream in;
	private final int except;
	private int crc = CRC32s.INIT_CRC;

	public CRC32InputStream(InputStream in, int except) {
		this.in = in;
		this.except = except;
	}

	public int available() throws IOException { return in.available(); }
	public void close() throws IOException { in.close(); }

	public int read() throws IOException {
		int b = in.read();
		if (b >= 0) crc = CRC32s.update(crc, b);
		else check();
		return b;
	}

	public int read(@NotNull byte[] b, int off, int len) throws IOException {
		len = in.read(b, off, len);
		if (len >= 0) crc = CRC32s.update(crc, b, off, len);
		else check();
		return len;
	}

	private void check() throws ZipException {
		crc = CRC32s.retVal(crc);
		if (except != crc) throw new ZipException("CRC32校验错误: except="+Integer.toHexString(except)+", read="+Integer.toHexString(crc));
	}
}