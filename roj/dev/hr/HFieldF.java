package roj.dev.hr;

/**
 * @author Roj234
 * @since 2023/1/14 0014 1:38
 */
final class HFieldF extends HField {
	private float ref;
	@Override
	public float F() {
		return ref;
	}
	@Override
	public void F(float i) {
		ref = i;
	}
}
