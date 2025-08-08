package roj.math;

import roj.util.Hasher;

/**
 * An three-dimensional {@code int}-based rectangle.
 */
public class Rect3i {
	public int minX, minY, minZ, maxX, maxY, maxZ;

	public Rect3i() {}

	public Rect3i(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		this.minX = minX;
		this.minY = minY;
		this.minZ = minZ;

		this.maxX = maxX;
		this.maxY = maxY;
		this.maxZ = maxZ;
		fix();
	}

	public Rect3i(Vec3i min, Vec3i max) {
		set(min, max);
	}

	public Rect3i(Rect3i other) {
		set(other);
	}

	public Rect3i set(int xmin, int ymin, int zmin, int xmax, int ymax, int zmax) {
		this.minX = xmin;
		this.minY = ymin;
		this.minZ = zmin;

		this.maxX = xmax;
		this.maxY = ymax;
		this.maxZ = zmax;
		fix();

		return this;
	}

	public Rect3i set(Vec3i min, Vec3i max) {
		this.minX = min.x;
		this.minY = min.y;
		this.minZ = min.z;

		this.maxX = max.x;
		this.maxY = max.y;
		this.maxZ = max.z;
		fix();

		return this;
	}

	public Rect3i set(Rect3i other) {
		this.minX = other.minX;
		this.minY = other.minY;
		this.minZ = other.minZ;

		this.maxX = other.maxX;
		this.maxY = other.maxY;
		this.maxZ = other.maxZ;
		fix();

		return this;
	}

	public Rect3i fix() {
		int tmp;

		if (minX > maxX) {
			tmp = maxX;
			maxX = minX;
			minX = tmp;
		}
		if (minY > maxY) {
			tmp = maxY;
			maxY = minY;
			minY = tmp;
		}
		if (minZ > maxZ) {
			tmp = maxZ;
			maxZ = minZ;
			minZ = tmp;
		}

		return this;
	}

	/**
	 * Returns the minimum axis values of this rectangle.
	 *
	 * @return the minimum axis values.
	 */
	public Vec3i min() {
		return new Vec3i(minX, minY, minZ);
	}

	/**
	 * Returns the maximum axis values of this rectangle.
	 *
	 * @return the maximum axis values.
	 */
	public Vec3i max() {
		return new Vec3i(maxX, maxY, maxZ);
	}

	public Vec3i[] vertices() {
		return new Vec3i[] {new Vec3i(minX, minY, minZ), // 000
							new Vec3i(minX, minY, maxZ), // 001
							new Vec3i(minX, maxY, minZ), // 010
							new Vec3i(minX, maxY, maxZ), // 011
							new Vec3i(maxX, minY, minZ), // 100
							new Vec3i(maxX, minY, maxZ), // 101
							new Vec3i(maxX, maxY, minZ), // 110
							new Vec3i(maxX, maxY, maxZ)  // 111
		};
	}

	public Vec3i[] minMax() {
		return new Vec3i[] {new Vec3i(minX, minY, minZ), // 000
							new Vec3i(maxX, maxY, maxZ) // 001
		};
	}

	public boolean contains(double x, double y, double z) {
		return minX <= x && x <= maxX && minY <= y && y <= maxY && minZ <= z && z <= maxZ;
	}

	public boolean contains(Vec3i p) {
		return minX <= p.x && p.x <= maxX && minY <= p.y && p.y <= maxY && minZ <= p.z && p.z <= maxZ;
	}

	public boolean contains(Rect3i o) {
		return maxX >= o.maxX && minX <= o.minX && maxY >= o.maxY && minY <= o.minY && maxZ >= o.maxZ && minZ <= o.minZ;
	}

	public boolean intersects(Rect3i o) {
		return !(maxX < o.minX || minX > o.maxX || maxY < o.minY || minY > o.maxY || maxZ < o.minZ || minZ > o.maxZ);
	}

	/**
	 * 注意，边界也计算在内
	 */
	public Rect3i intersectsWith(Rect3i o) {
		if (intersects(o)) {
			return new Rect3i(Math.max(minX, o.minX), Math.max(minY, o.minY), Math.max(minZ, o.minZ), Math.min(maxX, o.maxX), Math.min(maxY, o.maxY), Math.min(maxZ, o.maxZ));
		} else {
			return null;
		}
	}

	public Rect3i expandIf(Vec3i... points) {
		for (Vec3i v : points) {
			if (minX > v.x) minX = v.x;
			if (maxX < v.x) maxX = v.x;

			if (minY > v.y) minY = v.y;
			if (maxY < v.y) maxY = v.y;

			if (minZ > v.z) minZ = v.z;
			if (maxZ < v.z) maxZ = v.z;
		}

		return this;
	}

	public void expandIf(int x, int y, int z) {
		if (x < minX) {
			minX = x;
		} else if (x > maxX) {
			maxX = x;
		}
		if (y < minY) {
			minY = y;
		} else if (y > maxY) {
			maxY = y;
		}
		if (z < minZ) {
			minZ = z;
		} else if (z > maxZ) {
			maxZ = z;
		}
	}

	public Rect3i grow(int i) {
		minX -= i;
		minY -= i;
		minZ -= i;
		maxX += i;
		maxY += i;
		maxZ += i;
		return this;
	}

	public boolean isOnBorder(int x, int y, int z) {
		return x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
	}

	public static Rect3i from(Vec3i... points) {
		return new Rect3i(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, -Integer.MAX_VALUE, -Integer.MAX_VALUE, -Integer.MAX_VALUE).expandIf(points);
	}

	public int volume() {
		return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
	}

	public Rect3i copy() {
		return new Rect3i(this);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Rect3i)) return false;

		Rect3i other = (Rect3i) obj;
		return this.minX == other.minX && this.minY == other.minY && this.minZ == other.minZ && this.maxX == other.maxX && this.maxY == other.maxY && this.maxZ == other.maxZ;
	}

	@Override
	public int hashCode() {
		return new Hasher().add(minX).add(minY).add(minZ).add(maxX).add(maxY).add(maxZ).getHash();
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + " {" + minX + ", " + minY + ", " + minZ + ", " + maxX + ", " + maxY + ", " + maxZ + "}";
	}
}