package roj.archive.zip;

import org.jetbrains.annotations.NotNull;
import roj.crypt.CipherInputStream;
import roj.io.IOUtil;
import roj.io.source.SourceInputStream;
import roj.text.TextUtil;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * check HMAC when AES is in use
 * @author Roj234
 * @since 2023/3/14 0:42
 */
final class AESInputSt extends CipherInputStream {
	private boolean checked;

	public AESInputSt(InputStream in, ZipAES cip) {super(in, cip);}

	public int read(@NotNull byte[] b, int off, int len) throws IOException {
		len = super.read(b, off, len);
		if (len < 0) check();
		return len;
	}

	private void check() throws IOException {
		if (checked) return;
		checked = true;

		((SourceInputStream) in).remain += 10;
		byte[] mac = new byte[10];
		IOUtil.readFully(in, mac, 0, 10);

		byte[] except = ((ZipAES) cipher).getTrailers();

		if (!MessageDigest.isEqual(except, mac)) throw new IOException("校验失败(HMAC-SHA1): except="+TextUtil.bytes2hex(except)+", got="+TextUtil.bytes2hex(mac));
	}
}