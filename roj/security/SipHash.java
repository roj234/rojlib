package roj.security;

import roj.io.IOUtil;
import roj.util.DynByteBuf;

import java.security.SecureRandom;

/**
 * @author solo6975
 * @since 2022/3/21 13:42
 */
public class SipHash {
	private long k0, k1;
	private long v0, v1, v2, v3;

	private static final byte[] padding = new byte[8];

	public void setKeyDefault() {
		SecureRandom rnd = new SecureRandom();
		setKey(rnd.nextLong(), rnd.nextLong());
	}
	public void setKey(long k0, long k1) {
		reset(Long.reverseBytes(k0), Long.reverseBytes(k1));
	}
	private void reset(long k0, long k1) {
		this.k0 = k0;
		this.k1 = k1;
		v0 = k0 ^ 0x736f6d6570736575L;
		v1 = k1 ^ 0x646f72616e646f6dL;
		v2 = k0 ^ 0x6c7967656e657261L;
		v3 = k1 ^ 0x7465646279746573L;
	}

	public long digest(CharSequence msg) {
		reset(k0, k1);
		DynByteBuf b = IOUtil.getSharedByteBuf().putVStr(msg);
		// 我懒 反正也不会拿去比较
		update(b.put(padding));
		return hash();
	}

	public void update(DynByteBuf b) {
		long v0 = this.v0;
		long v1 = this.v1;
		long v2 = this.v2;
		long v3 = this.v3;

		while (b.readableBytes() >= 8) {
			long m = b.readLong();
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
	}

	public long hash() {
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
