package roj.math;

public class Vec3i extends Vector {
	public int x, y, z;

	public Vec3i() {}
	@Override
	public Vector newInstance() { return new Vec3i(); }

	public Vec3i(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}
	public Vec3i(Vector xy, int z) {
		this((int) xy.x(), (int) xy.y(), z);
	}
	public Vec3i(Vector xyz) {
		this((int) xyz.x(), (int) xyz.y(), (int) xyz.z());
	}

	public Vec3i set(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
	}
	public final Vec3i set(Vector xy, int z) {
		this.x = (int) xy.x();
		this.y = (int) xy.y();
		this.z = z;
		return this;
	}
	public final Vec3i add(int x, int y, int z) {
		this.x += x;
		this.y += y;
		this.z += z;
		return this;
	}

	public final Vec3i cross(Vector v) {
		int x1 = y * (int)v.z() - (int)v.y() * z;
		int y1 = z * (int)v.x() - (int)v.z() * x;
		z = x * (int)v.y() - (int)v.x() * y;

		x = x1;
		y = y1;

		return this;
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
		this.x = (int) x;
	}
	public final void y(double y) {
		this.y = (int) y;
	}
	public final void z(double z) {
		this.z = (int) z;
	}
	public final int axis() {
		return 3;
	}
}
