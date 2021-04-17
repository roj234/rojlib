package roj.crypt.eddsa;

import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * @author Roj234
 * @since 2023/8/4 0004 3:48
 */
public class EdKeyFactory extends KeyFactorySpi {
    protected PrivateKey engineGeneratePrivate(KeySpec ks) throws InvalidKeySpecException {
        if (ks instanceof PKCS8EncodedKeySpec) return new EdPrivateKey((PKCS8EncodedKeySpec)ks);

        throw new InvalidKeySpecException("unsupported "+ks.getClass().getName());
    }

    protected PublicKey engineGeneratePublic(KeySpec ks) throws InvalidKeySpecException {
        if (ks instanceof X509EncodedKeySpec) return new EdPublicKey((X509EncodedKeySpec)ks);

        throw new InvalidKeySpecException("unsupported "+ks.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    protected <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> ksType) throws InvalidKeySpecException {
        if (ksType.isAssignableFrom(X509EncodedKeySpec.class) && key instanceof EdPublicKey) {
            return (T) new X509EncodedKeySpec(key.getEncoded());
        } else if (ksType.isAssignableFrom(PKCS8EncodedKeySpec.class) && key instanceof EdPrivateKey) {
            return (T) new PKCS8EncodedKeySpec(key.getEncoded());
        }

        throw new InvalidKeySpecException("unsupported " + ksType.getName());
    }

    protected Key engineTranslateKey(Key key) throws InvalidKeyException {
        throw new InvalidKeyException("unsupported ");
    }
}
