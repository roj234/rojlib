package roj.net.mss;

import roj.collect.ToIntMap;
import roj.crypt.*;
import roj.crypt.ec.ECDH;
import roj.crypt.ec.NamedCurve;
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
	}

	public static final int PUB_X509_RSA = 0;
	public static final int PUB_X509_CERTIFICATE = 1;
	public static final int PUB_X509_EC = 2;
	public static final int PUB_X509_DSA = 3;
	public static final int ALL_CERTIFICATE_TYPE = 7;

	private static final Supplier<KeyAgreement>[] KEX_ID2INST = Helpers.cast(new Supplier<?>[32]);
	private static final ToIntMap<String> KEX_NAME2ID = new ToIntMap<>(32);
	public static byte getKeyAgreementId(String algorithm) {
		int i = KEX_NAME2ID.getOrDefault(algorithm, -1);
		if (i < 0) throw new IllegalArgumentException("Unregistered key exchange alg: " + algorithm);
		return (byte) i;
	}
	public static KeyAgreement getKeyAgreement(int type) {
		return KEX_ID2INST[type].get();
	}
	public static void register(int id, String algorithm, Supplier<KeyAgreement> kf) {
		if (KEX_ID2INST[id] != null) throw new IllegalArgumentException("Already has id " + id);
		KEX_ID2INST[id] = kf;
		KEX_NAME2ID.putInt(algorithm, id);
	}

	public static final int KEX_DH = 0;
	public static final int KEX_ECDH_SECP384R1 = 1;
	public static final int KEX_ECDH_BrainPool384R1 = 2;

	public static MSSCiphers
		CIPHER_AES_GCM = new SimpleCiphers(32, AES_GCM::new),
		CIPHER_SM4_CFB8 = new SimpleCiphers(32, SM4::new, MyCipher.MODE_CFB),
		CIPHER_XCHACHA20 = new SimpleCiphers(32, XChaCha::new),
		CIPHER_XCHACHA20_POLY1305 = new SimpleCiphers(32, XChaCha_Poly1305::new);

	public static Supplier<MessageDigest> SIGN_SHA256, SIGN_SHA384;

	static {
		// mandatory
		try {
			register(PUB_X509_RSA,  new JKeyFactory("RSA"));
			register(PUB_X509_CERTIFICATE, new JCertificateFactory());
			register(KEX_DH, "DH", DH::new);
			SIGN_SHA256 = cloneFrom("SHA-256");
		} catch (Exception e) {
			Helpers.athrow(e);
		}

		// optional
		try {
			register(PUB_X509_DSA,  new JKeyFactory("DSA"));
			register(PUB_X509_EC,  new JKeyFactory("EC"));
			registerECDH(KEX_ECDH_SECP384R1, NamedCurve.secp384r1);
			registerECDH(KEX_ECDH_BrainPool384R1, NamedCurve.bp384r1);
			SIGN_SHA384 = cloneFrom("SHA-384");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void registerECDH(int id, NamedCurve curve) {
		register(id, "ECDH-"+curve.getObjectId(), () -> new ECDH(curve));
	}

	private static Supplier<MessageDigest> cloneFrom(String algo) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance(algo);
		return () -> {
			try {
				return (MessageDigest) md.clone();
			} catch (Exception e) {
				return null;
			}
		};
	}

	public final short id;
	public final MSSCiphers ciphers;
	public final Supplier<MessageDigest> sign;

	public CipherSuite(int id, MSSCiphers ciphers, Supplier<MessageDigest> sign) {
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
