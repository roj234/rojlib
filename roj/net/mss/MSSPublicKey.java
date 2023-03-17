package roj.net.mss;

import javax.crypto.Cipher;

/**
 * @author solo6975
 * @since 2022/2/13 13:02
 */
public interface MSSPublicKey {
	String getAlgorithm();

	byte[] publicKey();
	Cipher publicCipher();
}
