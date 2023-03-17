package roj.crypt.eddsa.math;

import roj.crypt.eddsa.EdDSAParameterSpec;
import roj.io.IOUtil;

import static roj.crypt.eddsa.math.EdInteger.ONE;
import static roj.crypt.eddsa.math.EdInteger.ZERO;

public final class EdCurve {
    private static final EdCurve ed25519curve = new EdCurve(toByte("a3785913ca4deb75abd841414d0a700098e879777940c78c73fe6f2bee6c0352"), EdInteger.fromBytes(toByte("b0a00e4a271beec478e42fad0618432fa7d7fb3d99004d2b0bdfc14f8024832b")));
    public static final EdDSAParameterSpec ED_25519_CURVE_SPEC = new EdDSAParameterSpec(ed25519curve, "SHA-512", ed25519curve.createPoint(toByte("5866666666666666666666666666666666666666666666666666666666666666"), true));
	private static byte[] toByte(String s) { return IOUtil.SharedCoder.get().decodeHex(s); }

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

