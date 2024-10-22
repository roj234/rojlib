package roj.crypt;

import roj.crypt.eddsa.XDHUnofficial;

import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;

/**
 * @author Roj234
 * @since 2023/4/29 0029 3:44
 */
public class ILCrypto extends Provider {
	public static final String PROVIDER_NAME = "IL";
	public static Provider INSTANCE;


	public ILCrypto() {
		super(PROVIDER_NAME, 1.2, "RojLib Security Provider v1.2");
		setup();
		INSTANCE = this;
	}

	public static synchronized void register() {
		if (INSTANCE == null) Security.addProvider(new ILCrypto());
	}

	private void setup() {
		put("Cipher.ChaCha20", "roj.crypt.ChaCha");
		put("Cipher.XChaCha20", "roj.crypt.XChaCha");
		//put("Cipher.ChaCha20WithPoly1305", "roj.crypt.ChaCha_Poly1305");
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

	//块密码
	public static RCipherSpi Aes() {return new AES();}
	public static RCipherSpi SM4() {return new SM4();}
	//end 块密码
	//流密码
	public static RCipherSpi ChaCha() {return new ChaCha();}
	public static RCipherSpi ChaCha(int rounds) {return new ChaCha(rounds);}
	public static RCipherSpi XChaCha() {return new XChaCha();}
	public static RCipherSpi XChaCha(int rounds) {return new XChaCha(rounds);}
	//end 流密码
	//AEAD
	public static RCipherSpi AesGcm() {return new AES_GCM();}
	public static RCipherSpi ChaCha1305() {return new ChaCha_Poly1305(new ChaCha());}
	public static RCipherSpi XChaCha1305() {return new ChaCha_Poly1305(new XChaCha());}
	//end AEAD

	//Hash
	//public static BufferedDigest Blake3_Hash(int digestLength) {return new Blake3(digestLength);}
	public static BufferedDigest SM3() {return new SM3();}
	//end Hash
	//MAC
	@SuppressWarnings("unchecked")
	public static <T extends BufferedDigest & MessageAuthenticCode> T Blake3(int digestLength) {return (T) new Blake3(digestLength);}
	public static MessageAuthenticCode HMAC(MessageDigest digest) {return new HMAC(digest);}
	public static MessageAuthenticCode HMAC(MessageDigest digest, int blockSize) {return new HMAC(digest, blockSize);}
	public static MessageAuthenticCode Poly1305() {return new Poly1305();}
	//end MAC

	//密钥交换
	//public static KeyExchange DH() {return new DH();}
	//public static KeyExchange ECDH() {return new ECDH();}
	public static KeyExchange X25519DH() {return new XDHUnofficial();}
	//end 密钥交换

	//数字签名(复用Java的API)
}