package roj.security;

import roj.io.IOUtil;
import roj.util.ByteList;

/**
 * @author solo6975
 * @since 2022/3/21 13:42
 */
public class SipHash {
    private long k0, k1;
    private long v0, v1, v2, v3;

    private final byte[] tmp = new byte[8];
    private byte len, msgLen;

    public void setKeyDefault() {
        k0 = System.currentTimeMillis();
        k1 = System.nanoTime();
        setKey(k0, k1);
    }

    public void setKey(long k0, long k1) {
        init(this.k0 = Long.reverseBytes(k0),
             this.k1 = Long.reverseBytes(k1));
    }

    private void init(long k0, long k1) {
        v0 = k0 ^ 0x736f6d6570736575L;
        v1 = k1 ^ 0x646f72616e646f6dL;
        v2 = k0 ^ 0x6c7967656e657261L;
        v3 = k1 ^ 0x7465646279746573L;
        len = msgLen = 0;
    }

    public long digest(CharSequence msg) {
        init(k0, k1);
        compress(msg);
        return getDigest();
    }

    public void compress(CharSequence msg) {
        ByteList bl = IOUtil.getSharedByteBuf().put(tmp, 0, len).putUTFData(msg);
        compress(bl);
        msgLen += msg.length();
    }

    private void compress(ByteList bl) {
        long v0 = this.v0;
        long v1 = this.v1;
        long v2 = this.v2;
        long v3 = this.v3;

        while (bl.remaining() >= 8) {
            long m = bl.readLongLE();
            v0 ^= m;

            // SipRound
            v0 += v1;
            v1 <<= 13;
            v1 ^= v0;
            v0 <<= 32;

            v2 += v3;
            v3 <<= 16;
            v3 ^= v2;

            v0 += v3;
            v3 <<= 21;
            v3 ^= v0;

            v2 += v1;
            v1 <<= 17;
            v1 ^= v2;
            v2 <<= 32;
            // SipRound

            v3 ^= m;
        }

        this.v0 = v0;
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;

        bl.read(tmp, 0, len = (byte) bl.remaining());
    }

    public long getDigest() {
        ByteList bl = IOUtil.SharedCoder.get().wrap(tmp);
        bl.wIndex(len);
        bl.put(msgLen);
        compress(bl);

        long v0 = this.v0;
        long v1 = this.v1;
        long v2 = this.v2 ^ 0xFF;
        long v3 = this.v3;

        for (int i = 0; i < 2; i++) {
            // SipRound
            v0 += v1;
            v1 <<= 13;
            v1 ^= v0;
            v0 <<= 32;

            v2 += v3;
            v3 <<= 16;
            v3 ^= v2;

            v0 += v3;
            v3 <<= 21;
            v3 ^= v0;

            v2 += v1;
            v1 <<= 17;
            v1 ^= v2;
            v2 <<= 32;
            // SipRound
        }

        return v0 ^ v1 ^ v2 ^ v3;
    }
}
