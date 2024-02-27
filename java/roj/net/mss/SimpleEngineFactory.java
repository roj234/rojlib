package roj.net.mss;

import roj.collect.IntMap;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.function.Supplier;

/**
 * @author Roj233
 * @since 2021/12/24 22:44
 */
public final class SimpleEngineFactory implements Supplier<MSSEngine> {
	public static SimpleEngineFactory server() {return new SimpleEngineFactory(false);}
	public static SimpleEngineFactory client() {return new SimpleEngineFactory(true);}

	public SimpleEngineFactory(boolean client) {this.client = client;}

	private final boolean client;
	private MSSKeyPair pair;
	private int switches;
	private IntMap<MSSPublicKey> psc;

	public SimpleEngineFactory key(KeyPair key) throws GeneralSecurityException {return key(new MSSKeyPair(key));}
	public SimpleEngineFactory key(MSSKeyPair pair) {this.pair = pair;return this;}
	public MSSKeyPair getKeyPair() {return pair;}

	public SimpleEngineFactory switches(int switches) {
		this.switches = switches;
		return this;
	}

	public SimpleEngineFactory psc(int id, PublicKey key) { return psc(id, new MSSPublicKey(key)); }
	public SimpleEngineFactory psc(int id, MSSPublicKey key) {
		if (!client && !(key instanceof MSSKeyPair)) throw new IllegalArgumentException("Server mode psc must be private key");
		if (psc == null) psc = new IntMap<>();
		psc.putInt(id, key);
		return this;
	}

	@Override
	public MSSEngine get() {
		MSSEngine s = client ? new MSSEngineClient() : new MSSEngineServer();
		s.setDefaultCert(pair).switches(switches);
		s.setPreSharedCertificate(psc);
		return s;
	}
}