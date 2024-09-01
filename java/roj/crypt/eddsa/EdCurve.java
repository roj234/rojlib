package roj.crypt.eddsa;

import static roj.crypt.eddsa.EdInteger.ONE;
import static roj.crypt.eddsa.EdInteger.ZERO;

public final class EdCurve {
	final EdInteger D, twoD, I;
	final EdPoint P2_ZERO, P3_ZERO;

	public EdCurve(byte[] d, byte[] I) {
		this.D = EdInteger.fromBytes(d);
		twoD = this.D.add(this.D);
		this.I = EdInteger.fromBytes(I);

		P2_ZERO = EdPoint.p2(this, ZERO, ONE, ONE);
		P3_ZERO = EdPoint.p3(this, ZERO, ONE, ONE, ZERO);
	}

	EdPoint createPoint(byte[] P, boolean precompute) { return new EdPoint(this, P, precompute); }

	public int hashCode() { return D.hashCode() ^ I.hashCode(); }
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof EdCurve c)) return false;
		return D.equals(c.D) && I.equals(c.I);
	}

	public int getBits() { return 256; }
}