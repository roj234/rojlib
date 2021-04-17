package roj.math;

import roj.util.Hasher;


/**
 * A vector containing three {@code float}s.
 *
 * @author Maximilian Luz
 */
public class Vec3f {
    public float x, y, z;

    /**
     * Constructs a new vector and initializes the {@code x}-, {@code y}- and {@code z}-components to zero.
     */
    public Vec3f() {
        this(0, 0, 0);
    }

    /**
     * Constructs a new vector with the given values.
     *
     * @param x the {@code x}-component
     * @param y the {@code y}-component
     * @param z the {@code z}-component
     */
    public Vec3f(float x, float y, float z) {
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
    public Vec3f(Vec2f xy, float z) {
        this(xy.x, xy.y, z);
    }

    /**
     * Constructs a new vector by copying the specified one.
     *
     * @param xyz the vector from which the values should be copied.
     */
    public Vec3f(Vec3i xyz) {
        this(xyz.x, xyz.y, xyz.z);
    }

    /**
     * Constructs a new vector by copying the specified one.
     *
     * @param xyz the vector from which the values should be copied.
     */
    public Vec3f(Vec3f xyz) {
        this(xyz.x, xyz.y, xyz.z);
    }


    /**
     * Constructs a new vector by copying the the specified one, transforms doubles to float.
     *
     * @param xyz the vector from which the values should be copied.
     */
    public Vec3f(Vec3d xyz) {
        this((float) xyz.x, (float) xyz.y, (float) xyz.z);
    }

    /**
     * 使用欧拉角初始化一个单位向量
     * @param yaw 旋转X
     * @param pitch 旋转Y
     */
    public Vec3f(float yaw, float pitch) {
        y = -MathUtils.sin(pitch);

        float xz = MathUtils.cos(pitch);
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
    public Vec3f set(float x, float y, float z) {
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
    public Vec3f set(Vec2f xy, float z) {
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
    public Vec3f set(Vec3i xyz) {
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
    public Vec3f set(Vec3f xyz) {
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
    public Vec3f set(Vec3d xyz) {
        this.x = (float) xyz.x;
        this.y = (float) xyz.y;
        this.z = (float) xyz.z;
        return this;
    }

    /**
     * Calculates and returns the length of this vector.
     *
     * @return the length of this vector.
     */
    public float len() {
        return MathUtils.sqrt(x * x + y * y + z * z);
    }

    public float len2() {
        return x * x + y * y + z * z;
    }

    /**
     * Calculates the given vector's angle to this vector.
     *
     * @return the angle.
     */
    public double angle(Vec3f other) {
        double angle = dot(other) / len() * other.len();
        return Math.acos(angle);
    }

    /**
     * Normalizes this vector.
     *
     * @return this vector.
     */
    public Vec3f normalize() {
        float abs = MathUtils.sqrt(x * x + y * y + z * z);
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
    public Vec3f add(Vec3f v) {
        this.x += v.x;
        this.y += v.y;
        this.z += v.z;
        return this;
    }


    public Vec3f add(float x, float y, float z) {
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
    public Vec3f sub(Vec3f v) {
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
    public Vec3f mul(float scalar) {
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
    public float dot(Vec3f v) {
        return this.x * v.x + this.y * v.y + this.z * v.z;
    }

    /**
     * Calculates and returns the cross-product of this vector with the specified one.
     *
     * @param v the vector to calculate the cross-product with.
     * @return the cross-product of this vector and {@code v} (i.e {@code this cross v}).
     */
    public Vec3f cross(Vec3f v) {
        float x = this.y * v.z - v.y * this.z;
        float y = this.z * v.x - v.z * this.x;
        float z = this.x * v.y - v.x * this.y;

        this.x = x;
        this.y = y;
        this.z = z;

        return this;
    }

    /**
     * 自身在直线上的投影
     *
     * @param line 直线向量
     * @return 投影点
     */
    public Vec3f project(Vec3f line) {
        float len = line.len2();
        if (len == 0)
            return this;

        float dx = line.x;
        float dy = line.y;
        float dz = line.z;

        if (len != 1) {
            float abs = MathUtils.sqrt(len);
            dx /= abs;
            dy /= abs;
            dz /= abs;
        }

        float dxyz = dx * dy * dz;

        float x = dx * this.x + dxyz * this.y * this.z;
        float y = dy * this.y + dxyz * this.x * this.z;
        float z = dz * this.z + dxyz * this.x * this.y;

        this.x = x;
        this.y = y;
        this.z = z;

        return this;
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
    public static Vec3f normalize(Vec3f v) {
        return new Vec3f(v).normalize();
    }

    /**
     * Adds the given vectors and returns the result as new vector.
     *
     * @param a the first vector.
     * @param b the second vector.
     * @return the result of this addition, i.e. {@code a + b} as new vector.
     */
    public static Vec3f add(Vec3f a, Vec3f b) {
        return new Vec3f(a).add(b);
    }

    /**
     * Subtracts the given vectors and returns the result as new vector.
     *
     * @param a the vector to subtract from-
     * @param b the vector to subtract.
     * @return the result of this subtraction, i.e. {@code a - b} as new vector.
     */
    public static Vec3f sub(Vec3f a, Vec3f b) {
        return new Vec3f(a).sub(b);
    }

    /**
     * Multiplies the given vector and scalar value and stores the result in this vector.
     *
     * @param v      the vector to multiply with.
     * @param scalar the scalar value to multiply with.
     * @return the result of this multiplication, i.e. {@code v * scalar} as new vector.
     */
    public static Vec3f mul(Vec3f v, float scalar) {
        return new Vec3f(v).mul(scalar);
    }

    /**
     * Calculates and returns the dot-product of both specified vectors.
     *
     * @param a the first vector.
     * @param b the second vector.
     * @return the dot-product of {@code a} and {@code b} (i.e. {@code a dot b}).
     */
    public static float dot(Vec4f a, Vec3f b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    /**
     * Calculates and returns the cross-product of the two specified vectors.
     *
     * @param a the first vector.
     * @param b the second vector.
     * @return the cross-product both vectors, i.e {@code a cross b}.
     */
    public static Vec3f cross(Vec3f a, Vec3f b) {
        return new Vec3f(a).cross(b);
    }

    public static Vec3f project(Vec3f a, Vec3f b) {
        return new Vec3f(a).project(b);
    }

    public static Vec3d project(Vec3d a, Vec3d b) {
        return new Vec3d(a).project(b);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Vec3f)) return false;

        Vec3f other = (Vec3f) obj;
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
}
