package ilib.anim.timing;

/**
 * @author Roj234
 * @since 2021/5/27 22:53
 */
public class XnIn extends Simple {
	byte n;

	public XnIn(int m) {
		this.n = (byte) m;
	}

	@Override
	public Timing setConfig(int key, double value) {
		if (key != TFRegistry.CONFIG_EASE) return this;
		if (value == TFRegistry.EASE_IN_OUT) return n == 2 ? TFRegistry.X2_IN_OUT : TFRegistry.X3_IN_OUT;
		if (value == TFRegistry.EASE_OUT) return n == 2 ? TFRegistry.X2_OUT : TFRegistry.X3_OUT;
		return this;
	}

	public double getConfig(int key) {
		return TFRegistry.EASE_IN;
	}

	@Override
	public double interpolate(double x) {
		for (int i = 0; i < n; i++) {
			x *= x;
		}
		return x;
	}

	@Override
	public String name() {
		return "ilib:x_in_" + n;
	}
}
