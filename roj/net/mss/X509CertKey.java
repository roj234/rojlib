package roj.net.mss;

import java.security.cert.X509Certificate;

/**
 * @author Roj233
 * @since 2022/2/13 20:25
 */
public final class X509CertKey extends JPubKey {
    public final X509Certificate cer;

    public X509CertKey(int keyId, X509Certificate cer) {
        super(keyId, cer.getPublicKey());
        this.cer = cer;
    }

    @Override
    public String getAlgorithm() {
        return "X.509 Certificate";
    }
}
