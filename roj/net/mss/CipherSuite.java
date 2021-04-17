package roj.net.mss;

import roj.collect.ToIntMap;
import roj.crypt.*;
import roj.util.Helpers;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;

/**
 * @author solo6975
 * @since 2022/2/13 18:35
 */
public final class CipherSuite {
	private static final MSSPublicKeyFactory<?>[] PKF_ID2INST = new MSSPublicKeyFactory<?>[32];
	private static final ToIntMap<String> PKF_NAME2ID = new ToIntMap<>(32);
	public static byte getPublicKeyId(String algorithm) {
		int i = PKF_NAME2ID.getOrDefault(algorithm, -1);
		if (i < 0) throw new IllegalArgumentException("Unregistered public key alg: " + algorithm);
		return (byte) i;
	}
	public static MSSPublicKeyFactory<?> getPublicKeyFactory(int type) {
		return PKF_ID2INST[type];
	}
	public static void register(int id, MSSPublicKeyFactory<?> kf) {
		if (PKF_ID2INST[id] != null) throw new IllegalArgumentException("Already has id " + id);
		PKF_ID2INST[id] = kf;
		PKF_NAME2ID.putInt(kf.getAlgorithm(), id);
		ALL_CERTIFICATE_TYPE |= 1 << id;
	}

	public static final int PUB_X509_RSA = 0;
	public static final int PUB_X509_CERTIFICATE = 1;
	public static final int PUB_X509_EdDSA = 2;
	public static int ALL_CERTIFICATE_TYPE = 0;

	private static final Supplier<KeyAgreement>[] KEX_ID2INST = Helpers.cast(new Supplier<?>[32]);
	private static final ToIntMap<String> KEX_NAME2ID = new ToIntMap<>(32);
	public static byte getKeyAgreementId(String algorithm) {
		int i = KEX_NAME2ID.getOrDefault(algorithm, -1);
		if (i < 0) throw new IllegalArgumentException("Unregistered key exchange alg: " + algorithm);
		return (byte) i;
	}
	public static KeyAgreement getKeyAgreement(int type) {
		return type < 0 || type >= KEX_ID2INST.length || KEX_ID2INST[type] == null ? null : KEX_ID2INST[type].get();
	}
	public static void register(int id, String algorithm, Supplier<KeyAgreement> kf) {
		if (KEX_ID2INST[id] != null) throw new IllegalArgumentException("Already has id " + id);
		KEX_ID2INST[id] = kf;
		KEX_NAME2ID.putInt(algorithm, id);
	}

	public static final int KEX_DHE_ffdhe2048 = 0;
	public static final int KEX_ECDHE_secp384r1 = 1;

	public static final MSSCipherFactory
		CIPHER_AES_128_GCM = new SimpleCipherFactory(16, AES_GCM::new),
		CIPHER_AES_256_GCM = new SimpleCipherFactory(32, AES_GCM::new),
		CIPHER_CHACHA20_POLY1305 = new SimpleCipherFactory(32, ChaCha_Poly1305::ChaCha1305),
		CIPHER_XCHACHA20_POLY1305 = new SimpleCipherFactory(32, ChaCha_Poly1305::XChaCha1305);

	public static Supplier<MessageDigest> SIGN_SHA256, SIGN_SHA384;

	static {
		try {
			SIGN_SHA256 = cloneFrom("SHA-256");
			SIGN_SHA384 = cloneFrom("SHA-384");

			register(PUB_X509_RSA, new JKeyFactory("RSA"));
			register(PUB_X509_CERTIFICATE, new JCertificateFactory());
			try {
				ILProvider.register();
				register(PUB_X509_EdDSA, new JKeyFactory("EdDSA"));
			} catch (Exception ignored) {}

			_DHE(KEX_DHE_ffdhe2048, DHGroup.ffdhe2048);
			_ECDHE(KEX_ECDHE_secp384r1, ECGroup.secp384r1);
		} catch (Exception e) {
			Helpers.athrow(e);
		}
	}

	private static void _ECDHE(int i, ECGroup c) { register(i, "ECDHE-"+c.name, c); }
	private static void _DHE(int i, DHGroup c) { register(i, "DHE-"+c.name, c); }

	private static Supplier<MessageDigest> cloneFrom(String algo) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance(algo);
		return () -> {
			try { return (MessageDigest) md.clone();
			} catch (Exception e) { Helpers.athrow(e); return null; }
		};
	}

	public final short id;
	public final MSSCipherFactory ciphers;
	public final Supplier<MessageDigest> sign;

	public CipherSuite(int id, MSSCipherFactory ciphers, Supplier<MessageDigest> sign) {
		this.id = (short) id;
		this.ciphers = ciphers;
		this.sign = sign;
		if (ciphers == null || sign == null) throw new IllegalStateException("Wrong type id");
	}

	@Override
	public String toString() {
		return "CipherSuite{" + "id=" + Integer.toHexString(id) + ", ciphers=" + ciphers + ", sign=" + sign.get() + '}';
	}
}
