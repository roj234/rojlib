package roj.net.mss;

import java.security.PublicKey;

/**
 * @author solo6975
 * @since 2022/2/13 12:58
 */
public class SimplePubKey implements MSSPubKey {
    protected int id;
    protected PublicKey key;

    public SimplePubKey(PublicKey key) {
        this.key = key;
    }

    public SimplePubKey(int id, PublicKey key) {
        this.id = id;
        this.key = key;
    }

    @Override
    public String name() {
        return "SimplePubKey " + key;
    }

    @Override
    public int keyId() {
        return id;
    }

    @Override
    public PublicKey key() {
        return key;
    }
}
