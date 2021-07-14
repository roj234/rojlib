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
 * A vector containing four {@code float}s.
 *
 * @author Maximilian Luz
 */
public class Vec4f {
    public float x, y, z, w;


    /**
     * Constructs a new vector and initializes the {@code x}-, {@code y}- and {@code z}-components to zero, the
     * {@code w}-component to one.
     */
    public Vec4f() {
        this(0, 0, 0, 1);
    }

    public Vec4f(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Constructs a new vector with the given values.
     *
     * @param x the {@code x}-component
     * @param y the {@code y}-component
     * @param z the {@code z}-component
     * @param w the {@code w}-component
     */
    public Vec4f(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    /**
     * Constructs a new vector with the given values.
     *
     * @param xy the {@code x}-and {@code z}-components.
     * @param z  the {@code z}-component.
     * @param w  the {@code w}-component.
     */
    public Vec4f(Vec2f xy, float z, float w) {
        this(xy.x, xy.y, z, w);
    }

    /**
     * Constructs a new vector with the given values.
     *
     * @param xyz the {@code x}-, {@code y} and {@code z}-components.
     * @param w   the {@code w}-component
     */
    public Vec4f(Vec3f xyz, float w) {
        this(xyz.x, xyz.y, xyz.z, w);
    }

    /**
     * Constructs a new vector by copying the specified one.
     *
     * @param xyzw the vector from which the values should be copied.
     */
    public Vec4f(Vec4f xyzw) {
        this(xyzw.x, xyzw.y, xyzw.z, xyzw.w);
    }

    /**
     * Sets the components of this vector.
     *
     * @param x the {@code x}-component
     * @param y the {@code y}-component
     * @param z the {@code z}-component
     * @param w the {@code w}-component
     * @return this vector.
     */
    public Vec4f set(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        return this;
    }

    /**
     * Sets the components of this vector.
     *
     * @param xy the {@code x}-, {@code y}- and {@code z}-component.
     * @param z  the {@code z}-component.
     * @param w  the {@code w}-component.
     * @return this vector.
     */
    public Vec4f set(Vec2f xy, float z, float w) {
        this.x = xy.x;
        this.y = xy.y;
        this.z = z;
        this.w = w;
        return this;
    }

    /**
     * Sets the components of this vector.
     *
     * @param xyz the {@code x}-, {@code y}-component.
     * @param w   the {@code w}-component.
     * @return this vector.
     */
    public Vec4f set(Vec3f xyz, float w) {
        this.x = xyz.x;
        this.y = xyz.y;
        this.z = xyz.z;
        this.w = w;
        return this;
    }

    /**
     * Sets the components of this vector by copying the specified one.
     *
     * @param xyzw the vector from which the values should be copied.
     * @return this vector.
     */
    public Vec4f set(Vec4f xyzw) {
        this.x = xyzw.x;
        this.y = xyzw.y;
        this.z = xyzw.z;
        this.w = xyzw.w;
        return this;
    }


    /**
     * Calculates and returns the lenght of this vector. This calculation includes the {@code w}-component.
     *
     * @return the length of this vector.
     */
    public float len() {
        return MathUtils.sqrt(x * x + y * y + z * z + w * w);
    }

    /**
     * Calculates and returns the lengt of the vector, represented by {@code x}-, {@code y}- and {@code z}-components.
     *
     * @return the length of the vector described by the {@code x}-, {@code y}- and {@code z}-component.
     */
    public float len3() {
        return MathUtils.sqrt(x * x + y * y + z * z);
    }


    /**
     * Normalizes this vector. This calculation includes the {@code w}-component.
     *
     * @return this vector.
     */
    public Vec4f normalize() {
        float abs = len();
        x /= abs;
        y /= abs;
        z /= abs;
        w /= abs;
        return this;
    }

    /**
     * Normalizes the {@code x}-, {@code y}- and {@code z}-components. This calculation ignores the {@code w}-component
     * (does neither read or set it).
     *
     * @return this vector.
     */
    public Vec4f normalize3() {
        float abs = len3();
        x /= abs;
        y /= abs;
        z /= abs;
        return this;
    }

    /**
     * Adds the given vector to this vector and stores the result in this vector. This calculation includes the
     * {@code w}-component.
     *
     * @param v the vector to add.
     * @return this vector.
     */
    public Vec4f add(Vec4f v) {
        this.x += v.x;
        this.y += v.y;
        this.z += v.z;
        this.w += v.w;
        return this;
    }

    /**
     * Adds the {@code x}-, {@code y}- and {@code z}-components of the given vector to this vector and stores the
     * result in this vector. This calculation fully ignores the {@code w}-component.
     *
     * @param v the vector to add.
     * @return this vector.
     */
    public Vec4f add3(Vec4f v) {
        this.x += v.x;
        this.y += v.y;
        this.z += v.z;
        return this;
    }

    /**
     * Subtract the given vector from this vector (i.e. {@code this - v}} and stores the result in this vector. This
     * calculation includes the {@code w}-component.
     *
     * @param v the vector to subtract.
     * @return this vector.
     */
    public Vec4f sub(Vec4f v) {
        this.x -= v.x;
        this.y -= v.y;
        this.z -= v.z;
        this.w -= v.w;
        return this;
    }

    /**
     * Subtracts the {@code x}-, {@code y}- and {@code z}-components of the given vector from this vector
     * ({@code this - v}). This calculation fully ignores the {@code w}-component.
     *
     * @param v the vector to add.
     * @return this vector.
     */
    public Vec4f sub3(Vec4f v) {
        this.x -= v.x;
        this.y -= v.y;
        this.z -= v.z;
        return this;
    }

    /**
     * Multiplies this vector with the specified scalar value and stores the result in this vector.
     * This calculation includes the {@code w}-component.
     *
     * @param scalar the scalar value to multiply this vector with.
     * @return this vector.
     */
    public Vec4f mul(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
        this.z *= scalar;
        this.w *= scalar;
        return this;
    }

    /**
     * Multiplies the {@code x}-, {@code y}- and {@code z}-components of this vector with the specified scalar value
     * and stores the result in this vector. This calculation fully ignores the {@code w}-component.
     *
     * @param scalar the scalar value to multiply this vector with.
     * @return this vector.
     */
    public Vec4f mul3(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
        this.z *= scalar;
        return this;
    }

    /**
     * Calculates and returns the dot-product of this vector with the specified one. This calculation includes the
     * {@code w}-component.
     *
     * @param v the vector to calculate the dot product with.
     * @return the dot product of this vector and {@code v}.
     */
    public float dot(Vec4f v) {
        return this.x * v.x + this.y * v.y + this.z * v.z + this.w * v.w;
    }

    /**
     * Calculates and returns the dot-product of the {@code x}-, {@code y}- and {@code z}-components of this vector
     * with the specified one. This calculation fully ignores the {@code w}-component.
     *
     * @param v the vector to calculate the dot product with.
     * @return the dot product of the {@code x}-, {@code y}- and {@code z}-components of this vector and {@code v}.
     */
    public float dot3(Vec4f v) {
        return this.x * v.x + this.y * v.y + this.z * v.z;
    }


    /**
     * Normalizes the given vector and returns the result. This calculation includes the {@code w}-component.
     *
     * @param v the vector to normalize.
     * @return {@code v} as (new) normalized vector
     */
    public static Vec4f normalize(Vec4f v) {
        float abs = (float) Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z + v.w * v.w);
        return new Vec4f(v.x / abs, v.y / abs, v.z / abs, v.w / abs);
    }

    /**
     * Normalizes the given vector by its {@code x}-, {@code y}- and {@code z}-components. The {@code w}-component of
     * the returned (normalized) vector is set to one.
     *
     * @param v the vector to normalize.
     * @return a new vector containing the normalized {@code x}-, {@code y}- and {@code z}-components and one as the
     * {@code w}-component.
     */
    public static Vec4f normalize3(Vec4f v) {
        float abs = (float) Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
        return new Vec4f(v.x / abs, v.y / abs, v.z / abs, 1.f);
    }

    /**
     * Adds the two given vectors and returns the result of this operation as new vector. This calculation includes the
     * {@code w}-component.
     *
     * @param a the first vector to add.
     * @param b the second vector to add.
     * @return the result of this addition, i.e. {@code a + b}.
     */
    public static Vec4f add(Vec4f a, Vec4f b) {
        return new Vec4f(a.x + b.x, a.y + b.y, a.z + b.z, a.w + b.w);
    }

    /**
     * Adds the {@code x}-, {@code y}- and {@code z}-components of the two given vectors. This calculation fully
     * ignores the {@code w}-components and sets the {@code w}-component of the returned vector to one.
     *
     * @param a the first vector to add.
     * @param b the second vector to add.
     * @return the result of the addition of the {@code x}-, {@code y}- and {@code z}-components of both vectors and
     * one for the {@code w}-component.
     */
    public static Vec4f add3(Vec4f a, Vec4f b) {
        return new Vec4f(a.x + b.x, a.y + b.y, a.z + b.z, 1.0f);
    }

    /**
     * Subtracts the two given vectors and returns the result of this operation as new vector. This calculation
     * includes the {@code w}-component.
     *
     * @param a the vector to subtract from.
     * @param b the vector to subtract.
     * @return the result of this subtraction, i.e. {@code a - b}.
     */
    public static Vec4f sub(Vec4f a, Vec4f b) {
        return new Vec4f(a.x - b.x, a.y - b.y, a.z - b.z, a.w - b.w);
    }

    /**
     * Adds the {@code x}-, {@code y}- and {@code z}-components of the two given vectors ({@code a.xyz - b.xyz}}.
     * This calculation full ignores the {@code w}-components and sets the {@code w}-component of the returned vector
     * to one.
     *
     * @param a the vector to subtract from.
     * @param b the vector to subtract.
     * @return the result of the subtraction of the {@code x}-, {@code y}- and {@code z}-components of both vectors
     * ({@code a.xyz - b.xyz}} and one for the {@code w}-component.
     */
    public static Vec4f sub3(Vec4f a, Vec4f b) {
        return new Vec4f(a.x - b.x, a.y - b.y, a.z - b.z, 1.0f);
    }

    /**
     * Multiplies the given vector with the given scalar value and returns its result. This calculation includes the
     * {@code w}-component.
     *
     * @param v      the vector to be multiplied with.
     * @param scalar the scalar to be multiplied with.
     * @return the result of this multiplication, i.e. {@code a * b}.
     */
    public static Vec4f mul(Vec4f v, float scalar) {
        return new Vec4f(v.x *= scalar, v.y *= scalar, v.z *= scalar, v.w *= scalar);
    }

    /**
     * Multiplies the {@code x}-, {@code y}- and {@code z}-components of the given vector with the given scalar value
     * and returns its result. This calculation does not modify the {@code w}-component of the specified vector.
     *
     * @param v      the vector to be multiplied with.
     * @param scalar the scalar to be multiplied with.
     * @return the result of the multiplication of the scalar value with the {@code x}-, {@code y}- and
     * {@code z}-components of the specified vector (i.e. {@code v.xyz * scalar}) and {@code v.w} for the
     * {@code w}-component.
     */
    public static Vec4f mul3(Vec4f v, float scalar) {
        return new Vec4f(v.x *= scalar, v.y *= scalar, v.z *= scalar, v.w);
    }

    /**
     * Calculates and returns the dot-product of the two specified vectors. This calculation includes the
     * {@code w}-component of both vectors.
     *
     * @param a the first vector.
     * @param b the second vector.
     * @return the dot-product of {@code a} and {@code b}
     */
    public static float dot(Vec4f a, Vec4f b) {
        return a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
    }

    /**
     * Calculates and returns the dot-product of the {@code x}-, {@code y}- and {@code z}-components of the given
     * vectors. This calculation fully ignores the {@code w}-component of both vectors.
     *
     * @param a the first vector.
     * @param b the second vector.
     * @return the dot product of the {@code x}-, {@code y}- and {@code z}-components of both vectors.
     */
    public static float dot3(Vec4f a, Vec4f b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }


    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Vec4f)) return false;

        Vec4f other = (Vec4f) obj;
        return this.x == other.x
                && this.y == other.y
                && this.z == other.z
                && this.w == other.w;
    }

    @Override
    public int hashCode() {
        return new Hasher()
                .add(x)
                .add(y)
                .add(z)
                .add(w)
                .getHash();
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " {" + x + ", " + y + ", " + z + ", " + w + "}";
    }
}
