package roj.image.color;

import roj.math.Mat4x3d;
import roj.math.Vec3d;

/**
 * @author Roj234
 * @since 2025/11/11 14:41
 */
public class LinearColorSpace implements ColorSpace<Vec3d> {
	private final String name;
	private final Mat4x3d M, invM;

	public LinearColorSpace(
			String name,
			double m00, double m01, double m02,
			double m10, double m11, double m12,
			double m20, double m21, double m22
	) {
		this.name = name;
		M = new Mat4x3d(
				m00, m01, m02,
				m10, m11, m12,
				m20, m21, m22
		);
		invM = Mat4x3d.invert(M);
	}

	public LinearColorSpace(String name, LinearColorSpace cs) {
		this.name = name;
		M = cs.M;
		invM = cs.invM;
	}

	public Vec3d toCIEXYZ(Vec3d color) {return invM.mul(color);}
	public Vec3d fromCIEXYZ(Vec3d xyz) {return M.mul(xyz);}

	@Override
	public String toString() {return name+" color space: "+M;}
}
