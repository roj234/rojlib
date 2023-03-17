package roj.math;

import roj.util.Hasher;

/**
 * An three-dimensional {@code int}-based rectangle.
 */
public class Rect3i {
	public int xmin, ymin, zmin, xmax, ymax, zmax;

	public Rect3i() {}

	public Rect3i(int xmin, int ymin, int zmin, int xmax, int ymax, int zmax) {
		this.xmin = xmin;
		this.ymin = ymin;
		this.zmin = zmin;

		this.xmax = xmax;
		this.ymax = ymax;
		this.zmax = zmax;
		fix();
	}

	public Rect3i(Vec3i min, Vec3i max) {
		set(min, max);
	}

	public Rect3i(Rect3i other) {
		set(other);
	}

	public Rect3i set(int xmin, int ymin, int zmin, int xmax, int ymax, int zmax) {
		this.xmin = xmin;
		this.ymin = ymin;
		this.zmin = zmin;

		this.xmax = xmax;
		this.ymax = ymax;
		this.zmax = zmax;
		fix();

		return this;
	}

	public Rect3i set(Vec3i min, Vec3i max) {
		this.xmin = min.x;
		this.ymin = min.y;
		this.zmin = min.z;

		this.xmax = max.x;
		this.ymax = max.y;
		this.zmax = max.z;
		fix();

		return this;
	}

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
		return new Vec3i[] {new Vec3i(xmin, ymin, zmin), // 000
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
		return new Vec3i[] {new Vec3i(xmin, ymin, zmin), // 000
							new Vec3i(xmax, ymax, zmax) // 001
		};
	}

	public boolean contains(double x, double y, double z) {
		return xmin <= x && x <= xmax && ymin <= y && y <= ymax && zmin <= z && z <= zmax;
	}

	public boolean contains(Vec3i p) {
		return xmin <= p.x && p.x <= xmax && ymin <= p.x && p.x <= ymax && zmin <= p.x && p.x <= zmax;
	}

	public boolean contains(Rect3i o) {
		return xmax >= o.xmax && xmin <= o.xmin && ymax >= o.ymax && ymin <= o.ymin && zmax >= o.zmax && zmin <= o.zmin;
	}

	public boolean intersects(Rect3i o) {
		return !(xmax < o.xmin || xmin > o.xmax || ymax < o.ymin || ymin > o.ymax || zmax < o.zmin || zmin > o.zmax);
	}

	/**
	 * 注意，边界也计算在内
	 */
	public Rect3i intersectsWith(Rect3i o) {
		if (intersects(o)) {
			return new Rect3i(Math.max(xmin, o.xmin), Math.max(ymin, o.ymin), Math.max(zmin, o.zmin), Math.min(xmax, o.xmax), Math.min(ymax, o.ymax), Math.min(zmax, o.zmax));
		} else {
			return null;
		}
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

	public Rect3i grow(int i) {
		xmin -= i;
		ymin -= i;
		zmin -= i;
		xmax += i;
		ymax += i;
		zmax += i;
		return this;
	}

	public boolean isOnBorder(int x, int y, int z) {
		return x == xmin || x == xmax || y == ymin || y == ymax || z == zmin || z == zmax;
	}

	public static Rect3i from(Vec3i... points) {
		return new Rect3i(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, -Integer.MAX_VALUE, -Integer.MAX_VALUE, -Integer.MAX_VALUE).expandIf(points);
	}

	public int volume() {
		return (xmax - xmin + 1) * (ymax - ymin + 1) * (zmax - zmin + 1);
	}

	public Rect3i copy() {
		return new Rect3i(this);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Rect3i)) return false;

		Rect3i other = (Rect3i) obj;
		return this.xmin == other.xmin && this.ymin == other.ymin && this.zmin == other.zmin && this.xmax == other.xmax && this.ymax == other.ymax && this.zmax == other.zmax;
	}

	@Override
	public int hashCode() {
		return new Hasher().add(xmin).add(ymin).add(zmin).add(xmax).add(ymax).add(zmax).getHash();
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + " {" + xmin + ", " + ymin + ", " + zmin + ", " + xmax + ", " + ymax + ", " + zmax + "}";
	}
}