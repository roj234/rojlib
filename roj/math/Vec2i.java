package roj.math;

public class Vec2i extends Vector {
	public int x, y;

	public Vec2i() {}
	@Override
	public Vector newInstance() { return new Vec2i(); }

	public Vec2i(int x, int y) {
		this.x = x;
		this.y = y;
	}
	public Vec2i(Vector xy) {
		x = (int) xy.x();
		y = (int) xy.y();
	}

	public final Vec2i set(int x, int y) {
		this.x = x;
		this.y = y;
		return this;
	}
	public final Vec2i set(Vector xy) {
		x = (int) xy.x();
		y = (int) xy.y();
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
		this.x = (int) x;
	}
	public final void y(double y) {
		this.y = (int) y;
	}
	public final int axis() {
		return 2;
	}
}
