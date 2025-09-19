package roj.net.mss;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

/**
 * @author solo6975
 * @since 2022/2/13 13:06
 */
public class MSSKeyPair extends MSSPublicKey {
	public final PrivateKey pri;

	public MSSKeyPair(KeyPair pair) throws NoSuchAlgorithmException { this(pair.getPublic(), pair.getPrivate()); }

	public MSSKeyPair(PublicKey pub, PrivateKey pri) throws NoSuchAlgorithmException {
		super(pub);
		this.pri = pri;
	}

	public MSSKeyPair(X509Certificate pub, PrivateKey pri) throws CertificateEncodingException {
		super(pub);
		this.pri = pri;
		encode();
	}
}