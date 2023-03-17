package roj.crypt.eddsa;

import roj.crypt.eddsa.math.EdCurve;
import roj.crypt.eddsa.math.EdPoint;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;

public class EdDSAParameterSpec implements AlgorithmParameterSpec {
	private final EdCurve curve;
	private final String hashAlg;
	private final EdPoint B;

	public EdDSAParameterSpec(EdCurve curve, String hashAlg, EdPoint B) {
		try {
			MessageDigest hash = MessageDigest.getInstance(hashAlg);
			if (64 != hash.getDigestLength()) throw new IllegalArgumentException("Hash output is not 2b-bit");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException("Unsupported hash algorithm");
		}

		this.curve = curve;
		this.hashAlg = hashAlg;
		this.B = B;
	}

	public int hashCode() {
		return hashAlg.hashCode() ^ curve.hashCode() ^ B.hashCode();
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof EdDSAParameterSpec)) return false;
		EdDSAParameterSpec s = (EdDSAParameterSpec) o;
		return hashAlg.equals(s.hashAlg) && curve.equals(s.curve) && B.equals(s.B);
	}

	public String getHashAlgorithm() { return hashAlg; }
	public EdCurve getCurve() { return curve; }
	public EdPoint getB() { return B; }
}

