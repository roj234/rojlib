package roj.crypt.eddsa;

import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.nio.ByteBuffer;
import java.security.*;
import java.util.Arrays;

public final class EdSignature extends Signature implements Cloneable {
	private MessageDigest digest;
	private EdKey key;
	private DynByteBuf data = new ByteList();
	private boolean externalBuffer, prehash;

	public EdSignature() { super("EdDSA"); }

	@Override
	public Object clone() throws CloneNotSupportedException {
		EdSignature sig = (EdSignature) super.clone();
		sig.data = new ByteList();
		return sig;
	}

	@Override
	protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
		if (!(publicKey instanceof EdPublicKey))
			throw new InvalidKeyException("cannot identify EdDSA public key: " + publicKey.getClass());

		key = (EdPublicKey) publicKey;
		reset();
	}

	@Override
	protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
		if (!(privateKey instanceof EdPrivateKey)) {
			throw new InvalidKeyException("cannot identify EdDSA private key: " + privateKey.getClass());
		}

		key = (EdPrivateKey) privateKey;
		reset();
	}

	private void reset() {
		prehash = key.getParams().isPrehash();
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

	protected final void engineUpdate(byte b) { if (prehash) digest.update(b); else data.put(b); }
	protected final void engineUpdate(byte[] b, int off, int len) { if (prehash) digest.update(b, off, len); else data.put(b, off, len); }
	protected final void engineUpdate(ByteBuffer input) { if (prehash) digest.update(input); else data.put(input); }

	@Override
	protected byte[] engineSign() {
		try {
			return x_engineSign();
		} finally {
			reset();
		}
	}

	private byte[] x_engineSign() {
		ByteBuffer buf = prehash ? ByteBuffer.wrap(digest.digest()) : data.nioBuffer();
		EdPrivateKey edk = (EdPrivateKey) key;

		edk.getParams().addCtx(digest);
		digest.update(edk.getH(), 32, 32);
		buf.mark();
		digest.update(buf);

		byte[] r = EdInteger.scalar_mod_inline(digest.digest());
		EdPoint R = key.getParams().getB().scalarMultiplyShared(r);
		byte[] Rbyte = R.toByteArray();

		edk.getParams().addCtx(digest);
		digest.update(Rbyte);
		digest.update(edk.getAbyte());
		buf.reset();
		digest.update(buf);

		byte[] h = EdInteger.scalar_mod_inline(digest.digest());
		byte[] a = edk.getH();
		// h,a,r均只使用了低32字节
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

		EdPublicKey edk = (EdPublicKey) key;

		edk.getParams().addCtx(digest);
		digest.update(sigBytes, 0, 32);
		digest.update(edk.getAbyte());
		ByteBuffer buf = prehash ? ByteBuffer.wrap(digest.digest()) : data.nioBuffer();
		digest.update(buf);

		byte[] h = EdInteger.scalar_mod_inline(digest.digest());
		byte[] S = Arrays.copyOfRange(sigBytes, 32, 64);
		EdPoint R = key.getParams().getB().doubleScalarMultiplyShared(edk.getNegativeA(), h, S);
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
			if (prehash) update(buf.nioBuffer());
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
			if (prehash) update(buf.nioBuffer());
			return verify(sign);
		} finally {
			data = prev;
			externalBuffer = false;
		}
	}
}