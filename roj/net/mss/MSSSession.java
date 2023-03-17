package roj.net.mss;

import roj.util.DynByteBuf;

/**
 * @author solo6975
 * @since 2022/2/15 20:48
 */
public final class MSSSession {
	public DynByteBuf id;
	public final byte[] key;
	public final CipherSuite suite;

	public MSSSession(byte[] key, CipherSuite suite) {
		this.key = key;
		this.suite = suite;
	}
}
