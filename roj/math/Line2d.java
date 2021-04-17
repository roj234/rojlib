package roj.math;

import roj.util.Hasher;

public class Line2d {
    public Vec2d a, b;

    public Line2d(Vec2d a, Vec2d b) {
        this.a = a;
        this.b = b;
    }

    public double length() {
        return a.distance(b);
    }

    @Override
    public String toString() {
        return "Line2d{" + a + " => " + b + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Line2d d = (Line2d) o;
        return a.equals(d.a) && b.equals(d.b);
    }

    @Override
    public int hashCode() {
        return new Hasher().add(a).add(b).getHash();
    }
}