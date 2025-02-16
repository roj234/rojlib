package roj.math;

public class Vec3i extends Vector {
	public int x, y, z;

	public Vec3i() {}
	@Override
	public Vec3i copy() { return new Vec3i(this); }

	public Vec3i(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}
	public Vec3i(Vector xy, int z) {this((int) xy.x(), (int) xy.y(), z);}
	public Vec3i(Vector xyz) {this((int) xyz.x(), (int) xyz.y(), (int) xyz.z());}
	public Vec3i(Vec3i xyz) {this(xyz.x, xyz.y, xyz.z);}

	public final double x() {return x;}
	public final double y() {return y;}
	public final double z() {return z;}
	public final void x(double x) {this.x = (int) x;}
	public final void y(double y) {this.y = (int) y;}
	public final void z(double z) {this.z = (int) z;}
	public final int axis() {return 3;}

	public final Vec3i set(Vector v) {
		x = (int) v.x();
		y = (int) v.y();
		z = (int) v.z();
		return this;
	}
	public final Vec3i set(Vec3i v) {
		x = v.x;
		y = v.y;
		z = v.z;
		return this;
	}
	public final Vec3i set(Vector xy, int z) {
		this.x = (int) xy.x();
		this.y = (int) xy.y();
		this.z = z;
		return this;
	}
	public Vec3i set(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
	}
	public final Vec3i add(Vector v) {
		x += v.x();
		y += v.y();
		z += v.z();
		return this;
	}
	public final Vec3i add(Vec3i v) {
		x += v.x;
		y += v.y;
		z += v.z;
		return this;
	}
	public final Vec3i add(double x, double y, double z) {
		this.x += x;
		this.y += y;
		this.z += z;
		return this;
	}
	public final Vec3i add(int x, int y, int z) {
		this.x += x;
		this.y += y;
		this.z += z;
		return this;
	}
	public final Vec3i sub(Vector v) {
		x -= v.x();
		y -= v.y();
		z -= v.z();
		return this;
	}
	public final Vec3i mul(Vector v) {
		x *= v.x();
		y *= v.y();
		z *= v.z();
		return this;
	}
	public final Vec3i mul(Vec3i v) {
		x *= v.x;
		y *= v.y;
		z *= v.z;
		return this;
	}
	public final Vec3i mul(double scalar) {
		x *= scalar;
		y *= scalar;
		z *= scalar;
		return this;
	}
	public final Vec3i mul(int scalar) {
		x *= scalar;
		y *= scalar;
		z *= scalar;
		return this;
	}

	public final double lengthSquared() {return x*x+y*y+z*z;}
	public final double distanceSq(Vector v) {
		double t,d;
		t = v.x()-x;
		d = t * t;
		t = v.y()-y;
		d += t*t;
		t = v.z()-z;
		d += t*t;
		return d;
	}

	public final Vec3i normalize() {
		var len = lengthSquared();
		x /= len;
		y /= len;
		z /= len;
		return this;
	}

	public final double dot(Vector v) {return x*v.x()+y*v.y()+z*v.z();}
	/**
	 * 与a,b都垂直的向量
	 */
	public final Vec3i cross(Vector v) {
		int x1 = y * (int)v.z() - (int)v.y() * z;
		int y1 = z * (int)v.x() - (int)v.z() * x;
		z = x * (int)v.y() - (int)v.x() * y;
		x = x1;
		y = y1;
		return this;
	}
}