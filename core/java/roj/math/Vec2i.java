package roj.math;

public class Vec2i extends Vector {
    public int x, y;

    public Vec2i() {}
    @Override
    public Vec2i copy() { return new Vec2i(this); }

    public Vec2i(int x, int y) {this.x = x;this.y = y;}
    public Vec2i(Vector xy) {x = (int) xy.x();y = (int) xy.y();}
    public Vec2i(Vec2i xy) {x = xy.x;y = xy.y;}

    public final double x() {return x;}
    public final double y() {return y;}
    public final void x(double x) {this.x = (int) x;}
    public final void y(double y) {this.y = (int) y;}
    public final int axis() {return 2;}

    public final Vec2i set(int x, int y) {this.x = x;this.y = y;return this;}
    public final Vec2i set(double x, double y) {this.x = (int) x;this.y = (int) y;return this;}
    public final Vec2i set(Vec2i xy) {x = xy.x;y = xy.y;return this;}
    public final Vec2i set(Vector xy) {x = (int) xy.x();y = (int) xy.y();return this;}

    public final Vec2i add(Vector v) {
        x += v.x();
        y += v.y();
        return this;
    }
    public final Vec2i sub(Vector v) {
        x -= v.x();
        y -= v.y();
        return this;
    }

    public final Vec2i add(double x, double y) {
        this.x += x;
        this.y += y;
        return this;
    }
    public Vec2i mul(double scalar) {
        x *= scalar;
        y *= scalar;
        return this;
    }
    public final Vec2i mul(Vector v) {
        x *= v.x();
        y *= v.y();
        return this;
    }

    public double lengthSquared() { return x*x+y*y; }
    public final double distanceSq(Vector v) {
        double t,d;

        t = v.x()-x;
        d = t * t;
        t = v.y()-y;
        d += t*t;
        return d;
    }

    public final Vector cross(Vector v) {throw new UnsupportedOperationException();}
    public final double cross2(Vector v) {return x() * v.y() - y() * v.x();}

    public final double dot(Vector v) {return x * v.x() + y * v.y();}
    public final Vec2i normalize() {
        var len = length();
        x /= len;
        y /= len;
        return this;
    }
}