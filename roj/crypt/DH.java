package roj.crypt;

import roj.net.mss.MSSSubKey;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Diffie–Hellman Key Exchange
 * 草，我还真TM就是偶然看到了索菲·热尔曼质数
 * '安全素数也叫Sophie Germain素数,如果 p1 是素数,p2 = p1*2+1 也是素数,那么 p2 就是安全素数,反之则是不安全的。'
 * @author Roj233
 * @since 2022/2/12 17:25
 */
public class DH implements MSSSubKey {
    static final int PRIKEY_BITS = 40*8;
    static final int PRIME_BITS = 48*8;

    private final BigInteger p, g;
    private BigInteger mySec, myPub;

    public DH() {
        this(5, DEFAULT_PRIME);
    }

    public DH(int g, BigInteger p) {
        this.p = p;
        this.g = BigInteger.valueOf(g);
    }

    public static DH randomly(Random r) {
        int g;
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
        return new DH(g, BigInteger.probablePrime(PRIME_BITS, r));
    }

    public void initA(Random r, int sharedRandom) {
        this.mySec = new BigInteger(PRIKEY_BITS, r);
        this.myPub = this.g.modPow(mySec, p);
    }

    public int length() {
        return 1 + myPub.bitLength()/8;
    }

    public void clear() {
        this.myPub = this.mySec = null;
    }

    public void writeA(ByteBuffer bb) {
        bb.put(myPub.toByteArray());
    }

    public byte[] readA(ByteBuffer bb) {
        byte[] b = new byte[bb.remaining()];
        bb.get(b);
        return new BigInteger(b).modPow(mySec, p).toByteArray();
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
