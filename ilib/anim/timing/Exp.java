package ilib.anim.timing;

/**
 * @author Roj234
 * @since 2021/5/27 22:53
 */
public class Exp extends Simple {
	int mode;

	public Exp(int m) {
		this.mode = m;
	}

	@Override
	public double interpolate(double x) {
		switch (mode) {
			case 1:
				return Math.pow(2, 10 * (x - 1));
			case 2:
				return (-Math.pow(2, -10 * x) + 1);
			case 3: {
				if (x == 0 || x == 1) return x;

				x *= 2;

				if (x < 1F) return Math.pow(2, 10 * (x - 1));

				return (-Math.pow(2, -10 * (x - 1)) + 2);
			}
		}
		return 0;
	}

	@Override
	public String name() {
		return "ilib:exp_io_" + mode;
	}
}
