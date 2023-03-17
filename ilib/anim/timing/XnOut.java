package ilib.anim.timing;

/**
 * @author Roj234
 * @since 2021/5/27 22:53
 */
public class XnOut extends Simple {
	byte n;

	public XnOut(int m) {
		this.n = (byte) m;
	}

	@Override
	public Timing setConfig(int key, double value) {
		if (key != TFRegistry.CONFIG_EASE) return this;
		if (value == TFRegistry.EASE_IN_OUT) return n == 2 ? TFRegistry.X2_IN_OUT : TFRegistry.X3_IN_OUT;
		if (value == TFRegistry.EASE_IN) return n == 2 ? TFRegistry.X2_IN : TFRegistry.X3_IN;
		return this;
	}

	public double getConfig(int key) {
		return TFRegistry.EASE_OUT;
	}

	@Override
	public double interpolate(double x) {
		if (n == 2) return x * (x - 2);
		x -= 1;
		return x * x * x + 1;
	}

	@Override
	public String name() {
		return "ilib:x_out_" + n;
	}
}
