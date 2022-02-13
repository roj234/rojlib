package roj.net.mss;

import java.nio.ByteBuffer;

/**
 * Should be used with MessageDigest
 * @author solo6975
 * @since 2022/2/13 17:44
 */
public interface MSSSign {
    String name();
    int length();

    default void setSignKey(MSSPubKey key) {
        setSignKey(key.key().getEncoded());
    }
    void setSignKey(byte[] key);
    void updateSign(byte[] b, int off, int len);
    void updateSign(ByteBuffer b);
    byte[] sign();
}
