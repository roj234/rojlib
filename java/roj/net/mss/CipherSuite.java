package roj.net.mss;

import roj.collect.ToIntMap;
import roj.crypt.DHGroup;
import roj.crypt.ECGroup;
import roj.crypt.ILCrypto;
import roj.crypt.KeyExchange;
import roj.crypt.eddsa.XDHUnofficial;
import roj.util.Helpers;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;

/**
 * @author solo6975
 * @since 2022/2/13 18:35
 */
public final class CipherSuite {
	public static final int PUB_X509_RSA = 0, PUB_X509_CERTIFICATE = 1, PUB_X509_EdDSA = 2;
	public static final int KEX_DHE_ffdhe2048 = 0, KEX_ECDHE_secp384r1 = 1, KEX_XDHE_x25519 = 2;

	public static int ALL_PUBLIC_KEY_TYPE = 0;
	public static int ALL_KEY_EXCHANGE_TYPE = 0;

	private static final MSSKeyFormat[] PKF_ID2INST = new MSSKeyFormat[32];
	private static final ToIntMap<String> PKF_NAME2ID = new ToIntMap<>(32);
	public static int getKeyFormat(String algorithm) {
		int i = PKF_NAME2ID.getOrDefault(algorithm, -1);
		if (i < 0) throw new IllegalArgumentException("Unknown key format: "+algorithm);
		return i;
	}
	public static MSSKeyFormat getKeyFormat(int id) { return PKF_ID2INST[id]; }
	public static void register(int id, MSSKeyFormat kf) {
		if (PKF_ID2INST[id] != null) throw new IllegalArgumentException("Already has id "+id);
		PKF_ID2INST[id] = kf;
		PKF_NAME2ID.putInt(kf.getAlgorithm(), id);
		ALL_PUBLIC_KEY_TYPE |= 1 << id;
	}

	private static final Supplier<KeyExchange>[] KEX_ID2INST = Helpers.cast(new Supplier<?>[32]);
	private static final ToIntMap<String> KEX_NAME2ID = new ToIntMap<>(32);
	public static byte getKeyExchangeId(String algorithm) {
		int i = KEX_NAME2ID.getOrDefault(algorithm, -1);
		if (i < 0) throw new IllegalArgumentException("Unknown key agreement: "+algorithm);
		return (byte) i;
	}
	public static KeyExchange getKeyExchange(int type) {
		return type < 0 || type >= KEX_ID2INST.length || KEX_ID2INST[type] == null ? null : KEX_ID2INST[type].get();
	}
	public static void register(int id, String algorithm, Supplier<KeyExchange> kf) {
		if (KEX_ID2INST[id] != null) throw new IllegalArgumentException("Already has id "+id);
		KEX_ID2INST[id] = kf;
		KEX_NAME2ID.putInt(algorithm, id);
		ALL_KEY_EXCHANGE_TYPE |= 1 << id;
	}

	private static void _ECDHE(int i, ECGroup c) { register(i, "ECDHE-"+c.name, c); }
	private static void _DHE(int i, DHGroup c) {  }

	private static Supplier<MessageDigest> _HASH(String algo) {
		try {
			MessageDigest md = MessageDigest.getInstance(algo);
			return () -> {
				try { return (MessageDigest) md.clone();
				} catch (Exception e) { Helpers.athrow(e); return null; }
			};
		} catch (NoSuchAlgorithmException e) { return null; }
	}

	public static final CipherSuite TLS_AES_128_GCM_SHA256, TLS_AES_256_GCM_SHA384, TLS_CHACHA20_POLY1305_SHA256, MSS_XCHACHA20_POLY1305_SHA256;

	static {
		MSSCipherFactory
			CIPHER_AES_128_GCM = new SimpleCipherFactory(16, ILCrypto::AesGcm),
			CIPHER_AES_256_GCM = new SimpleCipherFactory(32, ILCrypto::AesGcm),
			CIPHER_CHACHA20_POLY1305 = new SimpleCipherFactory(32, ILCrypto::ChaCha1305),
			CIPHER_XCHACHA20_POLY1305 = new SimpleCipherFactory(32, ILCrypto::XChaCha1305);

		Supplier<MessageDigest> SIGN_SHA256 = _HASH("SHA-256"), SIGN_SHA384 = _HASH("SHA-384");
		try {
			ILCrypto.register();
			register(PUB_X509_RSA, new X509KeyFormat("RSA", "NONEwithRSA"));
			register(PUB_X509_CERTIFICATE, new X509CertFormat());
			register(PUB_X509_EdDSA, new X509KeyFormat("EdDSA", "EdDSA"));

			register(KEX_DHE_ffdhe2048, "DHE-ffdhe2048", DHGroup.ffdhe2048);
			register(KEX_ECDHE_secp384r1, "ECDHE-secp384r1", ECGroup.secp384r1);
			register(KEX_XDHE_x25519, "XDH-x25519", XDHUnofficial::new);
		} catch (Exception e) {
			Helpers.athrow(e);
		}

		TLS_AES_128_GCM_SHA256 = new CipherSuite(0x1301, CIPHER_AES_128_GCM, SIGN_SHA256);
		TLS_AES_256_GCM_SHA384 = new CipherSuite(0x1302, CIPHER_AES_256_GCM, SIGN_SHA384);
		TLS_CHACHA20_POLY1305_SHA256 = new CipherSuite(0x1303, CIPHER_CHACHA20_POLY1305, SIGN_SHA256);
		MSS_XCHACHA20_POLY1305_SHA256 = new CipherSuite(0x1304, CIPHER_XCHACHA20_POLY1305, SIGN_SHA256);
	}

	public final short id;
	public final MSSCipherFactory cipher;
	public final Supplier<MessageDigest> sign;

	public CipherSuite(int id, MSSCipherFactory cipher, Supplier<MessageDigest> sign) {
		this.id = (short) id;
		this.cipher = cipher;
		this.sign = sign;
		if (cipher == null || sign == null) throw new IllegalArgumentException("cipher or signer is null");
	}

	@Override
	public String toString() {
		return "CipherSuite{" + "id=" + Integer.toHexString(id) + ", ciphers=" + cipher + ", sign=" + sign.get() + '}';
	}
}