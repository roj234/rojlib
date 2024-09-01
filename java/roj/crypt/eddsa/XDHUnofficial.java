package roj.crypt.eddsa;

import roj.crypt.KeyExchange;
import roj.util.DynByteBuf;

import java.security.*;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2023/12/31 0031 19:22
 */
public class XDHUnofficial implements KeyExchange {
	private byte[] publicKey, privateKey;

	@Override
	public String getAlgorithm() { return "X25519"; }

	@Override
	public void init(SecureRandom r) {
		EdKeyGenerator kpg = new EdKeyGenerator();
		try {
			kpg.initialize(EdParameterSpec.ED25519_CURVE_SPEC, r);
		} catch (InvalidAlgorithmParameterException ignored) {}
		KeyPair kp = kpg.generateKeyPair();
		privateKey = Arrays.copyOf(((EdPrivateKey) kp.getPrivate()).getH(), 32);
		publicKey = ((EdPublicKey) kp.getPublic()).getAbyte();
	}

	@Override
	public int length() { return publicKey.length; }

	@Override
	public void writePublic(DynByteBuf bb) { bb.put(publicKey.length); }

	@Override
	public byte[] readPublic(DynByteBuf bb) throws GeneralSecurityException {
		EdPoint otherPub = new EdPoint(EdParameterSpec.ED25519_CURVE_SPEC.getCurve(), bb.toByteArray());

		EdPoint secKey = otherPub.scalarMultiplyShared(privateKey);
		byte[] array = secKey.toByteArray();
		for (int i = 0; i < array.length; i++) {
			if (array[i] != 0) return array;
		}
		throw new InvalidKeyException("Point has small order");
	}

	@Override
	public void clear() { Arrays.fill(privateKey, (byte) 0); privateKey = publicKey = null; }
}