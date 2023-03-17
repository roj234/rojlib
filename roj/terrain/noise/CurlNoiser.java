package roj.terrain.noise;

/**
 * @author Roj234
 * @since 2023/3/8 0008 23:38
 */
public class CurlNoiser implements Noiser {
	private final Noiser n;
	private final double e = 0.1;
	private final float div = (float) (1 / (e*2));

	public CurlNoiser(Noiser n) {
		this.n = n;
	}

	@Override
	public final double noise(double x, double y, double z) {
		double e = this.e;

		float n1 = (float) n.noise(x, y + e, z);
		float n2 = (float) n.noise(x, y - e, z);
		float n3 = (float) n.noise(x, y, z + e);
		float n4 = (float) n.noise(x, y, z - e);
		float n5 = (float) n.noise(x + e, y, z);
		float n6 = (float) n.noise(x - e, y, z);


		float x1 = (n2 - n1 - n4 + n3) * div;
		float y1 = (n4 - n3 - n6 + n5) * div;
		float z1 = (n6 - n5 - n2 + n1) * div;

		// normalize(vec3(x1,y1,z1) * div)
		float sum = x1*x1 + y1*y1 + z1*z1;
		return sum;
	}

}
