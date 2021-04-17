package roj.math;

import roj.util.Hasher;


/**
 * A row-major 4x4 {@code double} matrix.
 *
 * @author Maximilian Luz
 */
public class Mat4d {
    private Mat4d() {
    }

    private double[] raw = new double[16];


    /**
     * Constructs a new matrix with the given properties.
     */
    public Mat4d(double a11, double a12, double a13, double a14,
                 double a21, double a22, double a23, double a24,
                 double a31, double a32, double a33, double a34,
                 double a41, double a42, double a43, double a44) {

        raw[0] = a11;
        raw[1] = a12;
        raw[2] = a13;
        raw[3] = a14;
        raw[4] = a21;
        raw[5] = a22;
        raw[6] = a23;
        raw[7] = a24;
        raw[8] = a31;
        raw[9] = a32;
        raw[10] = a33;
        raw[11] = a34;
        raw[12] = a41;
        raw[13] = a42;
        raw[14] = a43;
        raw[15] = a44;
    }

    /**
     * Constructs a new matrix from the given array. The specified array is interpreted row-major wise.
     *
     * @param matrix the (at least) 16 element array from which the matrix should be created.
     */
    public Mat4d(double[] matrix) {
        System.arraycopy(matrix, 0, this.raw, 0, 16);
    }

    /**
     * Construct a new matrix by copying the specified one.
     *
     * @param matrix the matrix to be copied.
     */
    public Mat4d(Mat4d matrix) {
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

    public Mat4f toFloatMatrix() {
        return new Mat4f((float) raw[0], (float) raw[1], (float) raw[2], (float) raw[3],
                (float) raw[4], (float) raw[5], (float) raw[6], (float) raw[7],
                (float) raw[8], (float) raw[9], (float) raw[10], (float) raw[11],
                (float) raw[12], (float) raw[13], (float) raw[14], (float) raw[15]);
    }

    /**
     * Sets this matrix using the specified parameters.
     *
     * @return this matrix.
     */
    public Mat4d set(double a11, double a12, double a13, double a14,
                     double a21, double a22, double a23, double a24,
                     double a31, double a32, double a33, double a34,
                     double a41, double a42, double a43, double a44) {

        raw[0] = a11;
        raw[1] = a12;
        raw[2] = a13;
        raw[3] = a14;
        raw[4] = a21;
        raw[5] = a22;
        raw[6] = a23;
        raw[7] = a24;
        raw[8] = a31;
        raw[9] = a32;
        raw[10] = a33;
        raw[11] = a34;
        raw[12] = a41;
        raw[13] = a42;
        raw[14] = a43;
        raw[15] = a44;

        return this;
    }

    /**
     * Sets this matrix using the given array. The specified array is interpreted row-major wise.
     *
     * @param raw the (at least) 16 element array from which the matrix should be created.
     * @return this matrix.
     */
    public Mat4d set(double[] raw) {
        System.arraycopy(raw, 0, this.raw, 0, 16);
        return this;
    }

    /**
     * Sets this matrix by copying the specified one.
     *
     * @param other the matrix to be copied.
     * @return this matrix.
     */
    public Mat4d set(Mat4d other) {
        System.arraycopy(other.raw, 0, this.raw, 0, 16);
        return this;
    }

    /**
     * Sets this matrix to the identity-matrix.
     *
     * @return this matrix.
     */
    public Mat4d makeIdentity() {
        System.arraycopy(Mat4d.identity, 0, this.raw, 0, 16);
        return this;
    }

    /**
     * Sets this matrix to the orthographic projection matrix specified by the given parameters.
     *
     * @param left   the left plane.
     * @param right  the right plane.
     * @param top    the top plane.
     * @param bottom the bottom plane.
     * @param near   the near plane.
     * @param far    the far plane.
     * @return this matrix.
     */
    public Mat4d makeOrtho(double left, double right, double top, double bottom, double near, double far) {
        this.raw[0] = 2.0 / (right - left);
        this.raw[1] = 0;
        this.raw[2] = 0;
        this.raw[3] = -(right + left) / (right - left);

        this.raw[4] = 0;
        this.raw[5] = 2.0 / (top - bottom);
        this.raw[6] = 0;
        this.raw[7] = -(top + bottom) / (top - bottom);

        this.raw[8] = 0;
        this.raw[9] = 0;
        this.raw[10] = -2.0 / (far - near);
        this.raw[11] = -(far + near) / (far - near);

        this.raw[12] = 0;
        this.raw[13] = 0;
        this.raw[14] = 0;
        this.raw[15] = 1;

        return this;
    }

    /**
     * Sets this matrix to the perspective projection-matrix specified by the given parameters.
     *
     * @param fovy        the field-of-view.
     * @param aspectRatio the aspect ratio.
     * @param zNear       the near plane.
     * @param zFar        the far plane.
     * @return this matrix.
     */
    public Mat4d makePerspective(double fovy, double aspectRatio, double zNear, double zFar) {
        double f = (1 / Math.tan(Math.toRadians(fovy / 2)));

        this.raw[1] = 0;
        this.raw[2] = 0;
        this.raw[3] = 0;
        this.raw[4] = 0;
        this.raw[6] = 0;
        this.raw[7] = 0;
        this.raw[8] = 0;
        this.raw[9] = 0;
        this.raw[12] = 0;
        this.raw[13] = 0;
        this.raw[15] = 0;

        this.raw[0] = f / aspectRatio;
        this.raw[5] = f;
        this.raw[10] = (zFar + zNear) / (zNear - zFar);
        this.raw[11] = (2 * zNear * zFar) / (zNear - zFar);
        this.raw[14] = -1;

        return this;
    }

    /**
     * Set this matrix to a look-at transformation matrix, looking from {@code eye} to {@code center} with the
     * {@code up} vector indicating the upward looking direction.
     *
     * @param eye    the eye from which this transformation should "look".
     * @param center the center to which this transformation should "look".
     * @param up     the vector indicating the upwards looking direction.
     * @return this matrix.
     */
    public Mat4d makeLookAt(Vec3d eye, Vec3d center, Vec3d up) {
        return makeLookInDirection(eye, Vec3d.sub(center, eye), up);
    }

    /**
     * Set this matrix to a look-in-direction transformation matrix, looking from {@code eye} in direction of
     * {@code dir} with the {@code up} vector indicating the upward looking direction.
     *
     * @param eye the eye from which this transformation should "look".
     * @param dir the direction into which this transformation should "look".
     * @param up  the vector indicating the upwards looking direction.
     * @return this matrix.
     */
    public Mat4d makeLookInDirection(Vec3d eye, Vec3d dir, Vec3d up) {
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

        this.raw[0] = sideX;
        this.raw[1] = sideY;
        this.raw[2] = sideZ;
        this.raw[3] = 0;
        this.raw[4] = upX;
        this.raw[5] = upY;
        this.raw[6] = upZ;
        this.raw[7] = 0;
        this.raw[8] = -fwdX;
        this.raw[9] = -fwdY;
        this.raw[10] = -fwdZ;
        this.raw[11] = 0;
        this.raw[12] = 0;
        this.raw[13] = 0;
        this.raw[14] = 0;
        this.raw[15] = 1;

        return this.translate(-eye.x, -eye.y, -eye.z);
    }

    /**
     * Calculates the determinant of the upper left 3x3 sub-matrix.
     *
     * @return the calculated determinant of the upper left 3x3 sub-matrix.
     */
    public double det3() {
        return raw[0] * (raw[5] * raw[10] - raw[6] * raw[9])
                - raw[1] * (raw[4] * raw[10] - raw[6] * raw[8])
                + raw[2] * (raw[4] * raw[9] - raw[5] * raw[8]);
    }

    /**
     * Calculates the determinant of this matrix.
     *
     * @return the calculated determinant.
     */
    public double det() {
        // TODO: test!!
        // 2x2 determinants enumerated from left to right
        double d1 = raw[8] * raw[13] - raw[9] * raw[12];
        double d2 = raw[8] * raw[14] - raw[10] * raw[12];
        double d3 = raw[8] * raw[15] - raw[11] * raw[12];
        double d4 = raw[9] * raw[14] - raw[10] * raw[13];
        double d5 = raw[9] * raw[15] - raw[11] * raw[13];
        double d6 = raw[10] * raw[15] - raw[11] * raw[14];

        return raw[0] * (raw[5] * d6 - raw[6] * d5 + raw[7] * d4)
                - raw[1] * (raw[4] * d6 - raw[6] * d3 + raw[7] * d2)
                + raw[2] * (raw[4] * d5 - raw[5] * d3 + raw[7] * d1)
                - raw[3] * (raw[4] * d4 - raw[5] * d2 + raw[6] * d1);
    }

    /**
     * Tests whether this matrix is affine or not.
     *
     * @return {@code true} iff this matrix is affine.
     */
    public boolean isAffine() {
        return raw[12] == 0.0 && raw[13] == 0.0 && raw[14] == 0.0 && raw[15] == 1.0;
    }

    /**
     * Adds the specified matrix to this matrix and stores the result in this matrix.
     *
     * @param other the matrix to add.
     * @return this matrix.
     */
    public Mat4d add(Mat4d other) {
        raw[0] += other.raw[0];
        raw[1] += other.raw[1];
        raw[2] += other.raw[2];
        raw[3] += other.raw[3];
        raw[4] += other.raw[4];
        raw[5] += other.raw[5];
        raw[6] += other.raw[6];
        raw[7] += other.raw[7];
        raw[8] += other.raw[8];
        raw[9] += other.raw[9];
        raw[10] += other.raw[10];
        raw[11] += other.raw[11];
        raw[12] += other.raw[12];
        raw[13] += other.raw[13];
        raw[14] += other.raw[14];
        raw[15] += other.raw[15];
        return this;
    }

    /**
     * Subtracts the specified matrix from this matrix and stores the result in this matrix.
     *
     * @param other the matrix to subtract.
     * @return this matrix.
     */
    public Mat4d sub(Mat4d other) {
        raw[0] -= other.raw[0];
        raw[1] -= other.raw[1];
        raw[2] -= other.raw[2];
        raw[3] -= other.raw[3];
        raw[4] -= other.raw[4];
        raw[5] -= other.raw[5];
        raw[6] -= other.raw[6];
        raw[7] -= other.raw[7];
        raw[8] -= other.raw[8];
        raw[9] -= other.raw[9];
        raw[10] -= other.raw[10];
        raw[11] -= other.raw[11];
        raw[12] -= other.raw[12];
        raw[13] -= other.raw[13];
        raw[14] -= other.raw[14];
        raw[15] -= other.raw[15];
        return this;
    }

    /**
     * Multiplies this matrix with the specified matrix, i.e. {@code this * other}.
     *
     * @param other the matrix to multiply with.
     * @return this matrix.
     */
    public Mat4d mul(Mat4d other) {
        this.raw = new double[]{
                this.raw[0] * other.raw[0] + this.raw[1] * other.raw[4] + this.raw[2] * other.raw[8] + this.raw[3] * other.raw[12],
                this.raw[0] * other.raw[1] + this.raw[1] * other.raw[5] + this.raw[2] * other.raw[9] + this.raw[3] * other.raw[13],
                this.raw[0] * other.raw[2] + this.raw[1] * other.raw[6] + this.raw[2] * other.raw[10] + this.raw[3] * other.raw[14],
                this.raw[0] * other.raw[3] + this.raw[1] * other.raw[7] + this.raw[2] * other.raw[11] + this.raw[3] * other.raw[15],
                this.raw[4] * other.raw[0] + this.raw[5] * other.raw[4] + this.raw[6] * other.raw[8] + this.raw[7] * other.raw[12],
                this.raw[4] * other.raw[1] + this.raw[5] * other.raw[5] + this.raw[6] * other.raw[9] + this.raw[7] * other.raw[13],
                this.raw[4] * other.raw[2] + this.raw[5] * other.raw[6] + this.raw[6] * other.raw[10] + this.raw[7] * other.raw[14],
                this.raw[4] * other.raw[3] + this.raw[5] * other.raw[7] + this.raw[6] * other.raw[11] + this.raw[7] * other.raw[15],
                this.raw[8] * other.raw[0] + this.raw[9] * other.raw[4] + this.raw[10] * other.raw[8] + this.raw[11] * other.raw[12],
                this.raw[8] * other.raw[1] + this.raw[9] * other.raw[5] + this.raw[10] * other.raw[9] + this.raw[11] * other.raw[13],
                this.raw[8] * other.raw[2] + this.raw[9] * other.raw[6] + this.raw[10] * other.raw[10] + this.raw[11] * other.raw[14],
                this.raw[8] * other.raw[3] + this.raw[9] * other.raw[7] + this.raw[10] * other.raw[11] + this.raw[11] * other.raw[15],
                this.raw[12] * other.raw[0] + this.raw[13] * other.raw[4] + this.raw[14] * other.raw[8] + this.raw[15] * other.raw[12],
                this.raw[12] * other.raw[1] + this.raw[13] * other.raw[5] + this.raw[14] * other.raw[9] + this.raw[15] * other.raw[13],
                this.raw[12] * other.raw[2] + this.raw[13] * other.raw[6] + this.raw[14] * other.raw[10] + this.raw[15] * other.raw[14],
                this.raw[12] * other.raw[3] + this.raw[13] * other.raw[7] + this.raw[14] * other.raw[11] + this.raw[15] * other.raw[15]
        };

        return this;
    }

    /**
     * Multiplies this matrix with the specified vector.
     *
     * @param v the vector to multiply with.
     * @return the product of this matrix and the specified vectors.
     */
    public Vec4d mul(Vec4d v) {
        return new Vec4d(this.raw[0] * v.x + this.raw[1] * v.y + this.raw[2] * v.z + this.raw[3] * v.w,
                this.raw[4] * v.x + this.raw[5] * v.y + this.raw[6] * v.z + this.raw[7] * v.w,
                this.raw[8] * v.x + this.raw[9] * v.y + this.raw[10] * v.z + this.raw[11] * v.w,
                this.raw[12] * v.x + this.raw[13] * v.y + this.raw[14] * v.z + this.raw[15] * v.w);
    }

    /**
     * Multiplies this matrix with the specified scalar value.
     *
     * @param scalar the scalar value to multiply with.
     * @return this matrix.
     */
    public Mat4d mul(double scalar) {
        raw[0] *= scalar;
        raw[1] *= scalar;
        raw[2] *= scalar;
        raw[3] *= scalar;
        raw[4] *= scalar;
        raw[5] *= scalar;
        raw[6] *= scalar;
        raw[7] *= scalar;
        raw[8] *= scalar;
        raw[9] *= scalar;
        raw[10] *= scalar;
        raw[11] *= scalar;
        raw[12] *= scalar;
        raw[13] *= scalar;
        raw[14] *= scalar;
        raw[15] *= scalar;
        return this;
    }


    /**
     * Applies the specified translation to this matrix as if by multiplying this matrix with the according translation
     * matrix (i. e. {@code this * translation}).
     *
     * @param vec the vector specifying the translation.
     * @return this matrix.
     */
    public Mat4d translate(Vec3d vec) {
        return translate(vec.x, vec.y, vec.z);
    }

    /**
     * Applies the specified translation to this matrix as if by multiplying this matrix with the according translation
     * matrix (i. e. {@code this * translation}).
     *
     * @param x the x-axis translation component.
     * @param y the y-axis translation component.
     * @param z the z-axis translation component.
     * @return this matrix.
     */
    public Mat4d translate(double x, double y, double z) {
        this.raw[3] = this.raw[0] * x + this.raw[1] * y + this.raw[2] * z + this.raw[3];
        this.raw[7] = this.raw[4] * x + this.raw[5] * y + this.raw[6] * z + this.raw[7];
        this.raw[11] = this.raw[8] * x + this.raw[9] * y + this.raw[10] * z + this.raw[11];
        this.raw[15] = this.raw[12] * x + this.raw[13] * y + this.raw[14] * z + this.raw[15];
        return this;
    }

    /**
     * Applies the specified scaling-operation to this matrix as if by multiplying this matrix with the according
     * scale matrix (i. e. {@code this * scale}).
     *
     * @param vec the vector specifying the scale-transformation.
     * @return this matrix.
     */
    public Mat4d scale(Vec3d vec) {
        return scale(vec.x, vec.y, vec.z);
    }

    /**
     * Applies the specified scaling-operation to this matrix as if by multiplying this matrix with the according
     * scale matrix (i. e. {@code this * scale}).
     *
     * @param sx the x-axis scale component.
     * @param sy the y-axis scale component.
     * @param sz the z-axis scale component.
     * @return this matrix.
     */
    public Mat4d scale(double sx, double sy, double sz) {
        this.raw[0] *= sx;
        this.raw[1] *= sy;
        this.raw[2] *= sz;
        this.raw[4] *= sx;
        this.raw[5] *= sy;
        this.raw[6] *= sz;
        this.raw[8] *= sx;
        this.raw[9] *= sy;
        this.raw[10] *= sz;
        this.raw[12] *= sx;
        this.raw[13] *= sy;
        this.raw[14] *= sz;
        return this;
    }

    /**
     * Applies the specified rotation-operation to this matrix as if by multiplying this matrix with the according
     * rotation matrix (i. e. {@code this * rotation}).
     *
     * @param axis  the axis around which should be rotated.
     * @param angle the angle (in radians) specifying the rotation.
     * @return this matrix.
     */
    public Mat4d rotate(Vec3d axis, double angle) {
        return this.rotate(axis.x, axis.y, axis.z, angle);
    }

    /**
     * Applies the specified rotation-operation to this matrix as if by multiplying this matrix with the according
     * rotation matrix (i. e. {@code this * rotation}).
     *
     * @param x     the x part of the axis around which should be rotated.
     * @param y     the y part of the axis around which should be rotated.
     * @param z     the z part of the axis around which should be rotated.
     * @param angle the angle (in radians) specifying the rotation.
     * @return this matrix.
     */
    public Mat4d rotate(double x, double y, double z, double angle) {
        double s = Math.sin(angle);
        double c = Math.cos(angle);
        double omc = 1.0 - c;

        double xx = x * x;
        double xy = x * y;
        double xz = x * z;
        double yy = y * y;
        double yz = y * z;
        double zz = z * z;
        double xs = x * s;
        double ys = y * s;
        double zs = z * s;

        double r0 = xx * omc + c;
        double r1 = xy * omc - zs;
        double r2 = xz * omc + ys;
        double r4 = xy * omc + zs;
        double r5 = yy * omc + c;
        double r6 = yz * omc - xs;
        double r8 = xz * omc - ys;
        double r9 = yz * omc + xs;
        double r10 = zz * omc + c;

        this.raw = new double[]{
                this.raw[0] * r0 + this.raw[1] * r4 + this.raw[2] * r8,
                this.raw[0] * r1 + this.raw[1] * r5 + this.raw[2] * r9,
                this.raw[0] * r2 + this.raw[1] * r6 + this.raw[2] * r10,
                this.raw[3],
                this.raw[4] * r0 + this.raw[5] * r4 + this.raw[6] * r8,
                this.raw[4] * r1 + this.raw[5] * r5 + this.raw[6] * r9,
                this.raw[4] * r2 + this.raw[5] * r6 + this.raw[6] * r10,
                this.raw[7],
                this.raw[8] * r0 + this.raw[9] * r4 + this.raw[10] * r8,
                this.raw[8] * r1 + this.raw[9] * r5 + this.raw[10] * r9,
                this.raw[8] * r2 + this.raw[9] * r6 + this.raw[10] * r10,
                this.raw[11],
                this.raw[12] * r0 + this.raw[13] * r4 + this.raw[14] * r8,
                this.raw[12] * r1 + this.raw[13] * r5 + this.raw[14] * r9,
                this.raw[12] * r2 + this.raw[13] * r6 + this.raw[14] * r10,
                this.raw[15]
        };

        return this;
    }

    /**
     * Applies the specified {@code x}-axis rotation-operation to this matrix as if by multiplying this matrix with the
     * according rotation matrix (i. e. {@code this * rotation}).
     *
     * @param angle the angle (in radians) specifying the rotation around the {@code x}-axis.
     * @return this matrix.
     */
    public Mat4d rotateX(double angle) {
        double s = Math.sin(angle);
        double c = Math.cos(angle);

        this.raw = new double[]{
                this.raw[0], this.raw[1] * c + this.raw[2] * s, -this.raw[1] * s + this.raw[2] * c, this.raw[3],
                this.raw[4], this.raw[5] * c + this.raw[6] * s, -this.raw[5] * s + this.raw[6] * c, this.raw[7],
                this.raw[8], this.raw[9] * c + this.raw[10] * s, -this.raw[9] * s + this.raw[10] * c, this.raw[11],
                this.raw[12], this.raw[13] * c + this.raw[14] * s, -this.raw[13] * s + this.raw[14] * c, this.raw[15]
        };

        return this;
    }

    /**
     * Applies the specified {@code y}-axis rotation-operation to this matrix as if by multiplying this matrix with the
     * according rotation matrix (i. e. {@code this * rotation}).
     *
     * @param angle the angle (in radians) specifying the rotation around the {@code y}-axis.
     * @return this matrix.
     */
    public Mat4d rotateY(double angle) {
        double s = Math.sin(angle);
        double c = Math.cos(angle);

        this.raw = new double[]{
                this.raw[0] * c - this.raw[2] * s, this.raw[1], this.raw[0] * s + this.raw[2] * c, this.raw[3],
                this.raw[4] * c - this.raw[6] * s, this.raw[5], this.raw[4] * s + this.raw[6] * c, this.raw[7],
                this.raw[8] * c - this.raw[10] * s, this.raw[9], this.raw[8] * s + this.raw[10] * c, this.raw[11],
                this.raw[12] * c - this.raw[14] * s, this.raw[13], this.raw[12] * s + this.raw[14] * c, this.raw[15]
        };

        return this;
    }

    /**
     * Applies the specified {@code z}-axis rotation-operation to this matrix as if by multiplying this matrix with the
     * according rotation matrix (i. e. {@code this * rotation}).
     *
     * @param angle the angle (in radians) specifying the rotation around the {@code z}-axis.
     * @return this matrix.
     */
    public Mat4d rotateZ(double angle) {
        double s = Math.sin(Math.toRadians(angle));
        double c = Math.cos(Math.toRadians(angle));

        this.raw = new double[]{
                this.raw[0] * c + this.raw[1] * s, -this.raw[0] * s + this.raw[1] * c, this.raw[2], this.raw[3],
                this.raw[4] * c + this.raw[5] * s, -this.raw[4] * s + this.raw[5] * c, this.raw[6], this.raw[7],
                this.raw[8] * c + this.raw[9] * s, -this.raw[8] * s + this.raw[9] * c, this.raw[10], this.raw[11],
                this.raw[12] * c + this.raw[13] * s, -this.raw[12] * s + this.raw[13] * c, this.raw[14], this.raw[15]
        };

        return this;
    }

    /**
     * Transposes this matrix.
     *
     * @return this matrix.
     */
    public Mat4d transpose() {
        double swp;

        swp = this.raw[1];
        this.raw[1] = this.raw[4];
        this.raw[4] = swp;

        swp = this.raw[2];
        this.raw[2] = this.raw[8];
        this.raw[8] = swp;

        swp = this.raw[6];
        this.raw[6] = this.raw[9];
        this.raw[9] = swp;

        swp = this.raw[3];
        this.raw[3] = this.raw[12];
        this.raw[12] = swp;

        swp = this.raw[7];
        this.raw[7] = this.raw[13];
        this.raw[13] = swp;

        swp = this.raw[11];
        this.raw[11] = this.raw[14];
        this.raw[14] = swp;

        return this;
    }

    /**
     * Transposes the upper left 3x3 matrix, leaving all other elements as they are.
     *
     * @return this matrix.
     */
    public Mat4d transpose3() {
        double swp;

        swp = this.raw[1];
        this.raw[1] = this.raw[4];
        this.raw[4] = swp;

        swp = this.raw[2];
        this.raw[2] = this.raw[8];
        this.raw[8] = swp;

        swp = this.raw[6];
        this.raw[6] = this.raw[9];
        this.raw[9] = swp;

        return this;
    }

    /**
     * Inverts this matrix.
     *
     * @return this matrix.
     */
    public Mat4d invert() {
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
    public Mat4d invertAffine() {
        // calculate inverse of upper 3x3 matrix
        double m0 = raw[5] * raw[10] - raw[6] * raw[9];
        double m1 = raw[2] * raw[9] - raw[1] * raw[10];
        double m2 = raw[1] * raw[6] - raw[2] * raw[5];
        double m3 = raw[6] * raw[8] - raw[4] * raw[10];
        double m4 = raw[0] * raw[10] - raw[2] * raw[8];
        double m5 = raw[2] * raw[4] - raw[0] * raw[6];
        double m6 = raw[4] * raw[9] - raw[5] * raw[8];
        double m7 = raw[1] * raw[8] - raw[0] * raw[9];
        double m8 = raw[0] * raw[5] - raw[1] * raw[4];

        double det = raw[0] * m0 + raw[1] * (-m3) + raw[2] * m6;
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

        // calculate product of inverse upper 3x3 matrix and translation part
        double tx = m0 * (-raw[3]) + m1 * (-raw[7]) + m2 * (-raw[11]);
        double ty = m3 * (-raw[3]) + m4 * (-raw[7]) + m5 * (-raw[11]);
        double tz = m6 * (-raw[3]) + m7 * (-raw[7]) + m8 * (-raw[11]);

        // assemble inverse matrix
        this.raw = new double[]{
                m0, m1, m2, tx,
                m3, m4, m5, ty,
                m6, m7, m8, tz,
                0, 0, 0, 1
        };

        return this;
    }

    /**
     * Inverts this matrix. See {@link Mat4d#invert()} for a possibly more performant version, depending on the content
     * of the matrix.
     *
     * @return this matrix.
     */
    public Mat4d invertGeneral() {
        double d10d15 = this.raw[10] * this.raw[15] - this.raw[11] * this.raw[14];
        double d06d15 = this.raw[6] * this.raw[15] - this.raw[7] * this.raw[14];
        double d06d11 = this.raw[6] * this.raw[11] - this.raw[7] * this.raw[10];
        double d02d15 = this.raw[2] * this.raw[15] - this.raw[3] * this.raw[14];
        double d02d11 = this.raw[2] * this.raw[11] - this.raw[3] * this.raw[10];
        double d02d07 = this.raw[2] * this.raw[7] - this.raw[3] * this.raw[6];
        double d09d15 = this.raw[9] * this.raw[15] - this.raw[11] * this.raw[13];
        double d05d15 = this.raw[5] * this.raw[15] - this.raw[7] * this.raw[13];
        double d05d11 = this.raw[5] * this.raw[11] - this.raw[7] * this.raw[9];
        double d01d15 = this.raw[1] * this.raw[15] - this.raw[3] * this.raw[13];
        double d01d11 = this.raw[1] * this.raw[11] - this.raw[3] * this.raw[9];
        double d01d07 = this.raw[1] * this.raw[7] - this.raw[3] * this.raw[5];
        double d09d14 = this.raw[9] * this.raw[14] - this.raw[10] * this.raw[13];
        double d05d14 = this.raw[5] * this.raw[14] - this.raw[6] * this.raw[13];
        double d05d10 = this.raw[5] * this.raw[10] - this.raw[6] * this.raw[9];
        double d01d14 = this.raw[1] * this.raw[14] - this.raw[2] * this.raw[13];
        double d01d10 = this.raw[1] * this.raw[10] - this.raw[2] * this.raw[9];
        double d01d06 = this.raw[1] * this.raw[6] - this.raw[2] * this.raw[5];

        double[] tmp = {
                this.raw[5] * d10d15 - this.raw[9] * d06d15 + this.raw[13] * d06d11,
                -this.raw[1] * d10d15 + this.raw[9] * d02d15 - this.raw[13] * d02d11,
                this.raw[1] * d06d15 - this.raw[5] * d02d15 + this.raw[13] * d02d07,
                -this.raw[1] * d06d11 + this.raw[5] * d02d11 - this.raw[9] * d02d07,
                -this.raw[4] * d10d15 + this.raw[8] * d06d15 - this.raw[12] * d06d11,
                this.raw[0] * d10d15 - this.raw[8] * d02d15 + this.raw[12] * d02d11,
                -this.raw[0] * d06d15 + this.raw[4] * d02d15 - this.raw[12] * d02d07,
                this.raw[0] * d06d11 - this.raw[4] * d02d11 + this.raw[8] * d02d07,
                this.raw[4] * d09d15 - this.raw[8] * d05d15 + this.raw[12] * d05d11,
                -this.raw[0] * d09d15 + this.raw[8] * d01d15 - this.raw[12] * d01d11,
                this.raw[0] * d05d15 - this.raw[4] * d01d15 + this.raw[12] * d01d07,
                -this.raw[0] * d05d11 + this.raw[4] * d01d11 - this.raw[8] * d01d07,
                -this.raw[4] * d09d14 + this.raw[8] * d05d14 - this.raw[12] * d05d10,
                this.raw[0] * d09d14 - this.raw[8] * d01d14 + this.raw[12] * d01d10,
                -this.raw[0] * d05d14 + this.raw[4] * d01d14 - this.raw[12] * d01d06,
                this.raw[0] * d05d10 - this.raw[4] * d01d10 + this.raw[8] * d01d06
        };

        double det = this.raw[0] * tmp[0] + this.raw[1] * tmp[4] + this.raw[2] * tmp[8] + this.raw[3] * tmp[12];
        if (det == 0) return null;

        for (int i = 0; i < 16; i++)
            tmp[i] /= det;

        raw = tmp;
        return this;
    }

    /**
     * Creates a new matrix-instance containing the identity-matrix.
     *
     * @return a new identity matrix.
     */
    public static Mat4d identity() {
        return new Mat4d(identity);
    }

    /**
     * Creates a new orthogonal projection-matrix.
     *
     * @param left   the left plane.
     * @param right  the right plane.
     * @param top    the top plane.
     * @param bottom the bottom plane.
     * @param near   the near plane.
     * @param far    the far plane.
     * @return the created projection-matrix.
     */
    public static Mat4d ortho(double left, double right, double top, double bottom, double near, double far) {
        return new Mat4d().makeOrtho(left, right, top, bottom, near, far);
    }

    /**
     * Creates a new perspective projection-matrix.
     *
     * @param fovy        the field-of-view.
     * @param aspectRatio the aspect ratio.
     * @param zNear       the near plane.
     * @param zFar        the far plane.
     * @return the created projection-matrix.
     */
    public static Mat4d perspecive(double fovy, double aspectRatio, double zNear, double zFar) {
        return new Mat4d().makePerspective(fovy, aspectRatio, zNear, zFar);
    }

    /**
     * Creates a look-at transformation matrix, looking from {@code eye} to {@code center} with the {@code up} vector
     * indicating the upward looking direction.
     *
     * @param eye    the eye from which this transformation should "look".
     * @param center the center to which this transformation should "look".
     * @param up     the vector indicating the upwards looking direction.
     * @return the created look-at matrix.
     */
    public static Mat4d lookAt(Vec3d eye, Vec3d center, Vec3d up) {
        return new Mat4d().makeLookAt(eye, center, up);
    }

    /**
     * Creates a a look-in-direction transformation matrix, looking from {@code eye} in direction of {@code dir} with
     * the {@code up} vector indicating the upward looking direction.
     *
     * @param eye the eye from which this transformation should "look".
     * @param dir the direction into which this transformation should "look".
     * @param up  the vector indicating the upwards looking direction.
     * @return this matrix.
     */
    public static Mat4d lookInDirection(Vec3d eye, Vec3d dir, Vec3d up) {
        return new Mat4d().makeLookInDirection(eye, dir, up);
    }

    /**
     * Adds the specified matrices and returns the result as new matrix.
     *
     * @param a the first matrix.
     * @param b the second matrix.
     * @return the result of this addition, i.e. {@code a + b}.
     */
    public static Mat4d add(Mat4d a, Mat4d b) {
        return new Mat4d(a).add(b);
    }

    /**
     * Subtracts the specified matrices and returns the result as new matrix.
     *
     * @param a the matrix to subtract from.
     * @param b the matrix to subtract.
     * @return the result of this subtraction, i.e. {@code a - b}.
     */
    public static Mat4d sub(Mat4d a, Mat4d b) {
        return new Mat4d(a).sub(b);
    }

    /**
     * Multiplies the specified matrices and returns the result as new matrix.
     *
     * @param a the first matrix.
     * @param b the second matrix.
     * @return the result of this multiplication, i.e. {@code a * b}.
     */
    public static Mat4d mul(Mat4d a, Mat4d b) {
        return new Mat4d(a).mul(b);
    }

    /**
     * Multiplies the specified matrix with the specified vector in place, storing the result in the specified vector.
     *
     * @param m the matrix to multiply with.
     * @param v the vector to multiply with.
     * @return {@code v}.
     */
    public static Vec4d mulInPlace(Mat4d m, Vec4d v) {
        double x = m.raw[0] * v.x + m.raw[1] * v.y + m.raw[2] * v.z + m.raw[3] * v.w;
        double y = m.raw[4] * v.x + m.raw[5] * v.y + m.raw[6] * v.z + m.raw[7] * v.w;
        double z = m.raw[8] * v.x + m.raw[9] * v.y + m.raw[10] * v.z + m.raw[11] * v.w;
        double w = m.raw[12] * v.x + m.raw[13] * v.y + m.raw[14] * v.z + m.raw[15] * v.w;

        v.x = x;
        v.y = y;
        v.z = z;
        v.w = w;

        return v;
    }

    /**
     * Multiplies the specified matrix with the specified scalar value and returns the result in a new matrix.
     *
     * @param m      the matrix to multiply with.
     * @param scalar the scalar value to multiply with.
     * @return the result of this multiplication, i.e. {@code m * scalar}.
     */
    public static Mat4d mul(Mat4d m, double scalar) {
        return new Mat4d(m).mul(scalar);
    }

    /**
     * Applies the specified translation to the specified matrix as if by multiplying the matrix with the according
     * translation matrix (i. e. {@code m * translation}).
     *
     * @param m the matrix to be transformed.
     * @param v the vector specifying the translation.
     * @return the result of this transformation.
     */
    public static Mat4d translate(Mat4d m, Vec3d v) {
        return new Mat4d(m).translate(v);
    }

    /**
     * Applies the specified translation to the specified matrix as if by multiplying the matrix with the according
     * translation matrix (i. e. {@code m * translation}).
     *
     * @param m the matrix to be transformed.
     * @param x the x-axis translation component.
     * @param y the y-axis translation component.
     * @param z the z-axis translation component.
     * @return the result of this transformation.
     */
    public static Mat4d translate(Mat4d m, double x, double y, double z) {
        return new Mat4d(m).translate(x, y, z);
    }

    /**
     * Applies the specified scaling-operation to the specified matrix as if by multiplying this matrix with the
     * according scale matrix (i. e. {@code m * scale}).
     *
     * @param m the matrix to be transformed.
     * @param v the vector specifying the scale-transformation.
     * @return the result of this transformation.
     */
    public static Mat4d scale(Mat4d m, Vec3d v) {
        return new Mat4d(m).scale(v);
    }

    /**
     * Applies the specified scaling-operation to the specified matrix as if by multiplying this matrix with the
     * according scale matrix (i. e. {@code m * scale}).
     *
     * @param m the matrix to be transformed.
     * @param x the x-axis scale component.
     * @param y the y-axis scale component.
     * @param z the z-axis scale component.
     * @return the result of this transformation.
     */
    public static Mat4d scale(Mat4d m, double x, double y, double z) {
        return new Mat4d(m).scale(x, y, z);
    }

    /**
     * Applies the specified rotation-operation to the specified matrix as if by multiplying this matrix with the
     * according rotation matrix (i. e. {@code m * rotate}).
     *
     * @param m     the matrix to be transformed.
     * @param axis  the axis around which should be rotated.
     * @param angle the angle (in radians) specifying the rotation.
     * @return the result of this transformation.
     */
    public static Mat4d rotate(Mat4d m, Vec3d axis, double angle) {
        return new Mat4d(m).rotate(axis, angle);
    }

    /**
     * Applies the specified rotation-operation to the specified matrix as if by multiplying this matrix with the
     * according rotation matrix (i. e. {@code m * rotate}).
     *
     * @param m     the matrix to be transformed.
     * @param x     the x part of the axis around which should be rotated.
     * @param y     the y part of the axis around which should be rotated.
     * @param z     the z part of the axis around which should be rotated.
     * @param angle the angle (in radians) specifying the rotation.
     * @return the result of this transformation.
     */
    public static Mat4d rotate(Mat4d m, double x, double y, double z, double angle) {
        return new Mat4d(m).rotate(x, y, z, angle);
    }

    /**
     * Applies the specified {@code x}-axis rotation-operation to the specified matrix as if by multiplying this matrix
     * with the according rotation matrix (i. e. {@code m * rotation}).
     *
     * @param m     the matrix to be transformed.
     * @param angle the angle (in radians) specifying the rotation around the {@code x}-axis.
     * @return the result of this transformation.
     */
    public static Mat4d rotateX(Mat4d m, double angle) {
        return new Mat4d(m).rotateX(angle);
    }

    /**
     * Applies the specified {@code y}-axis rotation-operation to the specified matrix as if by multiplying this matrix
     * with the according rotation matrix (i. e. {@code m * rotation}).
     *
     * @param m     the matrix to be transformed.
     * @param angle the angle (in radians) specifying the rotation around the {@code y}-axis.
     * @return the result of this transformation.
     */
    public static Mat4d rotateY(Mat4d m, double angle) {
        return new Mat4d(m).rotateY(angle);
    }

    /**
     * Applies the specified {@code z}-axis rotation-operation to the specified matrix as if by multiplying this matrix
     * with the according rotation matrix (i. e. {@code m * rotation}).
     *
     * @param m     the matrix to be transformed.
     * @param angle the angle (in radians) specifying the rotation around the {@code z}-axis.
     * @return the result of this transformation.
     */
    public static Mat4d rotateZ(Mat4d m, double angle) {
        return new Mat4d(m).rotateZ(angle);
    }

    /**
     * Transposes the specified matrix.
     *
     * @param m the matrix to be transposed.
     * @return the transposed matrix.
     */
    public static Mat4d transpose(Mat4d m) {
        return new Mat4d(m).transpose();
    }

    /**
     * Inverts the specified matrix and returns the result as new matrix.
     *
     * @param m the matrix to be transposed.
     * @return the inverted matrix.
     */
    public static Mat4d invert(Mat4d m) {
        return new Mat4d(m).invert();
    }

    /**
     * Inverts the specified matrix as if it were an affine matrix and returns the result an new matrix.
     *
     * @param m the matrix to be transposed.
     * @return the inverted matrix.
     */
    public static Mat4d invertAffine(Mat4d m) {
        return new Mat4d(m).invertAffine();
    }

    /**
     * Inverts the specified matrix and returns the result as new matrix. See {@link Mat4d#invert(Mat4d)} for a
     * possibly more efficient version, depending on the contents of the matrix.
     *
     * @param m the matrix to be transposed.
     * @return the inverted matrix.
     */
    public static Mat4d invertGeneral(Mat4d m) {
        return new Mat4d(m).invertGeneral();
    }


    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Mat4d)) return false;

        Mat4d other = (Mat4d) obj;
        return this.raw[0] == other.raw[0]
                && this.raw[1] == other.raw[1]
                && this.raw[2] == other.raw[2]
                && this.raw[3] == other.raw[3]
                && this.raw[4] == other.raw[4]
                && this.raw[5] == other.raw[5]
                && this.raw[6] == other.raw[6]
                && this.raw[7] == other.raw[7]
                && this.raw[8] == other.raw[8]
                && this.raw[9] == other.raw[9]
                && this.raw[10] == other.raw[10]
                && this.raw[11] == other.raw[11]
                && this.raw[12] == other.raw[12]
                && this.raw[13] == other.raw[13]
                && this.raw[14] == other.raw[14]
                && this.raw[15] == other.raw[15];
    }

    @Override
    public int hashCode() {
        return new Hasher()
                .add(this.raw[0]).add(this.raw[1]).add(this.raw[2]).add(this.raw[3])
                .add(this.raw[4]).add(this.raw[5]).add(this.raw[6]).add(this.raw[7])
                .add(this.raw[8]).add(this.raw[9]).add(this.raw[10]).add(this.raw[11])
                .add(this.raw[12]).add(this.raw[13]).add(this.raw[14]).add(this.raw[15])
                .getHash();
    }


    /**
     * Identity matrix as array. Do not modify!
     */
    private static final double[] identity = {
            1.0, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0,
    };
}
