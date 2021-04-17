package roj.net.mss;

import roj.util.Helpers;

import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * @author Roj233
 * @since 2021/12/22 12:53
 */
public final class JCertificateFactory implements MSSPublicKeyFactory<X509Certificate> {
	private CertificateFactory factory;

	public JCertificateFactory() {
		try {
			factory = CertificateFactory.getInstance("X.509");
		} catch (Exception e) {
			Helpers.athrow(e);
		}
	}

	@Override
	public String getAlgorithm() {
		return "X.509";
	}

	@Override
	public byte[] encode(X509Certificate pk) throws GeneralSecurityException {
		return pk.getEncoded();
	}

	@Override
	public MSSPublicKey decode(byte[] data) throws GeneralSecurityException {
		return new JCertificateKey((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(data)));
	}
}
