package roj.net.mss.crypto;

import roj.crypt.KeyExchange;
import roj.util.DynByteBuf;

import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

import static roj.net.mss.crypto.DH.putFixedBigInt;

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
	public String getAlgorithm() { return "ECDH-"+group.getName(); }

	@Override
	public void init(SecureRandom r) {
		KeyPairGenerator kpg;
		try {
			kpg = KeyPairGenerator.getInstance("EC");
			kpg.initialize(group.getParams(), r);
			KeyPair pair = kpg.generateKeyPair();
			pub = ((ECPublicKey) pair.getPublic()).getW();
			ecAgreement.init(pair.getPrivate(), r);
		} catch (Exception e) {
			throw new IllegalStateException("Failed generate EC key", e);
		}
	}

	@Override
	public int length() {return 1 + getFieldSize() * 2;}

	@Override
	public void writePublic(DynByteBuf bb) {
		ECPoint w = pub;
		int fieldSize = getFieldSize();

		// 标准未压缩格式：0x04 + X + Y
		// 坐标长度 = (密钥长度 + 7) / 8。例如 P-256 是 32 字节
		bb.put(0x04);
		putFixedBigInt(bb, w.getAffineX(), fieldSize);
		putFixedBigInt(bb, w.getAffineY(), fieldSize);
	}

	private int getFieldSize() {return (group.getParams().getCurve().getField().getFieldSize() + 7) / 8;}

	@Override
	public byte[] readPublic(DynByteBuf bb) throws GeneralSecurityException {
		int fieldSize = getFieldSize();
		if (bb.readUnsignedByte() != 4) throw new InvalidKeyException("First byte must be 4");

		ECPoint point = new ECPoint(
			new BigInteger(1, bb.readBytes(fieldSize)),
			new BigInteger(1, bb.readBytes(fieldSize))
		);

		ecAgreement.doPhase(new ECPublicKey() {
			public ECPoint getW() { return point; }
			public String getAlgorithm() { return "EC"; }
			public String getFormat() { return null; }
			public byte[] getEncoded() { return null; }
			public ECParameterSpec getParams() { return group.getParams(); }
		}, true);

		return ecAgreement.generateSecret();
	}

	@Override
	public void clear() {
		pub = null;
	}
}