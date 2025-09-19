package roj.net.mss;

import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.security.*;
import java.security.spec.X509EncodedKeySpec;

/**
 * @author Roj233
 * @since 2021/12/22 12:53
 */
public final class X509KeyFormat implements MSSKeyFormat {
	private final KeyFactory factory;
	private final String signatureAlg;
	private Signature signature;

	public X509KeyFormat(String keyAlg, String signAlg) throws NoSuchAlgorithmException {
		factory = KeyFactory.getInstance(keyAlg);
		signatureAlg = signAlg;
		getSignature();
	}

	@Override
	public String getAlgorithm() { return factory.getAlgorithm(); }

	@Override
	public byte[] encode(MSSPublicKey key) { return ((PublicKey) key.key).getEncoded(); }
	@Override
	public MSSPublicKey decode(DynByteBuf data) throws GeneralSecurityException { return new MSSPublicKey(factory.generatePublic(new X509EncodedKeySpec(data.toByteArray()))); }

	@Override
	public boolean verify(MSSPublicKey key, byte[] data, byte[] sign) throws GeneralSecurityException {
		Signature i = getSignature();
		i.initVerify((PublicKey) key.key);
		i.update(data);
		return i.verify(sign);
	}
	@Override
	public byte[] sign(MSSKeyPair key, SecureRandom random, byte[] data) throws GeneralSecurityException {
		Signature i = getSignature();
		i.initSign(key.pri, random);
		i.update(data);
		return i.sign();
	}

	private Signature getSignature() throws NoSuchAlgorithmException {
		if (signature != null) {
			try {
				return (Signature) signature.clone();
			} catch (CloneNotSupportedException e) {
				return Helpers.maybeNull();
			}
		} else {
			Signature i = Signature.getInstance(signatureAlg);
			if (i instanceof Cloneable) signature = i;
			return i;
		}
	}
}