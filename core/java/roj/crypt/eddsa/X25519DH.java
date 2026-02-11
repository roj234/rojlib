package roj.crypt.eddsa;

import roj.crypt.KeyExchange;
import roj.util.DynByteBuf;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.util.Arrays;

import static roj.crypt.eddsa.EdInteger.*;

/**
 * @author Roj234
 * @since 2023/12/31 19:22
 */
public class X25519DH implements KeyExchange {
	private static final EdInteger X25519_a24 = of(121665);
	/*
	 * Constant-time Montgomery ladder that computes k*u
	 */
	private static EdInteger scalarMultiply(byte[] k, EdInteger u) {
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
			a24_times_E.mul(X25519_a24);

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
	private static int bitAt(byte[] arr, int index) {
		int byteIndex = index / 8;
		int bitIndex = index % 8;
		return (arr[byteIndex] & (1 << bitIndex)) >> bitIndex;
	}

	private byte[] publicKey, privateKey;

	@Override
	public String getAlgorithm() { return "X25519"; }

	@Override
	public void init(SecureRandom r) {
		byte[] seed = new byte[32];
		r.nextBytes(seed);

		EdPrivateKey priKey = new EdPrivateKey(seed, EdParameterSpec.X25519_CURVE_SPEC);
		privateKey = priKey.getPrivateKey();
		publicKey = priKey.getPublicKey();
	}

	@Override
	public int length() { return publicKey.length; }

	@Override
	public void writePublic(DynByteBuf bb) { bb.put(publicKey); }

	@Override
	public byte[] readPublic(DynByteBuf bb) throws GeneralSecurityException {
		//This creates a pre-compute table, and much slower when only use once (unlink curve.getBasePoint().scalarMultiply)
		//EdPoint publicKey = new EdPoint(EdParameterSpec.X25519_CURVE_SPEC.getCurve(), bb.toByteArray());
		//var sharedSecret = publicKey.scalarMultiplyShared(privateKey).getU().toByteArray();
		var sharedSecret = scalarMultiply(privateKey, EdInteger.fromBytes(bb.toByteArray())).toByteArray();

		int check = 0;
		for (byte b : sharedSecret) check |= b;
		if (check == 0) {
			throw new InvalidKeyException("Point has small order");
		}

		return sharedSecret;
	}

	@Override
	public void clear() { Arrays.fill(privateKey, (byte) 0); privateKey = publicKey = null; }
}