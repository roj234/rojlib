package roj.crypt;

import roj.concurrent.OperationDone;
import roj.io.IOUtil;
import roj.reflect.Unaligned;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * @author solo6975
 * @since 2022/2/13 17:52
 */
public final class KDF {
	public static byte[] PBKDF2_Derive(MessageAuthenticCode H, byte[] pass, byte[] salt, int cost, int len) {
		H.setSignKey(pass);

		byte[] out = new byte[len];

		byte[] io = Arrays.copyOf(salt, salt.length+4);
		byte[] tmp = new byte[H.getDigestLength()];

		int off = 0;
		int n = 1;
		while (off < len) {
			Unaligned.U.put32UB(io, Unaligned.ARRAY_BYTE_BASE_OFFSET + io.length-4, n++);
			H.update(io);
			salt = H.digestShared();
			System.arraycopy(salt, 0, tmp, 0, salt.length);

			for (int i = 1; i < cost; i++) {
				H.update(salt);
				salt = H.digestShared();
				for (int k = salt.length-1; k >= 0; k--) tmp[k] ^= salt[k];
			}

			System.arraycopy(tmp, 0, out, off, Math.min(tmp.length, len-off));
			off += tmp.length;
		}

		return out;
	}

	public static byte[] HKDF_extract(MessageAuthenticCode hmac, byte[] salt, byte[] IKM) {
		hmac.setSignKey(salt,0,salt.length);
		hmac.update(IKM);
		return hmac.digest();
	}

	public static byte[] HKDF_expand(MessageAuthenticCode mac, byte[] PRK, int L) {return HKDF_expand(mac, PRK, ByteList.EMPTY, L);}
	public static byte[] HKDF_expand(MessageAuthenticCode mac, byte[] PRK, DynByteBuf info, int L) {
		byte[] out = new byte[L];
		HKDF_expand(mac, PRK, info, L, out, Unaligned.ARRAY_BYTE_BASE_OFFSET);
		return out;
	}
	public static void HKDF_expand(MessageAuthenticCode mac, byte[] PRK, DynByteBuf info, int L, Object ref, long address) {
		if (PRK != null) mac.setSignKey(PRK);

		if (info != null) mac.update(info);
		mac.update((byte) 0);
		byte[] io = mac.digestShared();

		int off = 0;
		int i = 1;
		while (off < L) {
			mac.update(io);
			if (info != null) mac.update(info);
			mac.update((byte) i++);

			Unaligned.U.copyMemory(mac.digestShared(), Unaligned.ARRAY_BYTE_BASE_OFFSET, ref, address+off, Math.min(io.length, L-off));

			off += io.length;
		}
	}

	public static byte[] HKDF_HmacSha256(byte[] key, byte[] salt, int len) {
		try {
			return HKDF_expand(new HMAC(MessageDigest.getInstance("SHA-256")), key, salt == null ? ByteList.EMPTY : IOUtil.SharedBuf.get().wrap(salt), len);
		} catch (NoSuchAlgorithmException e) {
			throw OperationDone.NEVER;
		}
	}

	static int[] reverse(int[] arr, int i, int length) {
		if (--length <= 0) return arr;

		for (int e = Math.max((length + 1) >> 1, 1); i < e; i++) {
			int a = arr[i];
			arr[i] = arr[length - i];
			arr[length - i] = a;
		}
		return arr;
	}
}