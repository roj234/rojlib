package roj.crypt;

import java.security.spec.AlgorithmParameterSpec;

/**
 * @author Roj234
 * @since 2023/4/29 1:58
 */
public class IvParameterSpecNC implements AlgorithmParameterSpec {
	private final byte[] iv;
	public IvParameterSpecNC(byte[] iv) {
		if (iv.length == 0) throw new IllegalArgumentException("Empty IV");
		this.iv = iv;
	}
	public byte[] getIV() { return iv; }
}
