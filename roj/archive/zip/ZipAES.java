package roj.archive.zip;

import roj.crypt.AES;
import roj.crypt.HMAC;
import roj.crypt.MyCipher;
import roj.crypt.PBKDF2;
import roj.util.DynByteBuf;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/11/14 0012 0:16
 */
final class ZipAES extends MyCipher {
	static final int SALT_LENGTH = 16;
	static final int KEY_LENGTH = 32;
	static final int COMPOSITE_KEY_LENGTH = 2 * KEY_LENGTH + 2;
	static final int PBKDF2_ITERATION_COUNT = 1000;

	HMAC hmac;
	byte[] verifier = new byte[2];
	byte[] salt;
	boolean headerSent;

	public ZipAES() {
		super(new AES(), MODE_CTR);
		try {
			hmac = new HMAC(MessageDigest.getInstance("SHA1"));
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void setKey(byte[] key, int flags) {
		if (salt == null) {
			salt = new byte[SALT_LENGTH];
			new SecureRandom().nextBytes(salt);
		}

		byte[] compositeKey = PBKDF2.PBKDF2_Derive(hmac, key, salt, PBKDF2_ITERATION_COUNT, COMPOSITE_KEY_LENGTH);

		super.setKey(Arrays.copyOf(compositeKey, KEY_LENGTH), flags); // AES Key
		hmac.setSignKey(compositeKey, KEY_LENGTH, KEY_LENGTH); // HMAC key
		System.arraycopy(compositeKey, 2*KEY_LENGTH, verifier, 0, 2); // Verification key

		iv.array()[0] = 1;
	}

	@Override
	public void setOption(String key, Object value) {
		salt = (byte[]) value;
	}

	public boolean setKeyDecrypt(byte[] key, InputStream in) throws IOException {
		DataInputStream din = new DataInputStream(in);

		din.readFully(salt = new byte[SALT_LENGTH]);
		setKey(key, DECRYPT);
		int v = din.readUnsignedShort();
		return (((verifier[0] & 0xFF) << 8) | (verifier[1] & 0xFF)) == v;
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
	public void crypt(DynByteBuf in, DynByteBuf out) throws GeneralSecurityException {
		int outPos = out.wIndex();

		if ((mode & DECRYPT) != 0) {
			hmac.update(in);
		}

		if (!try4(in, out)) try1(in, out, in.readableBytes());

		if ((mode & DECRYPT) == 0) {
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
