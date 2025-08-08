package roj.math;

import org.jetbrains.annotations.Contract;
import roj.util.Hasher;

/**
 * @author Roj234
 * @since 2021/5/22 14:46
 */
@SuppressWarnings("fallthrough")
public abstract class Vector {
	public abstract Vector copy();

	@Contract(pure = true) public abstract double x();
	@Contract(pure = true) public abstract double y();
	@Contract(pure = true) public double z() {return 0;}
	@Contract(pure = true) public double w() {return 1;}

	public abstract void x(double x);
	public abstract void y(double y);
	public void z(double z) {throw new UnsupportedOperationException();}
	public void w(double w) {throw new UnsupportedOperationException();}
	public abstract int axis();

	public abstract Vector set(Vector v);
	public abstract Vector add(Vector v);
	public abstract Vector sub(Vector v);
	public abstract Vector mul(Vector v);
	public abstract Vector mul(double scalar);

	public final double length() { return Math.sqrt(lengthSquared()); }
	public final double distance(Vector v) { return Math.sqrt(distanceSq(v)); }
	public abstract double lengthSquared();
	public abstract double distanceSq(Vector v);

	public abstract double dot(Vector v);
	public abstract Vector cross(Vector b);
	public abstract Vector normalize();

	public final double angle(Vector v) {return dot(v) / Math.sqrt(lengthSquared() * v.lengthSquared());}

	@Override
	public final boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Vector)) return false;
		Vector b = ((Vector) o);
		if (axis() != b.axis()) return false;

		switch (axis()) {
			case 4: if (w() != b.w()) return false;
			case 3: if (z() != b.z()) return false;
		}
		if (x() != b.x()) return false;
		return y() == b.y();
	}
	@Override
	public final int hashCode() {
		Hasher h = new Hasher();
		switch (axis()) {
			case 4: h.add(w());
			case 3: h.add(z());
		}
		return h.add(y()).add(x()).getHash();
	}
	@Override
	public final String toString() {
		StringBuilder sb = new StringBuilder().append(getClass().getSimpleName()).append(" {").append(x()).append(", ").append(y());
		if (axis() >= 3) sb.append(", ").append(z());
		if (axis() == 4) sb.append(", ").append(w());
		return sb.append("}").toString();
	}
}
