package roj.math;

public class Vec4d extends Vector {
	public double x, y, z, s = 1;

	public Vec4d() {}
	@Override
	public Vector newInstance() { return new Vec4d(); }

	public Vec4d(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}
	public Vec4d(double x, double y, double z, double s) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.s = s;
	}
	public Vec4d(Vec2d xy, double z, double s) {
		this(xy.x, xy.y, z, s);
	}
	public Vec4d(Vec3f xyz, double s) {
		this(xyz.x, xyz.y, xyz.z, s);
	}
	public Vec4d(Vec4d xyzw) {
		this(xyzw.x, xyzw.y, xyzw.z, xyzw.s);
	}

	public Vec4d set(double x, double y, double z, double w) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.s = w;
		return this;
	}
	public Vec4d set(Vec3f xyz, double w) {
		this.x = xyz.x;
		this.y = xyz.y;
		this.z = xyz.z;
		this.s = w;
		return this;
	}
	public Vec4d set(Vec4d xyzw) {
		this.x = xyzw.x;
		this.y = xyzw.y;
		this.z = xyzw.z;
		this.s = xyzw.s;
		return this;
	}

	public static void main(String[] args) {
		double v = Math.sqrt(2) / 2;

		Vec4d rot = new Vec4d().makeRotation(Math.toRadians(45), new Vec3d(v,0,v));
		System.out.println(rot);
		Vec4d aaa = new Vec4d(2,0,0, 0);
		System.out.println(aaa.len(3));
		aaa.applyRotation(rot);
		System.out.println(aaa);
		System.out.println(aaa.len(3));
		System.out.println(rot);
	}

	// q*
	public Vec4d star() {
		x = -x;
		y = -y;
		z = -z;
		return this;
	}

	public Vec4d cross(Vector b) {
		cross(this, (Vec4d) b, this);
		return this;
	}

	private static void cross(Vec4d a, Vec4d b, Vec4d c) {
		double x1 = a.s*b.x + a.x*b.s + a.y*b.z - a.z*b.y;
		double y1 = a.s*b.y + a.y*b.s + a.z*b.x - a.x*b.z;
		double z1 = a.s*b.z + a.z*b.s + a.x*b.y - a.y*b.x;
		c.s = a.s*b.s - a.x*b.x - a.y*b.y - a.z*b.z;
		c.x = x1;
		c.y = y1;
		c.z = z1;
	}

	// q^-1 = q* / |q|^2
	public Vec4d inverse() {
		double len_2 = x * x + y * y + z * z + s * s;
		return (Vec4d) star().mul(1/len_2);
	}

	public Vec4d applyRotation(Vec4d rot) {
		cross(rot, this, this);
		cross(rot.inverse());
		rot.star();
		return this;
	}

	public Vec4d makeRotation(double angle, Vector axis) {
		angle /= 2;
		s = Math.cos(angle);
		double sin = Math.sin(angle);
		double len = axis.len2();
		x = sin*axis.x()/len;
		y = sin*axis.y()/len;
		z = sin*axis.z()/len;
		return this;
	}

	public double x() {
		return x;
	}
	public double y() {
		return y;
	}
	public double z() {
		return z;
	}
	public double w() {
		return s;
	}
	public void x(double x) {
		this.x = x;
	}
	public void y(double y) {
		this.y = y;
	}
	public void z(double z) {
		this.z = z;
	}
	public void w(double w) {
		this.s = w;
	}
	public int axis() {
		return 4;
	}
}
