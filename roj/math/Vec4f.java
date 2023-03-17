package roj.math;

@Deprecated
public class Vec4f extends Vector {
	public float x, y, z, w = 1;

	public Vec4f() {}
	@Override
	public Vector newInstance() { return new Vec4f(); }

	public Vec4f(float x, float y) {
		this.x = x;
		this.y = y;
	}
	public Vec4f(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}
	public Vec4f(float x, float y, float z, float w) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
	}

	public Vec4f(Vec2f xy, float z, float w) {
		this(xy.x, xy.y, z, w);
	}
	public Vec4f(Vec3f xyz, float w) {
		this(xyz.x, xyz.y, xyz.z, w);
	}
	public Vec4f(Vec4f xyzw) {
		this(xyzw.x, xyzw.y, xyzw.z, xyzw.w);
	}

	public Vec4f set(float x, float y, float z, float w) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
		return this;
	}
	public Vec4f set(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = 1;
		return this;
	}
	public Vec4f set(float x, float y) {
		this.x = x;
		this.y = y;
		this.z = 0;
		this.w = 1;
		return this;
	}
	public Vec4f set(Vec2f xy, float z, float w) {
		this.x = xy.x;
		this.y = xy.y;
		this.z = z;
		this.w = w;
		return this;
	}
	public Vec4f set(Vec3f xyz, float w) {
		this.x = xyz.x;
		this.y = xyz.y;
		this.z = xyz.z;
		this.w = w;
		return this;
	}

	public Vec4f add(float x, float y, float z, float w) {
		this.x += x;
		this.y += y;
		this.z += z;
		this.w += w;
		return this;
	}

	@Override
	public Vector cross(Vector v) {
		return null;
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
		return w;
	}
	public void x(double x) {
		this.x = (float) x;
	}
	public void y(double y) {
		this.y = (float) y;
	}
	public void z(double z) {
		this.z = (float) z;
	}
	public void w(double w) {
		this.w = (float) w;
	}
	public int axis() {
		return 4;
	}
}
