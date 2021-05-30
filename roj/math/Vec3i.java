package roj.math;

import roj.util.Hasher;


/**
 * A vector containing three {@code int}s.
 *
 * @author Maximilian Luz
 */
public class Vec3i implements Vec3 {
    public int x, y, z;


    /**
     * Constructs a new vector and initializes the {@code x}-, {@code y}- and {@code z}-components to zero.
     */
    public Vec3i() {
        this(0, 0, 0);
    }

    /**
     * Constructs a new vector with the given values.
     *
     * @param x the {@code x}-component
     * @param y the {@code y}-component
     * @param z the {@code z}-component
     */
    public Vec3i(int x, int y, int z) {
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
    public Vec3i(Vec2i xy, int z) {
        this(xy.x, xy.y, z);
    }

    /**
     * Constructs a new vector by copying the specified one.
     *
     * @param xyz the vector from which the values should be copied.
     */
    public Vec3i(Vec3i xyz) {
        this(xyz.x, xyz.y, xyz.z);
    }

    /**
     * Constructs a new vector by copying the specified one.
     *
     * @param xyz the vector from which the values should be copied.
     */
    public Vec3i(Vec3f xyz) {
        this((int)xyz.x, (int)xyz.y, (int)xyz.z);
    }

    /**
     * Constructs a new vector by copying the specified one.
     *
     * @param xyz the vector from which the values should be copied.
     */
    public Vec3i(Vec3d xyz) {
        this((int)xyz.x, (int)xyz.y, (int)xyz.z);
    }

    /**
     * Sets the components of this vector.
     *
     * @param x the {@code x}-component
     * @param y the {@code y}-component
     * @param z the {@code z}-component
     * @return this vector.
     */
    public Vec3i set(int x, int y, int z) {
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
    public Vec3i set(Vec2i xy, int z) {
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
    public Vec3i set(Vec3i xyz) {
        this.x = xyz.x;
        this.y = xyz.y;
        this.z = xyz.z;
        return this;
    }


    /**
     * Calculates and returns the length of this vector.
     *
     * @return the length of this vector.
     */
    public double len() {
        return MathUtils.sqrt(len2());
    }

    /**
     * Calculates and returns the length^2 of this vector.
     *
     * @return the length^2 of this vector.
     */
    public int len2() {
        return x * x + y * y + z * z;
    }

    /**
     * Adds the given vector to this vector and stores the result in this vector.
     *
     * @param v the vector to add.
     * @return this vector.
     */
    public Vec3i add(Vec3i v) {
        this.x += v.x;
        this.y += v.y;
        this.z += v.z;
        return this;
    }

    public Vec3i add(int x, int y, int z) {
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
    public Vec3i sub(Vec3i v) {
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
    public Vec3i mul(int scalar) {
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
    public int dot(Vec3i v) {
        return this.x * v.x + this.y * v.y + this.z * v.z;
    }

    /**
     * Calculates and returns the cross-product of this vector with the specified one.
     *
     * @param v the vector to calculate the cross-product with.
     * @return the cross-product of this vector and {@code v} (i.e {@code this cross v}).
     */
    public Vec3i cross(Vec3i v) {
        int x = this.y * v.z - v.y * this.z;
        int y = this.z * v.x - v.z * this.x;
        int z = this.x * v.y - v.x * this.y;

        this.x = x;
        this.y = y;
        this.z = z;

        return this;
    }


    /**
     * Adds the given vectors and returns the result as new vector.
     *
     * @param a the first vector.
     * @param b the second vector.
     * @return the result of this addition, i.e. {@code a + b} as new vector.
     */
    public static Vec3i add(Vec3i a, Vec3i b) {
        return new Vec3i(a).add(b);
    }

    /**
     * Subtracts the given vectors and returns the result as new vector.
     *
     * @param a the vector to subtract from-
     * @param b the vector to subtract.
     * @return the result of this subtraction, i.e. {@code a - b} as new vector.
     */
    public static Vec3i sub(Vec3i a, Vec3i b) {
        return new Vec3i(a).sub(b);
    }

    /**
     * Multiplies the given vector and scalar value and stores the result in this vector.
     *
     * @param v      the vector to multiply with.
     * @param scalar the scalar value to multiply with.
     * @return the result of this multiplication, i.e. {@code v * scalar} as new vector.
     */
    public static Vec3i mul(Vec3i v, int scalar) {
        return new Vec3i(v).mul(scalar);
    }

    /**
     * Calculates and returns the dot-product of both specified vectors.
     *
     * @param a the first vector.
     * @param b the second vector.
     * @return the dot-product of {@code a} and {@code b} (i.e. {@code a dot b}).
     */
    public static float dot(Vec4f a, Vec3i b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    /**
     * Calculates and returns the cross-product of the two specified vectors.
     *
     * @param a the first vector.
     * @param b the second vector.
     * @return the cross-product both vectors, i.e {@code a cross b}.
     */
    public static Vec3i cross(Vec3i a, Vec3i b) {
        return new Vec3i(a).cross(b);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Vec3i)) return false;

        Vec3i other = (Vec3i) obj;
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
        return this.getClass().getName() + " {" + x + ", " + y + ", " + z + "}";
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
}
