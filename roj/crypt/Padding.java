package roj.crypt;

import java.security.GeneralSecurityException;

/**
 * @author solo6975
 * @since 2022/2/12 20:32
 */
public interface Padding {
	int getPaddedLength(int data);

	void pad(byte[] src, int srcOff, int srcLen, byte[] dst, int dstOff) throws GeneralSecurityException;

	/**
	 * To Implementors: 可能会有src = dst的情况
	 */
	int unpad(byte[] src, int srcOff, int srcLen, byte[] dst, int dstOff) throws GeneralSecurityException;

	String name();

	int length();
}
