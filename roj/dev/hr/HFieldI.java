package roj.dev.hr;

/**
 * @author Roj234
 * @since 2023/1/14 0014 1:38
 */
final class HFieldI extends HField {
	private int ref;
	@Override
	public int I() {
		return ref;
	}
	@Override
	public void I(int i) {
		ref = i;
	}
}
