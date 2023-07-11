package roj.crypt;

import roj.util.ByteList;
import roj.util.DynByteBuf;
import sun.misc.Unsafe;

import java.security.SecureRandom;

/**
 * @author Roj234
 * @since 2023/4/29 0029 3:53
 */
public class HKDFPRNG extends SecureRandom {
	final HMAC kd;
	final byte[] prk;
	final DynByteBuf info;

	public HKDFPRNG(HMAC kd, byte[] prk, String name) {
		this.kd = kd;
		this.prk = prk;
		info = new ByteList().putLong(0).putUTF(name);
	}

	public void generate(Object pointer, int off, int len) {
		HMAC.HKDF_expand(kd, prk,info,len,pointer,off);
		incr();
	}

	@Override
	public void setSeed(long seed) {
		if (info == null) return;
		info.putLong(seed);
	}

	@Override
	public synchronized void setSeed(byte[] seed) {
		info.put(seed);
	}

	@Override
	public void nextBytes(byte[] b) {
		generate(b, Unsafe.ARRAY_BYTE_BASE_OFFSET, b.length);
		incr();
	}

	@Override
	public byte[] generateSeed(int numBytes) {
		byte[] b = HMAC.HKDF_expand(kd,prk,info,numBytes);
		incr();
		return b;
	}

	private void incr() { info.putLong(0, info.readLong(0)+1); }
}
