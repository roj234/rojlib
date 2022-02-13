package roj.net.mss;

import java.security.PublicKey;
import java.security.cert.X509Certificate;

/**
 * @author Roj233
 * @since 2022/2/13 20:25
 */
public final class X509CertKey implements MSSPubKey {
    public final int keyId;
    public final X509Certificate cer;

    public X509CertKey(int keyId, X509Certificate cer) {
        this.keyId = keyId;
        this.cer = cer;
    }

    @Override
    public String name() {
        return "X.509 Certificate";
    }

    @Override
    public int keyId() {
        return keyId;
    }

    @Override
    public PublicKey key() {
        return cer.getPublicKey();
    }
}
