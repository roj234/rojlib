package roj.math;

/**
 * An axis-aligned (two-dimensional) {@code int}-based rectangle.
 *
 * @author Maximilian Luz
 */
public class Rect2i {
	public int xmin, ymin, xmax, ymax;

	public Rect2i() {}

	public Rect2i(int xmin, int ymin, int xmax, int ymax) {
		this.xmin = xmin;
		this.ymin = ymin;
		this.xmax = xmax;
		this.ymax = ymax;
	}

	public Rect2i(Vec2i min, Vec2i max) {
		this.xmin = min.x;
		this.ymin = min.y;
		this.xmax = max.x;
		this.ymax = max.y;
	}

	public Rect2i(Rect2i other) {
		set(other);
	}

	public Rect2i set(int xmin, int ymin, int xmax, int ymax) {
		this.xmin = xmin;
		this.ymin = ymin;
		this.xmax = xmax;
		this.ymax = ymax;
		return this;
	}

	public Rect2i set(Vec2i min, Vec2i max) {
		this.xmin = min.x;
		this.ymin = min.y;
		this.xmax = max.x;
		this.ymax = max.y;
		return this;
	}

	public Rect2i set(Rect2i other) {
		this.xmin = other.xmin;
		this.ymin = other.ymin;
		this.xmax = other.xmax;
		this.ymax = other.ymax;
		return this;
	}

	public Vec2i min() {
		return new Vec2i(xmin, ymin);
	}

	public Vec2i max() {
		return new Vec2i(xmax, ymax);
	}


	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Rect2i)) return false;

		Rect2i other = (Rect2i) obj;
		return this.xmin == other.xmin && this.ymin == other.ymin && this.xmax == other.xmax && this.ymax == other.ymax;
	}

	@Override
	public int hashCode() {
		int i = xmin;
		i = 31 * i + ymin;
		i = 31 * i + xmax;
		i = 31 * i + ymax;
		return i;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + " {" + xmin + ", " + ymin + ", " + xmax + ", " + ymax + "}";
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

	public boolean contains(int x, int y) {
		return xmin <= x && x <= xmax && ymin <= y && y <= ymax;
	}

	public boolean contains(Vec2i p) {
		return xmin <= p.x && p.x <= xmax && ymin <= p.x && p.x <= ymax;
	}

	public boolean contains(Rect2i other) {
		return xmax >= other.xmax && xmin <= other.xmin && ymax >= other.ymax && ymin <= other.ymin;
	}

	public boolean intersects(Rect2i o) {
		return !(xmax < o.xmin || xmin > o.xmax || ymax < o.ymin || ymin > o.ymax);
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
