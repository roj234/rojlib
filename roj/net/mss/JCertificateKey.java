package roj.net.mss;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * @author Roj233
 * @since 2022/2/13 20:25
 */
public class JCertificateKey extends JKey {
	public final X509Certificate cer;

	public JCertificateKey(X509Certificate cer) {
		super(cer.getPublicKey());
		this.cer = cer;
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

	public void verify(X509TrustManager tm) throws CertificateException {
		tm.checkClientTrusted(new X509Certificate[] {cer}, cer.getPublicKey().getAlgorithm());
	}
}
