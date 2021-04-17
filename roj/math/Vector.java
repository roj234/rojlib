package roj.math;

import org.jetbrains.annotations.Contract;
import roj.util.Hasher;

/**
 * @author Roj234
 * @since 2021/5/22 14:46
 */
@SuppressWarnings("fallthrough")
public abstract class Vector {
	public abstract Vector newInstance();

	@Contract(pure = true)
	public abstract double x();
	@Contract(pure = true)
	public abstract double y();
	@Contract(pure = true)
	public double z() {
		return 0;
	}
	@Contract(pure = true)
	public double w() {
		return 1;
	}

	public abstract void x(double x);
	public abstract void y(double y);
	public void z(double z) {
		throw new UnsupportedOperationException();
	}
	public void w(double w) {
		throw new UnsupportedOperationException();
	}

	public abstract int axis();

	public Vector set(Vector v) {
		checkAxis(v);
		switch (axis()) {
			case 4: w(v.w());
			case 3: z(v.z());
		}
		x(v.x());
		y(v.y());
		return this;
	}
	public final Vector add(Vector v) {
		checkAxis(v);
		return add(v, axis());
	}
	public final Vector add(Vector v, int axis) {
		switch (axis) {
			case 4: w(w()+v.w());
			case 3: z(z()+v.z());
		}
		x(x()+v.x());
		y(y()+v.y());
		return this;
	}
	public final Vector add(double x, double y) {
		checkAxis(2);
		x(x()+x);
		y(y()+y);
		return this;
	}
	public Vector add(double x, double y, double z) {
		checkAxis(3);
		x(x()+x);
		y(y()+y);
		z(z()+z);
		return this;
	}
	public final Vector add(double x, double y, double z, double w) {
		checkAxis(4);
		x(x()+x);
		y(y()+y);
		z(z()+z);
		w(w()+w);
		return this;
	}
	public final Vector sub(Vector v) {
		checkAxis(v);
		return sub(v, axis());
	}
	public final Vector sub(Vector v, int axis) {
		switch (axis) {
			case 4: w(w()-v.w());
			case 3: z(z()-v.z());
		}
		x(x()-v.x());
		y(y()-v.y());
		return this;
	}
	public Vector mul(double scalar) {
		switch (axis()) {
			case 4: w(w()*scalar);
			case 3: z(z()*scalar);
		}
		x(x()*scalar);
		y(y()*scalar);
		return this;
	}
	public final Vector mul(Vector v) {
		checkAxis(v);
		return mul(axis());
	}
	public final Vector mul(Vector v, int axis) {
		switch (axis) {
			case 4: w(w()*v.w());
			case 3: z(z()*v.z());
		}
		x(x()*v.x());
		y(y()*v.y());
		return this;
	}

	public double len() { return Math.sqrt(dot(this, axis())); }
	public double len(int axis) { return Math.sqrt(dot(this, axis)); }
	public double len2() { return dot(this, axis()); }

	public final double distance(Vector v) { return Math.sqrt(distanceSq(v)); }
	public final double distanceSq(Vector v) {
		double t,d;

		t = v.x()-x();
		d = t * t;
		t = v.y()-y();
		d += t*t;

		switch (axis()) {
			case 4:
				t = v.w()-w();
				d += t*t;
			case 3:
				t = v.z()-z();
				d += t*t;
		}

		return d;
	}
	public final double angle(Vector v) {
		checkAxis(v);
		return dot(v) / Math.sqrt(len2() * v.len2());
	}

	public Vector normalize() {
		return normalize(axis());
	}
	public final Vector normalize(int axis) {
		double len = Math.sqrt(dot(this, axis));
		x(x()/len);
		y(y()/len);
		switch (axis) {
			case 4: w(w()/len);
			case 3: z(z()/len);
		}
		return this;
	}

	public final double dot(Vector v) {
		checkAxis(v);
		return dot(v, axis());
	}
	public final double dot(Vector v, int axis) {
		double d = v.x()*x()+v.y()*y();
		switch (axis) {
			case 4: d += v.w()*w();
			case 3: d += v.z()*z();
		}
		return d;
	}
	public abstract Vector cross(Vector v);
	public final double cross2(Vector v) {
		return x() * v.y() - y() * v.x();
	}

	private void checkAxis(Vector v) {
		if (v.axis() != axis()) throw new IllegalArgumentException("Axis are not same");
	}
	private void checkAxis(int v) {
		if (v > axis()) throw new IllegalArgumentException("Axis larger than "+axis());
	}

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
