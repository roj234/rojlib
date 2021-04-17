package ilib.anim.timing;

/**
 * @author Roj234
 * @since 2021/5/27 22:53
 */
public class XnHalf extends Simple {
	byte n;

	public XnHalf(int m) {
		this.n = (byte) m;
	}

	@Override
	public Timing setConfig(int key, double value) {
		if (key != TFRegistry.CONFIG_EASE) return this;
		if (value == TFRegistry.EASE_IN) return n == 1 ? TFRegistry.X2_IN : TFRegistry.X3_IN;
		if (value == TFRegistry.EASE_OUT) return n == 1 ? TFRegistry.X2_OUT : TFRegistry.X3_OUT;
		return this;
	}

	public double getConfig(int key) {
		return TFRegistry.EASE_IN_OUT;
	}

	@Override
	public double interpolate(double x) {
		x *= 2;

		if (x < 1F) {
			return (n == 1 ? 1 : x) * x * x / 2;
		}

		x -= n;

		if (n == 1) return (x * (x - 2) - 1) / 2;
		return (x * x * x + 2) / 2;
	}

	@Override
	public String name() {
		return "ilib:x_io_" + (n + 1);
	}
}
