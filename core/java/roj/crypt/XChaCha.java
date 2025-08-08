package roj.crypt;

import roj.reflect.Unaligned;

import java.security.InvalidAlgorithmParameterException;
import java.security.SecureRandom;

/**
 * <a href="https://datatracker.ietf.org/doc/html/draft-irtf-cfrg-xchacha">Draft-XChaCha</a>
 *
 * @author solo6975
 * @since 2022/2/14 19:41
 */
final class XChaCha extends ChaCha {
	XChaCha() {}

	int[] keyIv;

	void randIV(SecureRandom rnd) {
		keyIv = new int[14];
		System.arraycopy(key, 4, keyIv, 0, 8);
		for (int i = 8; i < 14; i++) keyIv[i] = rnd.nextInt();
		doSetIv();
	}
	void incrIV() {
		for (int i = 13; i >= 8; i--) {
			if (++keyIv[i] != 0) break;
		}
		doSetIv();
	}
	void setIv(byte[] iv) throws InvalidAlgorithmParameterException {
		if (iv.length != 24) throw new InvalidAlgorithmParameterException("iv.length("+iv.length+") != 24");
		if (keyIv == null) keyIv = new int[14];
		System.arraycopy(key, 4, keyIv, 0, 8);
		for (int i = 8; i < 14; i++) keyIv[i] = Unaligned.U.get32UL(iv, Unaligned.ARRAY_BYTE_BASE_OFFSET + ((i&7) << 2));
		doSetIv();
	}

	private void doSetIv() {
		int[] Key = key, Tmp = tmp;

		// HChaCha an intermediary step towards XChaCha based on the
		// construction and security proof used to create XSalsa20.
		//
		// cccccccc  cccccccc  cccccccc  cccccccc
		// kkkkkkkk  kkkkkkkk  kkkkkkkk  kkkkkkkk
		// kkkkkkkk  kkkkkkkk  kkkkkkkk  kkkkkkkk
		// nnnnnnnn  nnnnnnnn  nnnnnnnn  nnnnnnnn
		System.arraycopy(Key, 0, Tmp, 0, 4);    // Constant
		System.arraycopy(keyIv, 0, Tmp, 4, 12); // Key & Nonce

		Key[12] = 0;         // Counter
		Key[13] = 0;         // Padding
		Key[14] = keyIv[12]; // Nonce
		Key[15] = keyIv[13];

		Round(Tmp);

		// SubKey
		Key[4] = Tmp[0];
		Key[5] = Tmp[1];
		Key[6] = Tmp[2];
		Key[7] = Tmp[3];

		Key[8] = Tmp[12];
		Key[9] = Tmp[13];
		Key[10] = Tmp[14];
		Key[11] = Tmp[15];
	}
}
