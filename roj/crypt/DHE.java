package roj.crypt;

import roj.util.DynByteBuf;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Random;

/**
 * Diffie–Hellman Key Exchange
 * @author Roj233
 * @since 2022/2/12 17:25
 */
public final class DHE implements KeyAgreement {
	private final DHGroup group;
	private BigInteger mySec, myPub;

	public DHE(DHGroup group) { this.group = group; }

	public String getAlgorithm() { return "DHE-"+group.name; }

	public void init(SecureRandom r) {
		// short exponent
		mySec = new BigInteger(1, randomBitsIn(group.expSize, r));
		myPub = group.g.modPow(mySec, group.p);
	}

	private static byte[] randomBitsIn(int bits, Random rnd) {
		int len = (bits+7)>>>3;
		byte[] b = new byte[len];
		rnd.nextBytes(b);

		int extraBits = (len<<3) - bits;
		b[0] &= (1 << (8 - extraBits))-1;
		// ensure value inside [2^bits-1, 2^bits]
		b[0] |= 1 << (7-extraBits);
		return b;
	}

	public int length() { return lengthOf(myPub); }
	public static int lengthOf(BigInteger bi) { return 1 + bi.bitLength() / 8; }

	public void writePublic(DynByteBuf bb) { bb.put(myPub.toByteArray()); }
	public byte[] readPublic(DynByteBuf bb) {
		BigInteger peerPub = new BigInteger(bb.readBytes(bb.readableBytes()));
		if (peerPub.compareTo(BigInteger.ONE) <= 0 || peerPub.compareTo(group.p) >= 0)
			throw new IllegalStateException("公钥不满足 1 < Y < p-1");
		return peerPub.modPow(mySec, group.p).toByteArray();
	}

	public void clear() { myPub = mySec = null; }
}
