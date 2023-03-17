package roj.terrain.noise;

/**
 * @author Roj234
 * @since 2023/3/8 0008 22:26
 */
public interface Noiser {
	default double noise(double x, double y) {
		throw new UnsupportedOperationException(getClass().getName());
	}
	default double noise(double x, double y, double z) {
		throw new UnsupportedOperationException(getClass().getName());
	}
	default double noise(double x, double y, double z, double w) {
		throw new UnsupportedOperationException(getClass().getName());
	}
}
