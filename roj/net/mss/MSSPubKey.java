package roj.net.mss;

import roj.crypt.OAEP;

import javax.crypto.Cipher;

/**
 * @author solo6975
 * @since 2022/2/13 13:02
 */
public interface MSSPubKey {
    String getAlgorithm();
    int pskIdentity();

    byte[] encodedKey();

    Cipher encoder();

    default OAEP createOAEP() {
        return new OAEP(128);
    }
}
