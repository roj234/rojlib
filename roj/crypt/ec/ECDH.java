package roj.crypt.ec;

import roj.crypt.DH;
import roj.crypt.KeyAgreement;
import roj.util.DynByteBuf;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

/**
 * @author Roj234
 * @since 2022/11/11 0011 12:30
 */
public class ECDH implements KeyAgreement {
	final NamedCurve param;
	final javax.crypto.KeyAgreement ecAgreement;

	private BigInteger pri;
	private ECPoint pub;

	public ECDH(NamedCurve param) {
		this.param = param;
		try {
			ecAgreement = javax.crypto.KeyAgreement.getInstance("ECDH");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Not support ECDH", e);
		}
	}

	@Override
	public String getAlgorithm() {
		return "ECDH-"+param.getObjectId();
	}

	@Override
	public void init(SecureRandom r) {
		KeyPairGenerator kpg;
		try {
			kpg = KeyPairGenerator.getInstance("EC");
			kpg.initialize(param, r);
			KeyPair pair = kpg.generateKeyPair();
			pub = ((ECPublicKey) pair.getPublic()).getW();
			pri = ((ECPrivateKey) pair.getPrivate()).getS();
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
			@Override
			public ECPoint getW() {
				return point;
			}
			@Override
			public String getAlgorithm() {
				return "EC";
			}
			@Override
			public String getFormat() {
				return null;
			}
			@Override
			public byte[] getEncoded() {
				return null;
			}
			@Override
			public ECParameterSpec getParams() {
				return param;
			}
		}, true);

		return ecAgreement.generateSecret();
	}

	@Override
	public void clear() {
		pub = null;
		pri = null;
	}
}
