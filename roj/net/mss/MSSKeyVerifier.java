package roj.net.mss;

import java.security.GeneralSecurityException;

/**
 * @author solo6975
 * @since 2022/2/13 19:43
 */
public interface MSSKeyVerifier {
    void verify(MSSPubKey key) throws GeneralSecurityException;
}
