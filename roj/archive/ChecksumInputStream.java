package roj.archive;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Checksum;
import java.util.zip.ZipException;

/**
 * @author Roj234
 * @since 2023/3/14 0014 0:43
 */
public final class ChecksumInputStream extends InputStream {
	private final InputStream in;
	private final Checksum c;
	long checksum;

	public ChecksumInputStream(InputStream in, Checksum c, long checksum) {
		this.in = in;
		this.c = c;
		this.checksum = checksum;
	}

	public int available() throws IOException {
		return in.available();
	}

	public int read() throws IOException {
		int b = in.read();
		if (b >= 0) c.update(b);
		else check();
		return b;
	}

	public int read(@Nonnull byte[] b, int off, int len) throws IOException {
		len = in.read(b, off, len);
		if (len >= 0) c.update(b, off, len);
		else check();
		return len;
	}

	private void check() throws ZipException {
		if (checksum != c.getValue()) throw new ZipException("Checksum error: excepting " + Long.toHexString(checksum) + ", got " + Long.toHexString(c.getValue()));
	}

	public void close() throws IOException {
		in.close();
	}
}
