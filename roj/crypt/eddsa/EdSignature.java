package roj.crypt.eddsa;

import roj.crypt.eddsa.math.EdInteger;
import roj.crypt.eddsa.math.EdPoint;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import sun.security.x509.X509Key;

import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public final class EdSignature extends Signature {
	public static final String SIGNATURE_ALGORITHM = "NONEwithEdDSA";

	private MessageDigest digest;
	private EdKey key;
	private DynByteBuf data = new ByteList();
	private boolean externalBuffer;

	public EdSignature() { super(SIGNATURE_ALGORITHM); }
	public EdSignature(MessageDigest digest) {
		this();
		this.digest = digest;
	}

	@Override
	protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
		if (publicKey instanceof X509Key) {
			try {
				publicKey = new EdPublicKey(new X509EncodedKeySpec(publicKey.getEncoded()));
			} catch (InvalidKeySpecException ex) {
				throw new InvalidKeyException("cannot handle X.509 EdDSA public key: " + publicKey.getAlgorithm());
			}
		}

		if (publicKey instanceof EdPublicKey) {
			key = (EdPublicKey) publicKey;
		} else {
			throw new InvalidKeyException("cannot identify EdDSA public key: " + publicKey.getClass());
		}

		reset();
	}

	@Override
	protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
		EdPrivateKey privKey;
		if (privateKey instanceof EdPrivateKey) {
			key = privKey = (EdPrivateKey) privateKey;
		} else {
			throw new InvalidKeyException("cannot identify EdDSA private key: " + privateKey.getClass());
		}

		reset();
		digestInitSign(privKey);
	}

	private void digestInitSign(EdPrivateKey privKey) {
		digest.update(privKey.getH(), 32, 32);
	}

	private void reset() {
		if (digest == null) {
			try {
				digest = MessageDigest.getInstance(key.getParams().getHashAlgorithm());
			} catch (NoSuchAlgorithmException e) {
				Helpers.athrow(new InvalidKeyException("cannot get hash " + key.getParams().getHashAlgorithm()));
			}
		} else if (!key.getParams().getHashAlgorithm().equals(digest.getAlgorithm())) {
			Helpers.athrow(new InvalidKeyException("Key hash algorithm does not match chosen digest"));
		}
		digest.reset();
		if (!externalBuffer) data.clear();
	}

	protected final void engineUpdate(byte b) { data.put(b); }
	protected final void engineUpdate(byte[] b, int off, int len) { data.put(b, off, len); }

	@Override
	protected byte[] engineSign() {
		try {
			return x_engineSign();
		} finally {
			reset();
			digestInitSign((EdPrivateKey) key);
		}
	}

	private byte[] x_engineSign() {
		digest.update(data.nioBuffer());
		byte[] r = EdInteger.scalar_mod_inline(digest.digest());
		EdPoint R = key.getParams().getB().scalarMultiplyShared(r);
		byte[] Rbyte = R.toByteArray();

		digest.update(Rbyte);
		digest.update(((EdPrivateKey) key).getAbyte());
		digest.update(data.nioBuffer());

		byte[] h = EdInteger.scalar_mod_inline(digest.digest());
		byte[] a = ((EdPrivateKey) key).geta();
		byte[] S = EdInteger.scalar_mul_add_mod_inline(h, a, r);

		byte[] out = new byte[64];
		System.arraycopy(Rbyte, 0, out, 0, 32);
		System.arraycopy(S, 0, out, 32, 32);
		return out;
	}

	@Override
	protected boolean engineVerify(byte[] sigBytes) throws SignatureException {
		try {
			return x_engineVerify(sigBytes);
		} finally {
			reset();
		}
	}

	private boolean x_engineVerify(byte[] sigBytes) throws SignatureException {
		if (sigBytes.length != 64) throw new SignatureException("signature length is wrong");

		digest.update(sigBytes, 0, 32);
		digest.update(((EdPublicKey) key).getAbyte());
		digest.update(data.nioBuffer());

		byte[] h = EdInteger.scalar_mod_inline(digest.digest());
		byte[] S = Arrays.copyOfRange(sigBytes, 32, 64);
		EdPoint R = key.getParams().getB().doubleScalarMultiplyShared(((EdPublicKey) key).getNegativeA(), h, S);
		byte[] Rbyte = R.toByteArray();
		for (int i = 0; i < Rbyte.length; ++i) {
			if (Rbyte[i] != sigBytes[i]) return false;
		}
		return true;
	}

	@Override
	protected void engineSetParameter(String param, Object value) { throw new InvalidParameterException("no impl"); }
	@Override
	protected Object engineGetParameter(String param) { throw new InvalidParameterException("no impl"); }

	public byte[] signOneShot(DynByteBuf buf) throws SignatureException {
		DynByteBuf prev = data;
		data = buf;
		externalBuffer = true;
		try{
			return sign();
		} finally {
			data = prev;
			externalBuffer = false;
		}
	}

	public boolean verifyOneShot(DynByteBuf buf, byte[] sign) throws SignatureException {
		DynByteBuf prev = data;
		data = buf;
		externalBuffer = true;
		try{
			return verify(sign);
		} finally {
			data = prev;
			externalBuffer = false;
		}
	}
}

