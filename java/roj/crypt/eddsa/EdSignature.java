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
	private boolean prehash;

	public EdSignature() { super("EdDSA"); }

	// 三个核心函数
	private void init() {
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
		data.clear();
	}

	private byte[] doSign() {
		ByteBuffer buf = prehash ? ByteBuffer.wrap(digest.digest()) : data.nioBuffer();
		EdPrivateKey key = (EdPrivateKey) this.key;

		key.getParams().addCtx(digest);
		digest.update(key.getPrivateKey(), 32, 32);
		buf.mark();
		digest.update(buf);

		byte[] r = EdInteger.scalar_mod_inline(digest.digest());
		EdPoint R = key.getParams().getBasePoint().scalarMultiplyShared(r);
		byte[] Rbyte = R.toByteArray();

		key.getParams().addCtx(digest);
		digest.update(Rbyte);
		digest.update(key.getPublicKey());
		buf.reset();
		digest.update(buf);

		byte[] h = EdInteger.scalar_mod_inline(digest.digest());
		new Throwable().printStackTrace();
		byte[] a = key.getPrivateKey();
		// h,a,r均只使用了低32字节
		byte[] S = EdInteger.scalar_mul_add_mod_inline(h, a, r);

		byte[] out = new byte[64];
		System.arraycopy(Rbyte, 0, out, 0, 32);
		System.arraycopy(S, 0, out, 32, 32);
		return out;
	}

	private boolean doVerify(byte[] signature) throws SignatureException {
		if (signature.length != 64) throw new SignatureException("signature length is wrong");

		EdPublicKey key = (EdPublicKey) this.key;

		key.getParams().addCtx(digest);
		digest.update(signature, 0, 32);
		digest.update(key.getPublicKey());
		ByteBuffer buf = prehash ? ByteBuffer.wrap(digest.digest()) : data.nioBuffer();
		digest.update(buf);

		byte[] h = EdInteger.scalar_mod_inline(digest.digest());
		byte[] S = Arrays.copyOfRange(signature, 32, 64);
		EdPoint R = key.getParams().getBasePoint().doubleScalarMultiplyShared(key.getNegativePublicPoint(), h, S);
		byte[] Rbyte = R.toByteArray();
		for (int i = 0; i < Rbyte.length; ++i) {
			if (Rbyte[i] != signature[i]) return false;
		}
		return true;
	}
	// 三个核心函数

	public boolean verifyOneShot(DynByteBuf buf, byte[] sign) throws SignatureException {
		DynByteBuf prev = data;
		data = buf;
		try{
			if (prehash) digest.update(buf.nioBuffer());
			return doVerify(sign);
		} finally {
			data = prev;
		}
	}

	public byte[] signOneShot(DynByteBuf buf) {
		DynByteBuf prev = data;
		data = buf;
		try{
			if (prehash) digest.update(buf.nioBuffer());
			return doSign();
		} finally {
			data = prev;
		}
	}

	@Override
	protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
		if (!(publicKey instanceof EdPublicKey))
			throw new InvalidKeyException("cannot identify EdDSA public key: " + publicKey.getClass());

		key = (EdPublicKey) publicKey;
		init();
	}

	@Override
	protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
		if (!(privateKey instanceof EdPrivateKey)) {
			throw new InvalidKeyException("cannot identify EdDSA private key: " + privateKey.getClass());
		}

		key = (EdPrivateKey) privateKey;
		init();
	}

	protected final void engineUpdate(byte b) { if (prehash) digest.update(b); else data.put(b); }
	protected final void engineUpdate(byte[] b, int off, int len) { if (prehash) digest.update(b, off, len); else data.put(b, off, len); }
	protected final void engineUpdate(ByteBuffer input) { if (prehash) digest.update(input); else data.put(input); }

	@Override
	protected boolean engineVerify(byte[] sigBytes) throws SignatureException {
		try {
			return doVerify(sigBytes);
		} finally {
			data.clear();
		}
	}

	@Override
	protected byte[] engineSign() {
		try {
			return doSign();
		} finally {
			data.clear();
		}
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		EdSignature sig = (EdSignature) super.clone();
		sig.data = new ByteList();
		return sig;
	}

	@Override protected void engineSetParameter(String param, Object value) { throw new InvalidParameterException("no impl"); }
	@Override protected Object engineGetParameter(String param) { throw new InvalidParameterException("no impl"); }
}