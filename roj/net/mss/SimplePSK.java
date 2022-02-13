package roj.net.mss;

import javax.annotation.Nullable;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * @author solo6975
 * @since 2022/2/13 12:58
 */
public class SimplePSK implements MSSKeyPair {
    protected int       id;
    protected PublicKey key;
    protected PrivateKey pri;

    public SimplePSK(int id, PublicKey key) {
        this.id = id;
        this.key = key;
    }

    public SimplePSK(int id, PublicKey key, PrivateKey pri) {
        this.id = id;
        this.key = key;
        this.pri = pri;
    }

    @Override
    public String name() {
        return "SimplePubKey " + key;
    }

    @Override
    public PublicKey key() {
        return key;
    }

    @Override
    public int keyId() {
        return id;
    }

    @Nullable
    @Override
    public PrivateKey pri() {
        return pri;
    }
}
