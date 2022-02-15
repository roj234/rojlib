package roj.crypt;

import javax.crypto.IllegalBlockSizeException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * @author Roj233
 * @since 2022/2/17 22:05
 */
public class XTSCipher implements CipheR {
    private final CipheR cip;
    private byte[] key1, key2;
    private int flag;

    private long sectorNo;
    private final int GFA;

    private final ByteBuffer t1, t2;
    private final byte[] t3;

    public XTSCipher(CipheR cip) {
        this(cip, 2);
    }

    public XTSCipher(CipheR cip, int multiplier) {
        this.cip = cip;
        if (cip.getBlockSize() == 0) throw new IllegalArgumentException("Not a block cipher");
        t1 = ByteBuffer.allocate(cip.getBlockSize());
        t2 = ByteBuffer.allocate(cip.getBlockSize());
        t3 = new byte[cip.getBlockSize()];
        GFA = multiplier;
    }

    @Override
    public int getBlockSize() {
        return cip.getBlockSize();
    }

    @Override
    public String name() {
        return "XTS";
    }

    @Override
    public void setKey(byte[] key, int flags) {
        key1 = Arrays.copyOfRange(key, 0, key.length/2);
        key2 = Arrays.copyOfRange(key, key.length/2, key.length);

        sectorNo = 0;
        flag = flags;
    }

    @Override
    public int crypt(ByteBuffer in, ByteBuffer out) throws GeneralSecurityException {
        if (in.remaining() < cip.getBlockSize()) throw new IllegalBlockSizeException("At least one block is required for XTS");
        if (out.remaining() < in.remaining()) return BUFFER_OVERFLOW;

        byte[] b1 = t1.array();
        ByteBuffer t2 = this.t2;
        byte[] b2 = t2.array();
        ByteBuffer t2_ = t2.duplicate();
        byte[] b3 = this.t3;
        CipheR cip = this.cip;

        // begin of new block
        // X = E(I)
        t2.putLong(sectorNo++);
        while (t2.hasRemaining()) t2.put((byte) 0);

        cip.setKey(key1, CipheR.ENCRYPT);
        t2.position(0); t1.position(0);
        cip.crypt(t2, t1);

        cip.setKey(key2, flag);

        boolean DEC = (flag & CipheR.DECRYPT) != 0;
        int lim = DEC && in.remaining() % b1.length != 0 ? b1.length << 1 : b1.length;
        while (in.remaining() >= lim) {
            // C = E(P ^ X) ^ X
            for (int i = 0; i < b1.length; i++) b2[i] = (byte) (b1[i] ^ in.get());
            t2.position(0); t2_.position(0);
            cip.crypt(t2, t2_);

            for (int i = 0; i < b1.length; i++) out.put((byte) (b1[i] ^ b2[i]));

            // X *= a
            mul(b1, GFA);
        }

        if (in.hasRemaining()) {
            if (!DEC) {
                int delta = out.position() - b1.length;
                out.position(delta);

                // backup ciphertext remain
                int j = in.remaining();
                out.get(b3, 0, j).position(delta);

                // plaintext
                int i = 0;
                while (in.hasRemaining()) b2[i] = (byte) (b1[i++] ^ in.get());
                // steal ciphertext
                while (i < b1.length) b2[i] = (byte) (b1[i] ^ out.get(delta + i++));

                t2.position(0); t2_.position(0);
                cip.crypt(t2, t2_);

                for (i = 0; i < b1.length; i++) out.put((byte) (b1[i] ^ b2[i]));
                // write remain
                out.put(b3, 0, j);
            } else {
                // 备份X
                System.arraycopy(b1, 0, b3, 0, b1.length);

                // 读取block[len-2]
                in.get(b2);

                // next X
                mul(b1, GFA);

                // cipher
                for (int i = 0; i < b1.length; i++) b2[i] ^= b1[i];
                t2.position(0); t2_.position(0);
                cip.crypt(t2, t2_);

                // b2[0,rem] 最后的明文, [rem,len] 密文block[len-2] remain
                int rem = in.remaining();
                for (int i = 0; i < b1.length; i++) b2[i] ^= b1[i];

                // 恢复X
                byte[] t = b1;
                b1 = b3;
                b3 = t;

                // 备份明文
                System.arraycopy(b2, 0, b3, 0, rem);

                // 组装block[len-2]
                for (int i = 0; i < rem; i++) b2[i] = (byte) (b1[i] ^ in.get());
                for (int i = rem; i < b1.length; i++) b2[i] ^= b1[i];
                t2.position(0); t2_.position(0);
                cip.crypt(t2, t2_);

                // put plain[len-2]
                for (int i = 0; i < b1.length; i++) out.put((byte) (b1[i] ^ b2[i]));
                // put plain[len-1]
                out.put(b3, 0, rem);
            }
        }
        t2.position(0);
        return OK;
    }

    private static void mul(byte[] x, int y) {
        int carry = 0;
        for (int i = x.length - 1; i >= 0; i--) {
            int product = (x[i] & 0xFF) * y + carry;
            x[i] = (byte)product;
            carry = product >>> 8;
        }
    }
}
