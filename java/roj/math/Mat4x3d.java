package roj.math;

/**
 * A row-major 4x3 {@code double} (affine) matrix.
 */
public class Mat4x3d implements Cloneable {
	public double m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23;

	public Mat4x3d() {m00 = m11 = m22 = 1;}
	public Mat4x3d(double a11, double a12, double a13, double a14,
				   double a21, double a22, double a23, double a24,
				   double a31, double a32, double a33, double a34) {
		m00 = a11;
		m01 = a12;
		m02 = a13;
		m03 = a14;
		m10 = a21;
		m11 = a22;
		m12 = a23;
		m13 = a24;
		m20 = a31;
		m21 = a32;
		m22 = a33;
		m23 = a34;
	}
	public Mat4x3d(double[] matrix) {set(matrix);}
	public Mat4x3d(Mat4x3d matrix) {set(matrix);}

	/**
	 * Sets this matrix using the specified parameters.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d set(double a11, double a12, double a13, double a14,
					   double a21, double a22, double a23, double a24,
					   double a31, double a32, double a33, double a34) {
		m00 = a11;
		m01 = a12;
		m02 = a13;
		m03 = a14;
		m10 = a21;
		m11 = a22;
		m12 = a23;
		m13 = a24;
		m20 = a31;
		m21 = a32;
		m22 = a33;
		m23 = a34;
		return this;
	}

	/**
	 * Sets this matrix using the given array. The specified array is interpreted row-major wise.
	 *
	 * @param raw the (at least) 16 element array from which the matrix should be created.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d set(double[] raw) {
		m00 = raw[0];
		m01 = raw[1];
		m02 = raw[2];
		m03 = raw[3];
		m10 = raw[4];
		m11 = raw[5];
		m12 = raw[6];
		m13 = raw[7];
		m20 = raw[8];
		m21 = raw[9];
		m22 = raw[10];
		m23 = raw[11];
		return this;
	}

	/**
	 * Sets this matrix by copying the specified one.
	 *
	 * @param other the matrix to be copied.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d set(Mat4x3d other) {
		m00 = other.m00;
		m01 = other.m01;
		m02 = other.m02;
		m03 = other.m03;
		m10 = other.m10;
		m11 = other.m11;
		m12 = other.m12;
		m13 = other.m13;
		m20 = other.m20;
		m21 = other.m21;
		m22 = other.m22;
		m23 = other.m23;
		return this;
	}

	/**
	 * Sets this matrix to the identity-matrix.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d makeIdentity() {
		m00 = m11 = m22 = 1;
		m01 = m02 = m03 = m10 = m12 = m13 = m20 = m21 = m23 = 0;
		return this;
	}

	/**
	 * Sets this matrix to the orthographic projection matrix specified by the given parameters.
	 *
	 * @param left — 视锥体左侧面。
	 * @param right — 视锥体右侧面。
	 * @param top — 视锥体上侧面。
	 * @param bottom — 视锥体下侧面。
	 * @param near — 视锥体近端面。
	 * @param far — 视锥体远端面。
	 * <BR>
	 * 这些参数一起定义了viewing frustum（视锥体）。
	 *
	 * @return this matrix.
	 */
	public Mat4x3d makeOrtho(double left, double right, double top, double bottom, double near, double far) {
		m00 = 2.0f / (right - left);
		m01 = 0;
		m02 = 0;
		m03 = -(right + left) / (right - left);

		m10 = 0;
		m11 = 2.0f / (top - bottom);
		m12 = 0;
		m13 = -(top + bottom) / (top - bottom);

		m20 = 0;
		m21 = 0;
		m22 = -2.0f / (far - near);
		m23 = -(far + near) / (far - near);

		return this;
	}

	/**
	 * Set this matrix to a look-at transformation matrix, looking from {@code eye} to {@code center} with the
	 * {@code up} vector indicating the upward looking direction.
	 *
	 * @param eye the eye from which this transformation should "look".
	 * @param center the center to which this transformation should "look".
	 * @param up the vector indicating the upwards looking direction.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d makeLookAt(Vec3d eye, Vec3d center, Vec3d up) { return makeLookInDirection(eye, new Vec3d(center).sub(eye), up); }
	/**
	 * Set this matrix to a look-in-direction transformation matrix, looking from {@code eye} in direction of
	 * {@code dir} with the {@code up} vector indicating the upward looking direction.
	 *
	 * @param eye the eye from which this transformation should "look".
	 * @param dir the direction into which this transformation should "look".
	 * @param up the vector indicating the upwards looking direction.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d makeLookInDirection(Vec3d eye, Vec3d dir, Vec3d up) {
		double abs = Math.sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z);
		double fwdX = dir.x / abs;
		double fwdY = dir.y / abs;
		double fwdZ = dir.z / abs;

		double sideX = up.z * fwdY - up.y * fwdZ;
		double sideY = up.x * fwdZ - up.z * fwdX;
		double sideZ = up.y * fwdX - up.x * fwdY;

		abs = Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
		sideX /= abs;
		sideY /= abs;
		sideZ /= abs;

		double upX = sideY * fwdZ - sideZ * fwdY;
		double upY = sideZ * fwdX - sideX * fwdZ;
		double upZ = sideX * fwdY - sideY * fwdX;

		m00 = sideX;
		m01 = sideY;
		m02 = sideZ;
		m03 = 0;
		m10 = upX;
		m11 = upY;
		m12 = upZ;
		m13 = 0;
		m20 = -fwdX;
		m21 = -fwdY;
		m22 = -fwdZ;
		m23 = 0;

		return translate(- eye.x, - eye.y, - eye.z);
	}

	/**
	 * Calculates the determinant of the upper left 3x3 sub-matrix.
	 *
	 * @return the calculated determinant of the upper left 3x3 sub-matrix.
	 */
	public double det() {return m00 * (m11 * m22 - m12 * m21) - m01 * (m10 * m22 - m12 * m20) + m02 * (m10 * m21 - m11 * m20);}

	/**
	 * Tests whether this matrix is affine or not.
	 *
	 * @return {@code true} if this matrix is affine.
	 */
	public boolean isAffine() {return true;}

	/**
	 * Adds the specified matrix to this matrix and stores the result in this matrix.
	 *
	 * @param other the matrix to add.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d add(Mat4x3d other) {
		m00 += other.m00;
		m01 += other.m01;
		m02 += other.m02;
		m03 += other.m03;
		m10 += other.m10;
		m11 += other.m11;
		m12 += other.m12;
		m13 += other.m13;
		m20 += other.m20;
		m21 += other.m21;
		m22 += other.m22;
		m23 += other.m23;
		return this;
	}

	/**
	 * Subtracts the specified matrix from this matrix and stores the result in this matrix.
	 *
	 * @param other the matrix to subtract.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d sub(Mat4x3d other) {
		m00 -= other.m00;
		m01 -= other.m01;
		m02 -= other.m02;
		m03 -= other.m03;
		m10 -= other.m10;
		m11 -= other.m11;
		m12 -= other.m12;
		m13 -= other.m13;
		m20 -= other.m20;
		m21 -= other.m21;
		m22 -= other.m22;
		m23 -= other.m23;
		return this;
	}

	/**
	 * Multiplies this matrix with the specified matrix, i.e. {@code this * o}.
	 *
	 * @param o the matrix to multiply with.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d mul(Mat4x3d o) {
		double r0 = m00, r1 = m01, r2 = m02, r3 = m03;

		m00 = r0 * o.m00 + r1 * o.m10 + r2 * o.m20;
		m01 = r0 * o.m01 + r1 * o.m11 + r2 * o.m21;
		m02 = r0 * o.m02 + r1 * o.m12 + r2 * o.m22;
		m03 = r0 * o.m03 + r1 * o.m13 + r2 * o.m23 + r3;

		r0 = m10;
		r1 = m11;
		r2 = m12;
		r3 = m13;
		m10 = r0 * o.m00 + r1 * o.m10 + r2 * o.m20;
		m11 = r0 * o.m01 + r1 * o.m11 + r2 * o.m21;
		m12 = r0 * o.m02 + r1 * o.m12 + r2 * o.m22;
		m13 = r0 * o.m03 + r1 * o.m13 + r2 * o.m23 + r3;

		r0 = m20;
		r1 = m21;
		r2 = m22;
		r3 = m23;
		m20 = r0 * o.m00 + r1 * o.m10 + r2 * o.m20;
		m21 = r0 * o.m01 + r1 * o.m11 + r2 * o.m21;
		m22 = r0 * o.m02 + r1 * o.m12 + r2 * o.m22;
		m23 = r0 * o.m03 + r1 * o.m13 + r2 * o.m23 + r3;

		return this;
	}

	public Vector mul(Vector v) {return mul(v, new Vec3d());}
	public Vector mulInPlace(Vector v) {return mul(v, v);}
	public Vector mul(Vector v, Vector dst) {
		double x = m00 * v.x() + m01 * v.y() + m02 * v.z() + m03 * v.w();
		double y = m10 * v.x() + m11 * v.y() + m12 * v.z() + m13 * v.w();
		dst.z(m20 * v.x() + m21 * v.y() + m22 * v.z() + m23 * v.w());
		dst.x(x);
		dst.y(y);
		return dst;
	}

	public Mat4x3d mul(double scalar) {
		m00 *= scalar;
		m01 *= scalar;
		m02 *= scalar;
		m03 *= scalar;
		m10 *= scalar;
		m11 *= scalar;
		m12 *= scalar;
		m13 *= scalar;
		m20 *= scalar;
		m21 *= scalar;
		m22 *= scalar;
		m23 *= scalar;
		return this;
	}

	/**
	 * Applies the specified translation to this matrix as if by multiplying this matrix with the according translation
	 * matrix (i. e. {@code this * translation}).
	 *
	 * @param vec the vector specifying the translation.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d translate(Vector vec) {return translate(vec.x(), vec.y(), vec.z());}
	/**
	 * Applies the specified translation to this matrix as if by multiplying this matrix with the according translation
	 * matrix (i. e. {@code this * translation}).
	 *
	 * @param x the x-axis translation component.
	 * @param y the y-axis translation component.
	 * @param z the z-axis translation component.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d translate(double x, double y, double z) {
		m03 = m00 * x + m01 * y + m02 * z + m03;
		m13 = m10 * x + m11 * y + m12 * z + m13;
		m23 = m20 * x + m21 * y + m22 * z + m23;
		return this;
	}

	/**
	 * Applies an absolute translation to this matrix.
	 *
	 * @return this matrix.
	 *
	 * @see #translate(double, double, double)
	 */
	public Mat4x3d translateAbs(double x, double y, double z) {
		m03 = m00 * x + m01 * y + m02 * z;
		m13 = m10 * x + m11 * y + m12 * z;
		m23 = m20 * x + m21 * y + m22 * z;
		return this;
	}

	/**
	 * Reset this matrix's translation.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d resetTranslation() { m03 = m13 = m23 = 0; return this; }

	/**
	 * Applies the specified scaling-operation to this matrix as if by multiplying this matrix with the according
	 * scale matrix (i. e. {@code this * scale}).
	 *
	 * @param vec the vector specifying the scale-transformation.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d scale(Vector vec) { return scale(vec.x(), vec.y(), vec.z()); }

	/**
	 * Applies the specified scaling-operation to this matrix as if by multiplying this matrix with the according
	 * scale matrix (i. e. {@code this * scale}).
	 *
	 * @param sx the x-axis scale component.
	 * @param sy the y-axis scale component.
	 * @param sz the z-axis scale component.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d scale(double sx, double sy, double sz) {
		m00 *= sx;
		m01 *= sy;
		m02 *= sz;
		m10 *= sx;
		m11 *= sy;
		m12 *= sz;
		m20 *= sx;
		m21 *= sy;
		m22 *= sz;
		return this;
	}

	/**
	 * Applies the specified rotation-operation to this matrix as if by multiplying this matrix with the according
	 * rotation matrix (i. e. {@code this * rotation}).
	 *
	 * @param axis the axis around which should be rotated.
	 * @param angle the angle (in radians) specifying the rotation.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d rotate(Vector axis, double angle) {return rotate(axis.x(), axis.y(), axis.z(), angle);}
	/**
	 * Applies the specified rotation-operation to this matrix as if by multiplying this matrix with the according
	 * rotation matrix (i. e. {@code this * rotation}).
	 *
	 * @param x the x part of the axis around which should be rotated.
	 * @param y the y part of the axis around which should be rotated.
	 * @param z the z part of the axis around which should be rotated.
	 * @param angle the angle (in radians) specifying the rotation.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d rotate(double x, double y, double z, double angle) {
		double s = MathUtils.sin(angle);
		double c = MathUtils.cos(angle);
		double omc = 1.0f - c;

		double xz = x * z;
		double ys = y * s;

		double xx = x * x;
		double xy = x * y;
		double zs = z * s;

		double r0 = xx * omc + c;
		double r1 = xy * omc - zs;
		double r2 = xz * omc + ys;
		double r4 = xy * omc + zs;

		double yy = y * y;
		double yz = y * z;
		double zz = z * z;
		double xs = x * s;

		double r5 = yy * omc + c;
		double r6 = yz * omc - xs;
		double r8 = xz * omc - ys;
		double r9 = yz * omc + xs;
		double r10 = zz * omc + c;

		double u = m00, v = m01, w = m02;
		m00 = u * r0 + v * r4 + w * r8;
		m01 = u * r1 + v * r5 + w * r9;
		m02 = u * r2 + v * r6 + w * r10;

		u = m10;
		v = m11;
		w = m12;
		m10 = u * r0 + v * r4 + w * r8;
		m11 = u * r1 + v * r5 + w * r9;
		m12 = u * r2 + v * r6 + w * r10;

		u = m20;
		v = m21;
		w = m22;
		m20 = u * r0 + v * r4 + w * r8;
		m21 = u * r1 + v * r5 + w * r9;
		m22 = u * r2 + v * r6 + w * r10;

		return this;
	}

	/**
	 * Applies an absolute rotation-operation to this matrix.
	 *
	 * @return this matrix.
	 *
	 * @see #rotate(double, double, double, double)
	 */
	public Mat4x3d rotateAbs(double x, double y, double z, double angle) {
		double s = MathUtils.sin(angle);
		double c = MathUtils.cos(angle);
		double omc = 1.0f - c;

		double xy = x * y;
		double zs = z * s;

		m00 = (x * x) * omc + c;
		m01 = xy * omc - zs;
		double xz = x * z;
		double ys = y * s;
		m02 = xz * omc + ys;

		m10 = xy * omc + zs;
		m11 = (y * y) * omc + c;
		double yz = y * z;
		double xs = x * s;
		m12 = yz * omc - xs;

		m20 = xz * omc - ys;
		m21 = yz * omc + xs;
		m22 = (z * z) * omc + c;

		// 3 7 11 15 => translation
		return this;
	}

	/**
	 * Applies the specified {@code x}-axis rotation-operation to this matrix as if by multiplying this matrix with the
	 * according rotation matrix (i. e. {@code this * rotation}).
	 *
	 * @param angle the angle (in radians) specifying the rotation around the {@code x}-axis.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d rotateX(double angle) {
		double s = MathUtils.sin(angle);
		double c = MathUtils.cos(angle);

		double u = m01, v = m02;
		m01 = u * c + v * s;
		m02 = -u * s + v * c;

		u = m11;
		v = m12;
		m11 = u * c + v * s;
		m12 = -u * s + v * c;

		u = m21;
		v = m22;
		m21 = u * c + v * s;
		m22 = -u * s + v * c;

		return this;
	}

	/**
	 * Applies the specified {@code y}-axis rotation-operation to this matrix as if by multiplying this matrix with the
	 * according rotation matrix (i. e. {@code this * rotation}).
	 *
	 * @param angle the angle (in radians) specifying the rotation around the {@code y}-axis.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d rotateY(double angle) {
		double s = MathUtils.sin(angle);
		double c = MathUtils.cos(angle);

		double u = m00, v = m02;
		m00 = u * c - v * s;
		m02 = u * s + v * c;

		u = m10;
		v = m12;
		m10 = u * c - v * s;
		m12 = u * s + v * c;

		u = m20;
		v = m22;
		m20 = u * c - v * s;
		m22 = u * s + v * c;

		return this;
	}

	/**
	 * Applies the specified {@code z}-axis rotation-operation to this matrix as if by multiplying this matrix with the
	 * according rotation matrix (i. e. {@code this * rotation}).
	 *
	 * @param angle the angle (in radians) specifying the rotation around the {@code z}-axis.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d rotateZ(double angle) {
		double s = MathUtils.sin(angle);
		double c = MathUtils.cos(angle);

		double u = m00, v = m01;
		m00 = u * c + v * s;
		m01 = -u * s + v * c;

		u = m10;
		v = m11;
		m10 = u * c + v * s;
		m11 = -u * s + v * c;

		u = m20;
		v = m21;
		m20 = u * c + v * s;
		m21 = -u * s + v * c;

		return this;
	}

	/**
	 * Transposes the upper left 3x3 matrix, leaving all other elements as they are.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d transpose() {
		double swp;

		swp = m01;
		m01 = m10;
		m10 = swp;

		swp = m02;
		m02 = m20;
		m20 = swp;

		swp = m12;
		m12 = m21;
		m21 = swp;

		return this;
	}

	/**
	 * Inverts this matrix.
	 *
	 * @return this matrix.
	 */
	public Mat4x3d invert() {
		// calculate inverse of upper 3x3 matrix
		double m0 = m11 * m22 - m12 * m21;
		double m1 = m02 * m21 - m01 * m22;
		double m2 = m01 * m12 - m02 * m11;
		double m3 = m12 * m20 - m10 * m22;
		double m4 = m00 * m22 - m02 * m20;
		double m5 = m02 * m10 - m00 * m12;
		double m6 = m10 * m21 - m11 * m20;
		double m7 = m01 * m20 - m00 * m21;
		double m8 = m00 * m11 - m01 * m10;

		double det = m00 * m0 + m01 * (-m3) + m02 * m6;
		if (det == 0) return null;

		m0 /= det;
		m1 /= det;
		m2 /= det;
		m3 /= det;
		m4 /= det;
		m5 /= det;
		m6 /= det;
		m7 /= det;
		m8 /= det;

		m00 = m0;
		m01 = m1;
		m02 = m2;

		m10 = m3;
		m11 = m4;
		m12 = m5;

		m20 = m6;
		m21 = m7;
		m22 = m8;

		// calculate product of inverse upper 3x3 matrix and translation part
		double u = -m03, v = -m13, w = -m23;

		m03 = m0 * u + m1 * v + m2 * w; // tx
		m13 = m3 * u + m4 * v + m5 * w; // ty
		m23 = m6 * u + m7 * v + m8 * w; // tz

		// assemble inverse matrix
		return this;
	}

	/**
	 * Creates a new matrix-instance containing the identity-matrix.
	 *
	 * @return a new identity matrix.
	 */
	public static Mat4x3d identity() {return new Mat4x3d();}

	/**
	 * @see #ortho(double, double, double, double, double, double)
	 */
	public static Mat4x3d ortho(double left, double right, double top, double bottom, double near, double far) {return identity().makeOrtho(left, right, top, bottom, near, far);}
	/**
	 * @see #lookAt(Vec3d, Vec3d, Vec3d)
	 */
	public static Mat4x3d lookAt(Vec3d eye, Vec3d center, Vec3d up) {return identity().makeLookAt(eye, center, up);}
	/**
	 * @see #lookInDirection(Vec3d, Vec3d, Vec3d)
	 */
	public static Mat4x3d lookInDirection(Vec3d eye, Vec3d dir, Vec3d up) {return identity().makeLookInDirection(eye, dir, up);}

	/**
	 * @see #add(Mat4x3d)
	 */
	public static Mat4x3d add(Mat4x3d a, Mat4x3d b) {return new Mat4x3d(a).add(b);}
	/**
	 * @see #sub(Mat4x3d)
	 */
	public static Mat4x3d sub(Mat4x3d a, Mat4x3d b) {return new Mat4x3d(a).sub(b);}
	/**
	 * @see #mul(Mat4x3d)
	 */
	public static Mat4x3d mul(Mat4x3d a, Mat4x3d b) {return new Mat4x3d(a).mul(b);}
	/**
	 * @see #mul(double)
	 */
	public static Mat4x3d mul(Mat4x3d m, double scalar) {return new Mat4x3d(m).mul(scalar);}

	/**
	 * @see #translate(Vector)
	 */
	public static Mat4x3d translate(Mat4x3d m, Vector v) {return new Mat4x3d(m).translate(v);}
	/**
	 * @see #translate(double, double, double)
	 */
	public static Mat4x3d translate(Mat4x3d m, double x, double y, double z) {return new Mat4x3d(m).translate(x, y, z);}
	/**
	 * @see #scale(Vector)
	 */
	public static Mat4x3d scale(Mat4x3d m, Vector v) {return new Mat4x3d(m).scale(v);}
	/**
	 * @see #scale(double, double, double)
	 */
	public static Mat4x3d scale(Mat4x3d m, double x, double y, double z) {return new Mat4x3d(m).scale(x, y, z);}
	/**
	 * @see #rotate(Vector, double)
	 */
	public static Mat4x3d rotate(Mat4x3d m, Vector axis, double angle) {return new Mat4x3d(m).rotate(axis, angle);}
	/**
	 * @see #rotate(double, double, double, double)
	 */
	public static Mat4x3d rotate(Mat4x3d m, double x, double y, double z, double angle) {return new Mat4x3d(m).rotate(x, y, z, angle);}
	/**
	 * @see #rotateX(double)
	 */
	public static Mat4x3d rotateX(Mat4x3d m, double angle) {return new Mat4x3d(m).rotateX(angle);}
	/**
	 * @see #rotateY(double)
	 */
	public static Mat4x3d rotateY(Mat4x3d m, double angle) {return new Mat4x3d(m).rotateY(angle);}
	/**
	 * @see #rotateZ(double)
	 */
	public static Mat4x3d rotateZ(Mat4x3d m, double angle) {return new Mat4x3d(m).rotateZ(angle);}

	/**
	 * @see #transpose()
	 */
	public static Mat4x3d transpose(Mat4x3d m) {return new Mat4x3d(m).transpose();}
	/**
	 * @see #invert()
	 */
	public static Mat4x3d invert(Mat4x3d m) {return new Mat4x3d(m).invert();}

	public static Mat4x3d mix(Mat4x3d a, Mat4x3d b, double percentA, Mat4x3d store) {
		double percentB = 1 - percentA;

		store.m00 = a.m00 * percentA + b.m00 * percentB;
		store.m01 = a.m01 * percentA + b.m01 * percentB;
		store.m02 = a.m02 * percentA + b.m02 * percentB;
		store.m03 = a.m03 * percentA + b.m03 * percentB;
		store.m10 = a.m10 * percentA + b.m10 * percentB;
		store.m11 = a.m11 * percentA + b.m11 * percentB;
		store.m12 = a.m12 * percentA + b.m12 * percentB;
		store.m13 = a.m13 * percentA + b.m13 * percentB;
		store.m20 = a.m20 * percentA + b.m20 * percentB;
		store.m21 = a.m21 * percentA + b.m21 * percentB;
		store.m22 = a.m22 * percentA + b.m22 * percentB;
		store.m23 = a.m23 * percentA + b.m23 * percentB;

		return store;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Mat4x3d d)) return false;

		if (Double.compare(m00, d.m00) != 0) return false;
		if (Double.compare(m01, d.m01) != 0) return false;
		if (Double.compare(m02, d.m02) != 0) return false;
		if (Double.compare(m03, d.m03) != 0) return false;
		if (Double.compare(m10, d.m10) != 0) return false;
		if (Double.compare(m11, d.m11) != 0) return false;
		if (Double.compare(m12, d.m12) != 0) return false;
		if (Double.compare(m13, d.m13) != 0) return false;
		if (Double.compare(m20, d.m20) != 0) return false;
		if (Double.compare(m21, d.m21) != 0) return false;
		if (Double.compare(m22, d.m22) != 0) return false;
		return Double.compare(m23, d.m23) == 0;
	}

	@Override
	public int hashCode() {
		int h;
		long db;
		db = Double.doubleToLongBits(m00);
		h = (int) (db ^ (db >>> 32));
		db = Double.doubleToLongBits(m01);
		h = 31 * h + (int) (db ^ (db >>> 32));
		db = Double.doubleToLongBits(m02);
		h = 31 * h + (int) (db ^ (db >>> 32));
		db = Double.doubleToLongBits(m03);
		h = 31 * h + (int) (db ^ (db >>> 32));
		db = Double.doubleToLongBits(m10);
		h = 31 * h + (int) (db ^ (db >>> 32));
		db = Double.doubleToLongBits(m11);
		h = 31 * h + (int) (db ^ (db >>> 32));
		db = Double.doubleToLongBits(m12);
		h = 31 * h + (int) (db ^ (db >>> 32));
		db = Double.doubleToLongBits(m13);
		h = 31 * h + (int) (db ^ (db >>> 32));
		db = Double.doubleToLongBits(m20);
		h = 31 * h + (int) (db ^ (db >>> 32));
		db = Double.doubleToLongBits(m21);
		h = 31 * h + (int) (db ^ (db >>> 32));
		db = Double.doubleToLongBits(m22);
		h = 31 * h + (int) (db ^ (db >>> 32));
		db = Double.doubleToLongBits(m23);
		h = 31 * h + (int) (db ^ (db >>> 32));
		return h;
	}

	@Override
	public String toString() {return "Mat4x3{"+m00+","+m01+","+m02+","+m03+",\n"+m10+","+m11+","+m12+","+m13+",\n"+m20+","+m21+","+m22+","+m23+'}';}
}