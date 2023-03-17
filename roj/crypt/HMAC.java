package roj.crypt;

import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.security.DigestException;
import java.security.MessageDigest;

/**
 * @author solo6975
 * @since 2022/2/13 17:52
 */
public class HMAC extends MessageDigest implements MessageAuthenticCode {
	public final MessageDigest md;
	private final byte[] ipad, opad, tmp;

	public HMAC(MessageDigest md) {
		this(md, 64);
	}

	public HMAC(MessageDigest md, int blockSize) {
		this(md, blockSize, md.getAlgorithm() + "withHMAC");
	}

	protected HMAC(MessageDigest md, int blockSize, String alg) {
		super(alg);
		if (md instanceof HMAC) throw new IllegalStateException();
		this.md = md;
		this.ipad = new byte[blockSize];
		this.opad = new byte[blockSize];
		this.tmp = new byte[md.getDigestLength()];
	}

	@Override
	protected int engineGetDigestLength() {
		return md.getDigestLength();
	}

	@Override
	protected void engineUpdate(byte input) {
		md.update(input);
	}

	@Override
	protected void engineUpdate(byte[] input, int off, int len) {
		md.update(input, off, len);
	}

	@Override
	public void setSignKey(byte[] key, int off, int len) {
		if (len > ipad.length) {
			md.reset();
			md.update(key, off, len);
			key = md.digest();
			off = 0;
			len = key.length;
		}

		int i = 0;
		while (len-- > 0) {
			byte b = key[off++];
			ipad[i] = (byte) (b ^ 0x36);
			opad[i++] = (byte) (b ^ 0x5C);
		}

		// zero fill rest
		while (i < ipad.length) {
			ipad[i] = 0x36;
			opad[i++] = 0x5C;
		}

		reset();
	}

	@Override
	protected byte[] engineDigest() {
		return digestShared().clone();
	}

	public byte[] digestShared() {
		byte[] hash = tmp;
		try {
			md.digest(hash, 0, hash.length);
		} catch (DigestException e) {
			Helpers.athrow(e);
		}
		md.update(opad);
		md.update(hash);
		try {
			md.digest(hash, 0, hash.length);
		} catch (DigestException e) {
			Helpers.athrow(e);
		}
		reset();
		return hash;
	}

	@Override
	protected void engineReset() {
		md.reset();
		md.update(ipad);
	}

	@Override
	public String toString() {
		return getAlgorithm() + " Message Digest from RojLib";
	}

	public static byte[] HKDF_extract(HMAC hmac, byte[] salt, byte[] IKM) {
		hmac.setSignKey(salt,0,salt.length);
		return hmac.digest(IKM);
	}

	public static void HKDF_expand(HMAC hmac, byte[] PRK, DynByteBuf info, int L, DynByteBuf out) {
		hmac.setSignKey(PRK);

		if (info != null) hmac.update(info);
		hmac.update((byte) 0);
		byte[] io = hmac.digestShared();

		int i = 1;
		while (L > 0) {
			hmac.update(io);
			if (info != null) hmac.update(info);
			hmac.update((byte) i++);

			out.put(hmac.digestShared(),0,Math.min(L,io.length));

			L -= io.length;
		}
	}

	public static byte[] HKDF_expand(HMAC hmac, byte[] PRK, DynByteBuf info, int L) {
		hmac.setSignKey(PRK);

		byte[] out = new byte[L];

		if (info != null) hmac.update(info);
		hmac.update((byte) 0);
		byte[] io = hmac.digestShared();

		int off = 0;
		int i = 1;
		while (off < L) {
			hmac.update(io);
			if (info != null) hmac.update(info);
			hmac.update((byte) i++);

			System.arraycopy(hmac.digestShared(), 0, out, off, Math.min(io.length, L-off));

			off += io.length;
		}
		return out;
	}
}
