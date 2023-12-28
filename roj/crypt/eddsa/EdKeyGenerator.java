package roj.crypt.eddsa;

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;

public final class EdKeyGenerator extends KeyPairGeneratorSpi {
	private EdParameterSpec spec;
	private SecureRandom random;

	@Override
	public void initialize(int keysize, SecureRandom random) {
		if (keysize != 256) throw new InvalidParameterException("unknown key type.");
		spec = EdParameterSpec.ED25519_CURVE_SPEC;
		this.random = random;
	}

	@Override
	public void initialize(AlgorithmParameterSpec params, SecureRandom random) throws InvalidAlgorithmParameterException {
		if (!(params instanceof EdParameterSpec)) throw new InvalidAlgorithmParameterException("param is not EdParameterSpec");

		spec = (EdParameterSpec) params;
		this.random = random;
	}

	@Override
	public KeyPair generateKeyPair() {
		if (spec == null) initialize(256, new SecureRandom());

		byte[] seed = new byte[32];
		random.nextBytes(seed);

		EdPrivateKey priKey = new EdPrivateKey(seed, spec);
		EdPublicKey pubKey = new EdPublicKey(priKey);
		return new KeyPair(pubKey, priKey);
	}
}