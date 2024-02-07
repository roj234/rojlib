package roj.crypt;

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
		super(PROVIDER_NAME, 1.1, "RojLib Security Provider v1.1");
		setup();
		INSTANCE = this;
	}

	public static synchronized void register() {
		if (INSTANCE == null) Security.addProvider(new ILProvider());
	}

	private void setup() {
		put("Cipher.ChaCha20", "roj.crypt.ChaCha20");
		put("Cipher.XChaCha20", "roj.crypt.XChaCha20");
		put("Cipher.ChaCha20WithPoly1305", "roj.crypt.ChaCha20_Poly1305");
		put("Cipher.SM4", "roj.crypt.SM4");

		put("MessageDigest.Blake3", "roj.crypt.Blake3");
		put("MessageDigest.SM3", "roj.crypt.SM3");

		if (!Security.getAlgorithms("KeyFactory").contains("EdDSA")) {
			put("KeyFactory.EdDSA", "roj.crypt.eddsa.EdKeyFactory");
			put("KeyPairGenerator.EdDSA", "roj.crypt.eddsa.EdKeyGenerator");
			put("KeyPairGenerator.Ed25519", "roj.crypt.eddsa.EdKeyGenerator");
			put("Signature.EdDSA", "roj.crypt.eddsa.EdSignature");
			put("Signature.Ed25519", "roj.crypt.eddsa.EdSignature");
			//put("Signature.XDH", "roj.crypt.eddsa.XDHUnofficial");
			//put("Signature.X25519", "roj.crypt.eddsa.XDHUnofficial");
		}
	}
}