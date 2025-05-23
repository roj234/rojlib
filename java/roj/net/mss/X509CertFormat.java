package roj.net.mss;

import roj.crypt.CryptoFactory;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * @author Roj233
 * @since 2021/12/22 12:53
 */
public final class X509CertFormat implements MSSKeyFormat {
	private CertificateFactory factory;

	public X509CertFormat() {
		try {
			factory = CertificateFactory.getInstance("X.509");
		} catch (Exception e) {
			Helpers.athrow(e);
		}
	}

	@Override
	public String getAlgorithm() { return null; }

	@Override
	public byte[] encode(MSSPublicKey key) {
		try {
			return ((X509Certificate) key.key).getEncoded();
		} catch (CertificateEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	@Override
	public MSSPublicKey decode(DynByteBuf data) throws GeneralSecurityException { return new MSSPublicKey(factory.generateCertificate(data.asInputStream())); }

	@Override
	public boolean verify(MSSPublicKey key, byte[] data, byte[] sign) throws GeneralSecurityException {
		CryptoFactory.checkCertificateValidity(new X509Certificate[]{(X509Certificate) key.key});
		return true;
	}
	@Override
	public byte[] sign(MSSKeyPair key, SecureRandom random, byte[] data) throws GeneralSecurityException { return ArrayCache.BYTES; }
}