package roj.plugins.web.sso;

import roj.crypt.HMAC;
import roj.io.IOUtil;
import roj.reflect.Unsafe;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.URICoder;
import roj.util.ArrayCache;
import roj.util.BitStream;
import roj.util.DynByteBuf;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author Roj234
 * @since 2024/7/8 3:27
 */
public final class TOTP {
	public static HMAC createHMAC() throws NoSuchAlgorithmException {
		return new HMAC(MessageDigest.getInstance("SHA1"));
	}

	public static String makeTOTP(HMAC hmac_sha1, byte[] secretKey, long time) {
		hmac_sha1.init(secretKey, 0, secretKey.length);
		hmac_sha1.update(IOUtil.getSharedByteBuf().putLong(time / 30000));
		byte[] digest = hmac_sha1.digestShared();

		int offset = digest[digest.length-1]&0xf;
		int i = Unsafe.U.get32UB(digest, Unsafe.ARRAY_BYTE_BASE_OFFSET+offset)&Integer.MAX_VALUE;

		int otp = i % 1000000;

		char[] data = ArrayCache.getCharArray(6, false);
		int len = TextUtil.digitCount(otp);
		for (int j = 0; j < 6-len; j++) data[j] = '0';
		CharList.getChars(otp, 6, data);

		var otpStr = new String(data, 0, 6);

		ArrayCache.putArray(data);
		return otpStr;
	}

	public static String makeURL(byte[] secretKey, String account, String website) {
		var sb = IOUtil.getSharedCharBuf();
		URICoder.encodeURIComponent(sb.append("otpauth://totp/"), account).append("?secret=");

		var br = new BitStream(DynByteBuf.wrap(secretKey));
		// Base32NoPadding
		while (br.readableBits() > 0) {
			int i = br.readBit(5);
			if (i > 25) sb.append((char)('2'-26 + i));
			else sb.append((char)('A'+i));
		}
		return URICoder.encodeURIComponent(sb.append("&issuer="), website).toStringAndZero();
	}
}