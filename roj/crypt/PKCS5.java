package roj.crypt;

import javax.crypto.BadPaddingException;
import javax.crypto.ShortBufferException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

/**
 * @author solo6975
 * @since 2022/2/12 17:54
 */
public final class PKCS5 implements Padding {
    private final int block;

    public PKCS5(int padding) {
        this.block = padding;
    }

    @Override
    public int getPaddedLength(int data) {
        int len = block - data % block;
        return data + len;
    }

    public void pad(byte[] src, int srcLen, byte[] dst) throws GeneralSecurityException {
        System.arraycopy(src, 0, dst, 0, srcLen);
        int len = block - srcLen % block;

        int lim = srcLen;
        if (dst.length < lim + len)
            throw new ShortBufferException("Unable pad, more: " + (len + lim - dst.length));
        byte num = (byte) len;
        while (len-- > 0) dst[lim++] = num;
    }

    public int unpad(byte[] src, int srcLen, byte[] dst) throws GeneralSecurityException {
        byte last = src[srcLen - 1];
        int num = last & 0xff;
        if (num <= 0 || num > block) throw new BadPaddingException();

        int start = srcLen - num;
        if (start < 0) throw new BadPaddingException();

        for (int i = srcLen - 2; i >= start; i--) {
            if (src[i] != last) throw new BadPaddingException();
        }
        if (src != dst) System.arraycopy(src, 0, dst, 0, srcLen - num);
        return srcLen - num;
    }

    @Override
    public void unpad(ByteBuffer buf) throws GeneralSecurityException {
        byte last = buf.get(buf.limit() - 1);
        int num = last & 0xff;
        if (num <= 0 || num > block) throw new BadPaddingException();

        int start = buf.limit() - num;
        if (start < 0) throw new BadPaddingException();

        for (int i = buf.limit() - 2; i >= start; i--) {
            if (buf.get(i) != last) throw new BadPaddingException();
        }
        buf.limit(buf.limit() - num);
    }

    @Override
    public String name() {
        return "PKCS5";
    }

    @Override
    public int length() {
        return block;
    }
}
