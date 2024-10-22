package roj.crypt;

import roj.util.BitBuffer;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.Random;

/**
 * 一种可抵赖加密算法(Deniable Encryption).
 * 谐音梗扣钱！（雾
 * 你可以设置多个密钥，对应多个不同的明文
 * （长度相同是最好的）
 * 生成的密文是安全的，并且可以用这些密钥解密成不同的明文
 * 非随机数的1b和1i算法虽然选择记录重试次数而不是随机数，以方便压缩，但也不太可能暴露明文的复杂度
 * 使用xxHash比Blake3快50x
 * @author Roj234
 * @since 2024/7/10 0010 5:42
 */
public class VoidCrypt {
	public static class CipherPair {
		public final byte[] key;
		public final BitBuffer bitArray;
		int bit;

		public CipherPair(byte[] key, ByteList buf) {
			this.key = key;
			this.bitArray = new BitBuffer(buf.slice());
		}

		final void next(int bits) {bit = bitArray.readableBits() > 0 ? bitArray.readBit(bits) : 0;}

		public int reset() {
			bitArray.bitPos = 0;
			bitArray.list.rIndex = 0;
			return bitArray.readableBits();
		}
	}

	public static DynByteBuf statistic_encrypt(Random srnd, DynByteBuf ciphertext, CipherPair... pairs) {return useInt(pairs) ? _encrypt1i(srnd, ciphertext, pairs) : _encrypt1b(srnd, ciphertext, pairs);}
	private static boolean useInt(CipherPair[] pairs) {
		if (pairs.length > 11) return true;
		int min = Integer.MAX_VALUE, max = 0;
		for (CipherPair pair : pairs) {
			int bits = pair.bitArray.readableBits();
			min = Math.min(bits, min);
			max = Math.max(bits, max);
		}
		return max - min >= 256;
	}
	public static DynByteBuf _encrypt2r(Random srnd, DynByteBuf ciphertext, CipherPair... pairs) {
		if (pairs.length > 6) throw new IllegalArgumentException("超过物理上限！");
		int maxLen = 0;
		byte[][] sbox = new byte[pairs.length][];

		byte[] nonce = new byte[32];
		srnd.nextBytes(nonce);
		nonce[31] = (byte) ((nonce[31] & 0xF0) | 3);
		ciphertext.put(nonce);

		Blake3 hash1 = new Blake3(64);
		for (int i = 0; i < pairs.length; i++) {
			maxLen = Math.max(pairs[i].reset(), maxLen);
			hash1.update(pairs[i].key);
			hash1.update(nonce);
			sbox[i] = hash1.digest();
		}
		MT19937 rnd = new MT19937();

		maxLen /= 2;
		outer:
		for (int i = 0; i < maxLen; i++) {
			for (int j = 0; j < pairs.length; j++) pairs[j].next(2);

			retry:
			for (int tries = 0; tries <= 0xFFFF; tries++) {
				int nonce1 = rnd.nextInt(65536);
				for (int k = 0; k < pairs.length; k++) {
					int hash = XXHash.xxHash32(nonce1, sbox[k], 0, 64);
					if ((Integer.bitCount(hash & 0xFF) & 3) != pairs[k].bit) continue retry;
				}

				ciphertext.putShort(nonce1);
				continue outer;
			}
			throw new IllegalStateException("retry=65536");
		}

		return ciphertext;
	}
	public static DynByteBuf _encrypt1i(Random srnd, DynByteBuf ciphertext, CipherPair... pairs) {
		if (pairs.length > 24) throw new IllegalArgumentException("超过物理上限！");
		int maxLen = 0;
		byte[][] sbox = new byte[pairs.length][];

		byte[] nonce = new byte[32];
		srnd.nextBytes(nonce);
		nonce[31] = (byte) ((nonce[31] & 0xF0) | 1);
		ciphertext.put(nonce);

		Blake3 hash1 = new Blake3(64);
		for (int i = 0; i < pairs.length; i++) {
			maxLen = Math.max(pairs[i].reset(), maxLen);
			hash1.update(pairs[i].key);
			hash1.update(nonce);
			sbox[i] = hash1.digest();
		}

		int seed = new ByteList(nonce).readInt();

		for (int i = 0; i < maxLen; i++) {
			for (int j = 0; j < pairs.length; j++) pairs[j].next(1);

			int retry = 0;
			retry:
			for(;;) {
				if (++retry < 0) throw new IllegalStateException("retry="+retry);

				seed++;
				for (int j = 0; j < pairs.length; j++) {
					int hash = XXHash.xxHash32(seed, sbox[j], 0, 64);
					int bit = getProbI(hash) > 0 ? 1 : 0;
					if (bit != pairs[j].bit) continue retry;
				}

				ciphertext.putVUInt(retry);
				break;
			}
		}
		return ciphertext;
	}
	public static DynByteBuf _encrypt1b(Random srnd, DynByteBuf ciphertext, CipherPair... pairs) {
		if (pairs.length > 24) throw new IllegalArgumentException("超过物理上限！");
		int maxLen = 0;
		byte[][] sbox = new byte[pairs.length][];

		byte[] nonce = new byte[32];
		srnd.nextBytes(nonce);
		nonce[31] = (byte) ((nonce[31] & 0xF0) | 2);
		ciphertext.put(nonce);

		Blake3 hash1 = new Blake3(64);
		for (int i = 0; i < pairs.length; i++) {
			maxLen = Math.max(pairs[i].reset(), maxLen);
			hash1.update(pairs[i].key);
			hash1.update(nonce);
			sbox[i] = hash1.digest();
		}

		int seed = new ByteList(nonce).readInt();

		for (int i = 0; i < maxLen; i++) {
			for (int j = 0; j < pairs.length; j++) pairs[j].next(1);

			int retry = 0;
			retry:
			for(;;) {
				if (++retry < 0) throw new IllegalStateException("retry="+retry);

				seed++;
				for (int j = 0; j < pairs.length; j++) {
					int hash = XXHash.xxHash32(seed, sbox[j], 0, 64);
					int bit = getProb(hash) > 0 ? 1 : 0;
					if (bit != pairs[j].bit) continue retry;
				}

				ciphertext.putVUInt(retry);
				break;
			}
		}

		return ciphertext;
	}

	public static DynByteBuf statistic_decrypt(byte[] key, DynByteBuf ciphertext, DynByteBuf plaintext) {
		int seed = ciphertext.readInt(ciphertext.rIndex);

		Blake3 hash1 = new Blake3(64);
		hash1.update(key);
		byte[] nonce = ciphertext.readBytes(32);
		hash1.update(nonce);
		byte[] sbox = hash1.digest();

		var bw = new BitBuffer(plaintext);
		int retry, hash;

		switch (nonce[31]&0xF) {
			case 1 -> {
				while (ciphertext.isReadable()) {
					retry = ciphertext.readVUInt();
					hash = XXHash.xxHash32(seed += retry, sbox, 0, 64);
					bw.writeBit(1, getProbI(hash) > 0 ? 1 : 0);
				}
			}
			case 2 -> {
				while (ciphertext.isReadable()) {
					retry = ciphertext.readVUInt();
					hash = XXHash.xxHash32(seed += retry, sbox, 0, 64);
					bw.writeBit(1, getProb(hash) > 0 ? 1 : 0);
				}
			}
			case 3 -> {
				int len = ciphertext.readableBytes() / 2;
				while (len-- > 0) {
					retry = ciphertext.readUnsignedShort();
					hash = XXHash.xxHash32(retry, sbox, 0, 64);
					bw.writeBit(2, Integer.bitCount(hash & 0xFF) & 3);
				}
			}
		}
		bw.endBitWrite();
		return plaintext;
	}

	private static int getProb(int b) {return Integer.bitCount(b&0xFF)-4;}
	private static int getProbI(int b) {return Integer.bitCount(b)-16;}
}