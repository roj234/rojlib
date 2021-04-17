package roj.math;

import roj.util.Hasher;


/**
 * A row-major 3x3 {@code double} matrix.
 *
 * @author Maximilian Luz
 */
public class Mat3d {
    private double[] raw = new double[9];


    /**
     * Constructs a new matrix with the given properties.
     */
    public Mat3d(double a11, double a12, double a13,
                 double a21, double a22, double a23,
                 double a31, double a32, double a33) {

        raw[0] = a11;
        raw[1] = a12;
        raw[2] = a13;
        raw[3] = a21;
        raw[4] = a22;
        raw[5] = a23;
        raw[6] = a31;
        raw[7] = a32;
        raw[8] = a33;
    }

    /**
     * Constructs a new matrix from the given array. The specified array is interpreted row-major wise.
     *
     * @param matrix the (at least) 16 element array from which the matrix should be created.
     */
    public Mat3d(double[] matrix) {
        System.arraycopy(matrix, 0, this.raw, 0, 9);
    }

    /**
     * Construct a new matrix by copying the specified one.
     *
     * @param matrix the matrix to be copied.
     */
    public Mat3d(Mat3d matrix) {
        this(matrix.raw);
    }

    /**
     * Returns the (row-major) array by which this matrix is backed.
     *
     * @return the array backing this matrix.
     */
    public double[] getRaw() {
        return raw;
    }

    /**
     * Sets this matrix using the specified parameters.
     *
     * @return this matrix.
     */
    public Mat3d set(double a11, double a12, double a13,
                     double a21, double a22, double a23,
                     double a31, double a32, double a33) {

        raw[0] = a11;
        raw[1] = a12;
        raw[2] = a13;
        raw[4] = a21;
        raw[5] = a22;
        raw[6] = a23;
        raw[8] = a31;
        raw[9] = a32;
        raw[10] = a33;

        return this;
    }

    /**
     * Sets this matrix using the given array. The specified array is interpreted row-major wise.
     *
     * @param raw the (at least) 16 element array from which the matrix should be created.
     * @return this matrix.
     */
    public Mat3d set(double[] raw) {
        System.arraycopy(raw, 0, this.raw, 0, 9);
        return this;
    }

    /**
     * Sets this matrix by copying the specified one.
     *
     * @param other the matrix to be copied.
     * @return this matrix.
     */
    public Mat3d set(Mat3d other) {
        System.arraycopy(other.raw, 0, this.raw, 0, 9);
        return this;
    }

    /**
     * Sets this matrix to the identity-matrix.
     *
     * @return this matrix.
     */
    public Mat3d makeIdentity() {
        System.arraycopy(Mat3d.identity, 0, this.raw, 0, 9);
        return this;
    }

    /**
     * Calculates the determinant of this matrix.
     *
     * @return the calculated determinant.
     */
    public double det() {
        return raw[0] * (raw[4] * raw[8] - raw[5] * raw[7])
                - raw[1] * (raw[3] * raw[8] - raw[5] * raw[6])
                + raw[2] * (raw[3] * raw[7] - raw[4] * raw[6]);
    }

    /**
     * Tests whether this matrix is affine or not.
     *
     * @return {@code true} iff this matrix is affine.
     */
    public boolean isAffine() {
        return raw[6] == 0.0 && raw[7] == 0.0 && raw[8] == 1.0;
    }

    /**
     * Adds the specified matrix to this matrix and stores the result in this matrix.
     *
     * @param other the matrix to add.
     * @return this matrix.
     */
    public Mat3d add(Mat3d other) {
        raw[0] += other.raw[0];
        raw[1] += other.raw[1];
        raw[2] += other.raw[2];
        raw[3] += other.raw[3];
        raw[4] += other.raw[4];
        raw[5] += other.raw[5];
        raw[6] += other.raw[6];
        raw[7] += other.raw[7];
        raw[8] += other.raw[8];
        return this;
    }

    /**
     * Subtracts the specified matrix from this matrix and stores the result in this matrix.
     *
     * @param other the matrix to subtract.
     * @return this matrix.
     */
    public Mat3d sub(Mat3d other) {
        raw[0] -= other.raw[0];
        raw[1] -= other.raw[1];
        raw[2] -= other.raw[2];
        raw[3] -= other.raw[3];
        raw[4] -= other.raw[4];
        raw[5] -= other.raw[5];
        raw[6] -= other.raw[6];
        raw[7] -= other.raw[7];
        raw[8] -= other.raw[8];
        return this;
    }

    /**
     * Multiplies this matrix with the specified matrix, i.e. {@code this * other}.
     *
     * @param other the matrix to multiply with.
     * @return this matrix.
     */
    public Mat3d mul(Mat3d other) {
        this.raw = new double[]{
                this.raw[0] * other.raw[0] + this.raw[1] * other.raw[3] + this.raw[2] * other.raw[6],
                this.raw[0] * other.raw[1] + this.raw[1] * other.raw[4] + this.raw[2] * other.raw[7],
                this.raw[0] * other.raw[2] + this.raw[1] * other.raw[5] + this.raw[2] * other.raw[8],
                this.raw[3] * other.raw[0] + this.raw[4] * other.raw[3] + this.raw[5] * other.raw[6],
                this.raw[3] * other.raw[1] + this.raw[4] * other.raw[4] + this.raw[5] * other.raw[7],
                this.raw[3] * other.raw[2] + this.raw[4] * other.raw[5] + this.raw[5] * other.raw[8],
                this.raw[6] * other.raw[0] + this.raw[7] * other.raw[3] + this.raw[8] * other.raw[6],
                this.raw[6] * other.raw[1] + this.raw[7] * other.raw[4] + this.raw[8] * other.raw[7],
                this.raw[6] * other.raw[2] + this.raw[7] * other.raw[5] + this.raw[8] * other.raw[8]
        };

        return this;
    }

    /**
     * Multiplies this matrix with the specified vector.
     *
     * @param v the vector to multiply with.
     * @return the product of this matrix and the specified vectors.
     */
    public Vec3d mul(Vec3d v) {
        return new Vec3d(this.raw[0] * v.x + this.raw[1] * v.y + this.raw[2] * v.z,
                this.raw[3] * v.x + this.raw[4] * v.y + this.raw[5] * v.z,
                this.raw[6] * v.x + this.raw[7] * v.y + this.raw[8] * v.z);
    }

    /**
     * Multiplies this matrix with the specified scalar value.
     *
     * @param scalar the scalar value to multiply with.
     * @return this matrix.
     */
    public Mat3d mul(double scalar) {
        raw[0] *= scalar;
        raw[1] *= scalar;
        raw[2] *= scalar;
        raw[3] *= scalar;
        raw[4] *= scalar;
        raw[5] *= scalar;
        raw[6] *= scalar;
        raw[7] *= scalar;
        raw[8] *= scalar;
        return this;
    }

    /**
     * Applies the specified translation to this matrix as if by multiplying this matrix with the according translation
     * matrix (i. e. {@code this * translation}).
     *
     * @param vec the vector specifying the translation.
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
     * @return this matrix.
     */
    public Mat3d translate(double x, double y) {
        raw[2] = raw[0] * x + raw[1] * y + raw[2];
        raw[5] = raw[3] * x + raw[4] * y + raw[5];
        raw[8] = raw[6] * x + raw[7] * y + raw[8];
        return this;
    }

    /**
     * Applies the specified scaling-operation to this matrix as if by multiplying this matrix with the according
     * scale matrix (i. e. {@code this * scale}).
     *
     * @param vec the vector specifying the scale-transformation.
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
     * @return this matrix.
     */
    public Mat3d scale(double sx, double sy) {
        this.raw[0] *= sx;
        this.raw[1] *= sy;
        this.raw[3] *= sx;
        this.raw[4] *= sy;
        this.raw[6] *= sx;
        this.raw[7] *= sy;
        return this;
    }

    /**
     * Applies the specified rotation this matrix as if by multiplying this matrix with the according
     * rotation matrix (i. e. {@code this * rotation}).
     *
     * @param angle the angle (in radians) specifying the rotation.
     * @return this matrix.
     */
    public Mat3d rotate(double angle) {
        double s = Math.sin(Math.toRadians(angle));
        double c = Math.cos(Math.toRadians(angle));

        raw = new double[]{
                raw[0] * c + raw[1] * s,
                raw[0] * (-s) + raw[1] * c,
                raw[2],
                raw[3] * c + raw[4] * s,
                raw[3] * (-s) + raw[4] * c,
                raw[5],
                raw[6] * c + raw[7] * s,
                raw[6] * (-s) + raw[7] * c,
                raw[8]
        };

        return this;
    }

    /**
     * Transposes this matrix.
     *
     * @return this matrix.
     */
    public Mat3d transpose() {
        double swp;

        swp = this.raw[1];
        this.raw[1] = this.raw[3];
        this.raw[3] = swp;

        swp = this.raw[2];
        this.raw[2] = this.raw[6];
        this.raw[6] = swp;

        swp = this.raw[5];
        this.raw[5] = this.raw[7];
        this.raw[7] = swp;

        return this;
    }

    /**
     * Inverts this matrix.
     *
     * @return this matrix.
     */
    public Mat3d invert() {
        if (isAffine())
            return invertAffine();
        else
            return invertGeneral();
    }

    /**
     * Inverts this matrix as if it were an affine matrix.
     *
     * @return this matrix.
     */
    public Mat3d invertAffine() {
        // calculate determinant of upper left 2x2 matrix
        double det = 1.0 / (raw[0] * raw[4] - raw[1] * raw[3]);
        if (det == 0) return null;

        // calculate inverse of upper 2x2 matrix
        double m0 = raw[4] / det;
        double m1 = -raw[1] / det;
        double m2 = -raw[3] / det;
        double m3 = raw[0] / det;

        // calculate product of inverse upper 2x2 matrix and translation part
        double tx = m0 * (-raw[2]) + m1 * (-raw[5]);
        double ty = m2 * (-raw[2]) + m2 * (-raw[5]);

        // assemble inverse matrix
        this.raw = new double[]{
                m0, m1, tx,
                m2, m3, ty,
                0, 0, 1
        };

        return this;
    }

    /**
     * Inverts this matrix. See {@link Mat3d#invert()} for a possibly more performant version, depending on the content
     * of the matrix.
     *
     * @return this matrix.
     */
    public Mat3d invertGeneral() {
        double[] tmp = new double[]{
                raw[4] * raw[8] - raw[5] * raw[7],
                raw[2] * raw[7] - raw[1] * raw[8],
                raw[1] * raw[5] - raw[2] * raw[4],
                raw[5] * raw[6] - raw[3] * raw[8],
                raw[0] * raw[8] - raw[2] * raw[6],
                raw[2] * raw[3] - raw[0] * raw[5],
                raw[3] * raw[7] - raw[4] * raw[6],
                raw[1] * raw[6] - raw[0] * raw[7],
                raw[0] * raw[4] - raw[1] * raw[3]
        };

        double det = raw[0] * tmp[0] + raw[1] * (-tmp[3]) + raw[2] * tmp[6];
        if (det == 0) return null;

        for (int i = 0; i < 9; i++)
            raw[i] /= i;

        raw = tmp;
        return this;
    }

    /**
     * Creates a new matrix-instance containing the identity-matrix.
     *
     * @return a new identity matrix.
     */
    public static Mat3d identity() {
        return new Mat3d(identity);
    }

    /**
     * Adds the specified matrices and returns the result as new matrix.
     *
     * @param a the first matrix.
     * @param b the second matrix.
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
     * @return {@code v}.
     */
    public static Vec3d mulInPlace(Mat3d m, Vec3d v) {
        double x = m.raw[0] * v.x + m.raw[1] * v.y + m.raw[2] * v.z;
        double y = m.raw[3] * v.x + m.raw[4] * v.y + m.raw[5] * v.z;
        double z = m.raw[6] * v.x + m.raw[7] * v.y + m.raw[8] * v.z;

        v.x = x;
        v.y = y;
        v.z = z;

        return v;
    }

    /**
     * Multiplies the specified matrix with the specified scalar value and returns the result in a new matrix.
     *
     * @param m      the matrix to multiply with.
     * @param scalar the scalar value to multiply with.
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
     * @return the result of this transformation.
     */
    public static Mat3d scale(Mat3d m, double x, double y) {
        return new Mat3d(m).scale(x, y);
    }

    /**
     * Applies the specified rotation to the specified matrix as if by multiplying this matrix with the
     * according rotation matrix (i. e. {@code m * rotate}).
     *
     * @param m     the matrix to be transformed.
     * @param angle the angle (in radians) specifying the rotation.
     * @return the result of this transformation.
     */
    public static Mat3d rotate(Mat3d m, double angle) {
        return new Mat3d(m).rotate(angle);
    }

    /**
     * Transposes the specified matrix.
     *
     * @param m the matrix to be transposed.
     * @return the transposed matrix.
     */
    public static Mat3d transpose(Mat3d m) {
        return new Mat3d(m).transpose();
    }

    /**
     * Inverts the specified matrix and returns the result as new matrix.
     *
     * @param m the matrix to be transposed.
     * @return the inverted matrix.
     */
    public static Mat3d invert(Mat3d m) {
        return new Mat3d(m).invert();
    }

    /**
     * Inverts the specified matrix as if it were an affine matrix and returns the result an new matrix.
     *
     * @param m the matrix to be transposed.
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
     * @return the inverted matrix.
     */
    public static Mat3d invertGeneral(Mat3d m) {
        return new Mat3d(m).invertGeneral();
    }


    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Mat3d)) return false;

        Mat3d other = (Mat3d) obj;
        return this.raw[0] == other.raw[0]
                && this.raw[1] == other.raw[1]
                && this.raw[2] == other.raw[2]
                && this.raw[3] == other.raw[3]
                && this.raw[4] == other.raw[4]
                && this.raw[5] == other.raw[5]
                && this.raw[6] == other.raw[6]
                && this.raw[7] == other.raw[7]
                && this.raw[8] == other.raw[8];
    }

    @Override
    public int hashCode() {
        return new Hasher()
                .add(this.raw[0]).add(this.raw[1]).add(this.raw[2])
                .add(this.raw[3]).add(this.raw[4]).add(this.raw[5])
                .add(this.raw[6]).add(this.raw[7]).add(this.raw[8])
                .getHash();
    }


    /**
     * Identity matrix as array. Do not modify!
     */
    private static final double[] identity = {
            1.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0
    };
}
