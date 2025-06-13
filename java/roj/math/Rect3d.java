package roj.math;

import roj.util.Hasher;

/**
 * An three-dimensional {@code double}-based rectangle.
 */
public class Rect3d {
	public double minX, minY, minZ, maxX, maxY, maxZ;

	/**
	 * Constructs a new rectangle with the given properties.
	 *
	 * @param minX the minimum {@code x}-axis value contained in the rectangle.
	 * @param minY the minimum {@code y}-axis value contained in the rectangle.
	 * @param minZ the minimum {@code z}-axis value contained in the rectangle.
	 * @param maxX the maximum {@code x}-axis value contained in the rectangle.
	 * @param maxY the maximum {@code y}-axis value contained in the rectangle.
	 * @param maxZ the maximum {@code z}-axis value contained in the rectangle.
	 */
	public Rect3d(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		this.minX = minX;
		this.minY = minY;
		this.minZ = minZ;

		this.maxX = maxX;
		this.maxY = maxY;
		this.maxZ = maxZ;
	}

	/**
	 * Constructs a new rectangle with the given parameters.
	 *
	 * @param min the minimum axis values contained in this rectangle.
	 * @param max the maximum axis values contained in this rectangle.
	 */
	public Rect3d(Vec3d min, Vec3d max) {
		this.minX = min.x;
		this.minY = min.y;
		this.minZ = min.z;

		this.maxX = max.x;
		this.maxY = max.y;
		this.maxZ = max.z;
	}

	/**
	 * Constructs a new rectangle by copying the specified one.
	 *
	 * @param other the rectangle from which the new rectangle should be copy-constructed.
	 */
	public Rect3d(Rect3d other) {
		this.minX = other.minX;
		this.minY = other.minY;
		this.minZ = other.minZ;

		this.maxX = other.maxX;
		this.maxY = other.maxY;
		this.maxZ = other.maxZ;
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
	 *
	 * @return this rectangle.
	 */
	public Rect3d set(double xmin, double ymin, double zmin, double xmax, double ymax, double zmax) {
		this.minX = xmin;
		this.minY = ymin;
		this.minZ = zmin;

		this.maxX = xmax;
		this.maxY = ymax;
		this.maxZ = zmax;

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
	public Rect3d set(Vec3d min, Vec3d max) {
		this.minX = min.x;
		this.minY = min.y;
		this.minZ = min.z;

		this.maxX = max.x;
		this.maxY = max.y;
		this.maxZ = max.z;

		return this;
	}

	/**
	 * Sets this rectangle by copying the specified one.
	 *
	 * @param other the rectangle from which the new rectangle should be copy-constructed.
	 *
	 * @return this rectangle.
	 */
	public Rect3d set(Rect3d other) {
		this.minX = other.minX;
		this.minY = other.minY;
		this.minZ = other.minZ;

		this.maxX = other.maxX;
		this.maxY = other.maxY;
		this.maxZ = other.maxZ;

		return this;
	}

	public Rect3d fix() {
		double tmp;

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
	public Vec3d min() {
		return new Vec3d(minX, minY, minZ);
	}

	/**
	 * Returns the maximum axis values of this rectangle.
	 *
	 * @return the maximum axis values.
	 */
	public Vec3d max() {
		return new Vec3d(maxX, maxY, maxZ);
	}

	public Vec3d[] vertices() {
		return new Vec3d[] {new Vec3d(minX, minY, minZ), // 000
							new Vec3d(minX, minY, maxZ), // 001
							new Vec3d(minX, maxY, minZ), // 010
							new Vec3d(minX, maxY, maxZ), // 011
							new Vec3d(maxX, minY, minZ), // 100
							new Vec3d(maxX, minY, maxZ), // 101
							new Vec3d(maxX, maxY, minZ), // 110
							new Vec3d(maxX, maxY, maxZ)  // 111
		};
	}

	public Vec3d[] minMax() {
		return new Vec3d[] {new Vec3d(minX, minY, minZ), // 000
							new Vec3d(maxX, maxY, maxZ) // 001
		};
	}

	public boolean contains(double x, double y, double z) {
		return minX <= x && x <= maxX && minY <= y && y <= maxY && minZ <= z && z <= maxZ;
	}

	public boolean contains(Vec3d p) {
		return minX <= p.x && p.x <= maxX && minY <= p.y && p.y <= maxY && minZ <= p.z && p.z <= maxZ;
	}

	public boolean contains(Rect3d o) {
		return maxX >= o.maxX && minX <= o.minX && maxY >= o.maxY && minY <= o.minY && maxZ >= o.maxZ && minZ <= o.minZ;
	}

	public boolean intersects(Rect3d o) {
		return !(maxX < o.minX || minX > o.maxX || maxY < o.minY || minY > o.maxY || maxZ < o.minZ || minZ > o.maxZ);
	}

	/**
	 * 注意，边界也计算在内
	 */
	public Rect3d intersectsWith(Rect3d o) {
		if (intersects(o)) {
			return new Rect3d(Math.max(minX, o.minX), Math.max(minY, o.minY), Math.max(minZ, o.minZ), Math.min(maxX, o.maxX), Math.min(maxY, o.maxY), Math.min(maxZ, o.maxZ));
		} else {
			return null;
		}
	}

	public Rect3d expandIf(Vec3d... points) {
		for (Vec3d v : points) {
			if (minX > v.x) minX = v.x;
			if (maxX < v.x) maxX = v.x;

			if (minY > v.y) minY = v.y;
			if (maxY < v.y) maxY = v.y;

			if (minZ > v.z) minZ = v.z;
			if (maxZ < v.z) maxZ = v.z;
		}

		return this;
	}

	public void expandIf(double x, double y, double z) {
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

	public Rect3d grow(int i) {
		minX -= i;
		minY -= i;
		minZ -= i;
		maxX += i;
		maxY += i;
		maxZ += i;
		return this;
	}

	public static Rect3d from(Vec3d... points) {
		return new Rect3d(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE).expandIf(points);
	}

	public double volume() {
		return (maxX - minX + 1.0) * (maxY - minY + 1.0) * (maxZ - minZ + 1.0);
	}

	public Rect3d copy() {
		return new Rect3d(this);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Rect3d)) return false;

		Rect3d other = (Rect3d) obj;
		return this.minX == other.minX && this.minY == other.minY && this.minZ == other.minZ && this.maxX == other.maxX && this.maxY == other.maxY && this.maxZ == other.maxZ;
	}

	@Override
	public int hashCode() {
		return new Hasher().add(minX).add(minY).add(minZ).add(maxX).add(maxY).add(maxZ).getHash();
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + " {" + minX + ", " + minY + ", " + minZ + ", " + maxX + ", " + maxY + ", " + minZ + "}";
	}
}
