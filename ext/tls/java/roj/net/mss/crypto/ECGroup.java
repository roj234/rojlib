package roj.net.mss.crypto;

import roj.crypt.KeyExchange;

import java.security.AlgorithmParameters;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.util.function.Supplier;

/**
 * @author Roj234
 * @since 2022/11/11 13:28
 */
public final class ECGroup implements Supplier<KeyExchange> {
	public static final ECGroup secp256r1 = new ECGroup("secp256r1", "1.2.840.10045.3.1.7"), secp521r1 = new ECGroup("secp521r1", "1.3.132.0.35");

	private final String name, objectId;
	private final ECParameterSpec params;

	private ECGroup(String name, String objectId) {
		this.name = name;
		this.objectId = objectId;
		try {
			AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
			ap.init(new ECGenParameterSpec(name));
			this.params = ap.getParameterSpec(ECParameterSpec.class);
		} catch (Exception e) {
			throw new IllegalArgumentException("Unsupported curve: "+name, e);
		}
	}

	public String getName() { return name; }
	public String getObjectId() { return objectId; }
	public ECParameterSpec getParams() { return params; }

	@Override
	public KeyExchange get() { return new ECDH(this); }
}