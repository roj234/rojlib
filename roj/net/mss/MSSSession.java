package roj.net.mss;

/**
 * @author solo6975
 * @since 2022/2/15 20:48
 */
public final class MSSSession {
    public final int id;
    public final CipherSuite suite;
    public final byte[] sharedKey;

    public MSSSession(int id, CipherSuite suite, byte[] sharedKey) {
        if (id == 0) throw new IllegalArgumentException("Id should not be 0");
        this.id = id;
        this.suite = suite;
        this.sharedKey = sharedKey;
    }
}
