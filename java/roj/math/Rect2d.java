package roj.math;

import roj.util.Hasher;

import java.util.List;


/**
 * An axis-aligned (two-dimensional) {@code double}-based rectangle.
 *
 * @author Maximilian Luz
 */
public class Rect2d {
	public double xmin, ymin, xmax, ymax;

	/**
	 * Constructs a new rectangle with the given properties.
	 *
	 * @param xmin the minimum {@code x}-axis value contained in the rectangle.
	 * @param ymin the minimum {@code y}-axis value contained in the rectangle.
	 * @param xmax the maximum {@code x}-axis value contained in the rectangle.
	 * @param ymax the maximum {@code y}-axis value contained in the rectangle.
	 */
	public Rect2d(double xmin, double ymin, double xmax, double ymax) {
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
	public Rect2d(Vec2d min, Vec2d max) {
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
	public Rect2d(Rect2d other) {
		this.xmin = other.xmin;
		this.ymin = other.ymin;
		this.xmax = other.xmax;
		this.ymax = other.ymax;
	}


	/**
	 * Converts the given {@code Rect2i} to a {@code Rect2d} by promoting integer to doubles.
	 *
	 * @param other the {@code Rect2i} to convert.
	 *
	 * @return the given {@code Rect2i} as {@code Rect2d}.
	 */
	public static Rect2d from(Rect2i other) {
		return new Rect2d(other.xmin, other.ymin, other.xmax, other.ymax);
	}


	/**
	 * Sets this rectangle according to the specified parameters.
	 *
	 * @param xmin the minimum {@code x}-axis value contained in the rectangle.
	 * @param ymin the minimum {@code y}-axis value contained in the rectangle.
	 * @param xmax the maximum {@code x}-axis value contained in the rectangle.
	 * @param ymax the maximum {@code y}-axis value contained in the rectangle.
	 *
	 * @return this rectangle.
	 */
	public Rect2d set(double xmin, double ymin, double xmax, double ymax) {
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
	 *
	 * @return this rectangle.
	 */
	public Rect2d set(Vec2d min, Vec2d max) {
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
	 *
	 * @return this rectangle.
	 */
	public Rect2d set(Rect2d other) {
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
	public Vec2d min() {
		return new Vec2d(xmin, ymin);
	}

	/**
	 * Returns the maximum axis values of this rectangle.
	 *
	 * @return the maximum axis values.
	 */
	public Vec2d max() {
		return new Vec2d(xmax, ymax);
	}


	public Vec2d[] vertices() {
		return new Vec2d[] {new Vec2d(xmin, ymin), new Vec2d(xmin, ymax), new Vec2d(xmax, ymax), new Vec2d(xmax, ymin)};
	}

	public boolean contains(Vec2d p) {
		return xmin <= p.x && p.x <= xmax && ymin <= p.y && p.y <= ymax;
	}

	public boolean contains(double x, double y) {
		return xmin <= x && x <= xmax && ymin <= y && y <= ymax;
	}

	public boolean intersects(Rect2d other) {
		return !(xmax < other.xmin || xmin > other.xmax || ymax < other.ymin || ymin > other.ymax);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Rect2d)) return false;

		Rect2d other = (Rect2d) obj;
		return this.xmin == other.xmin && this.ymin == other.ymin && this.xmax == other.xmax && this.ymax == other.ymax;
	}

	@Override
	public int hashCode() {
		return new Hasher().add(xmin).add(ymin).add(xmax).add(ymax).getHash();
	}

	@Override
	public String toString() {
		return this.getClass() + " {" + xmin + ", " + ymin + ", " + xmax + ", " + ymax + "}";
	}

	public Rect2d fix() {
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

		return this;
	}

	public double length() {
		return (xmax - xmin) * (ymax - ymin);
	}

	public Rect2d expandIf(Vec2d... points) {
		for (Vec2d v : points) {
			if (xmin > v.x) xmin = v.x;
			if (xmax < v.x) xmax = v.x;

			if (ymin > v.y) ymin = v.y;
			if (ymax < v.y) ymax = v.y;
		}

		return this;
	}

	public Rect2d expandIf(List<Vec2d> points) {
		for (int i = 0; i < points.size(); i++) {
			Vec2d v = points.get(i);
			if (xmin > v.x) xmin = v.x;
			if (xmax < v.x) xmax = v.x;

			if (ymin > v.y) ymin = v.y;
			if (ymax < v.y) ymax = v.y;
		}

		return this;
	}

	public boolean contains(Rect2d other) {
		return xmax >= other.xmax && xmin <= other.xmin && ymax >= other.ymax && ymin <= other.ymin;
	}

	public Rect2d grow(int i) {
		xmin -= i;
		ymin -= i;
		xmax += i;
		ymax += i;
		return this;
	}

	public void expandIf(double x, double y, double z) {
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

	public double size() {
		return (xmax - xmin + 1) * (ymax - ymin + 1);
	}

	public Rect2d copy() {
		return new Rect2d(this);
	}

	/**
	 * Projects the given point from the given source rectangle to the given target rectangle.
	 *
	 * @param from the rectangle indicating the source coordinate system.
	 * @param to the rectangle indicating the target coordinate system.
	 * @param p the point to project.
	 *
	 * @return {@code p} projected from {@code from} to {@code to}.
	 */
	public static Vec2d project(Rect2d from, Rect2d to, Vec2d p) {
		return new Vec2d(to.xmin + (p.x - from.xmin) / (from.xmax - from.xmin) * (to.xmax - to.xmin), to.ymin + (p.y - from.ymin) / (from.ymax - from.ymin) * (to.ymax - to.ymin));
	}

	public static Rect2d transform(Mat3d transform, Rect2d bounds) {
		Vec3d min = transform.mul(new Vec3d(bounds.min(), 1.0f));
		Vec3d max = transform.mul(new Vec3d(bounds.max(), 1.0f));

		return new Rect2d(min.x / min.z, min.y / min.z, max.x / max.z, max.y / max.z);
	}

	public static Rect2d from(Vec2d... points) {
		return new Rect2d(Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE).expandIf(points);
	}
}
