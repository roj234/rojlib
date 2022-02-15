package roj.crypt;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

/**
 * @author solo6975
 * @since 2022/2/12 20:32
 */
public interface Padding {
    int getPaddedLength(int data);

    void pad(byte[] src, int srcLen, byte[] dst) throws GeneralSecurityException;

    /**
     * To Implementors: 可能会有src = dst的情况
     */
    int unpad(byte[] src, int srcLen, byte[] dst) throws GeneralSecurityException;

    default void unpad(ByteBuffer buf) throws GeneralSecurityException {
        if (buf.hasArray() && buf.position() == 0 && buf.arrayOffset() == 0) {
            int len = unpad(buf.array(), buf.limit(), buf.array());
            buf.limit(len);
        } else {
            byte[] tmp = new byte[buf.remaining()];
            int pos = buf.position();
            buf.get(tmp);
            int len = unpad(tmp, tmp.length, tmp);
            buf.limit(pos + len).position(pos);
        }
    }

    String name();
    int length();
}
