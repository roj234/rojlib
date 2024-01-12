package roj.minecraft.noise;

/**
 * @author Roj234
 * @since 2023/3/8 0008 22:27
 */
public final class Positive implements Noiser {
	private final Noiser n;

	public static Noiser decorate(Noiser n) {
		return new Positive(n);
	}

	public Positive(Noiser n) {
		this.n = n;
	}

	@Override
	public final double noise(double x, double y) {
		return n.noise(x,y)*0.5+0.5;
	}
	@Override
	public final double noise(double x, double y, double z) {
		return n.noise(x,y,z)*0.5+0.5;
	}
	@Override
	public final double noise(double x, double y, double z, double w) {
		return n.noise(x,y,z,w)*0.5+0.5;
	}
}