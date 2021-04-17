package roj.math;

import roj.util.Hasher;


/**
 * An axis-aligned (two-dimensional) {@code int}-based rectangle.
 *
 * @author Maximilian Luz
 */
public class Rect2i {
    public int xmin, ymin, xmax, ymax;

    /**
     * Constructs a new rectangle with the given properties.
     *
     * @param xmin the minimum {@code x}-axis value contained in the rectangle.
     * @param ymin the minimum {@code y}-axis value contained in the rectangle.
     * @param xmax the maximum {@code x}-axis value contained in the rectangle.
     * @param ymax the maximum {@code y}-axis value contained in the rectangle.
     */
    public Rect2i(int xmin, int ymin, int xmax, int ymax) {
        this.xmin = xmin;
        this.ymin = ymin;
        this.xmax = xmax;
        this.ymax = ymax;
    }

    /**
     * Constructs a new rectangle with the given parameters.
     *
     * @param min the minimum axis values contained in this rectangle.
     * @param max the maximum axis values contained in this rectangle.
     */
    public Rect2i(Vec2i min, Vec2i max) {
        this.xmin = min.x;
        this.ymin = min.y;
        this.xmax = max.x;
        this.ymax = max.y;
    }

    /**
     * Constructs a new rectangle by copying the specified one.
     *
     * @param other the rectangle from which the new rectangle should be copy-constructed.
     */
    public Rect2i(Rect2i other) {
        this.xmin = other.xmin;
        this.ymin = other.ymin;
        this.xmax = other.xmax;
        this.ymax = other.ymax;
    }

    /**
     * Sets this rectangle according to the specified parameters.
     *
     * @param xmin the minimum {@code x}-axis value contained in the rectangle.
     * @param ymin the minimum {@code y}-axis value contained in the rectangle.
     * @param xmax the maximum {@code x}-axis value contained in the rectangle.
     * @param ymax the maximum {@code y}-axis value contained in the rectangle.
     * @return this rectangle.
     */
    public Rect2i set(int xmin, int ymin, int xmax, int ymax) {
        this.xmin = xmin;
        this.ymin = ymin;
        this.xmax = xmax;
        this.ymax = ymax;
        return this;
    }

    /**
     * Sets this rectangle according to the specified parameters.
     *
     * @param min the minimum axis values contained in this rectangle.
     * @param max the maximum axis values contained in this rectangle.
     * @return this rectangle.
     */
    public Rect2i set(Vec2i min, Vec2i max) {
        this.xmin = min.x;
        this.ymin = min.y;
        this.xmax = max.x;
        this.ymax = max.y;
        return this;
    }

    /**
     * Sets this rectangle by copying the specified one.
     *
     * @param other the rectangle from which the new rectangle should be copy-constructed.
     * @return this rectangle.
     */
    public Rect2i set(Rect2i other) {
        this.xmin = other.xmin;
        this.ymin = other.ymin;
        this.xmax = other.xmax;
        this.ymax = other.ymax;
        return this;
    }

    /**
     * Returns the minimum axis values of this rectangle.
     *
     * @return the minimum axis values.
     */
    public Vec2i min() {
        return new Vec2i(xmin, ymin);
    }

    /**
     * Returns the maximum axis values of this rectangle.
     *
     * @return the maximum axis values.
     */
    public Vec2i max() {
        return new Vec2i(xmax, ymax);
    }


    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Rect2i)) return false;

        Rect2i other = (Rect2i) obj;
        return this.xmin == other.xmin
                && this.ymin == other.ymin
                && this.xmax == other.xmax
                && this.ymax == other.ymax;
    }

    @Override
    public int hashCode() {
        return new Hasher()
                .add(xmin)
                .add(ymin)
                .add(xmax)
                .add(ymax)
                .getHash();
    }

    @Override
    public String toString() {
        return this.getClass() + " {" + xmin + ", " + ymin + ", " + xmax + ", " + ymax + "}";
    }

    public Rect2i fix() {
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

        return this;
    }

    public int length() {
        return (xmax - xmin) * (ymax - ymin);
    }

    public Rect2i expandIf(Vec2i... points) {
        for (Vec2i v : points) {
            if (xmin > v.x) xmin = v.x;
            if (xmax < v.x) xmax = v.x;

            if (ymin > v.y) ymin = v.y;
            if (ymax < v.y) ymax = v.y;
        }

        return this;
    }

    public boolean contains(Rect2i other) {
        return xmax >= other.xmax && xmin <= other.xmin && ymax >= other.ymax && ymin <= other.ymin;
    }

    public Rect2i grow(int i) {
        xmin -= i;
        ymin -= i;
        xmax += i;
        ymax += i;
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
    }

    public int size() {
        return (xmax - xmin + 1) * (ymax - ymin + 1);
    }

    public Rect2i copy() {
        return new Rect2i(this);
    }
}
