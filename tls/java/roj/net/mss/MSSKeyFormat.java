package roj.net.mss;

import roj.util.DynByteBuf;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * @author Roj233
 * @since 2021/12/22 12:54
 */
public interface MSSKeyFormat {
	String getAlgorithm();

	byte[] encode(MSSPublicKey key);
	MSSPublicKey decode(DynByteBuf data) throws GeneralSecurityException;

	boolean verify(MSSPublicKey key, byte[] data, byte[] sign) throws GeneralSecurityException;
	byte[] sign(MSSKeyPair key, SecureRandom random, byte[] data) throws GeneralSecurityException;
}