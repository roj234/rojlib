package roj.math;

public class Vec2d extends Vector {
	public double x, y;

	public Vec2d() {}
	@Override
	public Vector newInstance() { return new Vec2d(); }

	public Vec2d(double x, double y) {this.x = x;this.y = y;}
	public Vec2d(Vector xy) {x = xy.x();y = xy.y();}

	public final Vec2d set(double x, double y) {this.x = x;this.y = y;return this;}
	public final Vec2d set(Vector xy) {x = xy.x();y = xy.y();return this;}

	@Override
	public final Vector cross(Vector v) {throw new UnsupportedOperationException();}

	public Vec2d rotateAbs(double angle) {
		double len = len();
		this.x = len * Math.sin(angle);
		this.y = len * Math.cos(angle);
		return this;
	}
	public double angle() {return Math.atan(y / x);}

	public final double x() {return x;}
	public final double y() {return y;}
	public final void x(double x) {this.x = x;}
	public final void y(double y) {this.y = y;}
	public final int axis() {return 2;}
}