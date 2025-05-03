package roj.crypt;

import roj.util.DynByteBuf;

import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

/**
 * @author Roj234
 * @since 2022/11/11 12:30
 */
final class ECDH implements KeyExchange {
	private final ECGroup group;
	private final KeyAgreement ecAgreement;

	private ECPoint pub;

	ECDH(ECGroup group) {
		this.group = group;
		try {
			ecAgreement = KeyAgreement.getInstance("ECDH");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Not support ECDH", e);
		}
	}

	@Override
	public String getAlgorithm() { return "ECDH-"+group.name; }

	@Override
	public void init(SecureRandom r) {
		KeyPairGenerator kpg;
		try {
			kpg = KeyPairGenerator.getInstance("EC");
			kpg.initialize(group, r);
			KeyPair pair = kpg.generateKeyPair();
			pub = ((ECPublicKey) pair.getPublic()).getW();
			ecAgreement.init(pair.getPrivate(), r);
		} catch (Exception e) {
			throw new IllegalStateException("Failed generate EC key", e);
		}
	}

	@Override
	public int length() {
		return 2 + DH.lengthOf(pub.getAffineX()) + DH.lengthOf(pub.getAffineY());
	}

	@Override
	public void writePublic(DynByteBuf bb) {
		byte[] bytes = pub.getAffineX().toByteArray();
		bb.putShort(bytes.length).put(bytes).put(pub.getAffineY().toByteArray());
	}

	@Override
	public byte[] readPublic(DynByteBuf bb) throws GeneralSecurityException {
		ECPoint point = new ECPoint(
			new BigInteger(bb.readBytes(bb.readUnsignedShort())),
			new BigInteger(bb.readBytes(bb.readableBytes())));

		ecAgreement.doPhase(new ECPublicKey() {
			public ECPoint getW() { return point; }
			public String getAlgorithm() { return "EC"; }
			public String getFormat() { return null; }
			public byte[] getEncoded() { return null; }
			public ECParameterSpec getParams() { return group; }
		}, true);

		return ecAgreement.generateSecret();
	}

	@Override
	public void clear() {
		pub = null;
	}
}