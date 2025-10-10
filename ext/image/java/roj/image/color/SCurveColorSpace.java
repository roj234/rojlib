package roj.image.color;

import roj.math.Vec3d;

/**
 * @author Roj234
 * @since 2025/11/11 14:43
 */
public class SCurveColorSpace extends LinearColorSpace {
	public SCurveColorSpace(String name, LinearColorSpace cs) {
		super(name, cs);
	}

	public SCurveColorSpace(
			String name,
			double m00, double m01, double m02,
			double m10, double m11, double m12,
			double m20, double m21, double m22
	) {
		super(name, m00, m01, m02, m10, m11, m12, m20, m21, m22);
	}

	public static double SCurve(double color) {
		var input = Math.abs(color);

		if (input > 0.0031308)
			return Math.copySign((1.055 * (Math.pow(input, (1.0 / 2.4)))) - 0.055, color);

		return color * 12.92;
	}
	public static double invSCurve(double color) {
		var input = Math.abs(color);

		if (input > 0.04045)
			return Math.copySign(Math.pow((input + 0.055) / 1.055, 2.4), color);

		return input / 12.92;
	}

	@Override
	public Vec3d toCIEXYZ(Vec3d color) {
		return super.toCIEXYZ(new Vec3d(invSCurve(color.x), invSCurve(color.y), invSCurve(color.z)));
	}

	@Override
	public Vec3d fromCIEXYZ(Vec3d xyz) {
		Vec3d color = super.fromCIEXYZ(xyz);
		color.x = SCurve(color.x);
		color.y = SCurve(color.y);
		color.z = SCurve(color.z);
		return color;
	}
}
