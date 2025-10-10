package roj.image.color;

import roj.math.Vec3d;

/**
 * @author Roj234
 * @since 2025/11/11 18:15
 */
public class OKLCHColorSpace extends OKLabColorSpace {
	@Override
	public Vec3d toCIEXYZ(Vec3d color) {
		var L = color.x;
		var C = color.y;
		var H = color.z;

		var a = C * Math.cos(H * Math.PI / 180);
		var b = C * Math.sin(H * Math.PI / 180);

		return super.toCIEXYZ(new Vec3d(L, a, b));
	}

	@Override
	public Vec3d fromCIEXYZ(Vec3d xyz) {
		var color = super.fromCIEXYZ(xyz);

		var a = color.y;
		var b = color.z;

		var H = Math.atan2(b, a) * 180 / Math.PI;
		var C = Math.sqrt(Math.fma(a, a, b * b));

		color.y = H;
		color.z = C;
		return color;
	}
}
