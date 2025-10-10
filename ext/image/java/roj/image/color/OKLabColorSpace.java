package roj.image.color;

import roj.math.Mat4x3d;
import roj.math.Vec3d;

/**
 * @author Roj234
 * @since 2025/11/11 18:15
 */
public class OKLabColorSpace implements ColorSpace<Vec3d> {
	private static final Mat4x3d M1 = new Mat4x3d(
			+0.8189330101, +0.3618667424, -0.1288597137,
			+0.0329845436, +0.9293118715, +0.0361456387,
			+0.0482003018, +0.2643662691, +0.6338517070
	);
	private static final Mat4x3d M2 = new Mat4x3d(
			+0.2104542553, +0.7936177850, -0.0040720468,
			+1.9779984951, -2.4285922050, +0.4505937099,
			+0.0259040371, +0.7827717662, -0.8086757660
	);
	private static final Mat4x3d invM1 = Mat4x3d.invert(M1);
	private static final Mat4x3d invM2 = Mat4x3d.invert(M2);

	@Override
	public Vec3d toCIEXYZ(Vec3d color) {
		var lms = invM2.mul(color);

		lms.x = Math.pow(lms.x, 3);
		lms.y = Math.pow(lms.y, 3);
		lms.z = Math.pow(lms.z, 3);

		return invM1.mul(lms, lms);
	}

	@Override
	public Vec3d fromCIEXYZ(Vec3d xyz) {
		var lms = M1.mul(xyz);

		lms.x = Math.pow(lms.x, 1/3d);
		lms.y = Math.pow(lms.y, 1/3d);
		lms.z = Math.pow(lms.z, 1/3d);

		return M2.mul(lms, lms);
	}
}
