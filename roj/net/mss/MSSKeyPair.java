package roj.net.mss;

import javax.crypto.Cipher;

/**
 * @author solo6975
 * @since 2022/2/13 13:06
 */
public interface MSSKeyPair extends MSSPubKey {
    Cipher decoder();
    int formatId();
}
