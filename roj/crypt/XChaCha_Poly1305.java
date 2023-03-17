package roj.crypt;

/**
 * @author solo6975
 * @since 2022/2/14 20:31
 */
public final class XChaCha_Poly1305 extends ChaCha_Poly1305 {
	private final byte[] tmpNonce = new byte[24];

	public XChaCha_Poly1305() {
		super(new XChaCha());
	}

	public XChaCha_Poly1305(XChaCha chacha) {
		super(chacha);
	}

	@Override
	public String getAlgorithm() {
		return "XChaCha_Poly1305";
	}

	@Override
	void generateNonce(int[] key) {
		prng.nextBytes(tmpNonce);
		c.setNonce(tmpNonce);
	}
}
