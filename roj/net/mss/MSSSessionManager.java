package roj.net.mss;

import javax.annotation.Nullable;

/**
 * @author solo6975
 * @since 2022/2/15 20:49
 */
public interface MSSSessionManager {
    MSSSession getSession(int sessionId);
    @Nullable
    MSSSession newSession(CipherSuite suite, byte[] sharedKey);
}
