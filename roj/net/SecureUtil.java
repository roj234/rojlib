package roj.net;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * @author Roj234
 * @since 2021/2/5 0:26
 */
@Deprecated
public final class SecureUtil {
	public static final String KEY_FORMAT = "PKCS12", MANAGER_FORMAT = "SunX509";

	public static KeyManager[] makeKeyManagers(InputStream pkPath, char[] passwd) throws IOException, GeneralSecurityException {
		KeyStore ks = KeyStore.getInstance(KEY_FORMAT);
		try (InputStream in = pkPath) { ks.load(in, passwd); }

		KeyManagerFactory kmf = KeyManagerFactory.getInstance(MANAGER_FORMAT);
		kmf.init(ks, passwd);

		return kmf.getKeyManagers();
	}
}