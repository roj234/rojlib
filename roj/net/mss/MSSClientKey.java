package roj.net.mss;

import java.security.GeneralSecurityException;

/**
 * @author solo6975
 * @since 2022/2/13 18:56
 */
public interface MSSClientKey {
    MSSPubKey decode(byte[] data) throws GeneralSecurityException;
}
