package roj.net;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

/**
 * @author Roj234
 * @since 2021/2/5 0:26
 */
@Deprecated
public final class SecureUtil {
	public static final String KEY_FORMAT = "PKCS12", MANAGER_FORMAT = "SunX509";

	public static void trustAllCertificates() {
		try {
			SSLContext.getDefault().init(null, new TrustManager[] {new TrustAllManager()}, null);
		} catch (NoSuchAlgorithmException | KeyManagementException ignored) {}
		// should not happen
	}

	public static final class TrustAllManager implements X509TrustManager {
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) {}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) {}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	}

	public static KeyManager[] makeKeyManagers(InputStream pkPath, char[] passwd) throws IOException, GeneralSecurityException {
		KeyStore ks = KeyStore.getInstance(KEY_FORMAT);
		try (InputStream in = pkPath) { ks.load(in, passwd); }

		KeyManagerFactory kmf = KeyManagerFactory.getInstance(MANAGER_FORMAT);
		kmf.init(ks, passwd);

		return kmf.getKeyManagers();
	}

}