package roj.net.mss;

import roj.crypt.CipheR;

/**
 * @author Roj233
 * @since 2021/12/22 12:36
 */
public interface MSSCiphers {
	int getKeySize();

	CipheR get();
}
