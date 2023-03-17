package roj.net.mss;

import javax.crypto.Cipher;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

/**
 * @author solo6975
 * @since 2022/2/13 12:58
 */
public class JKey implements MSSPublicKey {
	protected PublicKey key;

	public JKey(PublicKey key) {
		this.key = key;
	}

	@Override
	public String getAlgorithm() {
		return key.getAlgorithm();
	}

	@Override
	public byte[] publicKey() {
		return key.getEncoded();
	}

	@Override
	public Cipher publicCipher() {
		try {
			Cipher c = Cipher.getInstance(key.getAlgorithm());
			c.init(Cipher.DECRYPT_MODE, key);
			return c;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Should not happen");
		}
	}
}
