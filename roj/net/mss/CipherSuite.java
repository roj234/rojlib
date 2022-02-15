package roj.net.mss;

import roj.collect.CharMap;
import roj.crypt.*;

import java.util.function.Supplier;

/**
 * @author solo6975
 * @since 2022/2/13 18:35
 */
public final class CipherSuite {
    private static final CharMap<MSSClientKey> KEY_FORMAT = new CharMap<>();
    public static MSSClientKey getKey(int id) {
        return KEY_FORMAT.get((char) id);
    }
    public static void registerKey(int id, MSSClientKey kf) {
        KEY_FORMAT.put((char) id, kf);
    }

    public static final int KEY_X509_RSA         = 0;
    public static final int KEY_X509_CERTIFICATE = 1;
    public static final int KEY_X509_EC          = 2;
    public static final int KEY_X509_DH          = 3;

    public static MSSCiphers
            CIPHER_AES_GCM            = null,
            CIPHER_AES_CFB8           = new JCiphers("AES/CFB8/NoPadding", 32),
            CIPHER_SM4_CFB8           = new SimpleCiphers(32, SM4::new, MyCipher.MODE_CFB),
            CIPHER_XCHACHA20          = new SimpleCiphers(32, XChaCha::new),
            CIPHER_XCHACHA20_POLY1305 = new SimpleCiphers(32, XChaCha_Poly1305::new),
            CIPHER_AES_SIV            = new JCiphers("AES/SIV/NoPadding", 32);

    public static Supplier<MSSSign>
            SIGN_SHA256   = HMACSign.SHA256withHMAC::copy,
            SIGN_SHA384   = null,
            SIGN_POLY1305 = Poly1305::new;

    public static Supplier<MSSSubKey>
            SUBKEY_DHE   = DH::new,
            SUBKEY_ECDHE = ECDH::new;

    static {
        registerKey(KEY_X509_RSA, X509KeyFormat.RSA);
        registerKey(KEY_X509_CERTIFICATE, new X509CertKeyFormat());
        registerKey(KEY_X509_EC, X509KeyFormat.EC);
        registerKey(KEY_X509_DH, X509KeyFormat.DH);

        try {
            SIGN_SHA384 = HMACSign.SHA384withHMAC::copy;
        } catch (NullPointerException ignored) {}
    }

    public final int specificationId;
    public int       preference;

    public final MSSCiphers ciphers;
    public final Supplier<MSSSign> sign;
    public final Supplier<MSSSubKey> subKey;

    public CipherSuite(int id, MSSCiphers ciphers, Supplier<MSSSign> sign, Supplier<MSSSubKey> subKey) {
        this.specificationId = id;
        this.ciphers = ciphers;
        this.sign = sign;
        this.subKey = subKey;
        if (ciphers == null || sign == null || subKey == null)
            throw new IllegalStateException("Wrong type id");
    }

    @Override
    public String toString() {
        return "CipherSuite{" +
            "id=" + Integer.toHexString(specificationId) +
            ", ciphers=" + ciphers +
            ", sign=" + sign.get() +
            ", subKey=" + subKey.get() +
            '}';
    }
}
