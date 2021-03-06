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
 * A row-major 4x4 {@code float} matrix.
 *
 * @author Maximilian Luz
 */
public class Mat4f implements Cloneable {
    public Mat4f() {
        m00 = m11 = m22 = m33 = 1;
    }

    public float m00, m01, m02, m03,
            m10, m11, m12, m13,
            m20, m21, m22, m23,
            m30, m31, m32, m33;


    /**
     * Constructs a new matrix with the given properties.
     */
    public Mat4f(float a11, float a12, float a13, float a14,
                 float a21, float a22, float a23, float a24,
                 float a31, float a32, float a33, float a34,
                 float a41, float a42, float a43, float a44) {

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
    public Mat4f(float[] matrix) {
        set(matrix);
    }

    /**
     * Construct a new matrix by copying the specified one.
     *
     * @param matrix the matrix to be copied.
     */
    public Mat4f(Mat4f matrix) {
        set(matrix);
    }

    public Mat4d toDoubleMatrix() {
        return new Mat4d(m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23,
                m30, m31, m32, m33);
    }

    /**
     * Sets this matrix using the specified parameters.
     *
     * @return this matrix.
     */
    public Mat4f set(float a11, float a12, float a13, float a14,
                     float a21, float a22, float a23, float a24,
                     float a31, float a32, float a33, float a34,
                     float a41, float a42, float a43, float a44) {

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
    public Mat4f set(float[] raw) {
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
    public Mat4f set(Mat4f other) {
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
    public Mat4f makeIdentity() {
        m00 = m11 = m22 = m33 = 1;
        m01 = m02 = m03 = m10 = m12 = m13 = m20 = m21 = m23 = m30 = m31 = m32 = 0;
        return this;
    }

    /**
     * Sets this matrix to the orthographic projection matrix specified by the given parameters.
     *
     * @param left ??? ?????????????????????
     * @param right ??? ?????????????????????
     * @param top ??? ?????????????????????
     * @param bottom ??? ?????????????????????
     * @param near ??? ?????????????????????
     * @param far ??? ?????????????????????
     * <BR>
     * ???????????????????????????viewing frustum??????????????????
     * @return this matrix.
     */
    public Mat4f makeOrtho(float left, float right, float top, float bottom, float near, float far) {
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
    public Mat4f makePerspective(float fovy, float aspectRatio, float zNear, float zFar) {
        float f = (float) (1 / Math.tan(Math.toRadians(fovy / 2)));

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
    public Mat4f makeLookAt(Vec3f eye, Vec3f center, Vec3f up) {
        return makeLookInDirection(eye, Vec3f.sub(center, eye), up);
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
    public Mat4f makeLookInDirection(Vec3f eye, Vec3f dir, Vec3f up) {
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
    public float det3() {
        return m00 * (m11 * m22 - m12 * m21)
                - m01 * (m10 * m22 - m12 * m20)
                + m02 * (m10 * m21 - m11 * m20);
    }

    /**
     * Calculates the determinant of this matrix.
     *
     * @return the calculated determinant.
     */
    public float det() {
        // TODO: test!!
        // 2x2 determinants enumerated from left to right
        float d1 = m20 * m31 - m21 * m30;
        float d2 = m20 * m32 - m22 * m30;
        float d3 = m20 * m33 - m23 * m30;
        float d4 = m21 * m32 - m22 * m31;
        float d5 = m21 * m33 - m23 * m31;
        float d6 = m22 * m33 - m23 * m32;

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
    public Mat4f add(Mat4f other) {
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
    public Mat4f sub(Mat4f other) {
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
    public Mat4f mul(Mat4f o) {
        float r0 = m00, r1 = m01, r2 = m02, r3 = m03;

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

    public Vec4f mul(Vec4f v) {
        return mul(v, new Vec4f());
    }

    public Vec3f mul(Vec3f v) {
        return mul(v, new Vec3f());
    }

    /**
     * Multiplies this matrix with the specified vector.
     *
     * @param v the vector to multiply with.
     * @return the product of this matrix and the specified vectors.
     */
    public Vec4f mul(Vec4f v, Vec4f to) {
        float x = v.x, y = v.y, z = v.z, w = v.w;
        return to.set(m00 * x + m01 * y + m02 * z + m03 * w,
                      m10 * x + m11 * y + m12 * z + m13 * w,
                      m20 * x + m21 * y + m22 * z + m23 * w,
                      m30 * x + m31 * y + m32 * z + m33 * w);
    }

    /**
     * Multiplies this matrix with the specified vector.
     *
     * @param v the vector to multiply with.
     * @return the product of this matrix and the specified vectors.
     */
    public Vec3f mul(Vec3f v, Vec3f to) {
        float x = v.x, y = v.y, z = v.z;
        return to.set(m00 * x + m01 * y + m02 * z,
                      m10 * x + m11 * y + m12 * z,
                      m20 * x + m21 * y + m22 * z);
    }

    /**
     * Multiplies this matrix with the specified scalar value.
     *
     * @param scalar the scalar value to multiply with.
     * @return this matrix.
     */
    public Mat4f mul(float scalar) {
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
    public Mat4f translate(Vec3f vec) {
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
    public Mat4f translate(float x, float y, float z) {
        m03 = m00 * x + m01 * y + m02 * z + m03;
        m13 = m10 * x + m11 * y + m12 * z + m13;
        m23 = m20 * x + m21 * y + m22 * z + m23;
        m33 = m30 * x + m31 * y + m32 * z + m33;
        return this;
    }

    /**
     * Applies an absolute translation to this matrix.
     *
     * @see #translate(float, float, float)
     * @return this matrix.
     */
    public Mat4f translateAbs(float x, float y, float z) {
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
    public Mat4f resetTranslation() {
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
    public Mat4f scale(Vec3f vec) {
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
    public Mat4f scale(float sx, float sy, float sz) {
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
    public Mat4f rotate(Vec3f axis, float angle) {
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
    public Mat4f rotate(float x, float y, float z, float angle) {
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
     * @see #rotate(float, float, float, float)
     * @return this matrix.
     */
    public Mat4f rotateAbs(float x, float y, float z, float angle) {
        float s = MathUtils.sin(angle);
        float c = MathUtils.cos(angle);
        float omc = 1.0f - c;

        float xy = x * y;
        float zs = z * s;

        m00 = ( x * x ) * omc + c;
        m01 = xy * omc - zs;
        float xz = x * z;
        float ys = y * s;
        m02 = xz * omc + ys;

        m10 = xy * omc + zs;
        m11 = ( y * y ) * omc + c;
        float yz = y * z;
        float xs = x * s;
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
    public Mat4f rotateX(float angle) {
        float s = MathUtils.sin(angle);
        float c = MathUtils.cos(angle);

        float u = m01, v = m02;
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
    public Mat4f rotateY(float angle) {
        float s = MathUtils.sin(angle);
        float c = MathUtils.cos(angle);

        float u = m00, v = m02;
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
    public Mat4f rotateZ(float angle) {
        float s = MathUtils.sin(angle);
        float c = MathUtils.cos(angle);

        float u = m00, v = m01;
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
    public Mat4f transpose() {
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
    public Mat4f transpose3() {
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
    public Mat4f invert() {
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
    public Mat4f invertAffine() {
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
        float u = -m03, v = -m13, w = -m23;

        m03 = m0 * u + m1 * v + m2 * w; // tx
        m13 = m3 * u + m4 * v + m5 * w; // ty
        m23 = m6 * u + m7 * v + m8 * w; // tz

        m30 = m31 = m32 = 0;
        m33 = 1;

        // assemble inverse matrix

        return this;
    }

    /**
     * Inverts this matrix. See {@link Mat4f#invert()} for a possibly more performant version, depending on the content
     * of the matrix.
     *
     * @return this matrix.
     */
    public Mat4f invertGeneral() {
        float d10d15 = m22 * m33 - m23 * m32;
        float d06d15 = m12 * m33 - m13 * m32;
        float d06d11 = m12 * m23 - m13 * m22;
        float d02d15 = m02 * m33 - m03 * m32;
        float d02d11 = m02 * m23 - m03 * m22;
        float d02d07 = m02 * m13 - m03 * m12;
        float d09d15 = m21 * m33 - m23 * m31;
        float d05d15 = m11 * m33 - m13 * m31;
        float d05d11 = m11 * m23 - m13 * m21;
        float d01d15 = m01 * m33 - m03 * m31;
        float d01d11 = m01 * m23 - m03 * m21;
        float d01d07 = m01 * m13 - m03 * m11;
        float d09d14 = m21 * m32 - m22 * m31;
        float d05d14 = m11 * m32 - m12 * m31;
        float d05d10 = m11 * m22 - m12 * m21;
        float d01d14 = m01 * m32 - m02 * m31;
        float d01d10 = m01 * m22 - m02 * m21;
        float d01d06 = m01 * m12 - m02 * m11;

        float[] tmp = {
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

        float det = m00 * tmp[0] + m01 * tmp[4] + m02 * tmp[8] + m03 * tmp[12];
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
    public static Mat4f identity() {
        return new Mat4f().makeIdentity();
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
    public static Mat4f ortho(float left, float right, float top, float bottom, float near, float far) {
        return new Mat4f().makeOrtho(left, right, top, bottom, near, far);
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
    public static Mat4f perspecive(float fovy, float aspectRatio, float zNear, float zFar) {
        return new Mat4f().makePerspective(fovy, aspectRatio, zNear, zFar);
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
    public static Mat4f lookAt(Vec3f eye, Vec3f center, Vec3f up) {
        return new Mat4f().makeLookAt(eye, center, up);
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
    public static Mat4f lookInDirection(Vec3f eye, Vec3f dir, Vec3f up) {
        return new Mat4f().makeLookInDirection(eye, dir, up);
    }

    /**
     * Adds the specified matrices and returns the result as new matrix.
     *
     * @param a the first matrix.
     * @param b the second matrix.
     * @return the result of this addition, i.e. {@code a + b}.
     */
    public static Mat4f add(Mat4f a, Mat4f b) {
        return new Mat4f(a).add(b);
    }

    /**
     * Subtracts the specified matrices and returns the result as new matrix.
     *
     * @param a the matrix to subtract from.
     * @param b the matrix to subtract.
     * @return the result of this subtraction, i.e. {@code a - b}.
     */
    public static Mat4f sub(Mat4f a, Mat4f b) {
        return new Mat4f(a).sub(b);
    }

    /**
     * Multiplies the specified matrices and returns the result as new matrix.
     *
     * @param a the first matrix.
     * @param b the second matrix.
     * @return the result of this multiplication, i.e. {@code a * b}.
     */
    public static Mat4f mul(Mat4f a, Mat4f b) {
        return new Mat4f(a).mul(b);
    }

    /**
     * Multiplies the specified matrix with the specified vector in place, storing the result in the specified vector.
     *
     * @param m the matrix to multiply with.
     * @param v the vector to multiply with.
     * @return {@code v}.
     */
    public static Vec4f mulInPlace(Mat4f m, Vec4f v) {
        float x = m.m00 * v.x + m.m01 * v.y + m.m02 * v.z + m.m03 * v.w;
        float y = m.m10 * v.x + m.m11 * v.y + m.m12 * v.z + m.m13 * v.w;
        float z = m.m20 * v.x + m.m21 * v.y + m.m22 * v.z + m.m23 * v.w;
        float w = m.m30 * v.x + m.m31 * v.y + m.m32 * v.z + m.m33 * v.w;

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
    public static Mat4f mul(Mat4f m, float scalar) {
        return new Mat4f(m).mul(scalar);
    }

    /**
     * Applies the specified translation to the specified matrix as if by multiplying the matrix with the according
     * translation matrix (i. e. {@code m * translation}).
     *
     * @param m the matrix to be transformed.
     * @param v the vector specifying the translation.
     * @return the result of this transformation.
     */
    public static Mat4f translate(Mat4f m, Vec3f v) {
        return new Mat4f(m).translate(v);
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
    public static Mat4f translate(Mat4f m, float x, float y, float z) {
        return new Mat4f(m).translate(x, y, z);
    }

    /**
     * Applies the specified scaling-operation to the specified matrix as if by multiplying this matrix with the
     * according scale matrix (i. e. {@code m * scale}).
     *
     * @param m the matrix to be transformed.
     * @param v the vector specifying the scale-transformation.
     * @return the result of this transformation.
     */
    public static Mat4f scale(Mat4f m, Vec3f v) {
        return new Mat4f(m).scale(v);
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
    public static Mat4f scale(Mat4f m, float x, float y, float z) {
        return new Mat4f(m).scale(x, y, z);
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
    public static Mat4f rotate(Mat4f m, Vec3f axis, float angle) {
        return new Mat4f(m).rotate(axis, angle);
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
    public static Mat4f rotate(Mat4f m, float x, float y, float z, float angle) {
        return new Mat4f(m).rotate(x, y, z, angle);
    }

    /**
     * Applies the specified {@code x}-axis rotation-operation to the specified matrix as if by multiplying this matrix
     * with the according rotation matrix (i. e. {@code m * rotation}).
     *
     * @param m     the matrix to be transformed.
     * @param angle the angle (in radians) specifying the rotation around the {@code x}-axis.
     * @return the result of this transformation.
     */
    public static Mat4f rotateX(Mat4f m, float angle) {
        return new Mat4f(m).rotateX(angle);
    }

    /**
     * Applies the specified {@code y}-axis rotation-operation to the specified matrix as if by multiplying this matrix
     * with the according rotation matrix (i. e. {@code m * rotation}).
     *
     * @param m     the matrix to be transformed.
     * @param angle the angle (in radians) specifying the rotation around the {@code y}-axis.
     * @return the result of this transformation.
     */
    public static Mat4f rotateY(Mat4f m, float angle) {
        return new Mat4f(m).rotateY(angle);
    }

    /**
     * Applies the specified {@code z}-axis rotation-operation to the specified matrix as if by multiplying this matrix
     * with the according rotation matrix (i. e. {@code m * rotation}).
     *
     * @param m     the matrix to be transformed.
     * @param angle the angle (in radians) specifying the rotation around the {@code z}-axis.
     * @return the result of this transformation.
     */
    public static Mat4f rotateZ(Mat4f m, float angle) {
        return new Mat4f(m).rotateZ(angle);
    }

    /**
     * Transposes the specified matrix.
     *
     * @param m the matrix to be transposed.
     * @return the transposed matrix.
     */
    public static Mat4f transpose(Mat4f m) {
        return new Mat4f(m).transpose();
    }

    /**
     * Inverts the specified matrix and returns the result as new matrix.
     *
     * @param m the matrix to be transposed.
     * @return the inverted matrix.
     */
    public static Mat4f invert(Mat4f m) {
        return new Mat4f(m).invert();
    }

    /**
     * Inverts the specified matrix as if it were an affine matrix and returns the result an new matrix.
     *
     * @param m the matrix to be transposed.
     * @return the inverted matrix.
     */
    public static Mat4f invertAffine(Mat4f m) {
        return new Mat4f(m).invertAffine();
    }

    /**
     * Inverts the specified matrix and returns the result as new matrix. See {@link Mat4f#invert(Mat4f)} for a
     * possibly more efficient version, depending on the contents of the matrix.
     *
     * @param m the matrix to be transposed.
     * @return the inverted matrix.
     */
    public static Mat4f invertGeneral(Mat4f m) {
        return new Mat4f(m).invertGeneral();
    }

    public static Mat4f mix(Mat4f a, Mat4f b, float percentA, Mat4f store) {
        float percentB = 1 - percentA;

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
        if (!(obj instanceof Mat4f)) return false;

        Mat4f other = (Mat4f) obj;
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
        return "Mat4f{" + m00 + "," + m01 + "," + m02 + "," + m03 + ",\n" +
            m10 + "," + m11 + "," + m12 + "," + m13 + ",\n" +
            m20 + "," + m21 + "," + m22 + "," + m23 + ",\n" +
            m30 + "," + m31 + "," + m32 + "," + m33 + '}';
    }
}
