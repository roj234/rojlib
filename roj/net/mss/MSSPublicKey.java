package roj.net.mss;

import java.security.PublicKey;
import java.security.cert.Certificate;

/**
 * @author solo6975
 * @since 2022/2/13 13:02
 */
public class MSSPublicKey {
	public final Object key;
	private final byte format;
	private byte[] encoded;

	public MSSPublicKey(PublicKey key) { this.key = key; format = (byte) CipherSuite.getKeyFormat(key.getAlgorithm()); }
	public MSSPublicKey(Certificate key) { this.key = key; format = CipherSuite.PUB_X509_CERTIFICATE; }
	protected MSSPublicKey(Object key, byte format) { this.key = key; this.format = format; }

	public final int format() { return format; }
	public byte[] encode() { return encoded == null ? encoded = CipherSuite.getKeyFormat(format).encode(this) : encoded; }
}