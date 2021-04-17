package roj.math;

import roj.util.Hasher;

/**
 * An three-dimensional {@code int}-based rectangle.
 */
public class Rect3i {
    public int xmin, ymin, zmin, xmax, ymax, zmax;

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
    public Rect3i(int xmin, int ymin, int zmin, int xmax, int ymax, int zmax) {
        this.xmin = xmin;
        this.ymin = ymin;
        this.zmin = zmin;

        this.xmax = xmax;
        this.ymax = ymax;
        this.zmax = zmax;
        fix();
    }

    /**
     * Constructs a new rectangle with the given parameters.
     *
     * @param min the minimum axis values contained in this rectangle.
     * @param max the maximum axis values contained in this rectangle.
     */
    public Rect3i(Vec3i min, Vec3i max) {
        this.xmin = min.x;
        this.ymin = min.y;
        this.zmin = min.z;

        this.xmax = max.x;
        this.ymax = max.y;
        this.zmax = max.z;
        fix();
    }

    /**
     * Constructs a new rectangle by copying the specified one.
     *
     * @param other the rectangle from which the new rectangle should be copy-constructed.
     */
    public Rect3i(Rect3i other) {
        this.xmin = other.xmin;
        this.ymin = other.ymin;
        this.zmin = other.zmin;

        this.xmax = other.xmax;
        this.ymax = other.ymax;
        this.zmax = other.zmax;
        fix();
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
    public Rect3i set(int xmin, int ymin, int zmin, int xmax, int ymax, int zmax) {
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
    public Rect3i set(Vec3i min, Vec3i max) {
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
    public Rect3i set(Rect3i other) {
        this.xmin = other.xmin;
        this.ymin = other.ymin;
        this.zmin = other.zmin;

        this.xmax = other.xmax;
        this.ymax = other.ymax;
        this.zmax = other.zmax;
        fix();

        return this;
    }

    public Rect3i fix() {
        int tmp;

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

    /**
     * Returns the minimum axis values of this rectangle.
     *
     * @return the minimum axis values.
     */
    public Vec3i min() {
        return new Vec3i(xmin, ymin, zmin);
    }

    /**
     * Returns the maximum axis values of this rectangle.
     *
     * @return the maximum axis values.
     */
    public Vec3i max() {
        return new Vec3i(xmax, ymax, zmax);
    }

    public Vec3i[] vertices() {
        return new Vec3i[]{
                new Vec3i(xmin, ymin, zmin), // 000
                new Vec3i(xmin, ymin, zmax), // 001
                new Vec3i(xmin, ymax, zmin), // 010
                new Vec3i(xmin, ymax, zmax), // 011
                new Vec3i(xmax, ymin, zmin), // 100
                new Vec3i(xmax, ymin, zmax), // 101
                new Vec3i(xmax, ymax, zmin), // 110
                new Vec3i(xmax, ymax, zmax)  // 111
        };
    }

    public Vec3i[] minMax() {
        fix();
        return new Vec3i[]{
                new Vec3i(xmin, ymin, zmin), // 000
                new Vec3i(xmax, ymax, zmax) // 001
        };
    }

    public boolean contains(double x, double y, double z) {
        return xmin <= x && x <= xmax && ymin <= y && y <= ymax && zmin <= z && z <= zmax;
    }

    public boolean contains(Vec3i p) {
        return xmin <= p.x && p.x <= xmax && ymin <= p.x && p.x <= ymax && zmin <= p.x && p.x <= zmax;
    }

    public boolean intersects(Rect3i other) {
        return !(xmax < other.xmin || xmin > other.xmax || ymax < other.ymin || ymin > other.ymax || zmax < other.zmin || zmin > other.zmax);
    }

    /**
     * 注意，边界也计算在内
     */
    public Rect3i intersectsWith(Rect3i other) {
        if (intersects(other)) {
            return new Rect3i(Math.max(this.xmin, other.xmin), Math.max(this.ymin, other.ymin), Math.max(this.zmin, other.zmin),
                    Math.min(this.xmax, other.xmax), Math.min(this.ymax, other.ymax), Math.min(this.zmax, other.zmax));
        } else {
            return null;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Rect3i)) return false;

        Rect3i other = (Rect3i) obj;
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
        return this.getClass().getSimpleName() + " {" + xmin + ", " + ymin + ", " + zmin + ", " + xmax + ", " + ymax + ", " + zmax + "}";
    }

    public Rect3i expandIf(Vec3i... points) {
        for (Vec3i v : points) {
            if (xmin > v.x) xmin = v.x;
            if (xmax < v.x) xmax = v.x;

            if (ymin > v.y) ymin = v.y;
            if (ymax < v.y) ymax = v.y;

            if (zmin > v.z) zmin = v.z;
            if (zmax < v.z) zmax = v.z;
        }

        return this;
    }

    public static Rect3i from(Vec3i... points) {
        return new Rect3i(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, -Integer.MAX_VALUE, -Integer.MAX_VALUE, -Integer.MAX_VALUE).expandIf(points);
    }

    public boolean contains(Rect3i other) {
        return xmax >= other.xmax && xmin <= other.xmin && ymax >= other.ymax && ymin <= other.ymin && zmax >= other.zmax && zmin <= other.zmin;
    }

    public Rect3i grow(int i) {
        xmin -= i;
        ymin -= i;
        zmin -= i;
        xmax += i;
        ymax += i;
        zmax += i;
        return this;
    }

    public void expandIf(int x, int y, int z) {
        if (x < xmin) {
            xmin = x;
        } else if (x > xmax) {
            xmax = x;
        }
        if (y < ymin) {
            ymin = y;
        } else if (y > ymax) {
            ymax = y;
        }
        if (z < zmin) {
            zmin = z;
        } else if (z > zmax) {
            zmax = z;
        }
    }

    public int size() {
        return (xmax - xmin + 1) * (ymax - ymin + 1) * (zmax - zmin + 1);
    }

    public Rect3i copy() {
        return new Rect3i(this);
    }
}