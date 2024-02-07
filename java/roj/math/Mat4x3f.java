package roj.math;

import roj.util.Hasher;


/**
 * A row-major 4x3 {@code float} (affine) matrix.
 */
public class Mat4x3f implements Cloneable {
	public Mat4x3f() {
		m00 = m11 = m22 = 1;
	}

	public float m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23;


	/**
	 * Constructs a new matrix with the given properties.
	 */
	public Mat4x3f(float a11, float a12, float a13, float a14, float a21, float a22, float a23, float a24, float a31, float a32, float a33, float a34) {

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

	/**
	 * Constructs a new matrix from the given array. The specified array is interpreted row-major wise.
	 *
	 * @param matrix the (at least) 16 element array from which the matrix should be created.
	 */
	public Mat4x3f(float[] matrix) {
		set(matrix);
	}

	/**
	 * Construct a new matrix by copying the specified one.
	 *
	 * @param matrix the matrix to be copied.
	 */
	public Mat4x3f(Mat4x3f matrix) {
		set(matrix);
	}

	/**
	 * Sets this matrix using the specified parameters.
	 *
	 * @return this matrix.
	 */
	public Mat4x3f set(float a11, float a12, float a13, float a14, float a21, float a22, float a23, float a24, float a31, float a32, float a33, float a34) {

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
	public Mat4x3f set(float[] raw) {
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
	public Mat4x3f set(Mat4x3f other) {
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
	public Mat4x3f makeIdentity() {
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
	public Mat4x3f makeOrtho(float left, float right, float top, float bottom, float near, float far) {
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
	public Mat4x3f makeLookAt(Vector eye, Vector center, Vector up) {
		return makeLookInDirection(eye, center.newInstance().set(center).sub(eye), up);
	}

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
	public Mat4x3f makeLookInDirection(Vector eye, Vector dir, Vector up) {
		double abs = Math.sqrt(dir.x() * dir.x() + dir.y() * dir.y() + dir.z() * dir.z());
		double fwdX = dir.x() / abs;
		double fwdY = dir.y() / abs;
		double fwdZ = dir.z() / abs;

		double sideX = up.z() * fwdY - up.y() * fwdZ;
		double sideY = up.x() * fwdZ - up.z() * fwdX;
		double sideZ = up.y() * fwdX - up.x() * fwdY;

		abs = Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
		sideX /= abs;
		sideY /= abs;
		sideZ /= abs;

		float upX = (float) (sideY * fwdZ - sideZ * fwdY);
		float upY = (float) (sideZ * fwdX - sideX * fwdZ);
		float upZ = (float) (sideX * fwdY - sideY * fwdX);

		m00 = (float) sideX;
		m01 = (float) sideY;
		m02 = (float) sideZ;
		m03 = 0;
		m10 = upX;
		m11 = upY;
		m12 = upZ;
		m13 = 0;
		m20 = (float) -fwdX;
		m21 = (float) -fwdY;
		m22 = (float) -fwdZ;
		m23 = 0;

		return translate(-(float) eye.x(), -(float) eye.y(), -(float) eye.z());
	}

	/**
	 * Calculates the determinant of the upper left 3x3 sub-matrix.
	 *
	 * @return the calculated determinant of the upper left 3x3 sub-matrix.
	 */
	public float det() {
		return m00 * (m11 * m22 - m12 * m21) - m01 * (m10 * m22 - m12 * m20) + m02 * (m10 * m21 - m11 * m20);
	}

	/**
	 * Tests whether this matrix is affine or not.
	 *
	 * @return {@code true} if this matrix is affine.
	 */
	public boolean isAffine() {
		return true;
	}

	/**
	 * Adds the specified matrix to this matrix and stores the result in this matrix.
	 *
	 * @param other the matrix to add.
	 *
	 * @return this matrix.
	 */
	public Mat4x3f add(Mat4x3f other) {
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
	public Mat4x3f sub(Mat4x3f other) {
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
	public Mat4x3f mul(Mat4x3f o) {
		float r0 = m00, r1 = m01, r2 = m02, r3 = m03;

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

	public Vector mul(Vector v) {
		return mul(v, new Vec3f());
	}
	public Vector mul(Vector v, Vector dst) {
		dst.x(m00 * v.x() + m01 * v.y() + m02 * v.z() + m03 * v.w());
		dst.y(m10 * v.x() + m11 * v.y() + m12 * v.z() + m13 * v.w());
		dst.z(m20 * v.x() + m21 * v.y() + m22 * v.z() + m23 * v.w());
		return dst;
	}

	public Mat4x3f mul(float scalar) {
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
	public Mat4x3f translate(Vector vec) {
		return translate((float) vec.x(), (float) vec.y(), (float) vec.z());
	}

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
	public Mat4x3f translate(float x, float y, float z) {
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
	 * @see #translate(float, float, float)
	 */
	public Mat4x3f translateAbs(float x, float y, float z) {
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
	public Mat4x3f resetTranslation() {
		m03 = 0;
		m13 = 0;
		m23 = 0;
		return this;
	}

	/**
	 * Applies the specified scaling-operation to this matrix as if by multiplying this matrix with the according
	 * scale matrix (i. e. {@code this * scale}).
	 *
	 * @param vec the vector specifying the scale-transformation.
	 *
	 * @return this matrix.
	 */
	public Mat4x3f scale(Vector vec) {
		return scale((float) vec.x(), (float) vec.y(), (float) vec.z());
	}

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
	public Mat4x3f scale(float sx, float sy, float sz) {
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
	public Mat4x3f rotate(Vec3f axis, float angle) {
		return rotate(axis.x, axis.y, axis.z, angle);
	}

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
	public Mat4x3f rotate(float x, float y, float z, float angle) {
		float s = MathUtils.sin(angle);
		float c = MathUtils.cos(angle);
		float omc = 1.0f - c;

		float xz = x * z;
		float ys = y * s;

		float xx = x * x;
		float xy = x * y;
		float zs = z * s;

		float r0 = xx * omc + c;
		float r1 = xy * omc - zs;
		float r2 = xz * omc + ys;
		float r4 = xy * omc + zs;

		float yy = y * y;
		float yz = y * z;
		float zz = z * z;
		float xs = x * s;

		float r5 = yy * omc + c;
		float r6 = yz * omc - xs;
		float r8 = xz * omc - ys;
		float r9 = yz * omc + xs;
		float r10 = zz * omc + c;

		float u = m00, v = m01, w = m02;
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
	 * @see #rotate(float, float, float, float)
	 */
	public Mat4x3f rotateAbs(float x, float y, float z, float angle) {
		float s = MathUtils.sin(angle);
		float c = MathUtils.cos(angle);
		float omc = 1.0f - c;

		float xy = x * y;
		float zs = z * s;

		m00 = (x * x) * omc + c;
		m01 = xy * omc - zs;
		float xz = x * z;
		float ys = y * s;
		m02 = xz * omc + ys;

		m10 = xy * omc + zs;
		m11 = (y * y) * omc + c;
		float yz = y * z;
		float xs = x * s;
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
	public Mat4x3f rotateX(float angle) {
		float s = MathUtils.sin(angle);
		float c = MathUtils.cos(angle);

		float u = m01, v = m02;
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
	public Mat4x3f rotateY(float angle) {
		float s = MathUtils.sin(angle);
		float c = MathUtils.cos(angle);

		float u = m00, v = m02;
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
	public Mat4x3f rotateZ(float angle) {
		float s = MathUtils.sin(angle);
		float c = MathUtils.cos(angle);

		float u = m00, v = m01;
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
	public Mat4x3f transpose() {
		float swp;

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
	public Mat4x3f invert() {
		return invertAffine();
	}

	/**
	 * Inverts this matrix as if it were an affine matrix.
	 *
	 * @return this matrix.
	 */
	public Mat4x3f invertAffine() {
		// calculate inverse of upper 3x3 matrix
		float m0 = m11 * m22 - m12 * m21;
		float m1 = m02 * m21 - m01 * m22;
		float m2 = m01 * m12 - m02 * m11;
		float m3 = m12 * m20 - m10 * m22;
		float m4 = m00 * m22 - m02 * m20;
		float m5 = m02 * m10 - m00 * m12;
		float m6 = m10 * m21 - m11 * m20;
		float m7 = m01 * m20 - m00 * m21;
		float m8 = m00 * m11 - m01 * m10;

		float det = m00 * m0 + m01 * (-m3) + m02 * m6;
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
		float u = -m03, v = -m13, w = -m23;

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
	public static Mat4x3f identity() {
		return new Mat4x3f();
	}

	/**
	 * Creates a new orthogonal projection-matrix.
	 *
	 * @param left the left plane.
	 * @param right the right plane.
	 * @param top the top plane.
	 * @param bottom the bottom plane.
	 * @param near the near plane.
	 * @param far the far plane.
	 *
	 * @return the created projection-matrix.
	 */
	public static Mat4x3f ortho(float left, float right, float top, float bottom, float near, float far) {
		return new Mat4x3f().makeOrtho(left, right, top, bottom, near, far);
	}

	/**
	 * Creates a look-at transformation matrix, looking from {@code eye} to {@code center} with the {@code up} vector
	 * indicating the upward looking direction.
	 *
	 * @param eye the eye from which this transformation should "look".
	 * @param center the center to which this transformation should "look".
	 * @param up the vector indicating the upwards looking direction.
	 *
	 * @return the created look-at matrix.
	 */
	public static Mat4x3f lookAt(Vec3f eye, Vec3f center, Vec3f up) {
		return new Mat4x3f().makeLookAt(eye, center, up);
	}

	/**
	 * Creates a a look-in-direction transformation matrix, looking from {@code eye} in direction of {@code dir} with
	 * the {@code up} vector indicating the upward looking direction.
	 *
	 * @param eye the eye from which this transformation should "look".
	 * @param dir the direction into which this transformation should "look".
	 * @param up the vector indicating the upwards looking direction.
	 *
	 * @return this matrix.
	 */
	public static Mat4x3f lookInDirection(Vec3f eye, Vec3f dir, Vec3f up) {
		return new Mat4x3f().makeLookInDirection(eye, dir, up);
	}

	/**
	 * Adds the specified matrices and returns the result as new matrix.
	 *
	 * @param a the first matrix.
	 * @param b the second matrix.
	 *
	 * @return the result of this addition, i.e. {@code a + b}.
	 */
	public static Mat4x3f add(Mat4x3f a, Mat4x3f b) {
		return new Mat4x3f(a).add(b);
	}

	/**
	 * Subtracts the specified matrices and returns the result as new matrix.
	 *
	 * @param a the matrix to subtract from.
	 * @param b the matrix to subtract.
	 *
	 * @return the result of this subtraction, i.e. {@code a - b}.
	 */
	public static Mat4x3f sub(Mat4x3f a, Mat4x3f b) {
		return new Mat4x3f(a).sub(b);
	}

	/**
	 * Multiplies the specified matrices and returns the result as new matrix.
	 *
	 * @param a the first matrix.
	 * @param b the second matrix.
	 *
	 * @return the result of this multiplication, i.e. {@code a * b}.
	 */
	public static Mat4x3f mul(Mat4x3f a, Mat4x3f b) {
		return new Mat4x3f(a).mul(b);
	}

	/**
	 * Multiplies the specified matrix with the specified vector in place, storing the result in the specified vector.
	 *
	 * @param m the matrix to multiply with.
	 * @param v the vector to multiply with.
	 *
	 * @return {@code v}.
	 */
	public static Vector mulInPlace(Mat4x3f m, Vector v) {
		double x = m.m00 * v.x() + m.m01 * v.y() + m.m02 * v.z() + m.m03 * v.w();
		double y = m.m10 * v.x() + m.m11 * v.y() + m.m12 * v.z() + m.m13 * v.w();
		v.z(m.m20 * v.x() + m.m21 * v.y() + m.m22 * v.z() + m.m23 * v.w());
		v.x(x);
		v.y(y);
		return v;
	}

	/**
	 * Multiplies the specified matrix with the specified scalar value and returns the result in a new matrix.
	 *
	 * @param m the matrix to multiply with.
	 * @param scalar the scalar value to multiply with.
	 *
	 * @return the result of this multiplication, i.e. {@code m * scalar}.
	 */
	public static Mat4x3f mul(Mat4x3f m, float scalar) {
		return new Mat4x3f(m).mul(scalar);
	}

	/**
	 * Applies the specified translation to the specified matrix as if by multiplying the matrix with the according
	 * translation matrix (i. e. {@code m * translation}).
	 *
	 * @param m the matrix to be transformed.
	 * @param v the vector specifying the translation.
	 *
	 * @return the result of this transformation.
	 */
	public static Mat4x3f translate(Mat4x3f m, Vec3f v) {
		return new Mat4x3f(m).translate(v);
	}

	/**
	 * Applies the specified translation to the specified matrix as if by multiplying the matrix with the according
	 * translation matrix (i. e. {@code m * translation}).
	 *
	 * @param m the matrix to be transformed.
	 * @param x the x-axis translation component.
	 * @param y the y-axis translation component.
	 * @param z the z-axis translation component.
	 *
	 * @return the result of this transformation.
	 */
	public static Mat4x3f translate(Mat4x3f m, float x, float y, float z) {
		return new Mat4x3f(m).translate(x, y, z);
	}

	/**
	 * Applies the specified scaling-operation to the specified matrix as if by multiplying this matrix with the
	 * according scale matrix (i. e. {@code m * scale}).
	 *
	 * @param m the matrix to be transformed.
	 * @param v the vector specifying the scale-transformation.
	 *
	 * @return the result of this transformation.
	 */
	public static Mat4x3f scale(Mat4x3f m, Vec3f v) {
		return new Mat4x3f(m).scale(v);
	}

	/**
	 * Applies the specified scaling-operation to the specified matrix as if by multiplying this matrix with the
	 * according scale matrix (i. e. {@code m * scale}).
	 *
	 * @param m the matrix to be transformed.
	 * @param x the x-axis scale component.
	 * @param y the y-axis scale component.
	 * @param z the z-axis scale component.
	 *
	 * @return the result of this transformation.
	 */
	public static Mat4x3f scale(Mat4x3f m, float x, float y, float z) {
		return new Mat4x3f(m).scale(x, y, z);
	}

	/**
	 * Applies the specified rotation-operation to the specified matrix as if by multiplying this matrix with the
	 * according rotation matrix (i. e. {@code m * rotate}).
	 *
	 * @param m the matrix to be transformed.
	 * @param axis the axis around which should be rotated.
	 * @param angle the angle (in radians) specifying the rotation.
	 *
	 * @return the result of this transformation.
	 */
	public static Mat4x3f rotate(Mat4x3f m, Vec3f axis, float angle) {
		return new Mat4x3f(m).rotate(axis, angle);
	}

	/**
	 * Applies the specified rotation-operation to the specified matrix as if by multiplying this matrix with the
	 * according rotation matrix (i. e. {@code m * rotate}).
	 *
	 * @param m the matrix to be transformed.
	 * @param x the x part of the axis around which should be rotated.
	 * @param y the y part of the axis around which should be rotated.
	 * @param z the z part of the axis around which should be rotated.
	 * @param angle the angle (in radians) specifying the rotation.
	 *
	 * @return the result of this transformation.
	 */
	public static Mat4x3f rotate(Mat4x3f m, float x, float y, float z, float angle) {
		return new Mat4x3f(m).rotate(x, y, z, angle);
	}

	/**
	 * Applies the specified {@code x}-axis rotation-operation to the specified matrix as if by multiplying this matrix
	 * with the according rotation matrix (i. e. {@code m * rotation}).
	 *
	 * @param m the matrix to be transformed.
	 * @param angle the angle (in radians) specifying the rotation around the {@code x}-axis.
	 *
	 * @return the result of this transformation.
	 */
	public static Mat4x3f rotateX(Mat4x3f m, float angle) {
		return new Mat4x3f(m).rotateX(angle);
	}

	/**
	 * Applies the specified {@code y}-axis rotation-operation to the specified matrix as if by multiplying this matrix
	 * with the according rotation matrix (i. e. {@code m * rotation}).
	 *
	 * @param m the matrix to be transformed.
	 * @param angle the angle (in radians) specifying the rotation around the {@code y}-axis.
	 *
	 * @return the result of this transformation.
	 */
	public static Mat4x3f rotateY(Mat4x3f m, float angle) {
		return new Mat4x3f(m).rotateY(angle);
	}

	/**
	 * Applies the specified {@code z}-axis rotation-operation to the specified matrix as if by multiplying this matrix
	 * with the according rotation matrix (i. e. {@code m * rotation}).
	 *
	 * @param m the matrix to be transformed.
	 * @param angle the angle (in radians) specifying the rotation around the {@code z}-axis.
	 *
	 * @return the result of this transformation.
	 */
	public static Mat4x3f rotateZ(Mat4x3f m, float angle) {
		return new Mat4x3f(m).rotateZ(angle);
	}

	/**
	 * Transposes the specified matrix.
	 *
	 * @param m the matrix to be transposed.
	 *
	 * @return the transposed matrix.
	 */
	public static Mat4x3f transpose(Mat4x3f m) {
		return new Mat4x3f(m).transpose();
	}

	/**
	 * Inverts the specified matrix and returns the result as new matrix.
	 *
	 * @param m the matrix to be transposed.
	 *
	 * @return the inverted matrix.
	 */
	public static Mat4x3f invert(Mat4x3f m) {
		return new Mat4x3f(m).invert();
	}

	/**
	 * Inverts the specified matrix as if it were an affine matrix and returns the result an new matrix.
	 *
	 * @param m the matrix to be transposed.
	 *
	 * @return the inverted matrix.
	 */
	public static Mat4x3f invertAffine(Mat4x3f m) {
		return new Mat4x3f(m).invertAffine();
	}

	public static Mat4x3f mix(Mat4x3f a, Mat4x3f b, float percentA, Mat4x3f store) {
		float percentB = 1 - percentA;

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
	public boolean equals(Object obj) {
		if (!(obj instanceof Mat4x3f)) return false;

		Mat4x3f other = (Mat4x3f) obj;
		return m00 == other.m00 && m01 == other.m01 && m02 == other.m02 && m03 == other.m03 && m10 == other.m10 && m11 == other.m11 && m12 == other.m12 && m13 == other.m13 && m20 == other.m20 && m21 == other.m21 && m22 == other.m22 && m23 == other.m23;
	}

	@Override
	public int hashCode() {
		return new Hasher().add(m00).add(m01).add(m02).add(m03).add(m10).add(m11).add(m12).add(m13).add(m20).add(m21).add(m22).add(m23).getHash();
	}

	@Override
	public String toString() {
		return "Mat4x3{" + m00 + "," + m01 + "," + m02 + "," + m03 + ",\n" + m10 + "," + m11 + "," + m12 + "," + m13 + ",\n" + m20 + "," + m21 + "," + m22 + "," + m23 + '}';
	}
}
