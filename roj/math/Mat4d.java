package roj.math;

import roj.util.Hasher;


/**
 * A row-major 4x4 {@code double} matrix.
 *
 * @author Maximilian Luz
 */
public class Mat4d {
    public Mat4d() {}

    public double raw0,raw1,raw2,raw3,
            raw4,raw5,raw6,raw7,
            raw8,raw9,raw10,raw11,
            raw12,raw13,raw14,raw15;


    /**
     * Constructs a new matrix with the given properties.
     */
    public Mat4d(double a11, double a12, double a13, double a14,
                 double a21, double a22, double a23, double a24,
                 double a31, double a32, double a33, double a34,
                 double a41, double a42, double a43, double a44) {

        raw0 = a11;
        raw1 = a12;
        raw2 = a13;
        raw3 = a14;
        raw4 = a21;
        raw5 = a22;
        raw6 = a23;
        raw7 = a24;
        raw8 = a31;
        raw9 = a32;
        raw10 = a33;
        raw11 = a34;
        raw12 = a41;
        raw13 = a42;
        raw14 = a43;
        raw15 = a44;
    }

    /**
     * Constructs a new matrix from the given array. The specified array is interpreted row-major wise.
     *
     * @param matrix the (at least) 16 element array from which the matrix should be created.
     */
    public Mat4d(double[] matrix) {
        set(matrix);
    }

    /**
     * Construct a new matrix by copying the specified one.
     *
     * @param matrix the matrix to be copied.
     */
    public Mat4d(Mat4d matrix) {
        set(matrix);
    }

    public Mat4f toFloatMatrix() {
        return new Mat4f((float) raw0, (float) raw1, (float) raw2, (float) raw3,
                (float) raw4, (float) raw5, (float) raw6, (float) raw7,
                (float) raw8, (float) raw9, (float) raw10, (float) raw11,
                (float) raw12, (float) raw13, (float) raw14, (float) raw15);
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

        raw0 = a11;
        raw1 = a12;
        raw2 = a13;
        raw3 = a14;
        raw4 = a21;
        raw5 = a22;
        raw6 = a23;
        raw7 = a24;
        raw8 = a31;
        raw9 = a32;
        raw10 = a33;
        raw11 = a34;
        raw12 = a41;
        raw13 = a42;
        raw14 = a43;
        raw15 = a44;

        return this;
    }

    /**
     * Sets this matrix using the given array. The specified array is interpreted row-major wise.
     *
     * @param raw the (at least) 16 element array from which the matrix should be created.
     * @return this matrix.
     */
    public Mat4d set(double[] raw) {
        raw0 = raw[0];
        raw1 = raw[1];
        raw2 = raw[2];
        raw3 = raw[3];
        raw4 = raw[4];
        raw5 = raw[5];
        raw6 = raw[6];
        raw7 = raw[7];
        raw8 = raw[8];
        raw9 = raw[9];
        raw10 = raw[10];
        raw11 = raw[11];
        raw12 = raw[12];
        raw13 = raw[13];
        raw14 = raw[14];
        raw15 = raw[15];
        return this;
    }

    /**
     * Sets this matrix by copying the specified one.
     *
     * @param other the matrix to be copied.
     * @return this matrix.
     */
    public Mat4d set(Mat4d other) {
        raw0 = other.raw0;
        raw1 = other.raw1;
        raw2 = other.raw2;
        raw3 = other.raw3;
        raw4 = other.raw4;
        raw5 = other.raw5;
        raw6 = other.raw6;
        raw7 = other.raw7;
        raw8 = other.raw8;
        raw9 = other.raw9;
        raw10 = other.raw10;
        raw11 = other.raw11;
        raw12 = other.raw12;
        raw13 = other.raw13;
        raw14 = other.raw14;
        raw15 = other.raw15;
        return this;
    }

    /**
     * Sets this matrix to the identity-matrix.
     *
     * @return this matrix.
     */
    public Mat4d makeIdentity() {
        raw0 = raw5 = raw10 = raw15 = 1;
        raw1 = raw2 = raw3 = raw4 = raw6 = raw7 = raw8 = raw9 = raw11 = raw12 = raw13 = raw14 = 0;
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
     * @return this matrix.
     */
    public Mat4d makeOrtho(double left, double right, double top, double bottom, double near, double far) {
        raw0 = 2.0 / (right - left);
        raw1 = 0;
        raw2 = 0;
        raw3 = -(right + left) / (right - left);

        raw4 = 0;
        raw5 = 2.0 / (top - bottom);
        raw6 = 0;
        raw7 = -(top + bottom) / (top - bottom);

        raw8 = 0;
        raw9 = 0;
        raw10 = -2.0 / (far - near);
        raw11 = -(far + near) / (far - near);

        raw12 = 0;
        raw13 = 0;
        raw14 = 0;
        raw15 = 1;

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

        raw1 = 0;
        raw2 = 0;
        raw3 = 0;
        raw4 = 0;
        raw6 = 0;
        raw7 = 0;
        raw8 = 0;
        raw9 = 0;
        raw12 = 0;
        raw13 = 0;
        raw15 = 0;

        raw0 = f / aspectRatio;
        raw5 = f;
        raw10 = (zFar + zNear) / (zNear - zFar);
        raw11 = (2 * zNear * zFar) / (zNear - zFar);
        raw14 = -1;

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

        raw0 = sideX;
        raw1 = sideY;
        raw2 = sideZ;
        raw3 = 0;
        raw4 = upX;
        raw5 = upY;
        raw6 = upZ;
        raw7 = 0;
        raw8 = -fwdX;
        raw9 = -fwdY;
        raw10 = -fwdZ;
        raw11 = 0;
        raw12 = 0;
        raw13 = 0;
        raw14 = 0;
        raw15 = 1;

        return this.translate(-eye.x, -eye.y, -eye.z);
    }

    /**
     * Calculates the determinant of the upper left 3x3 sub-matrix.
     *
     * @return the calculated determinant of the upper left 3x3 sub-matrix.
     */
    public double det3() {
        return raw0 * (raw5 * raw10 - raw6 * raw9)
                - raw1 * (raw4 * raw10 - raw6 * raw8)
                + raw2 * (raw4 * raw9 - raw5 * raw8);
    }

    /**
     * Calculates the determinant of this matrix.
     *
     * @return the calculated determinant.
     */
    public double det() {
        // TODO: test!!
        // 2x2 determinants enumerated from left to right
        double d1 = raw8 * raw13 - raw9 * raw12;
        double d2 = raw8 * raw14 - raw10 * raw12;
        double d3 = raw8 * raw15 - raw11 * raw12;
        double d4 = raw9 * raw14 - raw10 * raw13;
        double d5 = raw9 * raw15 - raw11 * raw13;
        double d6 = raw10 * raw15 - raw11 * raw14;

        return raw0 * (raw5 * d6 - raw6 * d5 + raw7 * d4)
                - raw1 * (raw4 * d6 - raw6 * d3 + raw7 * d2)
                + raw2 * (raw4 * d5 - raw5 * d3 + raw7 * d1)
                - raw3 * (raw4 * d4 - raw5 * d2 + raw6 * d1);
    }

    /**
     * Tests whether this matrix is affine or not.
     *
     * @return {@code true} iff this matrix is affine.
     */
    public boolean isAffine() {
        return raw12 == 0.0 && raw13 == 0.0 && raw14 == 0.0 && raw15 == 1.0;
    }

    /**
     * Adds the specified matrix to this matrix and stores the result in this matrix.
     *
     * @param other the matrix to add.
     * @return this matrix.
     */
    public Mat4d add(Mat4d other) {
        raw0 += other.raw0;
        raw1 += other.raw1;
        raw2 += other.raw2;
        raw3 += other.raw3;
        raw4 += other.raw4;
        raw5 += other.raw5;
        raw6 += other.raw6;
        raw7 += other.raw7;
        raw8 += other.raw8;
        raw9 += other.raw9;
        raw10 += other.raw10;
        raw11 += other.raw11;
        raw12 += other.raw12;
        raw13 += other.raw13;
        raw14 += other.raw14;
        raw15 += other.raw15;
        return this;
    }

    /**
     * Subtracts the specified matrix from this matrix and stores the result in this matrix.
     *
     * @param other the matrix to subtract.
     * @return this matrix.
     */
    public Mat4d sub(Mat4d other) {
        raw0 -= other.raw0;
        raw1 -= other.raw1;
        raw2 -= other.raw2;
        raw3 -= other.raw3;
        raw4 -= other.raw4;
        raw5 -= other.raw5;
        raw6 -= other.raw6;
        raw7 -= other.raw7;
        raw8 -= other.raw8;
        raw9 -= other.raw9;
        raw10 -= other.raw10;
        raw11 -= other.raw11;
        raw12 -= other.raw12;
        raw13 -= other.raw13;
        raw14 -= other.raw14;
        raw15 -= other.raw15;
        return this;
    }

    /**
     * Multiplies this matrix with the specified matrix, i.e. {@code this * other}.
     *
     * @param other the matrix to multiply with.
     * @return this matrix.
     */
    public Mat4d mul(Mat4d other) {
        set(new double[]{
                raw0 * other.raw0 + raw1 * other.raw4 + raw2 * other.raw8 + raw3 * other.raw12,
                raw0 * other.raw1 + raw1 * other.raw5 + raw2 * other.raw9 + raw3 * other.raw13,
                raw0 * other.raw2 + raw1 * other.raw6 + raw2 * other.raw10 + raw3 * other.raw14,
                raw0 * other.raw3 + raw1 * other.raw7 + raw2 * other.raw11 + raw3 * other.raw15,
                raw4 * other.raw0 + raw5 * other.raw4 + raw6 * other.raw8 + raw7 * other.raw12,
                raw4 * other.raw1 + raw5 * other.raw5 + raw6 * other.raw9 + raw7 * other.raw13,
                raw4 * other.raw2 + raw5 * other.raw6 + raw6 * other.raw10 + raw7 * other.raw14,
                raw4 * other.raw3 + raw5 * other.raw7 + raw6 * other.raw11 + raw7 * other.raw15,
                raw8 * other.raw0 + raw9 * other.raw4 + raw10 * other.raw8 + raw11 * other.raw12,
                raw8 * other.raw1 + raw9 * other.raw5 + raw10 * other.raw9 + raw11 * other.raw13,
                raw8 * other.raw2 + raw9 * other.raw6 + raw10 * other.raw10 + raw11 * other.raw14,
                raw8 * other.raw3 + raw9 * other.raw7 + raw10 * other.raw11 + raw11 * other.raw15,
                raw12 * other.raw0 + raw13 * other.raw4 + raw14 * other.raw8 + raw15 * other.raw12,
                raw12 * other.raw1 + raw13 * other.raw5 + raw14 * other.raw9 + raw15 * other.raw13,
                raw12 * other.raw2 + raw13 * other.raw6 + raw14 * other.raw10 + raw15 * other.raw14,
                raw12 * other.raw3 + raw13 * other.raw7 + raw14 * other.raw11 + raw15 * other.raw15
        });

        return this;
    }

    /**
     * Multiplies this matrix with the specified vector.
     *
     * @param v the vector to multiply with.
     * @return the product of this matrix and the specified vectors.
     */
    public Vec4d mul(Vec4d v) {
        return new Vec4d(raw0 * v.x + raw1 * v.y + raw2 * v.z + raw3 * v.w,
                raw4 * v.x + raw5 * v.y + raw6 * v.z + raw7 * v.w,
                raw8 * v.x + raw9 * v.y + raw10 * v.z + raw11 * v.w,
                raw12 * v.x + raw13 * v.y + raw14 * v.z + raw15 * v.w);
    }

    /**
     * Multiplies this matrix with the specified scalar value.
     *
     * @param scalar the scalar value to multiply with.
     * @return this matrix.
     */
    public Mat4d mul(double scalar) {
        raw0 *= scalar;
        raw1 *= scalar;
        raw2 *= scalar;
        raw3 *= scalar;
        raw4 *= scalar;
        raw5 *= scalar;
        raw6 *= scalar;
        raw7 *= scalar;
        raw8 *= scalar;
        raw9 *= scalar;
        raw10 *= scalar;
        raw11 *= scalar;
        raw12 *= scalar;
        raw13 *= scalar;
        raw14 *= scalar;
        raw15 *= scalar;
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
        raw3 = raw0 * x + raw1 * y + raw2 * z + raw3;
        raw7 = raw4 * x + raw5 * y + raw6 * z + raw7;
        raw11 = raw8 * x + raw9 * y + raw10 * z + raw11;
        raw15 = raw12 * x + raw13 * y + raw14 * z + raw15;
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
        raw0 *= sx;
        raw1 *= sy;
        raw2 *= sz;
        raw4 *= sx;
        raw5 *= sy;
        raw6 *= sz;
        raw8 *= sx;
        raw9 *= sy;
        raw10 *= sz;
        raw12 *= sx;
        raw13 *= sy;
        raw14 *= sz;
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
        double ys = y * s;
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

        double u = raw0, v = raw1, w = raw2;
        raw0 = u * r0 + v * r4 + w * r8;
        raw1 = u * r1 + v * r5 + w * r9;
        raw2 = u * r2 + v * r6 + w * r10;

        u = raw4;v = raw5;w = raw6;
        raw4 = u * r0 + v * r4 + w * r8;
        raw5 = u * r1 + v * r5 + w * r9;
        raw6 = u * r2 + v * r6 + w * r10;

        u = raw8;v = raw9;w = raw10;
        raw8 = u * r0 + v * r4 + w * r8;
        raw9 = u * r1 + v * r5 + w * r9;
        raw10 = u * r2 + v * r6 + w * r10;

        u = raw12;v = raw13;w = raw14;
        raw12 = u * r0 + v * r4 + w * r8;
        raw13 = u * r1 + v * r5 + w * r9;
        raw14 = u * r2 + v * r6 + w * r10;

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

        double u = raw1, v = raw2;
        raw1 = u * c + v * s;
        raw2 = -u * s + v * c;

        u = raw5;v = raw6;
        raw5 = u * c + v * s;
        raw6 = -u * s + v * c;

        u = raw9;v = raw10;
        raw9 = u * c + v * s;
        raw10 = -u * s + v * c;

        u = raw13;v = raw14;
        raw13 = u * c + v * s;
        raw14 = -u * s + v * c;

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

        double u = raw0, v = raw2;
        raw0 = u * c - v * s;
        raw2 = u * s + v * c;

        u = raw4;v = raw6;
        raw4 = u * c - v * s;
        raw6 = u * s + v * c;

        u = raw8;v = raw10;
        raw8 = u * c - v * s;
        raw10 = u * s + v * c;

        u = raw12;v = raw14;
        raw12 = u * c - v * s;
        raw14 = u * s + v * c;

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

        double u = raw0, v = raw1;
        raw0 = u * c + v * s;
        raw1 = -u * s + v * c;

        u = raw4;v = raw5;
        raw4 = u * c + v * s;
        raw5 = -u * s + v * c;

        u = raw8;v = raw9;
        raw8 = u * c + v * s;
        raw9 = -u * s + v * c;

        u = raw12;v = raw13;
        raw12 = u * c + v * s;
        raw13 = -u * s + v * c;

        return this;
    }

    /**
     * Transposes this matrix.
     *
     * @return this matrix.
     */
    public Mat4d transpose() {
        double swp;

        swp = raw1;
        raw1 = raw4;
        raw4 = swp;

        swp = raw2;
        raw2 = raw8;
        raw8 = swp;

        swp = raw6;
        raw6 = raw9;
        raw9 = swp;

        swp = raw3;
        raw3 = raw12;
        raw12 = swp;

        swp = raw7;
        raw7 = raw13;
        raw13 = swp;

        swp = raw11;
        raw11 = raw14;
        raw14 = swp;

        return this;
    }

    /**
     * Transposes the upper left 3x3 matrix, leaving all other elements as they are.
     *
     * @return this matrix.
     */
    public Mat4d transpose3() {
        double swp;

        swp = raw1;
        raw1 = raw4;
        raw4 = swp;

        swp = raw2;
        raw2 = raw8;
        raw8 = swp;

        swp = raw6;
        raw6 = raw9;
        raw9 = swp;

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
        double m0 = raw5 * raw10 - raw6 * raw9;
        double m1 = raw2 * raw9 - raw1 * raw10;
        double m2 = raw1 * raw6 - raw2 * raw5;
        double m3 = raw6 * raw8 - raw4 * raw10;
        double m4 = raw0 * raw10 - raw2 * raw8;
        double m5 = raw2 * raw4 - raw0 * raw6;
        double m6 = raw4 * raw9 - raw5 * raw8;
        double m7 = raw1 * raw8 - raw0 * raw9;
        double m8 = raw0 * raw5 - raw1 * raw4;

        double det = raw0 * m0 + raw1 * (-m3) + raw2 * m6;
        if (det == 0) return null;

        m0 /= det;m1 /= det;m2 /= det;m3 /= det;m4 /= det;m5 /= det;m6 /= det;m7 /= det;m8 /= det;

        raw0 = m0;
        raw1 = m1;
        raw2 = m2;

        raw4 = m3;
        raw5 = m4;
        raw6 = m5;

        raw8 = m6;
        raw9 = m7;
        raw10 = m8;

        // calculate product of inverse upper 3x3 matrix and translation part
        double u = -raw3, v = -raw7, w = -raw11;

        raw3 = m0 * u + m1 * v + m2 * w; // tx
        raw7 = m3 * u + m4 * v + m5 * w; // ty
        raw11 = m6 * u + m7 * v + m8 * w; // tz

        raw12 = raw13 = raw14 = 0;
        raw15 = 1;

        // assemble inverse matrix

        return this;
    }

    /**
     * Inverts this matrix. See {@link Mat4d#invert()} for a possibly more performant version, depending on the content
     * of the matrix.
     *
     * @return this matrix.
     */
    public Mat4d invertGeneral() {
        double d10d15 = raw10 * raw15 - raw11 * raw14;
        double d06d15 = raw6 * raw15 - raw7 * raw14;
        double d06d11 = raw6 * raw11 - raw7 * raw10;
        double d02d15 = raw2 * raw15 - raw3 * raw14;
        double d02d11 = raw2 * raw11 - raw3 * raw10;
        double d02d07 = raw2 * raw7 - raw3 * raw6;
        double d09d15 = raw9 * raw15 - raw11 * raw13;
        double d05d15 = raw5 * raw15 - raw7 * raw13;
        double d05d11 = raw5 * raw11 - raw7 * raw9;
        double d01d15 = raw1 * raw15 - raw3 * raw13;
        double d01d11 = raw1 * raw11 - raw3 * raw9;
        double d01d07 = raw1 * raw7 - raw3 * raw5;
        double d09d14 = raw9 * raw14 - raw10 * raw13;
        double d05d14 = raw5 * raw14 - raw6 * raw13;
        double d05d10 = raw5 * raw10 - raw6 * raw9;
        double d01d14 = raw1 * raw14 - raw2 * raw13;
        double d01d10 = raw1 * raw10 - raw2 * raw9;
        double d01d06 = raw1 * raw6 - raw2 * raw5;

        double[] tmp = {
                raw5 * d10d15 - raw9 * d06d15 + raw13 * d06d11,
                -raw1 * d10d15 + raw9 * d02d15 - raw13 * d02d11,
                raw1 * d06d15 - raw5 * d02d15 + raw13 * d02d07,
                -raw1 * d06d11 + raw5 * d02d11 - raw9 * d02d07,
                -raw4 * d10d15 + raw8 * d06d15 - raw12 * d06d11,
                raw0 * d10d15 - raw8 * d02d15 + raw12 * d02d11,
                -raw0 * d06d15 + raw4 * d02d15 - raw12 * d02d07,
                raw0 * d06d11 - raw4 * d02d11 + raw8 * d02d07,
                raw4 * d09d15 - raw8 * d05d15 + raw12 * d05d11,
                -raw0 * d09d15 + raw8 * d01d15 - raw12 * d01d11,
                raw0 * d05d15 - raw4 * d01d15 + raw12 * d01d07,
                -raw0 * d05d11 + raw4 * d01d11 - raw8 * d01d07,
                -raw4 * d09d14 + raw8 * d05d14 - raw12 * d05d10,
                raw0 * d09d14 - raw8 * d01d14 + raw12 * d01d10,
                -raw0 * d05d14 + raw4 * d01d14 - raw12 * d01d06,
                raw0 * d05d10 - raw4 * d01d10 + raw8 * d01d06
        };

        double det = raw0 * tmp[0] + raw1 * tmp[4] + raw2 * tmp[8] + raw3 * tmp[12];
        if (det == 0) return null;

        for (int i = 0; i < 16; i++)
            tmp[i] /= det;

        set(tmp);

        return this;
    }

    /**
     * Creates a new matrix-instance containing the identity-matrix.
     *
     * @return a new identity matrix.
     */
    public static Mat4d identity() {
        return new Mat4d().makeIdentity();
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
        double x = m.raw0 * v.x + m.raw1 * v.y + m.raw2 * v.z + m.raw3 * v.w;
        double y = m.raw4 * v.x + m.raw5 * v.y + m.raw6 * v.z + m.raw7 * v.w;
        double z = m.raw8 * v.x + m.raw9 * v.y + m.raw10 * v.z + m.raw11 * v.w;
        double w = m.raw12 * v.x + m.raw13 * v.y + m.raw14 * v.z + m.raw15 * v.w;

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
        return raw0 == other.raw0
                && raw1 == other.raw1
                && raw2 == other.raw2
                && raw3 == other.raw3
                && raw4 == other.raw4
                && raw5 == other.raw5
                && raw6 == other.raw6
                && raw7 == other.raw7
                && raw8 == other.raw8
                && raw9 == other.raw9
                && raw10 == other.raw10
                && raw11 == other.raw11
                && raw12 == other.raw12
                && raw13 == other.raw13
                && raw14 == other.raw14
                && raw15 == other.raw15;
    }

    @Override
    public int hashCode() {
        return new Hasher()
                .add(raw0).add(raw1).add(raw2).add(raw3)
                .add(raw4).add(raw5).add(raw6).add(raw7)
                .add(raw8).add(raw9).add(raw10).add(raw11)
                .add(raw12).add(raw13).add(raw14).add(raw15)
                .getHash();
    }
}
