package roj.math;

public class Vec3f extends Vector {
	public float x, y, z;

	public Vec3f() {}
	@Override
	public Vector newInstance() { return new Vec3f(); }

	public Vec3f(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}
	public Vec3f(Vector xy, float z) {
		this((float) xy.x(), (float) xy.y(), z);
	}
	public Vec3f(Vector xyz) {
		this((float) xyz.x(), (float) xyz.y(), (float) xyz.z());
	}

	/**
	 * @param yaw 旋转X
	 * @param pitch 旋转Y
	 */
	public Vec3f(float yaw, float pitch) {
		y = -MathUtils.sin(pitch);

		float xz = MathUtils.cos(pitch);
		x = -xz * MathUtils.sin(yaw);
		z = xz * MathUtils.cos(yaw);
	}

	public final Vec3f set(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
	}
	public final Vec3f set(Vector xy, float z) {
		this.x = (float) xy.x();
		this.y = (float) xy.y();
		this.z = z;
		return this;
	}

	public final Vec3f cross(Vector v) {
		float x1 = y * (float)v.z() - (float)v.y() * z;
		float y1 = z * (float)v.x() - (float)v.z() * x;
		z = x * (float)v.y() - (float)v.x() * y;

		x = x1;
		y = y1;

		return this;
	}

	/**
	 * 自身在直线上的投影
	 *
	 * @param line 直线向量
	 *
	 * @return 投影点
	 */
	public Vec3f project(Vec3f line) {
		float len = (float) line.len2();
		if (len == 0) return this;

		float dx = line.x;
		float dy = line.y;
		float dz = line.z;

		if (len != 1) {
			float abs = MathUtils.sqrt(len);
			dx /= abs;
			dy /= abs;
			dz /= abs;
		}

		float dxyz = dx * dy * dz;

		float x = dx * this.x + dxyz * this.y * this.z;
		float y = dy * this.y + dxyz * this.x * this.z;
		float z = dz * this.z + dxyz * this.x * this.y;

		this.x = x;
		this.y = y;
		this.z = z;

		return this;
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
		this.x = (float) x;
	}
	public final void y(double y) {
		this.y = (float) y;
	}
	public final void z(double z) {
		this.z = (float) z;
	}
	public final int axis() {
		return 3;
	}
}
