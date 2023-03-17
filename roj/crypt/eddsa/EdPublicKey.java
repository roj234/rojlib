package roj.crypt.eddsa;

import roj.crypt.eddsa.math.EdCurve;
import roj.crypt.eddsa.math.EdPoint;

import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import static roj.crypt.eddsa.EdPrivateKey.OID_ED25519;
import static roj.crypt.eddsa.EdPrivateKey.OID_OLD;

public class EdPublicKey implements EdKey, PublicKey {
	private static final int OID_BYTE = 8;
	private static final int IDLEN_BYTE = 3;

	private final EdPoint Aneg;
	private final byte[] Abyte;
	private final EdDSAParameterSpec spec;

	public EdPublicKey(X509EncodedKeySpec spec) throws InvalidKeySpecException {
		this(EdPublicKey.decode(spec.getEncoded()), EdCurve.ED_25519_CURVE_SPEC);
	}

	public EdPublicKey(EdPrivateKey priKey) {
		this.Aneg = priKey.getA().negate();
		this.Abyte = priKey.getAbyte();
		this.spec = priKey.getParams();
	}

	public EdPublicKey(byte[] pk, EdDSAParameterSpec spec) {
		if (pk.length != 32) throw new IllegalArgumentException("public-key length is wrong");
		this.Aneg = new EdPoint(spec.getCurve(), pk).negate();
		this.Abyte = pk;
		this.spec = spec;
	}

	public EdPublicKey(EdPoint A, EdDSAParameterSpec spec) {
		this.Aneg = A.negate();
		this.Abyte = A.toByteArray();
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

	@Override
	public String getAlgorithm() {
		return "EdDSA";
	}

	@Override
	public String getFormat() {
		return "X.509";
	}

	@Override
	public byte[] getEncoded() {
		if (!spec.equals(EdCurve.ED_25519_CURVE_SPEC)) return null;

		int totlen = 12 + Abyte.length;
		byte[] rv = new byte[totlen];
		int i = 0;
		rv[i++] = 48;
		rv[i++] = (byte) (totlen - 2);
		rv[i++] = 48;
		rv[i++] = 5;
		rv[i++] = 6;
		rv[i++] = 3;
		rv[i++] = 43;
		rv[i++] = 101;
		rv[i++] = 112;
		rv[i++] = 3;
		rv[i++] = (byte) (1 + Abyte.length);
		rv[i++] = 0;
		System.arraycopy(Abyte, 0, rv, i, Abyte.length);
		return rv;
	}

	public EdPoint getNegativeA() { return Aneg; }
	public byte[] getAbyte() {
		return Abyte;
	}

	@Override
	public EdDSAParameterSpec getParams() { return spec; }

	public int hashCode() { return Arrays.hashCode(Abyte); }

	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof EdPublicKey)) return false;
		EdPublicKey pk = (EdPublicKey) o;
		return Arrays.equals(Abyte, pk.Abyte) && spec.equals(pk.spec);
	}
}

