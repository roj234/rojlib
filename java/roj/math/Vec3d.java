package roj.math;

public class Vec3d extends Vector {
	public double x, y, z;

	public Vec3d() {}
	@Override
	public Vector newInstance() { return new Vec3d(); }

	public Vec3d(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vec3d(Vector xy, double z) {
		this(xy.x(), xy.y(), z);
	}
	public Vec3d(Vector xyz) {
		this(xyz.x(), xyz.y(), xyz.z());
	}

	/**
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

	public final Vec3d set(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
	}
	public final Vec3d set(Vector xy, double z) {
		this.x = xy.x();
		this.y = xy.y();
		this.z = z;
		return this;
	}
	public final Vec2d xy() {
		return new Vec2d(x, y);
	}

	public final Vec3d normalizeUnit(double d) {
		double abs = 1 / (Math.sqrt(x * x + y * y + z * z) * d);
		x /= abs;
		y /= abs;
		z /= abs;
		return this;
	}

	public final Vec3d add(double x, double y, double z) {
		this.x += x;
		this.y += y;
		this.z += z;
		return this;
	}

	/**
	 * 与a,b都垂直的向量
	 */
	@Override
	public final Vector cross(Vector v) {
		double x1 = y * v.z() - v.y() * z;
		double y1 = z * v.x() - v.z() * x;
		z = x * v.y() - v.x() * y;
		x = x1;
		y = y1;
		return this;
	}

	/**
	 * 在直线上的投影
	 */
	public Vec3d project(Vec3d line) {
		return project(this, line, this);
	}

	/**
	 * 投影点
	 *
	 * @param v 还是容器
	 */
	public static Vec3d project(Vec3d point, Vec3d line, Vec3d v) {
		v.set(point)
		 // 1. Point和Line所在的平面的法线
		 .cross(line)
		 // 2. 与Line垂直和(法线垂直 => 平面内)的向量
		 .cross(line);

		double B = (line.x * point.y / line.y - point.x) / (v.x - line.x * v.y / line.y);
		return (Vec3d) v.mul(B).add(point);
	}

	public Vec2d projectXY() {
		double k = (x * x + y * y) / len2();
		return new Vec2d(k * x, k * y);
	}

	public final double x() {
		return x;
	}
	public final double y() {
		return y;
	}
	public final double z() {
		return z;
	}
	public final void x(double x) {
		this.x = x;
	}
	public final void y(double y) {
		this.y = y;
	}
	public final void z(double z) {
		this.z = z;
	}
	public final int axis() {
		return 3;
	}
}