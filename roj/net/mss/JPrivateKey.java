package roj.net.mss;

import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

/**
 * @author solo6975
 * @since 2022/2/13 12:58
 */
public class JPrivateKey extends JKey implements MSSPrivateKey {
	protected PrivateKey pri;
	protected byte[] publicBytes;
	protected byte id;

	public JPrivateKey(KeyPair pair) throws NoSuchAlgorithmException {
		this(pair.getPublic(), pair.getPrivate());
	}

	public JPrivateKey(PublicKey pub, PrivateKey pri) {
		super(pub);

		id = CipherSuite.getPublicKeyId(pub.getAlgorithm());
		this.pri = pri;
		publicBytes = pub.getEncoded();
	}

	public JPrivateKey(X509Certificate pub, PrivateKey pri) throws CertificateEncodingException {
		super(pub.getPublicKey());

		id = CipherSuite.PUB_X509_CERTIFICATE;
		this.pri = pri;
		publicBytes = pub.getEncoded();
	}

	@Override
	public Signature signer(SecureRandom random) {
		try {
			Signature c = Signature.getInstance("NONEwith"+key.getAlgorithm());
			c.initSign(pri, random);
			return c;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public int format() {
		return id & 0xFF;
	}

	@Override
	public byte[] publicKey() {
		return publicBytes;
	}
}
