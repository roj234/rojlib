package roj.crypt;

import roj.util.DynByteBuf;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * @author solo6975
 * @since 2022/2/14 0:49
 */
public interface KeyExchange {
	String getAlgorithm();

	void init(SecureRandom r);
	int length();
	void writePublic(DynByteBuf bb);
	byte[] readPublic(DynByteBuf bb) throws GeneralSecurityException;

	void clear();
}
