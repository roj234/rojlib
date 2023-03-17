package roj.terrain.noise;

import roj.math.MathUtils;

public class Hash implements Noiser {
	public Hash() {

	}

	public double noise(double x, double y) {
		double v = MathUtils.sin((x * 12.9898 + y * 78.233)) * 43758.5453123;
		return v > 0 ? v-(int)v : (int)v-v;
	}

	@Override
	public double noise(double x, double y, double z) {
		double v = 4096.0*MathUtils.sin(x*17.0 + y*59.4 + z*15.0);
		return v > 0 ? v-(int)v : (int)v-v;
	}
}
