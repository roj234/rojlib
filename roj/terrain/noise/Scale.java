package roj.terrain.noise;

public class Scale implements Noiser {
	private final Noiser n;
	private final double scale;

	public Scale(Noiser n, double scale) {
		this.n = n;
		this.scale = scale;
	}
	public double noise(double x, double y) {
		return n.noise(x*scale,y*scale);
	}
	public double noise(double x, double y, double z) {
		return n.noise(x*scale,y*scale,z*scale);
	}
	public double noise(double x, double y, double z, double w) {
		return n.noise(x*scale,y*scale,z*scale,w*scale);
	}
}
