package roj.dev.hr;

/**
 * @author Roj234
 * @since 2023/1/14 0014 1:38
 */
final class HFieldD extends HField {
	private double ref;
	@Override
	public double D() {
		return ref;
	}
	@Override
	public void D(double i) {
		ref = i;
	}
}
