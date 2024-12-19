package roj.math;

import roj.util.Hasher;


/**
 * A row-major 3x3 {@code double} matrix.
 *
 * @author Maximilian Luz
 */
public class Mat3d {
	public double m00, m01, m02, m10, m11, m12, m20, m21, m22;

	public Mat3d() {m00 = m11 = m22 = 1;}
	public Mat3d(double a11, double a12, double a13,
				 double a21, double a22, double a23,
				 double a31, double a32, double a33) {
		m00 = a11;
		m01 = a12;
		m02 = a13;
		m10 = a21;
		m11 = a22;
		m12 = a23;
		m20 = a31;
		m21 = a32;
		m22 = a33;
	}
	public Mat3d(double[] matrix) {set(matrix);}
	public Mat3d(Mat3d matrix) {set(matrix);}

	public Mat3d set(double a11, double a12, double a13,
					 double a21, double a22, double a23,
					 double a31, double a32, double a33) {
		m00 = a11;
		m01 = a12;
		m02 = a13;
		m10 = a21;
		m11 = a22;
		m12 = a23;
		m20 = a31;
		m21 = a32;
		m22 = a33;
		return this;
	}

	public Mat3d set(double[] raw) {
		m00 = raw[0];
		m01 = raw[1];
		m02 = raw[2];
		m10 = raw[3];
		m11 = raw[4];
		m12 = raw[5];
		m20 = raw[6];
		m21 = raw[7];
		m22 = raw[8];
		return this;
	}

	public Mat3d set(Mat3d other) {
		m00 = other.m00;
		m01 = other.m01;
		m02 = other.m02;
		m10 = other.m10;
		m11 = other.m11;
		m12 = other.m12;
		m20 = other.m20;
		m21 = other.m21;
		m22 = other.m22;
		return this;
	}

	public Mat3d makeIdentity() {
		m00 = m11 = m22 = 1;
		m01 = m02 = m10 = m12 = m20 = m21 = 0;
		return this;
	}

	/**
	 * Calculates the determinant of this matrix.
	 *
	 * @return the calculated determinant.
	 */
	public double det() {return m00 * (m11 * m22 - m12 * m21) - m01 * (m10 * m22 - m12 * m20) + m02 * (m10 * m21 - m11 * m20);}

	/**
	 * Tests whether this matrix is affine or not.
	 *
	 * @return {@code true} iff this matrix is affine.
	 */
	public boolean isAffine() {return m20 == 0.0 && m21 == 0.0 && m22 == 1.0;}

	/**
	 * Adds the specified matrix to this matrix and stores the result in this matrix.
	 *
	 * @param other the matrix to add.
	 *
	 * @return this matrix.
	 */
	public Mat3d add(Mat3d other) {
		m00 += other.m00;
		m01 += other.m01;
		m02 += other.m02;
		m10 += other.m10;
		m11 += other.m11;
		m12 += other.m12;
		m20 += other.m20;
		m21 += other.m21;
		m22 += other.m22;
		return this;
	}

	/**
	 * Subtracts the specified matrix from this matrix and stores the result in this matrix.
	 *
	 * @param other the matrix to subtract.
	 *
	 * @return this matrix.
	 */
	public Mat3d sub(Mat3d other) {
		m00 -= other.m00;
		m01 -= other.m01;
		m02 -= other.m02;
		m10 -= other.m10;
		m11 -= other.m11;
		m12 -= other.m12;
		m20 -= other.m20;
		m21 -= other.m21;
		m22 -= other.m22;
		return this;
	}

	/**
	 * Multiplies this matrix with the specified matrix, i.e. {@code this * other}.
	 *
	 * @param other the matrix to multiply with.
	 *
	 * @return this matrix.
	 */
	public Mat3d mul(Mat3d other) {
		set(m00 * other.m00 + m01 * other.m10 + m02 * other.m20,
			m00 * other.m01 + m01 * other.m11 + m02 * other.m21,
			m00 * other.m02 + m01 * other.m12 + m02 * other.m22,
			m10 * other.m00 + m11 * other.m10 + m12 * other.m20,
			m10 * other.m01 + m11 * other.m11 + m12 * other.m21,
			m10 * other.m02 + m11 * other.m12 + m12 * other.m22,
			m20 * other.m00 + m21 * other.m10 + m22 * other.m20,
			m20 * other.m01 + m21 * other.m11 + m22 * other.m21,
			m20 * other.m02 + m21 * other.m12 + m22 * other.m22);
		return this;
	}

	/**
	 * Multiplies this matrix with the specified vector.
	 *
	 * @param v the vector to multiply with.
	 *
	 * @return the product of this matrix and the specified vectors.
	 */
	public Vec3d mul(Vec3d v) {
		return new Vec3d(m00 * v.x + m01 * v.y + m02 * v.z, m10 * v.x + m11 * v.y + m12 * v.z, m20 * v.x + m21 * v.y + m22 * v.z);
	}

	/**
	 * Multiplies this matrix with the specified scalar value.
	 *
	 * @param scalar the scalar value to multiply with.
	 *
	 * @return this matrix.
	 */
	public Mat3d mul(double scalar) {
		m00 *= scalar;
		m01 *= scalar;
		m02 *= scalar;
		m10 *= scalar;
		m11 *= scalar;
		m12 *= scalar;
		m20 *= scalar;
		m21 *= scalar;
		m22 *= scalar;
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
	public Mat3d translate(Vec2d vec) {
		return translate(vec.x, vec.y);
	}

	/**
	 * Applies the specified translation to this matrix as if by multiplying this matrix with the according translation
	 * matrix (i. e. {@code this * translation}).
	 *
	 * @param x the x-axis translation component.
	 * @param y the y-axis translation component.
	 *
	 * @return this matrix.
	 */
	public Mat3d translate(double x, double y) {
		m02 = m00 * x + m01 * y + m02;
		m12 = m10 * x + m11 * y + m12;
		m22 = m20 * x + m21 * y + m22;
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
	public Mat3d scale(Vec2d vec) {
		return scale(vec.x, vec.y);
	}

	/**
	 * Applies the specified scaling-operation to this matrix as if by multiplying this matrix with the according
	 * scale matrix (i. e. {@code this * scale}).
	 *
	 * @param sx the x-axis scale component.
	 * @param sy the y-axis scale component.
	 *
	 * @return this matrix.
	 */
	public Mat3d scale(double sx, double sy) {
		this.m00 *= sx;
		this.m01 *= sy;
		this.m10 *= sx;
		this.m11 *= sy;
		this.m20 *= sx;
		this.m21 *= sy;
		return this;
	}

	/**
	 * Applies the specified rotation this matrix as if by multiplying this matrix with the according
	 * rotation matrix (i. e. {@code this * rotation}).
	 *
	 * @param angle the angle (in radians) specifying the rotation.
	 *
	 * @return this matrix.
	 */
	public Mat3d rotate(double angle) {
		double s = Math.sin(Math.toRadians(angle));
		double c = Math.cos(Math.toRadians(angle));

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
	 * Transposes this matrix.
	 *
	 * @return this matrix.
	 */
	public Mat3d transpose() {
		double swp;

		swp = this.m01;
		this.m01 = this.m10;
		this.m10 = swp;

		swp = this.m02;
		this.m02 = this.m20;
		this.m20 = swp;

		swp = this.m12;
		this.m12 = this.m21;
		this.m21 = swp;

		return this;
	}

	/**
	 * Inverts this matrix.
	 *
	 * @return this matrix.
	 */
	public Mat3d invert() {return isAffine() ? invertAffine() : invertGeneral();}

	/**
	 * Inverts this matrix as if it were an affine matrix.
	 *
	 * @return this matrix.
	 */
	public Mat3d invertAffine() {
		// calculate determinant of upper left 2x2 matrix
		double det = 1.0 / (m00 * m11 - m01 * m10);
		if (det == 0) return null;

		// calculate inverse of upper 2x2 matrix
		double m0 = m11 / det;
		double m1 = -m01 / det;
		double m2 = -m10 / det;
		double m3 = m00 / det;

		m00 = m0;
		m01 = m1;

		m10 = m2;
		m11 = m3;

		// calculate product of inverse upper 2x2 matrix and translation part
		double u = -m02, v = -m12;
		m02 = m0 * u + m1 * v; // tx
		m12 = m2 * u + m2 * v; // ty

		m20 = m21 = 0;
		m22 = 1;

		return this;
	}

	/**
	 * Inverts this matrix. See {@link Mat3d#invert()} for a possibly more performant version, depending on the content
	 * of the matrix.
	 *
	 * @return this matrix.
	 */
	public Mat3d invertGeneral() {
		double[] tmp = new double[] {m11 * m22 - m12 * m21, m02 * m21 - m01 * m22, m01 * m12 - m02 * m11,
									 m12 * m20 - m10 * m22, m00 * m22 - m02 * m20, m02 * m10 - m00 * m12,
									 m10 * m21 - m11 * m20, m01 * m20 - m00 * m21, m00 * m11 - m01 * m10};

		double det = m00 * tmp[0] + m01 * (-tmp[3]) + m02 * tmp[6];
		if (det == 0) return null;

		for (int i = 0; i < 9; i++)
			tmp[i] /= det;

		set(tmp);

		return this;
	}

	public static Mat3d identity() {return new Mat3d();}
	/**
	 * @see #add(Mat3d)
	 */
	public static Mat3d add(Mat3d a, Mat3d b) {return new Mat3d(a).add(b);}
	/**
	 * @see #sub(Mat3d)
	 */
	public static Mat3d sub(Mat3d a, Mat3d b) {return new Mat3d(a).sub(b);}
	/**
	 * @see #mul(Mat3d)
	 */
	public static Mat3d mul(Mat3d a, Mat3d b) {return new Mat3d(a).mul(b);}
	/**
	 * Multiplies the specified matrix with the specified vector in place, storing the result in the specified vector.
	 *
	 * @param m the matrix to multiply with.
	 * @param v the vector to multiply with.
	 *
	 * @return {@code v}.
	 */
	public static Vec3d mulInPlace(Mat3d m, Vec3d v) {
		double x = m.m00 * v.x + m.m01 * v.y + m.m02 * v.z;
		double y = m.m10 * v.x + m.m11 * v.y + m.m12 * v.z;
		double z = m.m20 * v.x + m.m21 * v.y + m.m22 * v.z;

		v.x = x;
		v.y = y;
		v.z = z;

		return v;
	}
	/**
	 * @see #mul(double)
	 */
	public static Mat3d mul(Mat3d m, double scalar) {return new Mat3d(m).mul(scalar);}

	/**
	 * @see #translate(Vec2d)
	 */
	public static Mat3d translate(Mat3d m, Vec2d v) {return new Mat3d(m).translate(v);}
	/**
	 * @see #translate(double, double)
	 */
	public static Mat3d translate(Mat3d m, double x, double y) {return new Mat3d(m).translate(x, y);}
	/**
	 * @see #scale(Vec2d)
	 */
	public static Mat3d scale(Mat3d m, Vec2d v) {return new Mat3d(m).scale(v);}
	/**
	 * @see #scale(double, double)
	 */
	public static Mat3d scale(Mat3d m, double x, double y) {return new Mat3d(m).scale(x, y);}
	/**
	 * @see #rotate(double)
	 */
	public static Mat3d rotate(Mat3d m, double angle) {return new Mat3d(m).rotate(angle);}

	/**
	 * @see #transpose()
	 */
	public static Mat3d transpose(Mat3d m) {return new Mat3d(m).transpose();}
	/**
	 * @see #invert()
	 */
	public static Mat3d invert(Mat3d m) {return new Mat3d(m).invert();}
	/**
	 * @see #invertAffine()
	 */
	public static Mat3d invertAffine(Mat3d m) {return new Mat3d(m).invertAffine();}
	/**
	 * @see #invertGeneral()
	 */
	public static Mat3d invertGeneral(Mat3d m) {return new Mat3d(m).invertGeneral();}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Mat3d d)) return false;

		if (Double.compare(m00, d.m00) != 0) return false;
		if (Double.compare(m01, d.m01) != 0) return false;
		if (Double.compare(m02, d.m02) != 0) return false;
		if (Double.compare(m10, d.m10) != 0) return false;
		if (Double.compare(m11, d.m11) != 0) return false;
		if (Double.compare(m12, d.m12) != 0) return false;
		if (Double.compare(m20, d.m20) != 0) return false;
		if (Double.compare(m21, d.m21) != 0) return false;
		return Double.compare(m22, d.m22) == 0;
	}
	@Override
	public int hashCode() {return new Hasher().add(m00).add(m01).add(m02).add(m10).add(m11).add(m12).add(m20).add(m21).add(m22).getHash();}
}