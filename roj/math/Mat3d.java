package roj.math;

import roj.util.Hasher;


/**
 * A row-major 3x3 {@code double} matrix.
 *
 * @author Maximilian Luz
 */
public class Mat3d {
	public double raw0, raw1, raw2, raw3, raw4, raw5, raw6, raw7, raw8;

	public Mat3d() {}

	/**
	 * Constructs a new matrix with the given properties.
	 */
	public Mat3d(double a11, double a12, double a13, double a21, double a22, double a23, double a31, double a32, double a33) {

		raw0 = a11;
		raw1 = a12;
		raw2 = a13;
		raw3 = a21;
		raw4 = a22;
		raw5 = a23;
		raw6 = a31;
		raw7 = a32;
		raw8 = a33;
	}

	/**
	 * Constructs a new matrix from the given array. The specified array is interpreted row-major wise.
	 *
	 * @param matrix the (at least) 16 element array from which the matrix should be created.
	 */
	public Mat3d(double[] matrix) {
		set(matrix);
	}

	/**
	 * Construct a new matrix by copying the specified one.
	 *
	 * @param matrix the matrix to be copied.
	 */
	public Mat3d(Mat3d matrix) {
		set(matrix);
	}

	/**
	 * Sets this matrix using the specified parameters.
	 *
	 * @return this matrix.
	 */
	public Mat3d set(double a11, double a12, double a13, double a21, double a22, double a23, double a31, double a32, double a33) {

		raw0 = a11;
		raw1 = a12;
		raw2 = a13;
		raw3 = a21;
		raw4 = a22;
		raw5 = a23;
		raw6 = a31;
		raw7 = a32;
		raw8 = a33;

		return this;
	}

	/**
	 * Sets this matrix using the given array. The specified array is interpreted row-major wise.
	 *
	 * @param raw the (at least) 16 element array from which the matrix should be created.
	 *
	 * @return this matrix.
	 */
	public Mat3d set(double[] raw) {
		raw0 = raw[0];
		raw1 = raw[1];
		raw2 = raw[2];
		raw3 = raw[3];
		raw4 = raw[4];
		raw5 = raw[5];
		raw6 = raw[6];
		raw7 = raw[7];
		raw8 = raw[8];
		return this;
	}

	/**
	 * Sets this matrix by copying the specified one.
	 *
	 * @param other the matrix to be copied.
	 *
	 * @return this matrix.
	 */
	public Mat3d set(Mat3d other) {
		raw0 = other.raw0;
		raw1 = other.raw1;
		raw2 = other.raw2;
		raw3 = other.raw3;
		raw4 = other.raw4;
		raw5 = other.raw5;
		raw6 = other.raw6;
		raw7 = other.raw7;
		raw8 = other.raw8;
		return this;
	}

	/**
	 * Sets this matrix to the identity-matrix.
	 *
	 * @return this matrix.
	 */
	public Mat3d makeIdentity() {
		raw0 = raw4 = raw8 = 1;
		raw1 = raw2 = raw3 = raw5 = raw6 = raw7 = 0;
		return this;
	}

	/**
	 * Calculates the determinant of this matrix.
	 *
	 * @return the calculated determinant.
	 */
	public double det() {
		return raw0 * (raw4 * raw8 - raw5 * raw7) - raw1 * (raw3 * raw8 - raw5 * raw6) + raw2 * (raw3 * raw7 - raw4 * raw6);
	}

	/**
	 * Tests whether this matrix is affine or not.
	 *
	 * @return {@code true} iff this matrix is affine.
	 */
	public boolean isAffine() {
		return raw6 == 0.0 && raw7 == 0.0 && raw8 == 1.0;
	}

	/**
	 * Adds the specified matrix to this matrix and stores the result in this matrix.
	 *
	 * @param other the matrix to add.
	 *
	 * @return this matrix.
	 */
	public Mat3d add(Mat3d other) {
		raw0 += other.raw0;
		raw1 += other.raw1;
		raw2 += other.raw2;
		raw3 += other.raw3;
		raw4 += other.raw4;
		raw5 += other.raw5;
		raw6 += other.raw6;
		raw7 += other.raw7;
		raw8 += other.raw8;
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
		raw0 -= other.raw0;
		raw1 -= other.raw1;
		raw2 -= other.raw2;
		raw3 -= other.raw3;
		raw4 -= other.raw4;
		raw5 -= other.raw5;
		raw6 -= other.raw6;
		raw7 -= other.raw7;
		raw8 -= other.raw8;
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
		set(new double[] {raw0 * other.raw0 + raw1 * other.raw3 + raw2 * other.raw6, raw0 * other.raw1 + raw1 * other.raw4 + raw2 * other.raw7,
						  raw0 * other.raw2 + raw1 * other.raw5 + raw2 * other.raw8, raw3 * other.raw0 + raw4 * other.raw3 + raw5 * other.raw6,
						  raw3 * other.raw1 + raw4 * other.raw4 + raw5 * other.raw7, raw3 * other.raw2 + raw4 * other.raw5 + raw5 * other.raw8,
						  raw6 * other.raw0 + raw7 * other.raw3 + raw8 * other.raw6, raw6 * other.raw1 + raw7 * other.raw4 + raw8 * other.raw7,
						  raw6 * other.raw2 + raw7 * other.raw5 + raw8 * other.raw8});

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
		return new Vec3d(raw0 * v.x + raw1 * v.y + raw2 * v.z, raw3 * v.x + raw4 * v.y + raw5 * v.z, raw6 * v.x + raw7 * v.y + raw8 * v.z);
	}

	/**
	 * Multiplies this matrix with the specified scalar value.
	 *
	 * @param scalar the scalar value to multiply with.
	 *
	 * @return this matrix.
	 */
	public Mat3d mul(double scalar) {
		raw0 *= scalar;
		raw1 *= scalar;
		raw2 *= scalar;
		raw3 *= scalar;
		raw4 *= scalar;
		raw5 *= scalar;
		raw6 *= scalar;
		raw7 *= scalar;
		raw8 *= scalar;
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
		raw2 = raw0 * x + raw1 * y + raw2;
		raw5 = raw3 * x + raw4 * y + raw5;
		raw8 = raw6 * x + raw7 * y + raw8;
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
		this.raw0 *= sx;
		this.raw1 *= sy;
		this.raw3 *= sx;
		this.raw4 *= sy;
		this.raw6 *= sx;
		this.raw7 *= sy;
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

		double u = raw0, v = raw1;
		raw0 = u * c + v * s;
		raw1 = -u * s + v * c;

		u = raw3;
		v = raw4;
		raw3 = u * c + v * s;
		raw4 = -u * s + v * c;

		u = raw6;
		v = raw7;
		raw6 = u * c + v * s;
		raw7 = -u * s + v * c;

		return this;
	}

	/**
	 * Transposes this matrix.
	 *
	 * @return this matrix.
	 */
	public Mat3d transpose() {
		double swp;

		swp = this.raw1;
		this.raw1 = this.raw3;
		this.raw3 = swp;

		swp = this.raw2;
		this.raw2 = this.raw6;
		this.raw6 = swp;

		swp = this.raw5;
		this.raw5 = this.raw7;
		this.raw7 = swp;

		return this;
	}

	/**
	 * Inverts this matrix.
	 *
	 * @return this matrix.
	 */
	public Mat3d invert() {
		if (isAffine()) {return invertAffine();} else return invertGeneral();
	}

	/**
	 * Inverts this matrix as if it were an affine matrix.
	 *
	 * @return this matrix.
	 */
	public Mat3d invertAffine() {
		// calculate determinant of upper left 2x2 matrix
		double det = 1.0 / (raw0 * raw4 - raw1 * raw3);
		if (det == 0) return null;

		// calculate inverse of upper 2x2 matrix
		double m0 = raw4 / det;
		double m1 = -raw1 / det;
		double m2 = -raw3 / det;
		double m3 = raw0 / det;

		raw0 = m0;
		raw1 = m1;

		raw3 = m2;
		raw4 = m3;

		// calculate product of inverse upper 2x2 matrix and translation part
		double u = -raw2, v = -raw5;
		raw2 = m0 * u + m1 * v; // tx
		raw5 = m2 * u + m2 * v; // ty

		raw6 = raw7 = 0;
		raw8 = 1;

		return this;
	}

	/**
	 * Inverts this matrix. See {@link Mat3d#invert()} for a possibly more performant version, depending on the content
	 * of the matrix.
	 *
	 * @return this matrix.
	 */
	public Mat3d invertGeneral() {
		double[] tmp = new double[] {raw4 * raw8 - raw5 * raw7, raw2 * raw7 - raw1 * raw8, raw1 * raw5 - raw2 * raw4,
									 raw5 * raw6 - raw3 * raw8, raw0 * raw8 - raw2 * raw6, raw2 * raw3 - raw0 * raw5,
									 raw3 * raw7 - raw4 * raw6, raw1 * raw6 - raw0 * raw7, raw0 * raw4 - raw1 * raw3};

		double det = raw0 * tmp[0] + raw1 * (-tmp[3]) + raw2 * tmp[6];
		if (det == 0) return null;

		for (int i = 0; i < 9; i++)
			tmp[i] /= det;

		set(tmp);

		return this;
	}

	/**
	 * Creates a new matrix-instance containing the identity-matrix.
	 *
	 * @return a new identity matrix.
	 */
	public static Mat3d identity() {
		return new Mat3d().makeIdentity();
	}

	/**
	 * Adds the specified matrices and returns the result as new matrix.
	 *
	 * @param a the first matrix.
	 * @param b the second matrix.
	 *
	 * @return the result of this addition, i.e. {@code a + b}.
	 */
	public static Mat3d add(Mat3d a, Mat3d b) {
		return new Mat3d(a).add(b);
	}

	/**
	 * Subtracts the specified matrices and returns the result as new matrix.
	 *
	 * @param a the matrix to subtract from.
	 * @param b the matrix to subtract.
	 *
	 * @return the result of this subtraction, i.e. {@code a - b}.
	 */
	public static Mat3d sub(Mat3d a, Mat3d b) {
		return new Mat3d(a).sub(b);
	}

	/**
	 * Multiplies the specified matrices and returns the result as new matrix.
	 *
	 * @param a the first matrix.
	 * @param b the second matrix.
	 *
	 * @return the result of this multiplication, i.e. {@code a * b}.
	 */
	public static Mat3d mul(Mat3d a, Mat3d b) {
		return new Mat3d(a).mul(b);
	}

	/**
	 * Multiplies the specified matrix with the specified vector in place, storing the result in the specified vector.
	 *
	 * @param m the matrix to multiply with.
	 * @param v the vector to multiply with.
	 *
	 * @return {@code v}.
	 */
	public static Vec3d mulInPlace(Mat3d m, Vec3d v) {
		double x = m.raw0 * v.x + m.raw1 * v.y + m.raw2 * v.z;
		double y = m.raw3 * v.x + m.raw4 * v.y + m.raw5 * v.z;
		double z = m.raw6 * v.x + m.raw7 * v.y + m.raw8 * v.z;

		v.x = x;
		v.y = y;
		v.z = z;

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
	public static Mat3d mul(Mat3d m, double scalar) {
		return new Mat3d(m).mul(scalar);
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
	public static Mat3d translate(Mat3d m, Vec2d v) {
		return new Mat3d(m).translate(v);
	}

	/**
	 * Applies the specified translation to the specified matrix as if by multiplying the matrix with the according
	 * translation matrix (i. e. {@code m * translation}).
	 *
	 * @param m the matrix to be transformed.
	 * @param x the x-axis translation component.
	 * @param y the y-axis translation component.
	 *
	 * @return the result of this transformation.
	 */
	public static Mat3d translate(Mat3d m, double x, double y) {
		return new Mat3d(m).translate(x, y);
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
	public static Mat3d scale(Mat3d m, Vec2d v) {
		return new Mat3d(m).scale(v);
	}

	/**
	 * Applies the specified scaling-operation to the specified matrix as if by multiplying this matrix with the
	 * according scale matrix (i. e. {@code m * scale}).
	 *
	 * @param m the matrix to be transformed.
	 * @param x the x-axis scale component.
	 * @param y the y-axis scale component.
	 *
	 * @return the result of this transformation.
	 */
	public static Mat3d scale(Mat3d m, double x, double y) {
		return new Mat3d(m).scale(x, y);
	}

	/**
	 * Applies the specified rotation to the specified matrix as if by multiplying this matrix with the
	 * according rotation matrix (i. e. {@code m * rotate}).
	 *
	 * @param m the matrix to be transformed.
	 * @param angle the angle (in radians) specifying the rotation.
	 *
	 * @return the result of this transformation.
	 */
	public static Mat3d rotate(Mat3d m, double angle) {
		return new Mat3d(m).rotate(angle);
	}

	/**
	 * Transposes the specified matrix.
	 *
	 * @param m the matrix to be transposed.
	 *
	 * @return the transposed matrix.
	 */
	public static Mat3d transpose(Mat3d m) {
		return new Mat3d(m).transpose();
	}

	/**
	 * Inverts the specified matrix and returns the result as new matrix.
	 *
	 * @param m the matrix to be transposed.
	 *
	 * @return the inverted matrix.
	 */
	public static Mat3d invert(Mat3d m) {
		return new Mat3d(m).invert();
	}

	/**
	 * Inverts the specified matrix as if it were an affine matrix and returns the result an new matrix.
	 *
	 * @param m the matrix to be transposed.
	 *
	 * @return the inverted matrix.
	 */
	public static Mat3d invertAffine(Mat3d m) {
		return new Mat3d(m).invertAffine();
	}

	/**
	 * Inverts the specified matrix and returns the result as new matrix. See {@link Mat3d#invert(Mat3d)} for a
	 * possibly more efficient version, depending on the contents of the matrix.
	 *
	 * @param m the matrix to be transposed.
	 *
	 * @return the inverted matrix.
	 */
	public static Mat3d invertGeneral(Mat3d m) {
		return new Mat3d(m).invertGeneral();
	}


	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Mat3d)) return false;

		Mat3d other = (Mat3d) obj;
		return this.raw0 == other.raw0 && this.raw1 == other.raw1 && this.raw2 == other.raw2 && this.raw3 == other.raw3 && this.raw4 == other.raw4 && this.raw5 == other.raw5 && this.raw6 == other.raw6 && this.raw7 == other.raw7 && this.raw8 == other.raw8;
	}

	@Override
	public int hashCode() {
		return new Hasher().add(this.raw0).add(this.raw1).add(this.raw2).add(this.raw3).add(this.raw4).add(this.raw5).add(this.raw6).add(this.raw7).add(this.raw8).getHash();
	}
}