package roj.terrain.noise;

/**
 * @author Roj234
 * @since 2023/3/8 0008 22:27
 */
public class Fractal implements Noiser {
	private final Noiser n;
	private final int octaves;
	private final float roughness, frequency;

	public Fractal(Noiser n) {
		this(n,6,0.5f,2f);
	}

	public Fractal(Noiser n, int octaves, float roughness, float frequency) {
		this.n = n;
		this.octaves = octaves;
		this.roughness = roughness;
		this.frequency = frequency;
	}

	@Override
	public final double noise(double x, double y) {
		float amplitude = .5f;
		float freq = 1;

		double value = 0;
		for (int octaves = this.octaves; octaves > 0; octaves--) {
			value += n.noise(x*freq, y*freq) * amplitude;

			freq *= frequency;
			amplitude *= roughness;
		}

		return value;
	}

	@Override
	public final double noise(double x, double y, double z) {
		float amplitude = .5f;
		float freq = 1;

		double value = 0;
		for (int octaves = this.octaves; octaves > 0; octaves--) {
			value += n.noise(x*freq, y*freq, z*freq) * amplitude;

			freq *= frequency;
			amplitude *= roughness;
		}

		return value;
	}

	@Override
	public final double noise(double x, double y, double z, double w) {
		float amplitude = .5f;
		float freq = 1;

		double value = 0;
		for (int octaves = this.octaves; octaves > 0; octaves--) {
			value += n.noise(x*freq, y*freq, z*freq, w*freq) * amplitude;

			freq *= frequency;
			amplitude *= roughness;
		}

		return value;
	}

	public static float[] noiseField2f(Noiser n, int w, int h, float[] arr, int octaves, float roughness, float frequency, float scale) {
		float amplitude = .5f;

		if (arr == null || arr.length<w*h) arr = new float[w*h];

		for (; octaves > 0; octaves--) {
			int pos = 0;
			for (int x = 0; x < w; x++) {
				for (int y = 0; y < h; y++) {
					arr[pos++] += (float) n.noise(x*scale, y*scale) * amplitude;
				}
			}

			scale *= frequency;
			amplitude *= roughness;
		}

		return arr;
	}

	public static float[] noiseField3f(Noiser n, int w, int h, int l, float[] arr, int octaves, float roughness, float frequency, float scale) {
		float amplitude = .5f;

		if (arr == null || arr.length<w*h*l) arr = new float[w*h*l];

		for (; octaves > 0; octaves--) {
			int pos = 0;
			for (int x = 0; x < w; x++) {
				for (int y = 0; y < h; y++) {
					for (int z = 0; z < l; z++) {
						arr[pos++] += (float) n.noise(x*scale, y*scale) * amplitude;
					}
				}
			}

			scale *= frequency;
			amplitude *= roughness;
		}

		return arr;
	}
}
