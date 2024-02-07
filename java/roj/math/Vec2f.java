package roj.math;

public class Vec2f extends Vector {
	public float x, y;

	public Vec2f() {}
	@Override
	public Vector newInstance() { return new Vec2f(); }

	public Vec2f(float x, float y) {
		this.x = x;
		this.y = y;
	}
	public Vec2f(Vector xy) {
		x = (float) xy.x();
		y = (float) xy.y();
	}

	public final Vec2f set(float x, float y) {
		this.x = x;
		this.y = y;
		return this;
	}
	public final Vector set(Vector xy) {
		x = (float) xy.x();
		y = (float) xy.y();
		return this;
	}

	@Override
	public final Vector cross(Vector v) {
		throw new UnsupportedOperationException("Not supported in Vec2");
	}

	public final double x() {
		return x;
	}
	public final double y() {
		return y;
	}
	public final void x(double x) {
		this.x = (float) x;
	}
	public final void y(double y) {
		this.y = (float) y;
	}
	public final int axis() {
		return 2;
	}
}
