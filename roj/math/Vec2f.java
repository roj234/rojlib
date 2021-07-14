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
 * A vector containing two {@code float}s.
 *
 * @author Maximilian Luz
 */
public class Vec2f {
    public float x, y;


    /**
     * Constructs a new vector and initializes the {@code x}- and {@code y}-components to zero.
     */
    public Vec2f() {
        this(0, 0);
    }

    /**
     * Constructs a new vector with the given values.
     *
     * @param x the {@code x}-component
     * @param y the {@code y}-component
     */
    public Vec2f(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Constructs a new vector by copying the specified one.
     *
     * @param xy the vector from which the values should be copied.
     */
    public Vec2f(Vec2f xy) {
        this(xy.x, xy.y);
    }

    /**
     * Constructs a new vector by copying the specified one, transforms int to float.
     *
     * @param xy the vector from which the values should be copied.
     */
    public Vec2f(Vec2i xy) {
        this(xy.x, xy.y);
    }

    /**
     * Constructs a new vector by copying the specified one, transforms doubles to float.
     *
     * @param xy the vector from which the values should be copied.
     */
    public Vec2f(Vec2d xy) {
        this((float) xy.x, (float) xy.y);
    }

    /**
     * Sets the components of this vector.
     *
     * @param x the {@code x}-component
     * @param y the {@code y}-component
     * @return this vector.
     */
    public Vec2f set(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    /**
     * Sets the components of this vector by copying the specified one.
     *
     * @param xy the vector from which the values should be copied.
     * @return this vector.
     */
    public Vec2f set(Vec2f xy) {
        this.x = xy.x;
        this.y = xy.y;
        return this;
    }

    /**
     * Sets the components of this vector by copying the specified one, transforms int to float.
     *
     * @param xy the vector from which the values should be copied.
     * @return this vector.
     */
    public Vec2f set(Vec2i xy) {
        this.x = xy.x;
        this.y = xy.y;
        return this;
    }

    /**
     * Sets the components of this vector by copying the specified one, transforms int to float.
     *
     * @param xy the vector from which the values should be copied.
     * @return this vector.
     */
    public Vec2f set(Vec2d xy) {
        this.x = (float) xy.x;
        this.y = (float) xy.y;
        return this;
    }

    /**
     * Calculates and returns the length of this vector.
     *
     * @return the length of this vector.
     */
    public float len() {
        return MathUtils.sqrt(len2());
    }

    public float len2() {
        return x * x + y * y;
    }


    /**
     * Normalizes this vector.
     *
     * @return this vector.
     */
    public Vec2f normalize() {
        float abs = len();
        x /= abs;
        y /= abs;
        return this;
    }

    /**
     * Adds the given vector to this vector and stores the result in this vector.
     *
     * @param v the vector to add.
     * @return this vector.
     */
    public Vec2f add(Vec2f v) {
        this.x += v.x;
        this.y += v.y;
        return this;
    }

    /**
     * Subtracts the given vector from this vector and stores the result in this vector.
     *
     * @param v the vector to subtract.
     * @return this vector.
     */
    public Vec2f sub(Vec2f v) {
        this.x -= v.x;
        this.y -= v.y;
        return this;
    }

    /**
     * Multiplies this vector with the specified scalar value and stores the result in this vector.
     *
     * @param scalar the scalar value to multiply this vector with.
     * @return this vector.
     */
    public Vec2f mul(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
        return this;
    }


    /**
     * Calculates and returns the dot-product of this vector with the specified one.
     *
     * @param v the vector to calculate the dot-product with.
     * @return the dot-product of this vector and {@code v}.
     */
    public float dot(Vec2f v) {
        return this.x * v.x + this.y * v.y;
    }

    /**
     * Calculates and returns the cross-product of this vector with the specified one.
     *
     * @param v the vector to calculate the cross-product with.
     * @return the cross-product of this vector and {@code v} (i.e {@code this cross v}).
     */
    public float cross(Vec2f v) {
        return this.x * v.y - this.y * v.x;
    }


    /**
     * Normalizes the given vector and returns the result.
     *
     * @param v the vector to normalize.
     * @return {@code v} as (new) normalized vector
     */
    public static Vec2f normalize(Vec2f v) {
        return new Vec2f(v).normalize();
    }


    /**
     * Adds the given vectors and returns the result as new vector.
     *
     * @param a the first vector.
     * @param b the second vector.
     * @return the result of this addition, i.e. {@code a + b} as new vector.
     */
    public static Vec2f add(Vec2f a, Vec2f b) {
        return new Vec2f(a).add(b);
    }

    /**
     * Subtracts the given vectors and returns the result as new vector.
     *
     * @param a the vector to subtract from-
     * @param b the vector to subtract.
     * @return the result of this subtraction, i.e. {@code a - b} as new vector.
     */
    public static Vec2f sub(Vec2f a, Vec2f b) {
        return new Vec2f(a).sub(b);
    }

    /**
     * Multiplies the given vector and scalar value and stores the result in this vector.
     *
     * @param v      the vector to multiply with.
     * @param scalar the scalar value to multiply with.
     * @return the result of this multiplication, i.e. {@code v * scalar} as new vector.
     */
    public static Vec2f mul(Vec2f v, float scalar) {
        return new Vec2f(v).mul(scalar);
    }

    /**
     * Calculates and returns the dot-product of both specified vectors.
     *
     * @param a the first vector.
     * @param b the second vector.
     * @return the dot-product of {@code a} and {@code b} (i.e. {@code a dot b}).
     */
    public static float dot(Vec2f a, Vec2f b) {
        return a.x * b.x + a.y * b.y;
    }

    /**
     * Calculates and returns the cross-product of the two specified vectors.
     *
     * @param a the first vector.
     * @param b the second vector.
     * @return the cross-product both vectors, i.e {@code a cross b}.
     */
    public static float cross(Vec2f a, Vec2f b) {
        return a.x * b.y - a.y * b.x;
    }


    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Vec2f)) return false;

        Vec2f other = (Vec2f) obj;
        return this.x == other.x
                && this.y == other.y;
    }

    @Override
    public int hashCode() {
        return new Hasher()
                .add(x)
                .add(y)
                .getHash();
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " {" + x + ", " + y + "}";
    }
}
