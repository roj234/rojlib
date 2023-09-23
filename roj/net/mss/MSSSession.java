package roj.net.mss;

/**
 * @author solo6975
 * @since 2022/2/15 20:48
 */
public final class MSSSession {
	public final byte[] id;
	public byte[] key;
	public CipherSuite suite;

	public MSSSession(byte[] id) { this.id = id; }
}
