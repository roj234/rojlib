package roj.crypt.eddsa;

import roj.crypt.asn1.DerValue;

import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import static roj.crypt.eddsa.EdPrivateKey.OID_ED25519;
import static roj.crypt.eddsa.EdPrivateKey.OID_OLD;

public final class EdPublicKey implements EdKey, PublicKey {
	private static final int OID_BYTE = 8;
	private static final int IDLEN_BYTE = 3;

	private final EdParameterSpec spec;

	// Point A
	private final EdPoint negativePublicPoint;
	private final byte[] publicKey;

	public EdPublicKey(X509EncodedKeySpec spec) throws InvalidKeySpecException {
		this(EdPublicKey.decode(spec.getEncoded()), EdParameterSpec.ED25519_CURVE_SPEC);
	}

	public EdPublicKey(EdPrivateKey privateKey) {
		this.negativePublicPoint = privateKey.getPublicPoint().negate();
		this.publicKey = privateKey.getPublicKey();
		this.spec = privateKey.getParams();
	}

	public EdPublicKey(byte[] publicPoint, EdParameterSpec spec) {
		if (publicPoint.length != 32) throw new IllegalArgumentException("public point length is wrong");
		this.negativePublicPoint = new EdPoint(spec.getCurve(), publicPoint).negate();
		this.publicKey = publicPoint;
		this.spec = spec;
	}

	private static byte[] decode(byte[] d) throws InvalidKeySpecException {
		try {
			int totlen = 44;
			byte idlen = 5;
			byte doid = d[OID_BYTE];
			if (doid == OID_OLD) {
				totlen = 47;
				idlen = 8;
			} else if (doid == OID_ED25519) {
				if (d[IDLEN_BYTE] == 7) {
					totlen = 46;
					idlen = 7;
				}
			} else {
				throw new InvalidKeySpecException("unsupported key spec");
			}
			if (d.length != totlen) {
				throw new InvalidKeySpecException("invalid key spec length");
			}
			int idx = 0;
			if (d[idx++] != 48 || d[idx++] != totlen - 2 || d[idx++] != 48 || d[idx++] != idlen || d[idx++] != 6 || d[idx++] != 3 || d[idx++] != 43 || d[idx++] != 101) {
				throw new InvalidKeySpecException("unsupported key spec");
			}
			++idx;
			if (doid == 100 ? d[idx++] != 10 || d[idx++] != 1 || d[idx++] != 1 : idlen == 7 && (d[idx++] != 5 || d[idx++] != 0)) {
				throw new InvalidKeySpecException("unsupported key spec");
			}
			if (d[idx++] != 3 || d[idx++] != 33 || d[idx++] != 0) {
				throw new InvalidKeySpecException("unsupported key spec");
			}
			byte[] rv = new byte[32];
			System.arraycopy(d, idx, rv, 0, 32);
			return rv;
		} catch (IndexOutOfBoundsException ioobe) {
			throw new InvalidKeySpecException(ioobe);
		}
	}

	@Override public EdParameterSpec getParams() { return spec; }

	public EdPoint getNegativePublicPoint() { return negativePublicPoint; }
	public byte[] getPublicKey() {return publicKey;}

	@Override public String getAlgorithm() {return "EdDSA";}
	@Override public String getFormat() {return "X.509";}
	@Override public byte[] getEncoded() {
		if (!spec.equals(EdParameterSpec.ED25519_CURVE_SPEC)) return null;

		byte[] rv = new byte[44];
		rv[0] = DerValue.SEQUENCE;
		rv[1] = 42;
		rv[2] = DerValue.SEQUENCE;
		rv[3] = 5;
		rv[4] = DerValue.OID;
		rv[5] = 3;
		rv[6] = 43;
		rv[7] = 101;
		rv[8] = 112;
		rv[9] = DerValue.BIT_STRING;
		rv[10] = 33;
		rv[11] = 0;
		System.arraycopy(publicKey, 0, rv, 12, publicKey.length);
		return rv;
	}

	public int hashCode() { return Arrays.hashCode(publicKey); }
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof EdPublicKey pk)) return false;
		return Arrays.equals(publicKey, pk.publicKey) && spec.equals(pk.spec);
	}
}