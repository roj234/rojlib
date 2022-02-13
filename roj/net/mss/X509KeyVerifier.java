package roj.net.mss;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * @author Roj233
 * @since 2022/2/13 20:25
 */
public class X509KeyVerifier implements MSSKeyVerifier {
    private static X509TrustManager systemDefault;

    final X509TrustManager tm;
    final CertificateFactory factory;

    public X509KeyVerifier() {
        try {
            factory = CertificateFactory.getInstance("X.509");
            getDefault();
            tm = systemDefault;
        } catch (Throwable e) {
            throw new Error();
        }
    }

    public X509KeyVerifier(X509TrustManager tm) {
        try {
            factory = CertificateFactory.getInstance("X.509");
        } catch (Throwable e) {
            throw new Error();
        }
        this.tm = tm;
    }

    private static void getDefault() throws NoSuchAlgorithmException {
        if (systemDefault == null) {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            try {
                tmf.init((KeyStore) null);
            } catch (KeyStoreException e) {
                throw new NoSuchAlgorithmException("Unable to find system default trust manger", e);
            }
            for (TrustManager manager : tmf.getTrustManagers()) {
                if (manager instanceof X509TrustManager) {
                    systemDefault = (X509TrustManager) manager;
                    return;
                }
            }
            throw new NoSuchAlgorithmException("Unable to find system default trust manger");
        }
    }

    public X509TrustManager getTrustManager() {
        return tm;
    }

    @Override
    public void verify(MSSPubKey key) throws CertificateException {
        X509Certificate cer = ((X509CertKey) key).cer;
        tm.checkClientTrusted(new X509Certificate[]{ cer }, cer.getPublicKey().getAlgorithm());
    }
}
