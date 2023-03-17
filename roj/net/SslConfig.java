package roj.net;

import java.io.InputStream;

/**
 * @author Roj234
 * @since 2021/2/5 0:28
 */
public interface SslConfig {
	boolean isServerSide();

	default boolean isNeedClientAuth() {
		return false;
	}

	InputStream getPkPath();

	InputStream getCaPath();

	char[] getPasswd();

	default Object getAllocator() {
		return null;
	}
}
