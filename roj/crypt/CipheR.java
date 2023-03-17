package roj.crypt;

import roj.util.DynByteBuf;

import java.security.GeneralSecurityException;

/**
 * @author Roj233
 * @since 2021/12/28 0:05
 */
public interface CipheR {
	int ENCRYPT = 0, DECRYPT = 1;

	default String getAlgorithm() {
		return getClass().getSimpleName();
	}

	// less than max is (optionally) zero-filled
	int getMaxKeySize();
	void setKey(byte[] key, int flags);

	default void setOption(String key, Object value) {}

	default boolean isBaseCipher() {return true;}

	/**
	 * Zero: stream cipher
	 */
	default int getBlockSize() {return 0;}

	default int getCryptSize(int data) {return data;}

	void crypt(DynByteBuf in, DynByteBuf out) throws GeneralSecurityException;

	default int cryptInline(DynByteBuf in, int length) throws GeneralSecurityException {
		int pos = in.wIndex();
		DynByteBuf inCopy = in.slice(in.rIndex, length);
		in.wIndex(in.rIndex);
		try {
			crypt(inCopy, in);
			return in.wIndex() - pos;
		} finally {
			in.wIndex(pos);
		}
	}
}
