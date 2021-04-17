package roj.crypt;

import java.util.Arrays;

/**
 * @author solo6975
 * @since 2022/2/13 17:52
 */
public class PBKDF2 {
	public static byte[] PBKDF2_Derive(MessageAuthenticCode H, byte[] pass, byte[] salt, int c, int L) {
		H.setSignKey(pass);

		byte[] out = new byte[L];

		byte[] io = Arrays.copyOf(salt, salt.length+4);
		byte[] tmp = new byte[H.getDigestLength()];

		int off = 0;
		int n = 1;
		while (off < L) {
			Conv.i2b(io, io.length-4, n++);
			H.update(io);
			salt = H.digestShared();
			System.arraycopy(salt, 0, tmp, 0, salt.length);

			for (int i = 1; i < c; i++) {
				H.update(salt);
				H.digestShared();
				for (int k = salt.length - 1; k >= 0; k--) tmp[k] ^= salt[k];
			}

			System.arraycopy(tmp, 0, out, off, Math.min(tmp.length, L-off));
			off += tmp.length;
		}

		return out;
	}
}
