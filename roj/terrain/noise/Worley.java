package roj.terrain.noise;

import roj.collect.LRUCache;
import roj.math.MathUtils;
import roj.math.MutableLong;
import roj.math.Vec3d;

public class Worley implements Noiser {
	private final Noiser n, r;

	private final LRUCache<MutableLong, Vec3d> nTmp = new LRUCache<>(512);
	private final MutableLong t = new MutableLong();

	private static final double farY = 43186.43289;
	private static final double[] vec = {0, -1, 1};

	public Worley(Noiser n) {
		this.n = r = n;
	}

	public Worley(Noiser n, Noiser r) {
		this.n = n;
		this.r = r;
	}

	public double noise(double x, double y) {
		double x0 = MathUtils.floor(x);
		double y0 = MathUtils.floor(y);

		double minDist = Double.MAX_VALUE;
		double value = 0;

		for (int i = 0; i < 3; i++) {
			double xx = vec[i];
			x0 += xx;

			for (int j = 0; j < 3; j++) {
				double yy = vec[j];
				y0 += yy;

				t.value = ((long) x0 << 32) | ((long)y0&0xFFFFFFFFL);
				Vec3d v = nTmp.get(t);
				if (v == null) {
					v = new Vec3d();
					v.x = x0+n.noise(x0, y0, 0);
					v.y = y0+n.noise(x0, y0, farY);
					v.z = r.noise(x0, y0);
					nTmp.put(new MutableLong(t.value), v);
				}

				double nx = x-v.x;
				nx *= nx;

				if (nx < minDist) {
					double ny = y-v.y;
					ny *= ny;

					if (nx+ny < minDist) {
						minDist = nx+ny;
						value = v.z;
					}
				}

				y0 -= yy;

			}
			x0 -= xx;
		}

		return value;
	}
}
