package roj.crypt;

import java.util.Random;

/**
 * <a href="https://datatracker.ietf.org/doc/html/draft-irtf-cfrg-xchacha">Draft-XChaCha</a>
 *
 * @author solo6975
 * @since 2022/2/14 19:41
 */
public final class XChaCha extends ChaCha {
	public XChaCha() {}

	public XChaCha(int round) {super(round);}

	boolean keySet;
	byte[] iv;

	@Override
	public String getAlgorithm() {
		return "XChaCha";
	}

	@Override
	public void setKey(byte[] pass, int flags) {
		super.setKey(pass, flags);
		keySet = true;
		if (iv != null) {
			setNonce(iv);
		}
	}

	@Override
	public void setOption(String key, Object value) {
		switch (key) {
			case NONCE:
				byte[] nonce = (byte[]) value;
				if (keySet) setNonce(nonce);
				else iv = nonce;
				break;
			case RANDOM_GENERATE_NONCE:
				Random rng = (Random) value;
				nonce = new byte[24];
				rng.nextBytes(nonce);
				if (keySet) setNonce(nonce);
				else iv = nonce;
				break;
		}
	}

	public void setNonce(byte[] v) {
		if (v.length != 24) throw new IllegalArgumentException("Nonce(IV) should be 192 bits length");

		int[] Src = key, Dst = tmp;

		// HChaCha an intermediary step towards XChaCha based on the
		// construction and security proof used to create XSalsa20.
		//
		// cccccccc  cccccccc  cccccccc  cccccccc
		// kkkkkkkk  kkkkkkkk  kkkkkkkk  kkkkkkkk
		// kkkkkkkk  kkkkkkkk  kkkkkkkk  kkkkkkkk
		// nnnnnnnn  nnnnnnnn  nnnnnnnn  nnnnnnnn
		System.arraycopy(Src, 0, Dst, 0, 12);
		Conv.b2i_LE(v, 0, 16, Dst, 12);

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
		Conv.b2i_LE(v, 16, 8, Src, 14);
		reset();
	}
}
