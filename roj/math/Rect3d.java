package roj.math;

import roj.util.Hasher;

/**
 * An three-dimensional {@code double}-based rectangle.
 */
public class Rect3d {
    public double xmin, ymin, zmin, xmax, ymax, zmax;

    /**
     * Constructs a new rectangle with the given properties.
     *
     * @param xmin the minimum {@code x}-axis value contained in the rectangle.
     * @param ymin the minimum {@code y}-axis value contained in the rectangle.
     * @param zmin the minimum {@code z}-axis value contained in the rectangle.
     * @param xmax the maximum {@code x}-axis value contained in the rectangle.
     * @param ymax the maximum {@code y}-axis value contained in the rectangle.
     * @param zmax the maximum {@code z}-axis value contained in the rectangle.
     */
    public Rect3d(double xmin, double ymin, double zmin, double xmax, double ymax, double zmax) {
        this.xmin = xmin;
        this.ymin = ymin;
        this.zmin = zmin;

        this.xmax = xmax;
        this.ymax = ymax;
        this.zmax = zmax;
    }

    /**
     * Constructs a new rectangle with the given parameters.
     *
     * @param min the minimum axis values contained in this rectangle.
     * @param max the maximum axis values contained in this rectangle.
     */
    public Rect3d(Vec3d min, Vec3d max) {
        this.xmin = min.x;
        this.ymin = min.y;
        this.zmin = min.z;

        this.xmax = max.x;
        this.ymax = max.y;
        this.zmax = max.z;
    }

    /**
     * Constructs a new rectangle by copying the specified one.
     *
     * @param other the rectangle from which the new rectangle should be copy-constructed.
     */
    public Rect3d(Rect3d other) {
        this.xmin = other.xmin;
        this.ymin = other.ymin;
        this.zmin = other.zmin;

        this.xmax = other.xmax;
        this.ymax = other.ymax;
        this.zmax = other.zmax;
    }


    /**
     * Sets this rectangle according to the specified parameters.
     *
     * @param xmin the minimum {@code x}-axis value contained in the rectangle.
     * @param ymin the minimum {@code y}-axis value contained in the rectangle.
     * @param zmin the minimum {@code z}-axis value contained in the rectangle.
     * @param xmax the maximum {@code x}-axis value contained in the rectangle.
     * @param ymax the maximum {@code y}-axis value contained in the rectangle.
     * @param zmax the maximum {@code z}-axis value contained in the rectangle.
     * @return this rectangle.
     */
    public Rect3d set(double xmin, double ymin, double zmin, double xmax, double ymax, double zmax) {
        this.xmin = xmin;
        this.ymin = ymin;
        this.zmin = zmin;

        this.xmax = xmax;
        this.ymax = ymax;
        this.zmax = zmax;

        return this;
    }

    /**
     * Sets this rectangle according to the specified parameters.
     *
     * @param min the minimum axis values contained in this rectangle.
     * @param max the maximum axis values contained in this rectangle.
     * @return this rectangle.
     */
    public Rect3d set(Vec3d min, Vec3d max) {
        this.xmin = min.x;
        this.ymin = min.y;
        this.zmin = min.z;

        this.xmax = max.x;
        this.ymax = max.y;
        this.zmax = max.z;

        return this;
    }

    /**
     * Sets this rectangle by copying the specified one.
     *
     * @param other the rectangle from which the new rectangle should be copy-constructed.
     * @return this rectangle.
     */
    public Rect3d set(Rect3d other) {
        this.xmin = other.xmin;
        this.ymin = other.ymin;
        this.zmin = other.zmin;

        this.xmax = other.xmax;
        this.ymax = other.ymax;
        this.zmax = other.zmax;

        return this;
    }

    public Rect3d fix() {
        double tmp;

        if (xmin > xmax) {
            tmp = xmax;
            xmax = xmin;
            xmin = tmp;
        }
        if (ymin > ymax) {
            tmp = ymax;
            ymax = ymin;
            ymin = tmp;
        }
        if (zmin > zmax) {
            tmp = zmax;
            zmax = zmin;
            zmin = tmp;
        }

        return this;
    }

    public double size() {
        return (xmax - xmin + 1.0) * (ymax - ymin + 1.0) * (zmax - zmin + 1.0);
    }

    /**
     * Returns the minimum axis values of this rectangle.
     *
     * @return the minimum axis values.
     */
    public Vec3d min() {
        return new Vec3d(xmin, ymin, zmin);
    }

    /**
     * Returns the maximum axis values of this rectangle.
     *
     * @return the maximum axis values.
     */
    public Vec3d max() {
        return new Vec3d(xmax, ymax, zmax);
    }


    public Vec3d[] vertices() {
        return new Vec3d[]{
                new Vec3d(xmin, ymin, zmin), // 000
                new Vec3d(xmin, ymin, zmax), // 001
                new Vec3d(xmin, ymax, zmin), // 010
                new Vec3d(xmin, ymax, zmax), // 011
                new Vec3d(xmax, ymin, zmin), // 100
                new Vec3d(xmax, ymin, zmax), // 101
                new Vec3d(xmax, ymax, zmin), // 110
                new Vec3d(xmax, ymax, zmax)  // 111
        };
    }

    public boolean contains(Vec3d p) {
        return xmin <= p.x && p.x <= xmax && ymin <= p.y && p.y <= ymax && zmin <= p.z && p.z <= zmax;
    }

    public boolean intersects(Rect3d other) {
        return !(xmax < other.xmin || xmin > other.xmax || ymax < other.ymin || ymin > other.ymax || zmax < other.zmin || zmin > other.zmax);
    }

    public boolean contains(Rect3d other) {
        return xmax >= other.xmax && xmin <= other.xmin && ymax >= other.ymax && ymin <= other.ymin && zmax >= other.zmax && zmin <= other.zmin;
    }

    public Rect3d grow(int i) {
        xmin -= i;
        ymin -= i;
        zmin -= i;
        xmax += i;
        ymax += i;
        zmax += i;
        return this;
    }

    public Rect3d copy() {
        return new Rect3d(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Rect3d)) return false;

        Rect3d other = (Rect3d) obj;
        return this.xmin == other.xmin
                && this.ymin == other.ymin
                && this.zmin == other.zmin
                && this.xmax == other.xmax
                && this.ymax == other.ymax
                && this.zmax == other.zmax;
    }

    @Override
    public int hashCode() {
        return new Hasher()
                .add(xmin)
                .add(ymin)
                .add(zmin)
                .add(xmax)
                .add(ymax)
                .add(zmax)
                .getHash();
    }

    @Override
    public String toString() {
        return this.getClass() + " {" + xmin + ", " + ymin + ", " + zmin + ", " + xmax + ", " + ymax + ", " + zmin + "}";
    }

    public Rect3d expandIf(Vec3d... points) {
        for (Vec3d v : points) {
            if (xmin > v.x) xmin = v.x;
            if (xmax < v.x) xmax = v.x;

            if (ymin > v.y) ymin = v.y;
            if (ymax < v.y) ymax = v.y;

            if (zmin > v.z) zmin = v.z;
            if (zmax < v.z) zmax = v.z;
        }

        return this;
    }

    public static Rect3d from(Vec3d... points) {
        return new Rect3d(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE).expandIf(points);
    }
}
