package roj.crypt.eddsa;

import roj.crypt.KeyAgreement;
import roj.crypt.eddsa.math.EdPoint;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.security.*;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2023/12/31 0031 19:22
 */
public class XDHUnofficial implements KeyAgreement {
	public static void main(String[] args) throws Exception {
		System.out.println("Ed25519 Signature");
		EdPrivateKey privateKey1 = new EdPrivateKey(new byte[32], EdParameterSpec.ED25519_CURVE_SPEC);
		EdSignature signature = new EdSignature();
		System.out.println("PrivateKey  ="+TextUtil.bytes2hex(privateKey1.getSeed()));
		ByteList data = new ByteList().putUTFData("TeSTsTRINGstring");
		System.out.println("Data        ="+data);
		signature.initSign(privateKey1);
		byte[] bytes = signature.signOneShot(data);
		// 2f7b9a4cb1864ab77d2be8e9b380bbf52a49797baca601788af78a1ec139ed14ce036cca6776bc4d1f07794eb85b8ff14256770bbd92b684bf1ad88d8fb5390f
		System.out.println("Sign(R|S)   ="+TextUtil.bytes2hex(bytes));
		signature.initVerify(new EdPublicKey(privateKey1));
		System.out.println("Valid       ="+signature.verifyOneShot(data, bytes));
		bytes[0]++;
		System.out.println("ValidMangle ="+signature.verifyOneShot(data, bytes));
		System.out.println();
		System.out.println("X25519 Key Exchange");
		XDHUnofficial a = new XDHUnofficial();
		XDHUnofficial b = new XDHUnofficial();
		a.init(new SecureRandom());
		b.init(new SecureRandom());
		System.out.println("PublicKeyA ="+TextUtil.bytes2hex(a.publicKey));
		System.out.println("PublicKeyB ="+TextUtil.bytes2hex(b.publicKey));
		System.out.println("PrivateKeyA="+TextUtil.bytes2hex(a.privateKey));
		System.out.println("PrivateKeyB="+TextUtil.bytes2hex(b.privateKey));
		a.readPublic(new ByteList(b.publicKey));
		b.readPublic(new ByteList(a.publicKey));
	}
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
		block: {
			for (int i = 0; i < array.length; i++) {
				if (array[i] != 0) break block;
			}
			throw new InvalidKeyException("Point has small order");
		}
		System.out.println("SharedKey  ="+TextUtil.bytes2hex(array));
		return array;
	}

	@Override
	public void clear() { Arrays.fill(privateKey, (byte) 0); privateKey = publicKey = null; }
}