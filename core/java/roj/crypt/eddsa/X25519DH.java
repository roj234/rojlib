package roj.crypt.eddsa;

import roj.crypt.KeyExchange;
import roj.util.DynByteBuf;

import javax.crypto.KeyAgreement;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

import static roj.crypt.eddsa.EdInteger.*;

/**
 * 未完工
 * @author Roj234
 * @since 2023/12/31 19:22
 */
class X25519DH implements KeyExchange {
	private byte[] publicKey, privateKey;

	@Override
	public String getAlgorithm() { return "X25519"; }

	@Override
	public void init(SecureRandom r) {
		EdKeyGenerator kpg = new EdKeyGenerator();
		try {
			kpg.initialize(EdParameterSpec.X25519_CURVE_SPEC, r);
		} catch (InvalidAlgorithmParameterException ignored) {}
		EdPrivateKey kp = (EdPrivateKey) kpg.generateKeyPair().getPrivate();
		privateKey = kp.getPrivateKey();
		publicKey = kp.getPublicKey();
	}

	public static void main(String[] args) throws Exception {
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");

		var jpk1 = kpg.generateKeyPair();
		var jpk2 = kpg.generateKeyPair();

		EdPrivateKey pk1 = new EdPrivateKey(new PKCS8EncodedKeySpec(jpk1.getPrivate().getEncoded()), EdParameterSpec.X25519_CURVE_SPEC);
		EdPrivateKey pk2 = new EdPrivateKey(new PKCS8EncodedKeySpec(jpk2.getPrivate().getEncoded()), EdParameterSpec.X25519_CURVE_SPEC);

		EdPoint secKey1 = pk1.getPublicPoint().scalarMultiply(pk2.getPrivateKey());
		EdPoint secKey2 = pk2.getPublicPoint().scalarMultiply(pk1.getPrivateKey());

		System.out.println(secKey1);
		System.out.println(secKey2);

		System.out.println(DynByteBuf.wrap(secKey1.getU()).hex());
		System.out.println(DynByteBuf.wrap(secKey2.getU()).hex());

		System.out.println(pointMultiply(pk1.getPrivateKey(), fromBytes(pk2.getPublicPoint().negate().getU())));
		System.out.println(pointMultiply(pk2.getPrivateKey(), fromBytes(pk1.getPublicPoint().negate().getU())));

		System.out.println(a24);
		KeyAgreement dh1 = KeyAgreement.getInstance("X25519");
		dh1.init(jpk1.getPrivate());
		dh1.doPhase(jpk2.getPublic(), true);
		System.out.println(DynByteBuf.wrap(dh1.generateSecret()).hex());

		KeyAgreement dh2 = KeyAgreement.getInstance("X25519");
		dh2.init(jpk2.getPrivate());
		dh2.doPhase(jpk1.getPublic(), true);
		System.out.println(DynByteBuf.wrap(dh2.generateSecret()).hex());
	}

/*	public static byte[] toX25519UCoordinate(EdPoint point) {
		// 确保点已归一化（转换为 P2 或 P3 格式）
		if (point.format != EdPoint.Format.P2 && point.format != EdPoint.Format.P3) {
			point = point.toP2(); // 或 point.toP3()
		}

		// 获取归一化的 y 坐标（Y/Z）
		EdInteger recip = point.Z.invert();
		EdInteger y = point.Y.mul(recip);

		// 获取曲线参数和常数
		EdInteger p = curve.getField().getModulus();

		// 计算 u = (1 + y) * (1 - y)^-1 mod p
		EdInteger num = ONE.add(y);            // 1 + y
		EdInteger den = ONE.sub(y);       // 1 - y
		EdInteger denInv = den.invert();  // (1 - y)^-1
		EdInteger u = num.mul(denInv).mod(p);

		// 转换为 32 字节小端序
		byte[] result = new byte[32];
		byte[] uBytes = u.toByteArray();
		System.arraycopy(uBytes, 0, result, 0, Math.min(32, uBytes.length));
		return result;
	}*/

	private static int bitAt(byte[] arr, int index) {
		int byteIndex = index / 8;
		int bitIndex = index % 8;
		return (arr[byteIndex] & (1 << bitIndex)) >> bitIndex;
	}

	static EdInteger a24 = of(121665);
	private static EdInteger pointMultiply(byte[] k, EdInteger u) {
		EdInteger x_1 = u;
		EdInteger x_2 = ONE.mutable();
		EdInteger z_2 = ZERO.mutable();
		EdInteger x_3 = u.mutable();
		EdInteger z_3 = ONE.mutable();
		int swap = 0;

		// Variables below are reused to avoid unnecessary allocation
		// They will be assigned in the loop, so initial value doesn't matter
		EdInteger m1 = ZERO.mutable();
		EdInteger DA = ZERO.mutable();
		EdInteger E = ZERO.mutable();
		EdInteger a24_times_E = ZERO.mutable();

		// Comments describe the equivalent operations from RFC 7748
		// In comments, A(m1) means the variable m1 holds the value A
		for (int t = 256 - 1; t >= 0; t--) {
			int k_t = bitAt(k, t);
			swap = swap ^ k_t;

			if (swap != 0) {
				var tmp = x_2;
				x_2 = x_3;
				x_3 = tmp;

				tmp = z_2;
				z_2 = z_3;
				z_3 = tmp;
			}

			swap = k_t;

			// A(m1) = x_2 + z_2
			m1.set(x_2).add(z_2);
			// D = x_3 - z_3
			// DA = D * A(m1)
			DA.set(x_3).sub(z_3).mul(m1);
			// AA(m1) = A(m1)^2
			m1.square();
			// B(x_2) = x_2 - z_2
			x_2.sub(z_2);
			// C = x_3 + z_3
			// CB(x_3) = C * B(x_2)
			x_3.add(z_3).mul(x_2);
			// BB(x_2) = B^2
			x_2.square();
			// E = AA(m1) - BB(x_2)
			E.set(m1).sub(x_2);
			// compute a24 * E using SmallValue
			a24_times_E.set(E);

			// (a - 2) / 4
			a24_times_E.mul(a24);

			// assign results to x_3, z_3, x_2, z_2
			// x_2 = AA(m1) * BB
			x_2.mul(m1);
			// z_2 = E * (AA(m1) + a24 * E)
			z_2.set(m1).add(a24_times_E).mul(E);
			// z_3 = x_1*(DA - CB(x_3))^2
			z_3.set(DA).sub(x_3).square().mul(x_1);
			// x_3 = (CB(x_3) + DA)^2
			x_3.add(DA).square();
		}

		if (swap != 0) {
			x_2 = x_3;
			z_2 = z_3;
		}

		// return (x_2 * z_2^(p - 2))
		return x_2.mul(z_2.invert());
	}

	@Override
	public int length() { return publicKey.length; }

	@Override
	public void writePublic(DynByteBuf bb) { bb.put(publicKey); }

	@Override
	public byte[] readPublic(DynByteBuf bb) throws GeneralSecurityException {
		EdPoint otherPub = new EdPoint(EdParameterSpec.X25519_CURVE_SPEC.getCurve(), bb.toByteArray());

		EdPoint secKey = otherPub.scalarMultiplyShared(privateKey);
		byte[] array = secKey.getU();
		for (int i = 0; i < array.length; i++) {
			if (array[i] != 0) return array;
		}
		throw new InvalidKeyException("Point has small order");
	}

	@Override
	public void clear() { Arrays.fill(privateKey, (byte) 0); privateKey = publicKey = null; }
}