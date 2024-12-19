package roj.crypt.eddsa;

import roj.crypt.DerivablePrivateKey;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

final class EdPrivateKey implements EdKey, DerivablePrivateKey {
	static final int OID_OLD = 100, OID_ED25519 = 112;
	private static final int OID_BYTE = 11, IDLEN_BYTE = 6;

	private final byte[] seed, h;
	private final EdPoint A;
	private final byte[] Abyte;

	private final EdParameterSpec spec;

	public EdPrivateKey(PKCS8EncodedKeySpec spec) throws InvalidKeySpecException {
		this(EdPrivateKey.decode(spec.getEncoded()), EdParameterSpec.ED25519_CURVE_SPEC);
	}

	public EdPrivateKey(byte[] seed, EdParameterSpec spec) {
		if (seed.length != 32) throw new IllegalArgumentException("seed length is wrong");

		this.spec = spec;
		this.seed = seed;

		MessageDigest hash;
		try {
			hash = MessageDigest.getInstance(spec.getHashAlgorithm());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException("Unsupported hash algorithm");
		}

		h = hash.digest(seed);
		// is
		// h[0] &= 0xF8;
		// h[31] &= 0x3F;
		// h[31] |= 0x40;
		// for LC=2 and bits=255 and Type=EdDSA
		prune(h, spec.getBits(), spec.getLogCofactor(), spec.getType());
		A = spec.getB().scalarMultiply(h);
		Abyte = A.toByteArray();
	}

	public EdPrivateKey(EdParameterSpec spec, byte[] h) {
		if (h.length != 64) throw new IllegalArgumentException("hash length is wrong");

		this.spec = spec;
		this.seed = null;

		this.h = h;
		prune(h, spec.getBits(), spec.getLogCofactor(), spec.getType());
		A = spec.getB().scalarMultiply(h);
		Abyte = A.toByteArray();
	}

	private static void prune(byte[] k, int bits, int logCofactor, EdParameterSpec.Type type) {
		int lastByteIndex = k.length/2 - 1;

		boolean flag;
		int highBits;
		if (type == EdParameterSpec.Type.XDH) {
			highBits = bits & 7;
			if (highBits == 0) highBits = 8;

			k[lastByteIndex] &= (1 << highBits) - 1;
		} else {
			int bitsDiff = k.length * 4 - bits;
			highBits = 8 - bitsDiff;

			k[lastByteIndex] &= (1 << highBits) - 1;
		}

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

	@Override
	public String getAlgorithm() { return spec.getType().name(); }

	@Override
	public String getFormat() { return seed == null ? null : "PKCS#8"; }

	@Override
	public byte[] getEncoded() {
		if (!spec.equals(EdParameterSpec.ED25519_CURVE_SPEC)) return null;

		if (seed == null) return null;

		int totlen = 16 + seed.length;
		byte[] rv = new byte[totlen];
		int i = 0;
		rv[i++] = 48;
		rv[i++] = (byte) (totlen - 2);
		rv[i++] = 2;
		rv[i++] = 1;
		rv[i++] = 0;
		rv[i++] = 48;
		rv[i++] = 5;
		rv[i++] = 6;
		rv[i++] = 3;
		rv[i++] = 43;
		rv[i++] = 101;
		rv[i++] = 112;
		rv[i++] = 4;
		rv[i++] = (byte) (2 + seed.length);
		rv[i++] = 4;
		rv[i++] = (byte) seed.length;
		System.arraycopy(seed, 0, rv, i, seed.length);
		return rv;
	}

	public byte[] getSeed() { return seed; }

	public byte[] getH() { return h; }

	public EdPoint getA() { return A; }
	public byte[] getAbyte() { return Abyte; }

	@Override
	public EdParameterSpec getParams() { return spec; }

	public int hashCode() { return Arrays.hashCode(h); }

	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof EdPrivateKey pk)) return false;
		return Arrays.equals(h, pk.h) && spec.equals(pk.spec);
	}

	@Override public PublicKey generatePublic() {return new EdPublicKey(this);}
}