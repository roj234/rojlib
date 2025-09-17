package roj.renderer.security;

import roj.crypt.RCipher;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

/**
 * @author Roj233
 * @since 2022/2/17 22:05
 */
public class XTSCipher extends RCipher {
	private final RCipher cip;
	private byte[] key1, key2;
	private int flag;

	private long sectorNo;
	private final int GFA;

	private final ByteList t1, t2;
	private final byte[] t3;

	public XTSCipher(RCipher cip) {
		this(cip, 2);
	}

	public XTSCipher(RCipher cip, int multiplier) {
		this.cip = cip;
		if (cip.engineGetBlockSize() == 0) throw new IllegalArgumentException("Not a block cipher");
		t1 = ByteList.allocate(cip.engineGetBlockSize(),cip.engineGetBlockSize());
		t2 = ByteList.allocate(cip.engineGetBlockSize(),cip.engineGetBlockSize());
		t3 = new byte[cip.engineGetBlockSize()];
		GFA = multiplier;
	}

	public int engineGetBlockSize() { return cip.engineGetBlockSize(); }
	public boolean isBaseCipher() { return false; }
	public String getAlgorithm() { return "XTS"; }

	@Override
	public void init(int flags, byte[] key, AlgorithmParameterSpec config, SecureRandom random) {
		key1 = Arrays.copyOfRange(key, 0, key.length / 2);
		key2 = Arrays.copyOfRange(key, key.length / 2, key.length);

		sectorNo = 0;
		flag = flags;
	}

	@Override
	public void crypt(DynByteBuf in, DynByteBuf out) throws ShortBufferException {
		if (in.readableBytes() < cip.engineGetBlockSize()) throw new ShortBufferException("At least one block is required for XTS");
		if (out.writableBytes() < in.readableBytes()) throw new ShortBufferException();

		byte[] b1 = t1.array();
		ByteList t2 = this.t2;
		byte[] b2 = t2.array();
		ByteList t2_ = ByteList.wrap(b2);
		byte[] b3 = this.t3;
		RCipher cip = this.cip;

		// begin of new block
		// X = E(I)
		t2.rIndex = 0;
		t2.wIndex(t2.capacity());
		t2.putLong(sectorNo++);
		for (int i = 8; i < b2.length; i++) b2[i] = 0;

		try {
			cip.init(Cipher.ENCRYPT_MODE, key1);
			t1.clear();
			cip.cryptOneBlock(t2, t1);

			cip.init(flag, key2);
		} catch (InvalidKeyException e) {
			throw new RuntimeException(e);
		}

		boolean DEC = flag == Cipher.DECRYPT_MODE;
		int lim = DEC && in.readableBytes() % b1.length != 0 ? b1.length << 1 : b1.length;
		while (in.readableBytes() >= lim) {
			// C = E(P ^ X) ^ X
			for (int i = 0; i < b1.length; i++) b2[i] = (byte) (b1[i] ^ in.readByte());
			t2.rIndex = 0;
			t2_.wIndex(0);
			cip.cryptOneBlock(t2, t2_);

			for (int i = 0; i < b1.length; i++) out.put((byte) (b1[i] ^ b2[i]));

			// X *= a
			mul(b1, GFA);
		}

		if (in.isReadable()) {
			if (!DEC) {
				int delta = out.wIndex() - b1.length;
				out.rIndex = delta;

				// backup ciphertext remain
				out.readFully(b3);

				// plaintext
				int i = 0;
				while (in.isReadable()) b2[i] = (byte) (b1[i++] ^ in.readByte());
				// steal ciphertext
				while (i < b1.length) b2[i] = (byte) (b1[i] ^ out.getByte(delta + i++));

				t2.rIndex = 0;
				t2_.wIndex(0);
				cip.cryptOneBlock(t2, t2_);

				for (i = 0; i < b1.length; i++) out.put((byte) (b1[i] ^ b2[i]));
				// write remain
				out.put(b3);
			} else {
				// 备份X
				System.arraycopy(b1, 0, b3, 0, b1.length);

				// 读取block[len-2]
				in.readFully(b2);

				// next X
				mul(b1, GFA);

				// cipher
				for (int i = 0; i < b1.length; i++) b2[i] ^= b1[i];
				t2.rIndex = 0;
				t2_.wIndex(0);
				cip.cryptOneBlock(t2, t2_);

				// b2[0,rem] 最后的明文, [rem,len] 密文block[len-2] remain
				int rem = in.readableBytes();
				for (int i = 0; i < b1.length; i++) b2[i] ^= b1[i];

				// 恢复X
				byte[] t = b1;
				b1 = b3;
				b3 = t;

				// 备份明文
				System.arraycopy(b2, 0, b3, 0, rem);

				// 组装block[len-2]
				for (int i = 0; i < rem; i++) b2[i] = (byte) (b1[i] ^ in.readByte());
				for (int i = rem; i < b1.length; i++) b2[i] ^= b1[i];
				t2.rIndex = 0;
				t2_.wIndex(0);
				cip.cryptOneBlock(t2, t2_);

				// put plain[len-2]
				for (int i = 0; i < b1.length; i++) out.put((byte) (b1[i] ^ b2[i]));
				// put plain[len-1]
				out.put(b3, 0, rem);
			}
		}
	}

	private static void mul(byte[] x, int y) {
		int carry = 0;
		for (int i = x.length - 1; i >= 0; i--) {
			int product = (x[i] & 0xFF) * y + carry;
			x[i] = (byte) product;
			carry = product >>> 8;
		}
	}
}
