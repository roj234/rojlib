package roj.crypt;

import java.security.spec.AlgorithmParameterSpec;

/**
 * @author Roj234
 * @since 2023/4/29 0029 1:58
 */
public class AEADParameterSpec extends IvParameterSpecNC implements AlgorithmParameterSpec {
	private int tagSize;

	public AEADParameterSpec(byte[] iv) { super(iv); }
	public AEADParameterSpec(byte[] iv, int tagSize) {
		super(iv);
		this.tagSize = tagSize;
	}

	public int getTagSize() { return tagSize; }
}
