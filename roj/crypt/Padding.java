package roj.crypt;

import java.security.GeneralSecurityException;

/**
 * @author solo6975
 * @since 2022/2/12 20:32
 */
public interface Padding {
    void encode(byte[] src, int srcLen, byte[] dst) throws GeneralSecurityException;
    int decode(byte[] src, int srcLen, byte[] dst) throws GeneralSecurityException;

    String name();
    int length();
}
