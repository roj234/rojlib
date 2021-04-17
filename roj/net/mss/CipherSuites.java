package roj.net.mss;

import static roj.net.mss.CipherSuite.*;

/**
 * @author solo6975
 * @since 2022/2/13 19:11
 */
public class CipherSuites {
	public static final CipherSuite
		TLS_AES_128_GCM_SHA256 = new CipherSuite(0x1301, CIPHER_AES_128_GCM, SIGN_SHA256),
		TLS_AES_256_GCM_SHA384 = new CipherSuite(0x1302, CIPHER_AES_256_GCM, SIGN_SHA384),
		TLS_CHACHA20_POLY1305_SHA256 = new CipherSuite(0x1303, CIPHER_CHACHA20_POLY1305, SIGN_SHA256),
		MSS_XCHACHA20_POLY1305_SHA256 = new CipherSuite(0x1304, CIPHER_XCHACHA20_POLY1305, SIGN_SHA256);
}