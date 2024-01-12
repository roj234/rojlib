package roj.crypt;

import java.util.Arrays;

/**
 * @author solo6975
 * @since 2022/2/13 17:52
 */
public class PBKDF2 {
	public static byte[] PBKDF2_Derive(MessageAuthenticCode H, byte[] pass, byte[] salt, int cost, int len) {
		H.setSignKey(pass);

		byte[] out = new byte[len];

		byte[] io = Arrays.copyOf(salt, salt.length+4);
		byte[] tmp = new byte[H.getDigestLength()];

		int off = 0;
		int n = 1;
		while (off < len) {
			Conv.i2b(io, io.length-4, n++);
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
}