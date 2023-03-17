package roj.dev.hr;

/**
 * @author Roj234
 * @since 2023/1/14 0014 1:38
 */
final class HFieldJ extends HField {
	private long ref;
	@Override
	public long J() {
		return ref;
	}
	@Override
	public void J(long i) {
		ref = i;
	}
}
