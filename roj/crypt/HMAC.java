package roj.crypt;

import roj.util.Helpers;

import java.security.DigestException;
import java.security.MessageDigest;

/**
 * @author solo6975
 * @since 2022/2/13 17:52
 */
public final class HMAC extends MessageDigest {
    public final MessageDigest md;
    private final int blockSize;
    private final byte[] ipad, opad, tmp;

    public HMAC(MessageDigest md) {
        super(md.getAlgorithm() + "withHMAC");
        if (md instanceof HMAC) throw new IllegalStateException();
        this.md = md;
        this.blockSize = md.getDigestLength();
        this.ipad = new byte[blockSize];
        this.opad = new byte[blockSize];
        this.tmp  = new byte[blockSize];
    }

    @Override
    protected int engineGetDigestLength() {
        return md.getDigestLength();
    }

    @Override
    protected void engineUpdate(byte input) {
        md.update(input);
    }

    @Override
    protected void engineUpdate(byte[] input, int off, int len) {
        md.update(input, off, len);
    }

    public void setKey(byte[] key, int off, int len) {
        md.reset();

        if (len > blockSize) {
            md.update(key, off, len);
            key = md.digest();
            off = 0;
            len = key.length;
        }
        int i = 0;
        while (off < len) {
            ipad[i  ] = (byte) (key[off  ] ^ 0x36);
            opad[i++] = (byte) (key[off++] ^ 0x5C);
        }

        reset();
    }

    @Override
    protected byte[] engineDigest() {
        return digestShared().clone();
    }

    public byte[] digestShared() {
        byte[] hash = tmp;
        try {
            md.digest(hash, 0, hash.length);
        } catch (DigestException e) {
            Helpers.athrow(e);
        }
        md.update(opad);
        md.update(hash);
        try {
            md.digest(hash, 0, hash.length);
        } catch (DigestException e) {
            Helpers.athrow(e);
        }
        reset();
        return hash;
    }

    @Override
    protected void engineReset() {
        md.reset();
        md.update(ipad);
    }

    @Override
    public String toString() {
        return getAlgorithm() + " Message Digest from RojLib";
    }
}
