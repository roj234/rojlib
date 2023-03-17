package roj.net;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2021/2/6 22:11
 */
public class ServerSslConf implements SslConfig {
	private final String keyStore;
	private final char[] password;

	public ServerSslConf(String keyStore, char[] password) {
		this.keyStore = keyStore;
		this.password = password;
	}

	@Override
	public boolean isServerSide() {
		return true;
	}

	@Override
	public InputStream getPkPath() {
		try {
			return new FileInputStream(keyStore);
		} catch (FileNotFoundException e) {
			return null;
		}
	}

	@Override
	public InputStream getCaPath() {
		return getPkPath();
	}

	@Override
	public char[] getPasswd() {
		return password;
	}
}
