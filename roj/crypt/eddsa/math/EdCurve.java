package roj.crypt.eddsa.math;

import static roj.crypt.eddsa.math.EdInteger.ONE;
import static roj.crypt.eddsa.math.EdInteger.ZERO;

public final class EdCurve {
	private final EdInteger d, d2;
	private final EdInteger I;
	public final EdPoint P2_ZERO, P3_ZERO;

	public EdCurve(byte[] d, EdInteger I) {
		this.d = EdInteger.fromBytes(d);
		d2 = this.d.add(this.d);
		this.I = I;

		P2_ZERO = EdPoint.p2(this, ZERO, ONE, ONE);
		P3_ZERO = EdPoint.p3(this, ZERO, ONE, ONE, ZERO);
	}

	public EdInteger getD() { return d; }
	public EdInteger getI() { return I; }
	public EdInteger get2D() { return d2; }

	public EdPoint createPoint(byte[] P, boolean precompute) { return new EdPoint(this, P, precompute); }

	public int hashCode() { return d.hashCode() ^ I.hashCode(); }
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof EdCurve)) return false;

		EdCurve c = (EdCurve) o;
		return d.equals(c.d) && I.equals(c.I);
	}

	public int getBits() { return 256; }
}