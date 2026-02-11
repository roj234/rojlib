package roj.net.mss.crypto;

import roj.crypt.KeyExchange;
import roj.util.DynByteBuf;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Random;

/**
 * Diffie–Hellman Key Exchange
 * @author Roj233
 * @since 2022/2/12 17:25
 */
final class DH implements KeyExchange {
	private final DHGroup group;
	private BigInteger mySec, myPub;

	DH(DHGroup group) { this.group = group; }

	public String getAlgorithm() { return "DH-"+group.name; }

	public void init(SecureRandom r) {
		mySec = new BigInteger(1, randomBitsIn(group.expSize, r));
		myPub = group.g.modPow(mySec, group.p);
	}

	private static byte[] randomBitsIn(int bits, Random rnd) {
		int len = (bits+7)>>>3;
		byte[] b = new byte[len];
		rnd.nextBytes(b);

		int extraBits = (len<<3) - bits;
		b[0] &= (1 << (8 - extraBits))-1;
		// ensure mySec.bitLength() == bits
		b[0] |= 1 << (7-extraBits);
		return b;
	}

	public int length() { return (group.p.bitLength() + 7) / 8; }

	public void writePublic(DynByteBuf bb) {
		int pLen = length();
		putFixedBigInt(bb, myPub, pLen);
	}
	public byte[] readPublic(DynByteBuf bb) {
		int pLen = length();
		byte[] data = bb.readBytes(pLen);
		BigInteger peerPub = new BigInteger(1, data);

		if (peerPub.compareTo(BigInteger.ONE) <= 0 || peerPub.compareTo(group.p.subtract(BigInteger.ONE)) >= 0)
			throw new IllegalStateException("对方公钥不满足 1 < Y < p-1");

		BigInteger shared = peerPub.modPow(mySec, group.p);
		return getFixedBytes(shared, pLen);
	}

	private static byte[] getFixedBytes(BigInteger bi, int size) {
		byte[] raw = bi.toByteArray();
		if (raw.length == size) return raw;

		byte[] result = new byte[size];
		if (raw.length > size) System.arraycopy(raw, raw.length - size, result, 0, size);
		else System.arraycopy(raw, 0, result, size - raw.length, raw.length);
		return result;
	}

	static void putFixedBigInt(DynByteBuf bb, BigInteger bi, int size) {
		byte[] bytes = bi.toByteArray();
		if (bytes.length == size) {
			bb.put(bytes);
		} else if (bytes.length > size) { // 漏掉了符号位补0
			bb.put(bytes, bytes.length - size, size);
		} else { // 长度不足，前面补0
			bb.putZero( size - bytes.length).put(bytes);
		}
	}

	public void clear() { myPub = mySec = null; }
}
