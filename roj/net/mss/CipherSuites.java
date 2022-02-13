package roj.net.mss;

import static roj.net.mss.CipherSuite.*;

/**
 * @author solo6975
 * @since 2022/2/13 19:11
 */
public class CipherSuites {
    public static final CipherSuite
        X509_DHE_AESGCM_SHA256_XHASH   = new CipherSuite(KEY_X509, CIPHER_AES_GCM, SIGN_SHA256withHMAC, HASH_None, SUBKEY_DHE),
        X509_DHE_SM4CFB_SHA256_XHASH   = new CipherSuite(KEY_X509, CIPHER_SM4_CFB8, SIGN_SHA256withHMAC, HASH_None, SUBKEY_DHE),
        X509_DHE_XCHACHA20_SHA256_XHASH = new CipherSuite(KEY_X509, CIPHER_XCHACHA20, SIGN_SHA256withHMAC, HASH_None, SUBKEY_DHE),
        X509_DHE_XCHACHA20POLY1305_SHA256_XHASH = new CipherSuite(KEY_X509, CIPHER_XCHACHA20_POLY1305, SIGN_SHA256withHMAC, HASH_None, SUBKEY_DHE),
        X509_ECDHE_AESGCM_SHA384_XHASH = new CipherSuite(KEY_X509, CIPHER_AES_GCM, SIGN_SHA256withHMAC, HASH_None, SUBKEY_ECDHE);
}
