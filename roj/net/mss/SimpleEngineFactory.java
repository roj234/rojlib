package roj.net.mss;

import roj.collect.IntMap;
import roj.crypt.KeyType;
import roj.net.SecureUtil;

import javax.net.ssl.KeyManager;
import javax.net.ssl.X509KeyManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.function.Supplier;

/**
 * @author Roj233
 * @since 2021/12/24 22:44
 */
public final class SimpleEngineFactory implements Supplier<MSSEngine> {
	public static SimpleEngineFactory server() {
		return new SimpleEngineFactory(false);
	}
	public static SimpleEngineFactory client() {
		return new SimpleEngineFactory(true);
	}

	public SimpleEngineFactory(boolean client) {this.client = client;}

	private final boolean client;
	private MSSKeyPair pair;
	private int switches;
	private IntMap<MSSPublicKey> psc;

	public SimpleEngineFactory key(KeyPair key) throws GeneralSecurityException {
		return key(new MSSKeyPair(key));
	}
	public SimpleEngineFactory key(MSSKeyPair pair) {
		this.pair = pair;
		return this;
	}

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
	public SimpleEngineFactory pscOnly(File file, String format) {
		if (!client) throw new IllegalArgumentException("Client mode only");
		if (file.isFile()) {
			KeyType kf = KeyType.getInstance(format);
			try {
				PublicKey pk = kf.getPublic(file);
				if (pk != null) {
					psc(0, pk);
					switches |= MSSEngine.PSC_ONLY;
				}
			} catch (IOException | GeneralSecurityException e) {
				e.printStackTrace();
			}
		}
		return this;
	}

	@Override
	public MSSEngine get() {
		MSSEngine s = client ? new MSSEngineClient() : new MSSEngineServer();
		s.setDefaultCert(pair).switches(switches);
		s.setPreSharedCertificate(psc);
		return s;
	}

	@Deprecated
	public static SimpleEngineFactory fromKeystore(InputStream ks, char[] pass, String keyType) throws IOException, GeneralSecurityException {
		KeyManager[] kmf = SecureUtil.makeKeyManagers(ks, pass);

		X509Certificate pubKey = null;
		PrivateKey privateKey = null;
		for (KeyManager manager : kmf) {
			if (manager instanceof X509KeyManager) {
				X509KeyManager km = (X509KeyManager) manager;
				String alias = km.chooseServerAlias(keyType, null, null);
				privateKey = km.getPrivateKey(alias);
				pubKey = km.getCertificateChain(alias)[0];
				break;
			}
		}
		if (pubKey == null) throw new UnrecoverableKeyException("No such key");

		return server().key(new MSSKeyPair(pubKey, privateKey));
	}
}