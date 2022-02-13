package roj.crypt;

import roj.net.mss.MSSSubKey;
import roj.util.ByteList;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Diffie–Hellman Key Exchange
 // 听说啥 '为了防止应用优化算法计算上述问题，质数p不是随便选择的，需要符合一定的条件'
 // 那么这条件是啥捏
 * @author Roj233
 * @since 2022/2/12 17:25
 */
public class DH implements MSSSubKey {
    static final int PRIKEY_BITS = 40*8;
    static final int PRIME_BITS = 48*8;

    private BigInteger p, g;
    private BigInteger mySec, myPub;
    private int pktLen;

    /**
     * First(Prime Sender)
     */
    public void init(Random r, boolean def) {
        BigInteger p;
        int g;
        if (def) {
            g = 5;
            p = DEFAULT_PRIME;
        } else {
            switch (r.nextInt(4)) {
                case 0:
                    g = 2;
                    break;
                case 1:
                    g = 3;
                    break;
                case 2:
                    g = 5;
                    break;
                case 3:
                default:
                    g = 7;
                    break;
            }
            p = BigInteger.probablePrime(PRIME_BITS, r);
            int i = r.nextInt(2333);
            while (i-- > 0) r.nextInt();
        }
        this.p = p;
        this.g = new BigInteger(1, new byte[]{(byte) g});
        this.mySec = new BigInteger(PRIKEY_BITS, r);
        this.myPub = this.g.modPow(mySec, p);
        this.pktLen = def ? 4 + myPub.bitLength()/8 : 10 + p.bitLength()/8 + this.g.bitLength()/8 + myPub.bitLength()/8;
    }

    /**
     * Second(Prime Receiver)
     */
    public void init(Random r) {
        this.mySec = new BigInteger(PRIKEY_BITS, r);
    }

    public int length() {
        return pktLen;
    }

    public void reset() {
        this.myPub = this.mySec = null;
    }

    public boolean write1(ByteBuffer bb) {
        if (bb.remaining() < pktLen) return false;
        bb.put((byte) (p != DEFAULT_PRIME ? 1 : 0));
        if (p != DEFAULT_PRIME) {
            byte[] b = p.toByteArray();
            bb.putChar((char) b.length).put(b);
            b = g.toByteArray();
            bb.putChar((char) b.length).put(b);
        }
        return write2(bb);
    }

    public BigInteger read1(ByteBuffer bb) {
        if (bb.get() == 1) {
            byte[] b = new byte[bb.getChar()];
            bb.get(b);
            p = new BigInteger(b);
            b = new byte[bb.getChar()];
            bb.get(b);
            g = new BigInteger(b);
        } else {
            g = new BigInteger("5");
            p = DEFAULT_PRIME;
        }
        myPub = g.modPow(mySec, p);
        this.pktLen = 2 + myPub.bitLength()/8;
        return read2(bb);
    }

    public boolean write2(ByteBuffer bb) {
        if (bb.remaining() < pktLen) return false;
        byte[] b = myPub.toByteArray();
        bb.putChar((char) b.length).put(b);
        return true;
    }

    public BigInteger read2(ByteBuffer bb) {
        byte[] b = new byte[bb.getChar()];
        bb.get(b);
        return new BigInteger(b).modPow(mySec, p);
    }

    public void write1(ByteList bb) {
        bb.put((byte) (p != DEFAULT_PRIME ? 1 : 0));
        if (p != DEFAULT_PRIME) {
            byte[] b = p.toByteArray();
            bb.putShort(b.length).put(b);
            b = g.toByteArray();
            bb.putShort(b.length).put(b);
        }
        write2(bb);
    }

    public BigInteger read1(ByteList bb) {
        if (bb.readByte() == 1) {
            byte[] b = new byte[bb.readChar()];
            bb.read(b);
            p = new BigInteger(b);
            b = new byte[bb.readChar()];
            bb.read(b);
            g = new BigInteger(b);
        } else {
            g = new BigInteger("5");
            p = DEFAULT_PRIME;
        }
        myPub = g.modPow(mySec, p);
        this.pktLen = 2 + myPub.bitLength()/8;
        return read2(bb);
    }

    public void write2(ByteList bb) {
        byte[] b = myPub.toByteArray();
        bb.putShort((char) b.length).put(b);
    }

    public BigInteger read2(ByteList bb) {
        byte[] b = new byte[bb.readChar()];
        bb.read(b);
        return new BigInteger(b).modPow(mySec, p);
    }

    /*
     // 若质数p为索菲·热尔曼质数，则2p+1亦为质数
     BigInteger first = new BigInteger("9791");
     BigInteger two = new BigInteger("2");
     for (int i = 0; i < 666; i++) {
     first = first.multiply(two).add(BigInteger.ONE);
     }
     System.out.println(first.bitLength());
     System.out.println(first);
     */
    static final BigInteger DEFAULT_PRIME = new BigInteger("2998116586122293571412084445657803437967605850964581968" +
    "274850316977188064185728750316020026991035912326456584911775839738890428596280969646046145483515179790717174193570" +
    "956675422655623243381737703486783487");
}
