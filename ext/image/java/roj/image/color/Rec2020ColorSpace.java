package roj.image.color;

import roj.math.Vec3d;

/**
 * @author Roj234
 * @since 2025/11/11 14:43
 */
public class Rec2020ColorSpace extends LinearColorSpace {
	public Rec2020ColorSpace() {
		super("Rec.2020",
				1.716651187971268, -0.355670783776392, -0.253366281373660,
				-0.666684351832489, 1.616481236634939, 0.0157685458139111,
				0.017639857445311, -0.042770613257809, 0.942103121235474
		);
	}

	// https://en.wikipedia.org/wiki/Rec._2020#:~:text=%5B14%5D-,Transfer%20characteristics,-%5Bedit%5D
	private static final double recAlpha = 1.09929682680944, recBeta = 0.018053968510807;
	public static double gamma(double val) {
		if (val >= recBeta)
			return recAlpha * Math.pow(val, 0.45) - (recAlpha - 1);

		return 4.5 * val;

	}
	public static double invGamma(double val) {
		if (val >= recBeta * 4.5) {
			return Math.pow((val + recAlpha - 1) / recAlpha, 1/0.45);
		}

		return val / 4.5;
	}

	@Override
	public Vec3d toCIEXYZ(Vec3d color) {
		return super.toCIEXYZ(new Vec3d(invGamma(color.x), invGamma(color.y), invGamma(color.z)));
	}

	@Override
	public Vec3d fromCIEXYZ(Vec3d xyz) {
		Vec3d color = super.fromCIEXYZ(xyz);
		color.x = gamma(color.x);
		color.y = gamma(color.y);
		color.z = gamma(color.z);
		return color;
	}
}
