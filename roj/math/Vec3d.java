package roj.math;

import roj.util.Hasher;


/**
 * A vector containing three {@code double}s.
 *
 * @author Maximilian Luz
 */
public class Vec3d implements Vec3 {
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
        return dot(other) / (len() * other.len());
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
        double len = line.len2();
        if (len == 0)
            return this;

        double dx = line.x;
        double dy = line.y;
        double dz = line.z;

        if (len != 1) {
            double abs = MathUtils.sqrt(len);
            dx /= abs;
            dy /= abs;
            dz /= abs;
        }

        double dxyz = dx * dy * dz;

        double x = dx * this.x + dxyz * this.y * this.z;
        double y = dy * this.y + dxyz * this.x * this.z;
        double z = dz * this.z + dxyz * this.x * this.y;

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
