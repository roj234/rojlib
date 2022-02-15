package roj.net.mss;

import java.security.GeneralSecurityException;

/**
 * @author solo6975
 * @since 2022/2/13 19:43
 */
public interface MSSKeyManager {
    void verify(MSSPubKey key) throws GeneralSecurityException;

    default int getRequiredClientKeyDesc() {
        return 0;
    }

    default byte[] getClientKey(int clientKeyDesc) {
        return null;
    }

    default byte getClientKeyType(int clientKeyDesc) {
        return 0;
    }
}
