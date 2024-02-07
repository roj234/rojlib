package roj.net.mss;

import roj.util.ArrayCache;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.*;
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

	private static X509TrustManager systemDefault;
	public static X509TrustManager getDefault() throws NoSuchAlgorithmException {
		if (systemDefault == null) {
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			try {
				tmf.init((KeyStore) null);
			} catch (KeyStoreException e) {
				throw new NoSuchAlgorithmException("Unable to find system default trust manger", e);
			}
			for (TrustManager manager : tmf.getTrustManagers()) {
				if (manager instanceof X509TrustManager) {
					return systemDefault = (X509TrustManager) manager;
				}
			}
			throw new NoSuchAlgorithmException("Unable to find system default trust manger");
		}
		return systemDefault;
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
		X509Certificate key1 = ((X509Certificate) key.key);
		getDefault().checkClientTrusted(new X509Certificate[] {key1}, key1.getPublicKey().getAlgorithm());
		return true;
	}
	@Override
	public byte[] sign(MSSKeyPair key, SecureRandom random, byte[] data) throws GeneralSecurityException { return ArrayCache.BYTES; }
}