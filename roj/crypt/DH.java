package roj.crypt;

import roj.math.MutableBigInteger;
import roj.util.DynByteBuf;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Diffie–Hellman Key Exchange
 * @author Roj233
 * @since 2022/2/12 17:25
 */
public class DH implements KeyAgreement {
	static final int PRIKEY_BITS = 40 * 8;
	static final int PRIME_BITS = 48 * 8;

	private final BigInteger p, g;
	private BigInteger mySec, myPub;

	public DH() {
		this(5, DEFAULT_PRIME);
	}

	public DH(int g, BigInteger p) {
		this.p = p;
		this.g = BigInteger.valueOf(g);
	}

	@Override
	public String getAlgorithm() {
		return "DH";
	}

	public void init(SecureRandom r) {
		this.mySec = new BigInteger(PRIKEY_BITS, r);
		this.myPub = this.g.modPow(mySec, p);
	}

	public int length() {
		return lengthOf(myPub);
	}

	public static int lengthOf(BigInteger bi) {
		return 1 + bi.bitLength() / 8;
	}

	public void writePublic(DynByteBuf bb) {
		bb.put(myPub.toByteArray());
	}
	public byte[] readPublic(DynByteBuf bb) {
		return new BigInteger(bb.readBytes(bb.readableBytes())).modPow(mySec, p).toByteArray();
	}

	public void clear() {
		this.myPub = this.mySec = null;
	}

	public static BigInteger srPrime(int bits) {
		bits -= 14;
		if (bits < 0) throw new IllegalStateException("bits must larger than 14");

		MutableBigInteger first = new MutableBigInteger(9791);
		MutableBigInteger one = new MutableBigInteger(1);
		MutableBigInteger two = new MutableBigInteger(2);

		MutableBigInteger tmp = new MutableBigInteger();

		while (bits-- > 0) {
			first.multiply(two, tmp);
			tmp.add(one);

			MutableBigInteger swap = tmp;
			tmp = first;
			first = swap;
		}

		return first.toBigInteger();
	}

	static final BigInteger DEFAULT_PRIME = new BigInteger(
		"2998116586122293571412084445657803437967605850964581968" +
			"2748503169771880641857287503160200269910359123264565849" +
			"1177583973889042859628096964604614548351517979071717419" +
			"3570956675422655623243381737703486783487");
}
