package roj.crypt;

import javax.crypto.BadPaddingException;
import javax.crypto.ShortBufferException;
import java.security.GeneralSecurityException;

/**
 * @author solo6975
 * @since 2022/2/12 17:54
 */
public final class PKCS5 implements Padding {
	private final int block;

	public PKCS5(int padding) {
		this.block = padding;
	}

	@Override
	public int getPaddedLength(int data) {
		int len = block - data % block;
		return data + len;
	}

	@Override
	public void pad(byte[] src, int srcOff, int srcLen, byte[] dst, int dstOff) throws GeneralSecurityException {
		System.arraycopy(src, srcOff, dst, dstOff, srcLen);
		int len = block - srcLen % block;

		int lim = srcLen;
		if (dst.length < lim + len) throw new ShortBufferException("Unable pad, more: " + (len + lim - dst.length));
		byte num = (byte) len;
		while (len-- > 0) dst[dstOff + lim++] = num;
	}

	@Override
	public int unpad(byte[] src, int srcOff, int srcLen, byte[] dst, int dstOff) throws GeneralSecurityException {
		byte last = src[srcOff + srcLen - 1];
		int num = last & 0xff;
		if (num <= 0 || num > block) throw new BadPaddingException();

		int start = srcLen - num;
		if (start < 0) throw new BadPaddingException();
		start += srcOff;

		for (int i = srcOff + srcLen - 2; i >= start; i--) {
			if (src[i] != last) throw new BadPaddingException();
		}
		if (src != dst || srcOff != dstOff) System.arraycopy(src, srcOff, dst, dstOff, srcLen - num);
		return srcLen - num;
	}

	@Override
	public String name() {
		return "PKCS5";
	}

	@Override
	public int length() {
		return block;
	}
}
