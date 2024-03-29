package roj.crypt;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;

/**
 * @author Roj234
 * @since 2023/4/29 0029 3:44
 */
public class ILProvider extends Provider {
	public static final String PROVIDER_NAME = "IL";
	public static Provider INSTANCE;

	public ILProvider() {
		super(PROVIDER_NAME, 1.0, "RojLib Security Provider v1.0");
		AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
			setup();
			return null;
		});
		INSTANCE = this;
	}

	public static synchronized void register() {
		if (INSTANCE == null) Security.addProvider(new ILProvider());
	}

	private void setup() {
		//put("Cipher.AES", "roj.crypt.AES");
		put("Cipher.AESGCM", "roj.crypt.AES_GCM");
		put("Cipher.ChaCha20", "roj.crypt.ChaCha20");
		put("Cipher.XChaCha20", "roj.crypt.XChaCha20");
		put("Cipher.ChaCha20WithPoly1305", "roj.crypt.ChaCha20_Poly1305");
		put("Cipher.SM4", "roj.crypt.SM4");

		put("MessageDigest.Blake3", "roj.crypt.Blake3");
		put("MessageDigest.SM3", "roj.crypt.SM3");

		put("KeyFactory.EdDSA", "roj.crypt.eddsa.EdKeyFactory");
		put("KeyPairGenerator.EdDSA", "roj.crypt.eddsa.EdKeyGenerator");
		put("Signature.NONEwithEdDSA", "roj.crypt.eddsa.EdSignature");
	}
}
