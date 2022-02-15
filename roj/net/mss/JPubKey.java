package roj.net.mss;

import javax.crypto.Cipher;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

/**
 * @author solo6975
 * @since 2022/2/13 12:58
 */
public class JPubKey implements MSSPubKey {
    protected int id;
    protected PublicKey key;

    public JPubKey(PublicKey key) {
        this.key = key;
    }

    public JPubKey(int identity, PublicKey key) {
        this.id = identity;
        this.key = key;
    }

    @Override
    public String getAlgorithm() {
        return "SimplePubKey " + key;
    }

    @Override
    public int pskIdentity() {
        return id;
    }

    @Override
    public byte[] encodedKey() {
        return key.getEncoded();
    }

    @Override
    public Cipher encoder() {
        try {
            Cipher c = Cipher.getInstance(key.getAlgorithm());
            c.init(Cipher.ENCRYPT_MODE, key);
            return c;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Should not happen");
        }
    }
}
