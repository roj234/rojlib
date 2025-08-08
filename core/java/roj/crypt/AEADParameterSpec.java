package roj.crypt;

/**
 * @author Roj234
 * @since 2023/4/29 1:58
 */
public class AEADParameterSpec extends IvParameterSpecNC {
	private int tagSize;

	public AEADParameterSpec(byte[] iv) { super(iv); }
	public AEADParameterSpec(byte[] iv, int tagSize) {
		super(iv);
		this.tagSize = tagSize;
	}

	public int getTagSize() { return tagSize; }
}
