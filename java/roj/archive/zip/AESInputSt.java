package roj.archive.zip;

import org.jetbrains.annotations.NotNull;
import roj.crypt.CipherInputStream;
import roj.io.IOUtil;
import roj.io.source.SourceInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.ZipException;

/**
 * check HMAC when AES is in use
 * @author Roj234
 * @since 2023/3/14 0014 0:42
 */
final class AESInputSt extends CipherInputStream {
	private boolean checked;

	public AESInputSt(InputStream in, ZipAES cip) {
		super(in, cip);
	}

	public int read(@NotNull byte[] b, int off, int len) throws IOException {
		len = super.read(b, off, len);
		if (len < 0) check();
		return len;
	}

	private void check() throws IOException {
		if (checked) return;
		checked = true;

		((SourceInputStream) in).remain += 10;
		byte[] remote = new byte[10];
		IOUtil.readFully(in, remote, 0, 10);

		byte[] local = ((ZipAES) c).getTrailers();

		if (!MessageDigest.isEqual(local, remote)) throw new ZipException("HMAC checksum: excepting " + Arrays.toString(local) + ", got " + Arrays.toString(remote) + "\n" + "You can disable this by unset VERIFY bit");
	}
}