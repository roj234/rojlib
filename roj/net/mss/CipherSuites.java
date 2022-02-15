package roj.net.mss;

import static roj.net.mss.CipherSuite.*;

/**
 * @author solo6975
 * @since 2022/2/13 19:11
 */
public class CipherSuites {
    public static final CipherSuite
DHE_SM4CFB_SHA256            = new CipherSuite(0x0001, CIPHER_SM4_CFB8, SIGN_SHA256, SUBKEY_DHE),
DHE_XCHACHA20_SHA256         = new CipherSuite(0x0002, CIPHER_XCHACHA20, SIGN_SHA256, SUBKEY_DHE),
DHE_XCHACHA20POLY1305_SHA256 = new CipherSuite(0x0003, CIPHER_XCHACHA20_POLY1305, SIGN_SHA256, SUBKEY_DHE),
ECDHE_AESSIV_SHA256          = new CipherSuite(0x0004, CIPHER_AES_SIV, SIGN_SHA256, SUBKEY_ECDHE),
ECDHE_AESCFB_SHA256          = new CipherSuite(0x0004, CIPHER_AES_CFB8, SIGN_SHA256, SUBKEY_ECDHE),
ECDHE_XCHACHA20_SHA256       = new CipherSuite(0x0005, CIPHER_XCHACHA20, SIGN_SHA256, SUBKEY_ECDHE);
}