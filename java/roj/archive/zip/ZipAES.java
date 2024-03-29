package roj.archive.zip;

import roj.crypt.AES;
import roj.crypt.FeedbackCipher;
import roj.crypt.HMAC;
import roj.crypt.PBKDF2;
import roj.io.IOUtil;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import javax.crypto.Cipher;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/11/14 0012 0:16
 */
final class ZipAES extends FeedbackCipher {
	static final int SALT_LENGTH = 16;
	static final int KEY_LENGTH = 32;
	static final int COMPOSITE_KEY_LENGTH = 2 * KEY_LENGTH + 2;
	static final int PBKDF2_ITERATION_COUNT = 1000;

	final HMAC hmac;
	final byte[] verifier = new byte[2];
	byte[] salt;

	public ZipAES() {
		super(new AES(), MODE_CTR);
		try {
			hmac = new HMAC(MessageDigest.getInstance("SHA1"));
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void init(int mode, byte[] key, AlgorithmParameterSpec par, SecureRandom random) {
		decrypt = mode == Cipher.DECRYPT_MODE;

		if (salt == null) salt = SecureRandom.getSeed(SALT_LENGTH);

		byte[] compositeKey = PBKDF2.PBKDF2_Derive(hmac, key, salt, PBKDF2_ITERATION_COUNT, COMPOSITE_KEY_LENGTH);

		try {
			cip.init(Cipher.ENCRYPT_MODE, Arrays.copyOf(compositeKey, KEY_LENGTH)); // AES Key
		} catch (InvalidKeyException e) {
			Helpers.athrow(e);
		}
		hmac.setSignKey(compositeKey, KEY_LENGTH, KEY_LENGTH); // HMAC key
		System.arraycopy(compositeKey, 2*KEY_LENGTH, verifier, 0, 2); // Verification key

		tmp.clear();
		vec.clear();

		Arrays.fill(vec.array(), (byte) 0);
		vec.array()[0] = 1;
		vec.wIndex(16);
	}

	public boolean setKeyDecrypt(byte[] key, InputStream in) throws IOException {
		salt = new byte[SALT_LENGTH];
		IOUtil.readFully(in, salt, 0, salt.length);
		init(DECRYPT_MODE, key, null, null);
		return (verifier[0]&0xFF) == in.read() && (verifier[1]&0xFF) == in.read();
	}

	public void sendHeaders(OutputStream out) throws IOException {
		out.write(salt);
		out.write(verifier);
	}

	public void sendTrailers(OutputStream out) throws IOException  {
		out.write(hmac.digestShared(), 0, 10);
	}

	public byte[] getTrailers() {
		return Arrays.copyOf(hmac.digestShared(), 10);
	}

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
}
