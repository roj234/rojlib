package roj.net.mss;

import java.security.GeneralSecurityException;

/**
 * @author Roj233
 * @since 2021/12/22 12:54
 */
public interface MSSPublicKeyFactory<T> {
	String getAlgorithm();

	byte[] encode(T publicKey) throws GeneralSecurityException;
	MSSPublicKey decode(byte[] data) throws GeneralSecurityException;
}
