package roj.crypt;

import javax.crypto.BadPaddingException;
import javax.crypto.ShortBufferException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * 最优非对称加密填充（英语：Optimal Asymmetric Encryption Padding，缩写：OAEP）
 * @author solo6975
 * @since 2022/2/12 17:54
 */
public final class OAEP implements Padding {
    static final MessageDigest COMM_H;
    static {
        try {
            COMM_H = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new Error("Oops, not a standard java?");
        }
    }

    private final byte[] t, r;
    private final MessageDigest G, H;
    private Random rnd;

    /**
     * 使用默认约定创建
     * @param n 非对称最大加密字节
     */
    public OAEP(int n) {
        int k0 = COMM_H.getDigestLength();
        assert n >= 2 * k0 : "n must >= 2k0";

        this.t = new byte[n - k0];
        this.r = new byte[k0];
        try {
            G = new PartialHashBlockChain((MessageDigest) COMM_H.clone(), n - k0);
            H = (MessageDigest) COMM_H.clone();
        } catch (CloneNotSupportedException e) {
            throw new Error();
        }
    }

    /**
     * 使用自定义约定创建
     * @param n 非对称最大加密字节
     * @param G G 将 k0 长的 r 扩展至 n - k0 长
     * @param H H 将 n - k0 长的 X 缩短至 k0 长
     */
    public OAEP(int n, MessageDigest G, MessageDigest H) {
        int k0 = H.getDigestLength();
        assert n >= 2 * k0 : "n must >= 2k0";

        this.t = new byte[n - k0];
        this.r = new byte[k0];
        this.G = G;
        this.H = H;
    }

    public void setRnd(Random rnd) {
        this.rnd = rnd;
    }

    /**
     * 数据(密文)长度
     */
    public int length() {
        return t.length + r.length;
    }

    @Override
    public int getPaddedLength(int data) {
        return length();
    }

    public void pad(byte[] src, int srcLen, byte[] dst) throws GeneralSecurityException {
        if (srcLen > t.length) throw new BadPaddingException("Unable to pad");
        if (dst.length < length()) throw new ShortBufferException();

        // 随机生成r
        rnd.nextBytes(r);

        byte[] t = this.t;
        byte[] r = this.r;

        // G 将 k0 长的 r 扩展至 n - k0 长
        G.update(r, 0, r.length);
        G.digest(t, 0, t.length);

        // X = m00...0 ^ G(r)
        for (int i = 0; i < srcLen; i++) {
            // 用 k1 位长的 0 将消息填充至 n - k0 位的长度
            t[i] ^= src[i];
        }

        System.arraycopy(t, 0, dst, 0, t.length);
        // 复用下tmp保存r
        System.arraycopy(r, 0, t, 0, r.length);

        // H 将 n - k0 长的 X 缩短至 k0 长
        H.update(dst, 0, t.length);
        H.digest(r, 0, r.length);

        // Y = r ^ H(X)
        for (int i = 0; i < r.length; i++) {
            r[i] ^= t[i];
        }

        System.arraycopy(r, 0, dst, t.length, r.length);
    }

    public int unpad(byte[] src, int srcOff, byte[] dst) throws GeneralSecurityException {
        if (src.length - srcOff < length()) throw new BadPaddingException();

        int m_len = t.length;
        if (dst.length < m_len) throw new ShortBufferException();

        byte[] r = this.r;

        // 恢复随机串 r 为 Y ^ H(X)
        H.update(src, srcOff, m_len);
        H.digest(r, 0, r.length);

        int off = srcOff + m_len;
        for (int i = 0; i < r.length; i++) {
            r[i] ^= src[off++];
        }

        // 恢复消息 m00...0 为 X ^ G(r)
        G.update(r, 0, r.length);

        if (src != dst) {
            G.digest(dst, 0, m_len);
            for (int i = 0; i < m_len; i++) {
                dst[i] ^= src[i];
            }
        } else {
            byte[] t = this.t;
            G.digest(t, 0, m_len);
            for (int i = 0; i < m_len; i++) {
                dst[i] ^= t[i];
            }
        }

        while (m_len-- > 0) if (dst[m_len] != 0) return m_len+1;
        return 0;
    }

    @Override
    public String name() {
        return "OAEP";
    }
}
