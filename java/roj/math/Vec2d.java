package roj.math;

public class Vec2d extends Vector {
	public double x, y;

	public Vec2d() {}
	@Override
	public Vector copy() { return new Vec2d(this); }

	public Vec2d(double x, double y) {this.x = x;this.y = y;}
	public Vec2d(Vector xy) {x = xy.x();y = xy.y();}

    public Vec2d rotateAbs(double angle) {
        double len = length();
        this.x = len * Math.sin(angle);
        this.y = len * Math.cos(angle);
        return this;
    }

	public final double x() {return x;}
	public final double y() {return y;}
	public final void x(double x) {this.x = x;}
	public final void y(double y) {this.y = y;}
	public final int axis() {return 2;}

    public final Vec2d set(double x, double y) {this.x = x;this.y = y;return this;}
    public final Vec2d set(Vector xy) {x = xy.x();y = xy.y();return this;}

    public final Vec2d add(Vector v) {
        x += v.x();
        y += v.y();
        return this;
    }
    public final Vec2d sub(Vector v) {
        x -= v.x();
        y -= v.y();
        return this;
    }

    public final Vec2d add(double x, double y) {
        this.x += x;
        this.y += y;
        return this;
    }

    public final Vec2d mul(double scalar) {
        x *= scalar;
        y *= scalar;
        return this;
    }
    public final Vec2d mul(Vector v) {
        x *= v.x();
        y *= v.y();
        return this;
    }

    public double lengthSquared() { return x*x+y*y; }
    public final double distanceSq(Vector v) {
        double t,d;
        t = v.x()-x();
        d = t * t;
        t = v.y()-y();
        d += t*t;
        return d;
    }

    public final double angle() {return Math.atan(y / x);}

    public final Vector cross(Vector v) {throw new UnsupportedOperationException();}
    public final double cross2(Vector v) {return x() * v.y() - y() * v.x();}

    public final double dot(Vector v) {return x * v.x() + y * v.y();}
    public final Vec2d normalize() {
        double len = length();
        x /= len;
        y /= len;
        return this;
    }
}