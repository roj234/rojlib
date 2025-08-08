package roj.crypt.eddsa;

import roj.io.IOUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;

public class EdParameterSpec implements AlgorithmParameterSpec {
	private static final EdCurve ed25519curve = new EdCurve(toByte("a3785913ca4deb75abd841414d0a700098e879777940c78c73fe6f2bee6c0352"), toByte("b0a00e4a271beec478e42fad0618432fa7d7fb3d99004d2b0bdfc14f8024832b"));
	public static final EdParameterSpec ED25519_CURVE_SPEC = new EdParameterSpec(Type.EdDSA, 255, 3, ed25519curve, "SHA-512", toByte("5866666666666666666666666666666666666666666666666666666666666666"));
	public static final EdParameterSpec X25519_CURVE_SPEC = new EdParameterSpec(Type.XDH, 255, 3, ed25519curve, null, toByte("0000000000000000000000000000000000000000000000000000000000000009"));
	private static byte[] toByte(String s) { return IOUtil.decodeHex(s); }

	private final Type type;
	private final int bits;
	private final byte cofactor;

	private final EdCurve curve;
	private final String hashAlg;
	private final EdPoint basePoint;

	private final byte[] aad = null;

	public EdParameterSpec(Type type, int bits, int cofactor, EdCurve curve, String hashAlg, byte[] basePoint) {
		this.type = type;
		this.bits = bits;
		this.cofactor = (byte) cofactor;

		if (type == Type.EdDSA) try {
			MessageDigest hash = MessageDigest.getInstance(hashAlg);
			if (basePoint.length * 2 != hash.getDigestLength()) throw new IllegalArgumentException("Hash output is not 2b-bit");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException("Unsupported hash algorithm");
		}

		this.curve = curve;
		this.hashAlg = hashAlg;
		this.basePoint = curve.createPoint(basePoint, true);
	}

	public Type getType() { return type; }
	public int getBits() { return bits; }
	public byte getLogCofactor() { return cofactor; }

	public String getHashAlgorithm() { return hashAlg; }
	public EdCurve getCurve() { return curve; }
	public EdPoint getBasePoint() { return basePoint; }

	public enum Type { EdDSA, XDH }

	// Ed25519ph and Ed25519ctx
	public void addCtx(MessageDigest digest) {}
	public boolean isPrehash() { return false; }

	public int hashCode() {
		return hashAlg.hashCode() ^ curve.hashCode() ^ basePoint.hashCode();
	}
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof EdParameterSpec spec)) return false;
		return hashAlg.equals(spec.hashAlg) && curve.equals(spec.curve) && basePoint.equals(spec.basePoint);
	}
}