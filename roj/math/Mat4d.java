/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.math;

import roj.util.Hasher;


/**
 * A row-major 4x4 {@code double} matrix.
 *
 * @author Maximilian Luz
 */
public class Mat4d implements Cloneable {
    public Mat4d() {}

    public double m00, m01, m02, m03,
            m10, m11, m12, m13,
            m20, m21, m22, m23,
            m30, m31, m32, m33;


    /**
     * Constructs a new matrix with the given properties.
     */
    public Mat4d(double a11, double a12, double a13, double a14,
                 double a21, double a22, double a23, double a24,
                 double a31, double a32, double a33, double a34,
                 double a41, double a42, double a43, double a44) {

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
        m30 = a41;
        m31 = a42;
        m32 = a43;
        m33 = a44;
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
        return new Mat4f((float) m00, (float) m01, (float) m02, (float) m03,
                (float) m10, (float) m11, (float) m12, (float) m13,
                (float) m20, (float) m21, (float) m22, (float) m23,
                (float) m30, (float) m31, (float) m32, (float) m33);
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
        m30 = a41;
        m31 = a42;
        m32 = a43;
        m33 = a44;

        return this;
    }

    /**
     * Sets this matrix using the given array. The specified array is interpreted row-major wise.
     *
     * @param raw the (at least) 16 element array from which the matrix should be created.
     * @return this matrix.
     */
    public Mat4d set(double[] raw) {
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
        m30 = raw[12];
        m31 = raw[13];
        m32 = raw[14];
        m33 = raw[15];
        return this;
    }

    /**
     * Sets this matrix by copying the specified one.
     *
     * @param other the matrix to be copied.
     * @return this matrix.
     */
    public Mat4d set(Mat4d other) {
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
        m30 = other.m30;
        m31 = other.m31;
        m32 = other.m32;
        m33 = other.m33;
        return this;
    }

    /**
     * Sets this matrix to the identity-matrix.
     *
     * @return this matrix.
     */
    public Mat4d makeIdentity() {
        m00 = m11 = m22 = m33 = 1;
        m01 = m02 = m03 = m10 = m12 = m13 = m20 = m21 = m23 = m30 = m31 = m32 = 0;
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

        m30 = 0;
        m31 = 0;
        m32 = 0;
        m33 = 1;

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
        double f = 1 / Math.tan(Math.toRadians(fovy / 2));

        m01 = 0;
        m02 = 0;
        m03 = 0;
        m10 = 0;
        m12 = 0;
        m13 = 0;
        m20 = 0;
        m21 = 0;
        m30 = 0;
        m31 = 0;
        m33 = 0;

        m00 = f / aspectRatio;
        m11 = f;
        m22 = (zFar + zNear) / (zNear - zFar);
        m23 = (2 * zNear * zFar) / (zNear - zFar);
        m32 = -1;

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
        m30 = 0;
        m31 = 0;
        m32 = 0;
        m33 = 1;

        return this.translate(-eye.x, -eye.y, -eye.z);
    }

    /**
     * Calculates the determinant of the upper left 3x3 sub-matrix.
     *
     * @return the calculated determinant of the upper left 3x3 sub-matrix.
     */
    public double det3() {
        return m00 * (m11 * m22 - m12 * m21)
                - m01 * (m10 * m22 - m12 * m20)
                + m02 * (m10 * m21 - m11 * m20);
    }

    /**
     * Calculates the determinant of this matrix.
     *
     * @return the calculated determinant.
     */
    public double det() {
        // TODO: test!!
        // 2x2 determinants enumerated from left to right
        double d1 = m20 * m31 - m21 * m30;
        double d2 = m20 * m32 - m22 * m30;
        double d3 = m20 * m33 - m23 * m30;
        double d4 = m21 * m32 - m22 * m31;
        double d5 = m21 * m33 - m23 * m31;
        double d6 = m22 * m33 - m23 * m32;

        return m00 * (m11 * d6 - m12 * d5 + m13 * d4)
                - m01 * (m10 * d6 - m12 * d3 + m13 * d2)
                + m02 * (m10 * d5 - m11 * d3 + m13 * d1)
                - m03 * (m10 * d4 - m11 * d2 + m12 * d1);
    }

    /**
     * Tests whether this matrix is affine or not.
     *
     * @return {@code true} iff this matrix is affine.
     */
    public boolean isAffine() {
        return m30 == 0.0f && m31 == 0.0f && m32 == 0.0f && m33 == 1.0f;
    }

    /**
     * Adds the specified matrix to this matrix and stores the result in this matrix.
     *
     * @param other the matrix to add.
     * @return this matrix.
     */
    public Mat4d add(Mat4d other) {
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
        m30 += other.m30;
        m31 += other.m31;
        m32 += other.m32;
        m33 += other.m33;
        return this;
    }

    /**
     * Subtracts the specified matrix from this matrix and stores the result in this matrix.
     *
     * @param other the matrix to subtract.
     * @return this matrix.
     */
    public Mat4d sub(Mat4d other) {
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
        m30 -= other.m30;
        m31 -= other.m31;
        m32 -= other.m32;
        m33 -= other.m33;
        return this;
    }

    /**
     * Multiplies this matrix with the specified matrix, i.e. {@code this * o}.
     *
     * @param o the matrix to multiply with.
     * @return this matrix.
     */
    public Mat4d mul(Mat4d o) {
        double r0 = m00, r1 = m01, r2 = m02, r3 = m03;

        m00 = r0 * o.m00 + r1 * o.m10 + r2 * o.m20 + r3 * o.m30;
        m01 = r0 * o.m01 + r1 * o.m11 + r2 * o.m21 + r3 * o.m31;
        m02 = r0 * o.m02 + r1 * o.m12 + r2 * o.m22 + r3 * o.m32;
        m03 = r0 * o.m03 + r1 * o.m13 + r2 * o.m23 + r3 * o.m33;

        r0 = m10; r1 = m11; r2 = m12; r3 = m13;
        m10 = r0 * o.m00 + r1 * o.m10 + r2 * o.m20 + r3 * o.m30;
        m11 = r0 * o.m01 + r1 * o.m11 + r2 * o.m21 + r3 * o.m31;
        m12 = r0 * o.m02 + r1 * o.m12 + r2 * o.m22 + r3 * o.m32;
        m13 = r0 * o.m03 + r1 * o.m13 + r2 * o.m23 + r3 * o.m33;

        r0 = m20; r1 = m21; r2 = m22; r3 = m23;
        m20 = r0 * o.m00 + r1 * o.m10 + r2 * o.m20 + r3 * o.m30;
        m21 = r0 * o.m01 + r1 * o.m11 + r2 * o.m21 + r3 * o.m31;
        m22 = r0 * o.m02 + r1 * o.m12 + r2 * o.m22 + r3 * o.m32;
        m23 = r0 * o.m03 + r1 * o.m13 + r2 * o.m23 + r3 * o.m33;

        r0 = m30; r1 = m31; r2 = m32; r3 = m33;
        m30 = r0 * o.m00 + r1 * o.m10 + r2 * o.m20 + r3 * o.m30;
        m31 = r0 * o.m01 + r1 * o.m11 + r2 * o.m21 + r3 * o.m31;
        m32 = r0 * o.m02 + r1 * o.m12 + r2 * o.m22 + r3 * o.m32;
        m33 = r0 * o.m03 + r1 * o.m13 + r2 * o.m23 + r3 * o.m33;

        return this;
    }

    /**
     * Multiplies this matrix with the specified vector.
     *
     * @param v the vector to multiply with.
     * @return the product of this matrix and the specified vectors.
     */
    public Vec4d mul(Vec4d v) {
        return new Vec4d(m00 * v.x + m01 * v.y + m02 * v.z + m03 * v.w,
                m10 * v.x + m11 * v.y + m12 * v.z + m13 * v.w,
                m20 * v.x + m21 * v.y + m22 * v.z + m23 * v.w,
                m30 * v.x + m31 * v.y + m32 * v.z + m33 * v.w);
    }

    /**
     * Multiplies this matrix with the specified scalar value.
     *
     * @param scalar the scalar value to multiply with.
     * @return this matrix.
     */
    public Mat4d mul(double scalar) {
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
        m30 *= scalar;
        m31 *= scalar;
        m32 *= scalar;
        m33 *= scalar;
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
        m03 = m00 * x + m01 * y + m02 * z + m03;
        m13 = m10 * x + m11 * y + m12 * z + m13;
        m23 = m20 * x + m21 * y + m22 * z + m23;
        m33 = m30 * x + m31 * y + m32 * z + m33;
        return this;
    }

    /**
     * Applies an absolute translation to this matrix.
     *
     * @see #translate(double, double, double)
     * @return this matrix.
     */
    public Mat4d translateAbs(double x, double y, double z) {
        m03 = m00 * x + m01 * y + m02 * z;
        m13 = m10 * x + m11 * y + m12 * z;
        m23 = m20 * x + m21 * y + m22 * z;
        m33 = m30 * x + m31 * y + m32 * z;
        return this;
    }

    /**
     * Reset this matrix's translation.
     * @return this matrix.
     */
    public Mat4d resetTranslation() {
        m03 = 0;
        m13 = 0;
        m23 = 0;
        m33 = 0;
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
        m00 *= sx;
        m01 *= sy;
        m02 *= sz;
        m10 *= sx;
        m11 *= sy;
        m12 *= sz;
        m20 *= sx;
        m21 *= sy;
        m22 *= sz;
        m30 *= sx;
        m31 *= sy;
        m32 *= sz;
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

        u = m10;v = m11;w = m12;
        m10 = u * r0 + v * r4 + w * r8;
        m11 = u * r1 + v * r5 + w * r9;
        m12 = u * r2 + v * r6 + w * r10;

        u = m20;v = m21;w = m22;
        m20 = u * r0 + v * r4 + w * r8;
        m21 = u * r1 + v * r5 + w * r9;
        m22 = u * r2 + v * r6 + w * r10;

        u = m30;v = m31;w = m32;
        m30 = u * r0 + v * r4 + w * r8;
        m31 = u * r1 + v * r5 + w * r9;
        m32 = u * r2 + v * r6 + w * r10;

        return this;
    }

    /**
     * Applies an absolute rotation-operation to this matrix.
     * @see #rotate(double, double, double, double)
     * @return this matrix.
     */
    public Mat4d rotateAbs(double x, double y, double z, double angle) {
        double s = MathUtils.sin(angle);
        double c = MathUtils.cos(angle);
        double omc = 1.0f - c;

        double xy = x * y;
        double zs = z * s;

        m00 = ( x * x ) * omc + c;
        m01 = xy * omc - zs;
        double xz = x * z;
        double ys = y * s;
        m02 = xz * omc + ys;

        m10 = xy * omc + zs;
        m11 = ( y * y ) * omc + c;
        double yz = y * z;
        double xs = x * s;
        m12 = yz * omc - xs;

        m20 = xz * omc - ys;
        m21 = yz * omc + xs;
        m22 = ( z * z ) * omc + c;

        // 3 7 11 15 => translation

        m30 = m31 = m32 = 0;

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
        double s = MathUtils.sin(angle);
        double c = MathUtils.cos(angle);

        double u = m01, v = m02;
        m01 = u * c + v * s;
        m02 = -u * s + v * c;

        u = m11;v = m12;
        m11 = u * c + v * s;
        m12 = -u * s + v * c;

        u = m21;v = m22;
        m21 = u * c + v * s;
        m22 = -u * s + v * c;

        u = m31;v = m32;
        m31 = u * c + v * s;
        m32 = -u * s + v * c;

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
        double s = MathUtils.sin(angle);
        double c = MathUtils.cos(angle);

        double u = m00, v = m02;
        m00 = u * c - v * s;
        m02 = u * s + v * c;

        u = m10;v = m12;
        m10 = u * c - v * s;
        m12 = u * s + v * c;

        u = m20;v = m22;
        m20 = u * c - v * s;
        m22 = u * s + v * c;

        u = m30;v = m32;
        m30 = u * c - v * s;
        m32 = u * s + v * c;

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
        double s = MathUtils.sin(angle);
        double c = MathUtils.cos(angle);

        double u = m00, v = m01;
        m00 = u * c + v * s;
        m01 = -u * s + v * c;

        u = m10;v = m11;
        m10 = u * c + v * s;
        m11 = -u * s + v * c;

        u = m20;v = m21;
        m20 = u * c + v * s;
        m21 = -u * s + v * c;

        u = m30;v = m31;
        m30 = u * c + v * s;
        m31 = -u * s + v * c;

        return this;
    }

    /**
     * Transposes this matrix.
     *
     * @return this matrix.
     */
    public Mat4d transpose() {
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

        swp = m03;
        m03 = m30;
        m30 = swp;

        swp = m13;
        m13 = m31;
        m31 = swp;

        swp = m23;
        m23 = m32;
        m32 = swp;

        return this;
    }

    /**
     * Transposes the upper left 3x3 matrix, leaving all other elements as they are.
     *
     * @return this matrix.
     */
    public Mat4d transpose3() {
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

        m0 /= det;m1 /= det;m2 /= det;m3 /= det;m4 /= det;m5 /= det;m6 /= det;m7 /= det;m8 /= det;

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

        m30 = m31 = m32 = 0;
        m33 = 1;

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
        double d10d15 = m22 * m33 - m23 * m32;
        double d06d15 = m12 * m33 - m13 * m32;
        double d06d11 = m12 * m23 - m13 * m22;
        double d02d15 = m02 * m33 - m03 * m32;
        double d02d11 = m02 * m23 - m03 * m22;
        double d02d07 = m02 * m13 - m03 * m12;
        double d09d15 = m21 * m33 - m23 * m31;
        double d05d15 = m11 * m33 - m13 * m31;
        double d05d11 = m11 * m23 - m13 * m21;
        double d01d15 = m01 * m33 - m03 * m31;
        double d01d11 = m01 * m23 - m03 * m21;
        double d01d07 = m01 * m13 - m03 * m11;
        double d09d14 = m21 * m32 - m22 * m31;
        double d05d14 = m11 * m32 - m12 * m31;
        double d05d10 = m11 * m22 - m12 * m21;
        double d01d14 = m01 * m32 - m02 * m31;
        double d01d10 = m01 * m22 - m02 * m21;
        double d01d06 = m01 * m12 - m02 * m11;

        double[] tmp = {
                m11 * d10d15 - m21 * d06d15 + m31 * d06d11,
                -m01 * d10d15 + m21 * d02d15 - m31 * d02d11,
                m01 * d06d15 - m11 * d02d15 + m31 * d02d07,
                -m01 * d06d11 + m11 * d02d11 - m21 * d02d07,
                -m10 * d10d15 + m20 * d06d15 - m30 * d06d11,
                m00 * d10d15 - m20 * d02d15 + m30 * d02d11,
                -m00 * d06d15 + m10 * d02d15 - m30 * d02d07,
                m00 * d06d11 - m10 * d02d11 + m20 * d02d07,
                m10 * d09d15 - m20 * d05d15 + m30 * d05d11,
                -m00 * d09d15 + m20 * d01d15 - m30 * d01d11,
                m00 * d05d15 - m10 * d01d15 + m30 * d01d07,
                -m00 * d05d11 + m10 * d01d11 - m20 * d01d07,
                -m10 * d09d14 + m20 * d05d14 - m30 * d05d10,
                m00 * d09d14 - m20 * d01d14 + m30 * d01d10,
                -m00 * d05d14 + m10 * d01d14 - m30 * d01d06,
                m00 * d05d10 - m10 * d01d10 + m20 * d01d06
        };

        double det = m00 * tmp[0] + m01 * tmp[4] + m02 * tmp[8] + m03 * tmp[12];
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
        double x = m.m00 * v.x + m.m01 * v.y + m.m02 * v.z + m.m03 * v.w;
        double y = m.m10 * v.x + m.m11 * v.y + m.m12 * v.z + m.m13 * v.w;
        double z = m.m20 * v.x + m.m21 * v.y + m.m22 * v.z + m.m23 * v.w;
        double w = m.m30 * v.x + m.m31 * v.y + m.m32 * v.z + m.m33 * v.w;

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

    public static Mat4d mix(Mat4d a, Mat4d b, double percentA, Mat4d store) {
        double percentB = 1 - percentA;

        store.m00 = a.m00 * percentA +  b.m00 * percentB;
        store.m01 = a.m01 * percentA +  b.m01 * percentB;
        store.m02 = a.m02 * percentA +  b.m02 * percentB;
        store.m03 = a.m03 * percentA +  b.m03 * percentB;
        store.m10 = a.m10 * percentA +  b.m10 * percentB;
        store.m11 = a.m11 * percentA +  b.m11 * percentB;
        store.m12 = a.m12 * percentA +  b.m12 * percentB;
        store.m13 = a.m13 * percentA +  b.m13 * percentB;
        store.m20 = a.m20 * percentA +  b.m20 * percentB;
        store.m21 = a.m21 * percentA +  b.m21 * percentB;
        store.m22 = a.m22 * percentA + b.m22 * percentB;
        store.m23 = a.m23 * percentA + b.m23 * percentB;
        store.m30 = a.m30 * percentA + b.m30 * percentB;
        store.m31 = a.m31 * percentA + b.m31 * percentB;
        store.m32 = a.m32 * percentA + b.m32 * percentB;
        store.m33 = a.m33 * percentA + b.m33 * percentB;

        return store;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Mat4d)) return false;

        Mat4d other = (Mat4d) obj;
        return m00 == other.m00
                && m01 == other.m01
                && m02 == other.m02
                && m03 == other.m03
                && m10 == other.m10
                && m11 == other.m11
                && m12 == other.m12
                && m13 == other.m13
                && m20 == other.m20
                && m21 == other.m21
                && m22 == other.m22
                && m23 == other.m23
                && m30 == other.m30
                && m31 == other.m31
                && m32 == other.m32
                && m33 == other.m33;
    }

    @Override
    public int hashCode() {
        return new Hasher()
                .add(m00).add(m01).add(m02).add(m03)
                .add(m10).add(m11).add(m12).add(m13)
                .add(m20).add(m21).add(m22).add(m23)
                .add(m30).add(m31).add(m32).add(m33)
                .getHash();
    }

    @Override
    public String toString() {
        return "Mat4d{" + m00 + "," + m01 + "," + m02 + "," + m03 + ",\n" +
                m10 + "," + m11 + "," + m12 + "," + m13 + ",\n" +
                m20 + "," + m21 + "," + m22 + "," + m23 + ",\n" +
                m30 + "," + m31 + "," + m32 + "," + m33 + '}';
    }
}
