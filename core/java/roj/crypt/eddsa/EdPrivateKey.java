package roj.crypt.eddsa;

import roj.crypt.DerivablePrivateKey;
import roj.crypt.asn1.DerValue;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

final class EdPrivateKey implements EdKey, DerivablePrivateKey {
	static final int OID_OLD = 100, OID_ED25519 = 112;
	private static final int OID_BYTE = 11, IDLEN_BYTE = 6;

	private final EdParameterSpec spec;

	private final byte[] seed, privateKey;
	private final EdPoint publicPoint;
	// compacted form of Public Point
	private final byte[] publicKey;

	public EdPrivateKey(PKCS8EncodedKeySpec spec) throws InvalidKeySpecException {
		this(EdPrivateKey.decode(spec.getEncoded()), EdParameterSpec.ED25519_CURVE_SPEC);
	}
	public EdPrivateKey(PKCS8EncodedKeySpec spec, EdParameterSpec spec2) throws InvalidKeySpecException {
		this(EdPrivateKey.decode(spec.getEncoded()), spec2);
	}

	public EdPrivateKey(byte[] seed, EdParameterSpec spec) {
		if (seed.length != 32) throw new IllegalArgumentException("seed length is wrong");

		this.spec = spec;
		this.seed = seed;

		if (spec.getHashAlgorithm() != null) {
			MessageDigest hash;
			try {
				hash = MessageDigest.getInstance(spec.getHashAlgorithm());
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalArgumentException("Unsupported hash algorithm");
			}

			privateKey = hash.digest(seed);
		} else {
			privateKey = seed.clone();
		}

		prune(privateKey, spec.getBits(), spec.getLogCofactor(), spec.getType());
		publicPoint = spec.getBasePoint().scalarMultiply(privateKey);
		publicKey = publicPoint.toByteArray();
	}

	private static void prune(byte[] k, int bits, int logCofactor, EdParameterSpec.Type type) {
		int lastByteIndex = k.length/2 - 1;

		int highBits;
		if (type == EdParameterSpec.Type.XDH) {
			highBits = bits & 7;
			if (highBits == 0) highBits = 8;
			lastByteIndex = k.length - 1;
		} else {
			// h[0] &= 0xF8;
			// h[31] &= 0x3F;
			// h[31] |= 0x40;
			// for LC=2 and bits=255 and Type=EdDSA

			int bitsDiff = k.length * 4 - bits;
			highBits = 8 - bitsDiff;

		}
		k[lastByteIndex] &= (1 << highBits) - 1;

		if (highBits == 0) k[lastByteIndex-1] |= 0x80;
		else k[lastByteIndex] |= 1 << (highBits-1);

		k[0] &= 0xFF << logCofactor;
	}

	private static byte[] decode(byte[] d) throws InvalidKeySpecException {
		try {
			int totlen = 48;
			byte idlen = 5;
			byte doid = d[OID_BYTE];
			if (doid == OID_OLD) {
				totlen = 49;
				idlen = 8;
			} else if (doid == OID_ED25519 || doid == 110) {
				if (d[IDLEN_BYTE] == 7) {
					totlen = 50;
					idlen = 7;
				}
			} else {
				throw new InvalidKeySpecException("unsupported key spec "+doid);
			}
			if (d.length != totlen) {
				throw new InvalidKeySpecException("invalid key spec length");
			}
			int idx = 0;
			if (d[idx++] != 48 || d[idx++] != totlen - 2 || d[idx++] != 2 || d[idx++] != 1 || d[idx++] != 0 || d[idx++] != 48 || d[idx++] != idlen || d[idx++] != 6 || d[idx++] != 3 || d[idx++] != 43 || d[idx++] != 101) {
				throw new InvalidKeySpecException("unsupported key spec");
			}
			++idx;
			if (doid == 100) {
				if (d[idx++] != 10 || d[idx++] != 1 || d[idx++] != 1) {
					throw new InvalidKeySpecException("unsupported key spec");
				}
			} else {
				if (idlen == 7 && (d[idx++] != 5 || d[idx++] != 0)) {
					throw new InvalidKeySpecException("unsupported key spec");
				}
				if (d[idx++] != 4 || d[idx++] != 34) {
					throw new InvalidKeySpecException("unsupported key spec");
				}
			}
			if (d[idx++] != 4 || d[idx++] != 32) {
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

	public byte[] getSeed() { return seed; }
	public byte[] getPrivateKey() { return privateKey; }

	public EdPoint getPublicPoint() { return publicPoint; }
	public byte[] getPublicKey() { return publicKey; }

	@Override public PublicKey generatePublic() {return new EdPublicKey(this);}

	@Override public String getAlgorithm() { return spec.getType().name(); }
	@Override public String getFormat() { return "PKCS#8"; }
	@Override public byte[] getEncoded() {
		if (!spec.equals(EdParameterSpec.ED25519_CURVE_SPEC)) return null;
		byte[] der = new byte[48];
		der[0] = DerValue.SEQUENCE;
		der[1] = 46;
		der[2] = DerValue.INTEGER;
		der[3] = 1;
		der[4] = 0;
		der[5] = DerValue.SEQUENCE;
		der[6] = 5;
		der[7] = DerValue.OID;
		der[8] = 3;
		der[9] = 43;
		der[10] = 101;
		der[11] = 112;
		der[12] = DerValue.OCTET_STRING;
		der[13] = 34;
		der[14] = DerValue.OCTET_STRING;
		der[15] = 32;
		System.arraycopy(seed, 0, der, 16, seed.length);
		return der;
	}

	public int hashCode() { return Arrays.hashCode(privateKey); }
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof EdPrivateKey pk)) return false;
		return Arrays.equals(privateKey, pk.privateKey) && spec.equals(pk.spec);
	}
}