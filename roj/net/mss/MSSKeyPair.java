package roj.net.mss;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;

/**
 * @author solo6975
 * @since 2022/2/13 13:06
 */
public interface MSSKeyPair extends MSSPubKey {
    @Nullable
    PrivateKey pri();

    default Cipher priEncoder() {
        PrivateKey key = pri();
        if (key == null) throw new UnsupportedOperationException();
        try {
            Cipher c = Cipher.getInstance(key.getAlgorithm());
            c.init(Cipher.ENCRYPT_MODE, key);
            return c;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Should not happen");
        }
    }

    default Cipher priDecoder() {
        PrivateKey key = pri();
        if (key == null) throw new UnsupportedOperationException();
        try {
            Cipher c = Cipher.getInstance(key.getAlgorithm());
            c.init(Cipher.DECRYPT_MODE, key);
            return c;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Should not happen");
        }
    }
}
