package roj.net.mss;

import roj.crypt.HMAC;
import roj.util.EmptyArrays;
import roj.util.Helpers;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author solo6975
 * @since 2022/2/13 17:45
 */
public class HMACSign implements MSSSign {
    public static final HMACSign SHA256withHMAC;
    public static final HMACSign SHA384withHMAC;

    static {
        HMACSign t;
        try {
            t = new HMACSign(MessageDigest.getInstance("SHA-256"), EmptyArrays.BYTES, 80);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            t = null;
        }
        SHA256withHMAC = t;

        try {
            t = new HMACSign(MessageDigest.getInstance("SHA-384"), EmptyArrays.BYTES, 80);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            t = null;
        }
        SHA384withHMAC = t;
    }

    private final HMAC md;
    private final byte[] salt;
    private final int iteration;

    public HMACSign(MessageDigest md, byte[] salt, int iteration) {
        this.md = new HMAC(md);
        this.salt = salt;
        this.iteration = iteration;
    }

    @Override
    public String name() {
        return md.getAlgorithm();
    }

    @Override
    public int length() {
        return md.getDigestLength();
    }

    @Override
    public void setSignKey(byte[] key) {
        md.setKey(key, 0, key.length);
    }

    @Override
    public void updateSign(byte[] b, int off, int len) {
        md.update(b, off, len);
    }

    @Override
    public void updateSign(ByteBuffer b) {
        md.update(b);
    }

    @Override
    public byte[] sign() {
        byte[] data = md.digestShared();

        int len = iteration;
        while (--len > 0) {
            md.update(data);
            md.update(salt);
            md.digestShared();
        }
        return md.digestShared();
    }

    public MSSSign copy() {
        try {
            return new HMACSign((MessageDigest) md.md.clone(), salt, iteration);
        } catch (CloneNotSupportedException e) {
            Helpers.athrow(e);
            return null;
        }
    }
}
