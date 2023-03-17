package roj.net.mss;

import roj.crypt.CipheR;
import roj.util.DynByteBuf;
import roj.util.EmptyArrays;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * @author Roj233
 * @since 2021/12/22 19:18
 */
public final class JCiphers implements MSSCiphers {
	private final String alg;
	private final int keySize;

	public JCiphers(String alg, int keySize) {
		this.alg = alg;
		this.keySize = keySize;
	}

	@Override
	public int getKeySize() {
		return keySize;
	}

	@Override
	public CipheR get() {
		return new Delegate(alg);
	}

	private static final class Delegate implements CipheR {
		byte[] tmp;
		private final Cipher cip;

		Delegate(String name) {
			try {
				this.cip = Cipher.getInstance(name);
			} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
				throw new IllegalStateException("Unable to create the Cipher", e);
			}
			this.tmp = EmptyArrays.BYTES;
		}

		@Override
		public String getAlgorithm() {
			return cip.getAlgorithm();
		}

		@Override
		public int getMaxKeySize() {
			return 16;
		}

		@Override
		public void setKey(byte[] key, int flags) {
			SecretKeySpec sk = new SecretKeySpec(key, cip.getAlgorithm().substring(0, cip.getAlgorithm().indexOf('/')));
			try {
				cip.init((flags&CipheR.DECRYPT) != 0 ? Cipher.DECRYPT_MODE : Cipher.ENCRYPT_MODE, sk,
					new IvParameterSpec(Arrays.copyOf(key, 16)));
			} catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
				e.printStackTrace();
				System.out.println("Failed to initialize cipher");
			}
		}

		@Override
		public void crypt(DynByteBuf in, DynByteBuf out) throws GeneralSecurityException {
			int rm = in.readableBytes();
			if (out.writableBytes() < rm) throw new ShortBufferException();
			if (tmp.length < rm) tmp = new byte[rm];
			if (in.hasArray()) {
				int len = cip.update(in.array(), in.arrayOffset() + in.rIndex, in.readableBytes(), tmp);
				in.wIndex(in.rIndex);
				out.put(tmp, 0, len);
			} else {
				int r = in.readableBytes();
				if (tmp.length < rm + r) tmp = new byte[rm + r];
				in.read(tmp, 0, r);
				int len = cip.update(tmp, 0, r, tmp, r);
				out.put(tmp, r, len);
			}
		}
	}
}
