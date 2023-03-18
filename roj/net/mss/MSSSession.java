package roj.net.mss;

import roj.util.DynByteBuf;

/**
 * @author solo6975
 * @since 2022/2/15 20:48
 */
public final class MSSSession {
	public DynByteBuf id;
	public final byte[] key;
	public final MSSCipherFactory ciphers;

	public MSSSession(byte[] key, MSSCipherFactory ciphers) {
		this.key = key;
		this.ciphers = ciphers;
	}
}
