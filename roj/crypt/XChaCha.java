package roj.crypt;

import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.security.InvalidAlgorithmParameterException;

/**
 * <a href="https://datatracker.ietf.org/doc/html/draft-irtf-cfrg-xchacha">Draft-XChaCha</a>
 *
 * @author solo6975
 * @since 2022/2/14 19:41
 */
public final class XChaCha extends ChaCha {
	public XChaCha() {}
	public XChaCha(int round) {super(round);}

	byte[] iv;

	void rngIv() {
		if (iv == null) iv = new byte[24];
		rng.nextBytes(iv);
		setIv(iv);
	}

	void setIv(byte[] iv) {
		if (iv.length != 24) Helpers.athrow(new InvalidAlgorithmParameterException("Nonce 长度应当是24, got " + iv.length));
		ByteList b = IOUtil.SharedCoder.get().wrap(iv);

		int[] Src = key, Dst = tmp;

		// HChaCha an intermediary step towards XChaCha based on the
		// construction and security proof used to create XSalsa20.
		//
		// cccccccc  cccccccc  cccccccc  cccccccc
		// kkkkkkkk  kkkkkkkk  kkkkkkkk  kkkkkkkk
		// kkkkkkkk  kkkkkkkk  kkkkkkkk  kkkkkkkk
		// nnnnnnnn  nnnnnnnn  nnnnnnnn  nnnnnnnn
		System.arraycopy(Src, 0, Dst, 0, 12);
		Dst[12] = b.readIntLE();
		Dst[13] = b.readIntLE();
		Dst[14] = b.readIntLE();
		Dst[15] = b.readIntLE();

		Round(Dst, round);

		// Put SubKey
		Src[4] = Dst[0];
		Src[5] = Dst[1];
		Src[6] = Dst[2];
		Src[7] = Dst[3];

		Src[8] = Dst[12];
		Src[9] = Dst[13];
		Src[10] = Dst[14];
		Src[11] = Dst[15];

		Src[12] = 0;
		Src[13] = 0;

		// Put Remain Nonce
		Src[14] = b.readIntLE();
		Src[15] = b.readIntLE();
		reset();
	}
}
