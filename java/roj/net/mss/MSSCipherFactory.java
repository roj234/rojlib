package roj.net.mss;

import roj.crypt.RCipherSpi;

/**
 * @author Roj233
 * @since 2021/12/22 12:36
 */
public interface MSSCipherFactory {
	int getKeySize();
	RCipherSpi get();
}
