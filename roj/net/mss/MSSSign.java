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
        setSignKey(key.encodedKey());
    }
    void setSignKey(byte[] key);
    default void updateSign(byte[] b) { updateSign(b, 0, b.length); }
    void updateSign(byte[] b, int off, int len);
    void updateSign(ByteBuffer b);
    byte[] sign();
}
