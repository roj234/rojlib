package roj.archive.zip;

import roj.crypt.CipherInputStream;
import roj.crypt.CryptoFactory;
import roj.crypt.FeedbackCipher;
import roj.crypt.HMAC;
import roj.io.IOUtil;
import roj.io.source.Source;
import roj.io.source.SourceInputStream;
import roj.text.TextUtil;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/11/14 0:16
 */
final class ZipAES extends FeedbackCipher {
	static final int SALT_LENGTH = 16;
	static final int KEY_LENGTH = 32;
	static final int COMPOSITE_KEY_LENGTH = 2 * KEY_LENGTH + 2;
	static final int PBKDF2_ITERATION_COUNT = 1000;

	final HMAC hmac;
	final byte[] check = new byte[2];
	byte[] salt;

	public ZipAES() {
		super(CryptoFactory.AES(), MODE_CTR);
		hmac = new HMAC(CryptoFactory.getMessageDigest("SHA-1"));
	}

	@Override
	public void init(boolean encrypt, byte[] key, AlgorithmParameterSpec par, SecureRandom random) {
		decrypt = !encrypt;

		if (salt == null) salt = SecureRandom.getSeed(SALT_LENGTH);

		byte[] compositeKey = CryptoFactory.PBKDF2_Derive(hmac, key, salt, PBKDF2_ITERATION_COUNT, COMPOSITE_KEY_LENGTH);

		try {
			cip.init(true, Arrays.copyOf(compositeKey, KEY_LENGTH)); // AES Key
		} catch (InvalidKeyException e) {
			Helpers.athrow(e);
		}
		hmac.init(compositeKey, KEY_LENGTH, KEY_LENGTH); // HMAC key
		System.arraycopy(compositeKey, 2*KEY_LENGTH, check, 0, 2); // Verification key

		tmp.clear();
		vec.clear();

		Arrays.fill(vec.array(), (byte) 0);
		vec.array()[0] = 1;
		vec.wIndex(16);
	}

	public boolean setKeyDecrypt(byte[] key, InputStream in) throws IOException {
		salt = new byte[SALT_LENGTH];
		IOUtil.readFully(in, salt, 0, salt.length);
		init(false, key, null, null);
		return (check[0]&0xFF) == in.read() && (check[1]&0xFF) == in.read();
	}

	public void writeHeader(OutputStream out) throws IOException {
		out.write(salt);
		out.write(check);
	}

	public void writeFooter(OutputStream out) throws IOException  {
		out.write(hmac.digestShared(), 0, 10);
	}

	public byte[] getMac() {return Arrays.copyOf(hmac.digestShared(), 10);}

	@Override
	public void crypt(DynByteBuf in, DynByteBuf out) {
		int outPos = out.wIndex();

		if (decrypt) hmac.update(in);

		try4(in, out);
		try1(in, out, in.readableBytes());

		if (!decrypt) {
			int outRpos = out.rIndex;
			out.rIndex = outPos;
			hmac.update(out);
			out.rIndex = outRpos;
		}
	}

	/**
	 * Override increment to behave like in Zip implementation
	 */
	@Override
	protected void increment(byte[] b) {
		for (int i = 0; i < 16; i++) {
			if (b[i] == -1) {
				b[i] = 0;
			} else {
				b[i]++;
				break;
			}
		}
	}

	static final class MacChecker extends CipherInputStream {
		private boolean checked;

		MacChecker(InputStream in, ZipAES cip) {super(in, cip);}

		@Override
		public void finish() throws IOException {
			super.finish();

			if (eof) {
				if (checked) return;
				checked = true;

				Source source = ((SourceInputStream) in).src;
				byte[] exceptMac = new byte[10];
				source.readFully(exceptMac);

				byte[] realMac = ((ZipAES) cipher).getMac();

				if (!MessageDigest.isEqual(realMac, exceptMac))
					throw new IOException("校验失败(HMAC-SHA1): except="+ TextUtil.bytes2hex(exceptMac)+", got="+TextUtil.bytes2hex(realMac));
			}
		}
	}
}
