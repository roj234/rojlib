package roj.net.mss;

import javax.crypto.Cipher;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;

/**
 * @author solo6975
 * @since 2022/2/13 12:58
 */
public class JKeyPair extends JPubKey implements MSSKeyPair {
    protected PrivateKey pri;
    protected byte[] encodedPub;
    protected byte format;

    public JKeyPair(KeyPair pair) throws NoSuchAlgorithmException {
        this(0, pair);
    }

    public JKeyPair(int identity, KeyPair pair) throws NoSuchAlgorithmException {
        super(identity, pair.getPublic());
        MSSKeyFormat<?> fmt = MSSKeyFormat.getInstance(pair.getPublic().getAlgorithm());
        format = (byte) fmt.formatId();
        pri = pair.getPrivate();
    }

    public JKeyPair(int id, int format, byte[] key, PrivateKey pri) {
        super(id, null);
        this.format = (byte) format;
        this.pri = pri;
        this.encodedPub = key;
    }

    @Override
    public String getAlgorithm() {
        return "SimpleKeyPair " + key;
    }

    @Override
    public Cipher decoder() {
        try {
            Cipher c = Cipher.getInstance(pri.getAlgorithm());
            c.init(Cipher.DECRYPT_MODE, pri);
            return c;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Should not happen");
        }
    }

    @Override
    public int formatId() {
        return format & 0xFF;
    }

    @Override
    public byte[] encodedKey() {
        return encodedPub != null ? encodedPub.clone() : super.encodedKey();
    }
}
