package roj.dev.hr;

/**
 * @author Roj234
 * @since 2023/1/14 0014 1:38
 */
final class HFieldH extends HField {
	private HObject ref;
	@Override
	public HObject H() {
		return ref;
	}
	@Override
	public void H(HObject i) {
		ref = i;
	}
}
