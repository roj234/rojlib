package roj.net.mss;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

/**
 * @author Roj233
 * @since 2021/12/22 12:53
 */
public final class JKeyFactory implements MSSPublicKeyFactory<PublicKey> {
	private final KeyFactory factory;

	public JKeyFactory(String alg) throws NoSuchAlgorithmException {
		factory = KeyFactory.getInstance(alg);
	}

	public JKeyFactory(KeyFactory factory) {
		this.factory = factory;
	}

	@Override
	public String getAlgorithm() {
		return factory.getAlgorithm();
	}

	@Override
	public byte[] encode(PublicKey pub) {
		return pub.getEncoded();
	}

	@Override
	public MSSPublicKey decode(byte[] data) throws GeneralSecurityException {
		return new JKey(factory.generatePublic(new X509EncodedKeySpec(data)));
	}
}
