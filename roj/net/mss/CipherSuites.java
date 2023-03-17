package roj.net.mss;

import static roj.net.mss.CipherSuite.*;

/**
 * @author solo6975
 * @since 2022/2/13 19:11
 */
public class CipherSuites {
	public static final CipherSuite SM4CFB_SHA256 = new CipherSuite(0x0001, CIPHER_SM4_CFB8, SIGN_SHA256),
		XCHACHA20_SHA256 = new CipherSuite(0x0002, CIPHER_XCHACHA20, SIGN_SHA256),
		XCHACHA20POLY1305_SHA256 = new CipherSuite(0x0003, CIPHER_XCHACHA20_POLY1305, SIGN_SHA256),
		AESGCM_SHA256 = new CipherSuite(0x0006, CipherSuite.CIPHER_AES_GCM, SIGN_SHA256);
}