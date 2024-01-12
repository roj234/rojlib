package roj.minecraft.noise;

import roj.math.MathUtils;

import java.util.Random;

/**
 * Simplex Noise Generator
 *
 * @author Roj233
 * @since 2021/4/21 22:51
 */
public class Simplex implements Noiser {
	public Simplex(Random r) {
		short[] perm = new short[256];
		for (int i = 0; i < 256; i++) {
			perm[i] = (short) i;
		}
		shuffle(perm, r);
		this.perm = new short[512];
		this.permMod12 = new short[512];
		System.arraycopy(perm, 0, this.perm, 0, 256);
		System.arraycopy(perm, 0, this.perm, 256, 256);
		for (int i = 0; i < 256; i++) {
			this.permMod12[i] = (short) (perm[i] % 12);
		}
		System.arraycopy(permMod12, 0, permMod12, 256, 256);
	}

	public static void shuffle(short[] arr, Random random) {
		for (int i = 0; i < arr.length; i++) {
			short a = arr[i];
			int an = random.nextInt(arr.length);
			arr[i] = arr[an];
			arr[an] = a;
		}
	}

	private final short[] perm, permMod12;

	// Simplex noise in 2D, 3D and 4D
	private static final double[] grad3 = {1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1, 0, 1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, -1, 0, 1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1};
	private static final double[] grad4 = {0, 1, 1, 1, 0, 1, 1, -1, 0, 1, -1, 1, 0, 1, -1, -1, 0, -1, 1, 1, 0, -1, 1, -1, 0, -1, -1, 1, 0, -1, -1, -1, 1, 0, 1, 1, 1, 0, 1, -1, 1, 0, -1, 1, 1, 0, -1,
										   -1, -1, 0, 1, 1, -1, 0, 1, -1, -1, 0, -1, 1, -1, 0, -1, -1, 1, 1, 0, 1, 1, 1, 0, -1, 1, -1, 0, 1, 1, -1, 0, -1, -1, 1, 0, 1, -1, 1, 0, -1, -1, -1, 0, 1, -1,
										   -1, 0, -1, 1, 1, 1, 0, 1, 1, -1, 0, 1, -1, 1, 0, 1, -1, -1, 0, -1, 1, 1, 0, -1, 1, -1, 0, -1, -1, 1, 0, -1, -1, -1, 0};

	// Skewing and unskewing factors for 2, 3, and 4 dimensions
	private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
	private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;

	private static final double F3 = 1.0 / 3.0;
	private static final double G3 = 1.0 / 6.0;

	private static final double F4 = (Math.sqrt(5.0) - 1.0) / 4.0;
	private static final double G4 = (5.0 - Math.sqrt(5.0)) / 20.0;

	private static double dot(int i, double x, double y) {
		i *= 3;
		return grad3[i++] * x + grad3[i] * y;
	}

	private static double dot(int i, double x, double y, double z) {
		i *= 3;
		return grad3[i++] * x + grad3[i++] * y + grad3[i] * z;
	}

	private static double dot(int i, double x, double y, double z, double w) {
		i <<= 2;
		return grad4[i++] * x + grad4[i++] * y + grad4[i++] * z + grad4[i] * w;
	}

	// 2D simplex noise
	public double noise(double xin, double yin) {
		//将输入点进行坐标偏移，向下取整得到原点，转换到单形空间
		double s = (xin + yin) * F2; // Hairy factor for 2D
		int ix = MathUtils.floor(xin + s);
		int iy = MathUtils.floor(yin + s);

		double t = (ix + iy) * G2;
		//得到转换前输入点到原点距离
		double x0 = xin - ix + t;
		double y0 = yin - iy + t;

		//确定顶点在哪个三角形内
		// lower triangle, XY order: (0,0)->(1,0)->(1,1)
		// upper triangle, YX order: (0,0)->(0,1)->(1,1)
		int i1 = x0 > y0 ? 1 : 0;
		// A step of (1,0) in (i,j) means a step of (1-c,-c) in (x,y), and
		// a step of (0,1) in (i,j) means a step of (-c,1-c) in (x,y), where
		// c = (3-sqrt(3))/6

		double n = 0; // Noise contributions from the three corners

		// Work out the hashed gradient indices of the three simplex corners
		int ii = ix & 255;
		int jj = iy & 255;

		//输入点到第一个顶点的距离
		double t0 = 0.5 - x0 * x0 - y0 * y0;
		if (t0 > 0) {
			int gi0 = permMod12[ii + perm[jj]];
			t0 *= t0;
			n += t0 * t0 * dot(gi0, x0, y0);  // (x,y) of grad3 used for 2D gradient
		}

		//输入点到第二个顶点的距离
		double x1 = x0 - i1 + G2; // Offsets for middle corner in (x,y) unskewed coords
		double y1 = y0 - 1 + i1 + G2;
		double t1 = 0.5 - x1 * x1 - y1 * y1;
		if (t1 > 0) {
			int gi1 = permMod12[ii + i1 + perm[jj + 1 - i1]];
			t1 *= t1;
			n += t1 * t1 * dot(gi1, x1, y1);
		}

		//输入点到第三个顶点的距离
		x1 = x0 - 1.0 + 2.0 * G2; // Offsets for last corner in (x,y) unskewed coords
		y1 = y0 - 1.0 + 2.0 * G2;
		t1 = 0.5 - x1 * x1 - y1 * y1;
		if (t1 > 0) {
			int gi2 = permMod12[ii + 1 + perm[jj + 1]];
			t1 *= t1;
			n += t1 * t1 * dot(gi2, x1, y1);
		}

		// 缩放到[-1,1]
		return 70*n;
	}

	// 3D simplex noise
	public double noise(double xin, double yin, double zin) {
		// Skew the input space to determine which simplex cell we're in
		double s = (xin + yin + zin) * F3; // Very nice and simple skew factor for 3D
		int i = MathUtils.floor(xin + s);
		int j = MathUtils.floor(yin + s);
		int k = MathUtils.floor(zin + s);

		double t = (i + j + k) * G3;
		double X0 = i - t; // Unskew the cell origin back to (x,y,z) space
		double Y0 = j - t;
		double Z0 = k - t;
		double x0 = xin - X0; // The x,y,z distances from the cell origin
		double y0 = yin - Y0;
		double z0 = zin - Z0;
		// For the 3D case, the simplex shape is a slightly irregular tetrahedron.
		// Determine which simplex we are in.
		int i1, j1, k1; // Offsets for second corner of simplex in (i,j,k) coords
		int i2, j2, k2; // Offsets for third corner of simplex in (i,j,k) coords
		if (x0 >= y0) {
			j1 = 0;
			i2 = 1;
			if (y0 >= z0) {
				i1 = 1;
				k1 = 0;
				j2 = 1;
				k2 = 0;
			} // X Y Z order
			else if (x0 >= z0) {
				i1 = 1;
				k1 = 0;
				j2 = 0;
				k2 = 1;
			} // X Z Y order
			else {
				i1 = 0;
				k1 = 1;
				j2 = 0;
				k2 = 1;
			} // Z X Y order
		} else { // x0<y0
			i1 = 0;
			j2 = 1;
			if (y0 < z0) {
				j1 = 0;
				k1 = 1;
				i2 = 0;
				k2 = 1;
			} // Z Y X order
			else if (x0 < z0) {
				j1 = 1;
				k1 = 0;
				i2 = 0;
				k2 = 1;
			} // Y Z X order
			else {
				j1 = 1;
				k1 = 0;
				i2 = 1;
				k2 = 0;
			} // Y X Z order
		}
		// A step of (1,0,0) in (i,j,k) means a step of (1-c,-c,-c) in (x,y,z),
		// a step of (0,1,0) in (i,j,k) means a step of (-c,1-c,-c) in (x,y,z), and
		// a step of (0,0,1) in (i,j,k) means a step of (-c,-c,1-c) in (x,y,z), where
		// c = 1/6.
		double n = 0; // Noise contributions from the four corners

		// Work out the hashed gradient indices of the four simplex corners
		int ii = i & 255;
		int jj = j & 255;
		int kk = k & 255;
		int gi0 = permMod12[ii + perm[jj + perm[kk]]];
		int gi1 = permMod12[ii + i1 + perm[jj + j1 + perm[kk + k1]]];
		int gi2 = permMod12[ii + i2 + perm[jj + j2 + perm[kk + k2]]];
		int gi3 = permMod12[ii + 1 + perm[jj + 1 + perm[kk + 1]]];
		// Calculate the contribution from the four corners
		double t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0;
		if (t0 > 0) {
			t0 *= t0;
			n += t0 * t0 * dot(gi0, x0, y0, z0);
		}
		double x1 = x0 - i1 + G3; // Offsets for second corner in (x,y,z) coords
		double y1 = y0 - j1 + G3;
		double z1 = z0 - k1 + G3;
		double t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
		if (t1 > 0) {
			t1 *= t1;
			n += t1 * t1 * dot(gi1, x1, y1, z1);
		}

		x1 = x0 - i2 + 2.0 * G3; // Offsets for third corner in (x,y,z) coords
		y1 = y0 - j2 + 2.0 * G3;
		z1 = z0 - k2 + 2.0 * G3;
		t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
		if (t1 > 0) {
			t1 *= t1;
			n += t1 * t1 * dot(gi2, x1, y1, z1);
		}

		x1 = x0 - 1.0 + 3.0 * G3; // Offsets for last corner in (x,y,z) coords
		y1 = y0 - 1.0 + 3.0 * G3;
		z1 = z0 - 1.0 + 3.0 * G3;
		t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
		if (t1 > 0) {
			t1 *= t1;
			n += t1 * t1 * dot(gi3, x1, y1, z1);
		}

		return 32.0*n;
	}

	// 4D simplex noise, better simplex rank ordering method 2012-03-09
	public double noise(double x, double y, double z, double w) {
		double n0, n1, n2, n3, n4; // Noise contributions from the five corners
		// Skew the (x,y,z,w) space to determine which cell of 24 simplices we're in
		double s = (x + y + z + w) * F4; // Factor for 4D skewing
		int i = MathUtils.floor(x+s);
		int j = MathUtils.floor(y+s);
		int k = MathUtils.floor(z+s);
		int l = MathUtils.floor(w+s);

		double t = (i + j + k + l) * G4; // Factor for 4D unskewing
		// Unskew the cell origin back to (x,y,z,w) space
		double x0 = x - i + t;  // The x,y,z,w distances from the cell origin
		double y0 = y - j + t;
		double z0 = z - k + t;
		double w0 = w - l + t;
		// For the 4D case, the simplex is a 4D shape I won't even try to describe.
		// To find out which of the 24 possible simplices we're in, we need to
		// determine the magnitude ordering of x0, y0, z0 and w0.
		// Six pair-wise comparisons are performed between each possible pair
		// of the four coordinates, and the results are used to rank the numbers.
		int rankx = 0;
		int ranky = 0;
		int rankz = 0;
		int rankw = 0;
		if (x0 > y0) rankx++; else ranky++;
		if (x0 > z0) rankx++; else rankz++;
		if (x0 > w0) rankx++; else rankw++;
		if (y0 > z0) ranky++; else rankz++;
		if (y0 > w0) ranky++; else rankw++;
		if (z0 > w0) rankz++; else rankw++;
		// [rankx, ranky, rankz, rankw] is a 4-vector with the numbers 0, 1, 2 and 3
		// in some order. We use a thresholding to set the coordinates in turn.

		// Work out the hashed gradient indices of the five simplex corners
		int ii = i & 255;
		int jj = j & 255;
		int kk = k & 255;
		int ll = l & 255;

		double n = 0;

		int i0, j0, k0, l0; // The integer offsets for the [m] simplex corner
		double x1, y1, z1, w1, t1, g4 = 0;
		for (int m = 4; m >= 0; m--) {
			i0 = rankx >= m ? 1 : 0;
			j0 = ranky >= m ? 1 : 0;
			k0 = rankz >= m ? 1 : 0;
			l0 = rankw >= m ? 1 : 0;

			x1 = x0 - i0 + g4;
			y1 = y0 - j0 + g4;
			z1 = z0 - k0 + g4;
			w1 = w0 - l0 + g4;

			g4 += G4;

			t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1 - w1 * w1;
			if (t1 > 0) {
				int gi = perm[ii + i0 + perm[jj + j0 + perm[kk + k0 + perm[ll + l0]]]] % 32;
				t1 *= t1;
				n += t1 * t1 * dot(gi, x1, y1, z1, w1);
			}
		}

		return 27*n;
	}
}