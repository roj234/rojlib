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
 * A vector containing three {@code double}s.
 *
 * @author Maximilian Luz
 */
public class Vec3d implements Vector {
    public double x, y, z;


    /**
     * Constructs a new vector and initializes the {@code x}-, {@code y}- and {@code z}-components to zero.
     */
    public Vec3d() {
        this(0, 0, 0);
    }

    /**
     * Constructs a new vector with the given values.
     *
     * @param x the {@code x}-component
     * @param y the {@code y}-component
     * @param z the {@code z}-component
     */
    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Constructs a new vector with the given values.
     *
     * @param xy the {@code x}- and {@code y}-components.
     * @param z  the {@code z}-components.
     */
    public Vec3d(Vec2d xy, double z) {
        this(xy.x, xy.y, z);
    }

    /**
     * Constructs a new vector by copying the specified one.
     *
     * @param xyz the vector from which the values should be copied.
     */
    public Vec3d(Vec3i xyz) {
        this(xyz.x, xyz.y, xyz.z);
    }

    /**
     * Constructs a new vector by copying the specified one.
     *
     * @param xyz the vector from which the values should be copied.
     */
    public Vec3d(Vec3d xyz) {
        this(xyz.x, xyz.y, xyz.z);
    }

    /**
     * Constructs a new vector by copying the the specified one, transforms floats to doubles.
     *
     * @param xyz the vector from which the values should be copied.
     */
    public Vec3d(Vec3f xyz) {
        this(xyz.x, xyz.y, xyz.z);
    }

    /**
     * 使用欧拉角初始化一个单位向量
     * @param yaw 旋转X
     * @param pitch 旋转Y
     */
    public Vec3d(double yaw, double pitch) {
        y = -MathUtils.sin(pitch);

        double xz = MathUtils.cos(pitch);
        x = -xz * MathUtils.sin(yaw);
        z = xz * MathUtils.cos(yaw);

        /** 左手系
            x = MathUtils.cos(yaw) * xz;
            y = MathUtils.sin(yaw) * xz;
            z = MathUtils.sin(pitch);
        */
    }

    /**
     * Sets the components of this vector.
     *
     * @param x the {@code x}-component
     * @param y the {@code y}-component
     * @param z the {@code z}-component
     * @return this vector.
     */
    public Vec3d set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    /**
     * Sets the components of this vector.
     *
     * @param xy the {@code x}-, {@code y}- and {@code z}-component.
     * @param z  the {@code z}-component.
     * @return this vector.
     */
    public Vec3d set(Vec2f xy, double z) {
        this.x = xy.x;
        this.y = xy.y;
        this.z = z;
        return this;
    }

    /**
     * Sets the components of this vector by copying the specified one.
     *
     * @param xyz the vector from which the values should be copied.
     * @return this vector.
     */
    public Vec3d set(Vec3i xyz) {
        this.x = xyz.x;
        this.y = xyz.y;
        this.z = xyz.z;
        return this;
    }

    /**
     * Sets the components of this vector by copying the specified one.
     *
     * @param xyz the vector from which the values should be copied.
     * @return this vector.
     */
    public Vec3d set(Vec3d xyz) {
        this.x = xyz.x;
        this.y = xyz.y;
        this.z = xyz.z;
        return this;
    }

    /**
     * Sets the components of this vector by copying the the specified one, transforms floats to doubles.
     *
     * @param xyz the vector from which the values should be copied.
     * @return this vector.
     */
    public Vec3d set(Vec3f xyz) {
        this.x = xyz.x;
        this.y = xyz.y;
        this.z = xyz.z;
        return this;
    }

    /**
     * Distance to other vector
     */
    public double distance(Vec3d vec) {
        return Math.sqrt(distanceSq(vec));
    }

    /**
     * Distance^2 to other vector
     */
    public double distanceSq(Vec3d vec) {
        double t = vec.x - this.x;
        double v = t * t;
        t = vec.y - this.y;
        v += t * t;
        t = vec.z - this.z;
        return v + t * t;
    }

    /**
     * Creates a new Vec2d containing the x and y value of this vector.
     *
     * @return the x and y component of this vector in a new Vec2d.
     */
    public Vec2d xy() {
        return new Vec2d(x, y);
    }

    /**
     * Calculates and returns the length of this vector.
     *
     * @return the length of this vector.
     */
    public double len() {
        return MathUtils.sqrt(x * x + y * y + z * z);
    }

    public double len2() {
        return x * x + y * y + z * z;
    }

    /**
     * Calculates the given vector's angle to this vector.
     *
     * @return the angle.
     */
    public double angle(Vec3d other) {
        return dot(other) / Math.sqrt(len2() * other.len2());
    }

    /**
     * Normalizes this vector.
     *
     * @return this vector.
     */
    public Vec3d normalize() {
        double abs = MathUtils.sqrt(x * x + y * y + z * z);
        x /= abs;
        y /= abs;
        z /= abs;
        return this;
    }

    public Vec3d normalizeUnit(double d) {
        double abs = 1 / (MathUtils.sqrt(x * x + y * y + z * z) * d);
        x /= abs;
        y /= abs;
        z /= abs;
        return this;
    }

    /**
     * Adds the given vector to this vector and stores the result in this vector.
     *
     * @param v the vector to add.
     * @return this vector.
     */
    public Vec3d add(Vec3d v) {
        this.x += v.x;
        this.y += v.y;
        this.z += v.z;
        return this;
    }

    public Vec3d add(double x, double y, double z) {
        this.x += x;
        this.y += y;
        this.z += z;
        return this;
    }

    /**
     * Subtracts the given vector from this vector and stores the result in this vector.
     *
     * @param v the vector to subtract.
     * @return this vector.
     */
    public Vec3d sub(Vec3d v) {
        this.x -= v.x;
        this.y -= v.y;
        this.z -= v.z;
        return this;
    }

    /**
     * Multiplies this vector with the specified scalar value and stores the result in this vector.
     *
     * @param scalar the scalar value to multiply this vector with.
     * @return this vector.
     */
    public Vec3d mul(double scalar) {
        this.x *= scalar;
        this.y *= scalar;
        this.z *= scalar;
        return this;
    }

    /**
     * Calculates and returns the dot-product of this vector with the specified one.
     *
     * @param v the vector to calculate the dot-product with.
     * @return the dot-product of this vector and {@code v}.
     */
    public double dot(Vec3d v) {
        return this.x * v.x + this.y * v.y + this.z * v.z;
    }

    public double dot(double vx, double vy, double vz) {
        return this.x * vx + this.y * vy + this.z * vz;
    }

    /**
     * 外积/法线(与a,b都垂直的向量) <BR>
     * Calculates and returns the cross-product of this vector with the specified one.
     *
     * @param v the vector to calculate the cross-product with.
     * @return the cross-product of this vector and {@code v} (i.e {@code this cross v}).
     */
    public Vec3d cross(Vec3d v) {
        double x = this.y * v.z - v.y * this.z;
        double y = this.z * v.x - v.z * this.x;
        this.z = this.x * v.y - v.x * this.y;

        this.x = x;
        this.y = y;

        return this;
    }

    /**
     * 自身在直线上的投影
     *
     * @param line 直线向量
     * @return 投影点
     */
    public Vec3d project(Vec3d line) {
        return MathUtils.project(this, line, this);
    }

    public Vec2d projectXY() {
        double k = (x * x + y * y) / len2();
        return new Vec2d(k * x, k * y);
    }

    /**
     * Normalizes the given vector and returns the result.
     *
     * @param v the vector to normalize.
     * @return {@code v} as (new) normalized vector
     */
    public static Vec3d normalize(Vec3d v) {
        return new Vec3d(v).normalize();
    }

    /**
     * Adds the given vectors and returns the result as new vector.
     *
     * @param a the first vector.
     * @param b the second vector.
     * @return the result of this addition, i.e. {@code a + b} as new vector.
     */
    public static Vec3d add(Vec3d a, Vec3d b) {
        return new Vec3d(a).add(b);
    }

    /**
     * Subtracts the given vectors and returns the result as new vector.
     *
     * @param a the vector to subtract from-
     * @param b the vector to subtract.
     * @return the result of this subtraction, i.e. {@code a - b} as new vector.
     */
    public static Vec3d sub(Vec3d a, Vec3d b) {
        return new Vec3d(a).sub(b);
    }

    /**
     * Multiplies the given vector and scalar value and stores the result in this vector.
     *
     * @param v      the vector to multiply with.
     * @param scalar the scalar value to multiply with.
     * @return the result of this multiplication, i.e. {@code v * scalar} as new vector.
     */
    public static Vec3d mul(Vec3d v, double scalar) {
        return new Vec3d(v).mul(scalar);
    }


    /**
     * Calculates and returns the dot-product of both specified vectors.
     *
     * @param a the first vector.
     * @param b the second vector.
     * @return the dot-product of {@code a} and {@code b} (i.e. {@code a dot b}).
     */
    public static double dot(Vec4f a, Vec3d b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    /**
     * Calculates and returns the cross-product of the two specified vectors.
     *
     * @param a the first vector.
     * @param b the second vector.
     * @return the cross-product both vectors, i.e {@code a cross b}.
     */
    public static Vec3d cross(Vec3d a, Vec3d b) {
        return new Vec3d(a).cross(b);
    }

    public static Vec3d project(Vec3d a, Vec3d b) {
        return new Vec3d(a).project(b);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Vec3d)) return false;

        Vec3d other = (Vec3d) obj;
        return this.x == other.x
                && this.y == other.y
                && this.z == other.z;
    }

    @Override
    public int hashCode() {
        return new Hasher()
                .add(x)
                .add(y)
                .add(z)
                .getHash();
    }

    @Override
    public String toString() {
        return "Vec3d {" + x + ", " + y + ", " + z + "}";
    }

    @Override
    public double x() {
        return x;
    }

    @Override
    public double y() {
        return y;
    }

    @Override
    public double z() {
        return z;
    }

    @Override
    public void x(double x) {
        this.x = x;
    }

    @Override
    public void y(double y) {
        this.y = y;
    }

    @Override
    public void z(double z) {
        this.z = z;
    }

    @Override
    public int axis() {
        return 3;
    }
}
