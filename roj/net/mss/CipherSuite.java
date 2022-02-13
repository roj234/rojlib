package roj.net.mss;

import roj.collect.CharMap;
import roj.crypt.*;

import java.util.function.Supplier;

/**
 * @author solo6975
 * @since 2022/2/13 18:35
 */
public final class CipherSuite {
    public static final int AsymmetricKeyType_OFFSET    = 0;
    public static final int SymmetricCipherType_OFFSET  = 8;
    public static final int SignatureType_OFFSET        = 16;
    public static final int VerificationType_OFFSET     = 24;
    public static final int SubkeyType_OFFSET           = 28;

    private static final CharMap<MSSClientKey>        KEYS_BY_ID     = new CharMap<>();
    private static final CharMap<MSSCiphers>          CIPHERS_BY_ID  = new CharMap<>();
    private static final CharMap<Supplier<MSSSign>>   SIGNS_BY_ID    = new CharMap<>();
    private static final CharMap<Supplier<MSSHash>>   HASHES_BY_ID   = new CharMap<>();
    private static final CharMap<Supplier<MSSSubKey>> SUBKEYS_BY_ID  = new CharMap<>();

    public static void registerKey(int id, MSSClientKey kf) {
        KEYS_BY_ID.put((char) id, kf);
    }
    public static void registerCipher(int id, MSSCiphers cip) {
        CIPHERS_BY_ID.put((char) id, cip);
    }
    public static void registerSignature(int id, Supplier<MSSSign> sign) {
        SIGNS_BY_ID.put((char) id, sign);
    }
    public static void registerHash(int id, Supplier<MSSHash> hash) {
        HASHES_BY_ID.put((char) id, hash);
    }
    public static void registerSubkey(int id, Supplier<MSSSubKey> hash) {
        SUBKEYS_BY_ID.put((char) id, hash);
    }

    public static <T> Supplier<T> identity(T t) {
        return () -> t;
    }

    public static final int KEY_X509              = 0;
    public static final int KEY_X509_CERTIFICATE  = 1;
    public static final int KEY_RSA_ROJ234_BIGINT = 2;

    public static final int CIPHER_AES_GCM            = 0;
    public static final int CIPHER_AES_CFB8           = 1;
    public static final int CIPHER_SM4_CFB8           = 2;
    public static final int CIPHER_XCHACHA20          = 3;
    public static final int CIPHER_XCHACHA20_POLY1305 = 4;
    public static final int CIPHER_CHACHA20           = 5;
    public static final int CIPHER_CHACHA20_POLY1305  = 6;

    public static final int SIGN_SHA256withHMAC = 0;
    public static final int SIGN_SHA384withHMAC = 1;
    public static final int SIGN_POLY1305       = 2;

    public static final int HASH_None    = 0;
    public static final int HASH_Adler32 = 1;

    public static final int SUBKEY_DHE   = 0;
    public static final int SUBKEY_ECDHE = 1;

    static {
        registerKey(KEY_X509, JKeyFormat.JAVARSA);
        try {
            registerKey(KEY_X509_CERTIFICATE, new X509KeyFormat());
        } catch (Throwable ignored) {}

        registerCipher(CIPHER_AES_GCM, new JCiphers("AES/GCM/NoPadding", 32));
        registerCipher(CIPHER_AES_CFB8, new JCiphers("AES/CFB8/NoPadding", 32));

        registerCipher(CIPHER_SM4_CFB8, new SimpleCiphers(32, SM4::new, MyCipher.MODE_CFB));
        registerCipher(CIPHER_XCHACHA20, new SimpleCiphers(32, XChaCha::new));
        registerCipher(CIPHER_XCHACHA20_POLY1305, new SimpleCiphers(32, XChaCha_Poly1305::new));

        try {
            registerSignature(SIGN_SHA256withHMAC, HMACSign.SHA256withHMAC::copy);
        } catch (NullPointerException ignored) {}
        try {
            registerSignature(SIGN_SHA384withHMAC, HMACSign.SHA384withHMAC::copy);
        } catch (NullPointerException ignored) {}
        registerSignature(SIGN_POLY1305, Poly1305::new);

        registerHash(HASH_None, identity(new NullHash()));
        registerHash(HASH_Adler32, Adler32Hash::new);

        registerSubkey(SUBKEY_DHE, DH::new);
    }

    public final int specificationId;
    public int       preference;

    public final MSSClientKey keyFormat;
    public final MSSCiphers ciphers;
    public final Supplier<MSSSign> sign;
    public final Supplier<MSSHash> hash;
    public final Supplier<MSSSubKey> subKey;

    public CipherSuite(int keyType, int cipherType, int signatureType, int verificationType, int subkeyType) {
        this.specificationId =
            (keyType << AsymmetricKeyType_OFFSET) |
            (cipherType << SymmetricCipherType_OFFSET) |
            (signatureType << SignatureType_OFFSET) |
            (verificationType << VerificationType_OFFSET) |
            (subkeyType << SubkeyType_OFFSET);
        this.keyFormat = KEYS_BY_ID.get((char) keyType);
        this.ciphers = CIPHERS_BY_ID.get((char) cipherType);
        this.sign = SIGNS_BY_ID.get((char) signatureType);
        this.hash = HASHES_BY_ID.get((char) verificationType);
        this.subKey = SUBKEYS_BY_ID.get((char) subkeyType);
    }

    public DH createDHE(int rnd) {
        return new DH();
    }

    @Override
    public String toString() {
        return "CipherSuite{" +
            "id=" + Integer.toHexString(specificationId) +
            ", order=" + preference +
            ", keyFormat=" + keyFormat +
            ", ciphers=" + ciphers +
            ", sign=" + sign.get() +
            ", hash=" + hash.get() +
            ", subKey=" + subKey.get() +
            '}';
    }
}
