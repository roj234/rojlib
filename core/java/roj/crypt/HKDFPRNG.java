package roj.crypt;

import roj.reflect.Unsafe;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.security.SecureRandom;

/**
 * @author Roj234
 * @since 2023/4/29 3:53
 */
public class HKDFPRNG extends SecureRandom {
	final MessageAuthenticCode kd;
	final DynByteBuf info;

	public HKDFPRNG(MessageAuthenticCode kd, byte[] prk, String name) {
		this.kd = kd;
		kd.init(prk);
		info = new ByteList().putLong(0).putUTF(name);
	}

	@Override public void setSeed(long seed) {if (info != null) info.putLong(seed);}
	@Override public synchronized void setSeed(byte[] seed) {info.put(seed);}

	@Override
	public void nextBytes(byte[] b) {
		CryptoFactory.HKDF_expand(kd, null, info, b.length, b, Unsafe.ARRAY_BYTE_BASE_OFFSET);
		incr();
	}

	@Override
	public byte[] generateSeed(int numBytes) {
		byte[] b = CryptoFactory.HKDF_expand(kd,null,info,numBytes);
		incr();
		return b;
	}

	private void incr() { info.setLong(0, info.getLong(0)+1); }
}
