package roj.crypt;

import roj.concurrent.OperationDone;
import roj.io.IOUtil;
import roj.reflect.FieldAccessor;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import sun.misc.Unsafe;

import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

	public static byte[] HKDF_expand(MessageAuthenticCode mac, byte[] PRK, int L) { return HKDF_expand(mac, PRK, ByteList.EMPTY, L); }
	public static byte[] HKDF_expand(MessageAuthenticCode mac, byte[] PRK, DynByteBuf info, int L) {
		byte[] out = new byte[L];
		HKDF_expand(mac, PRK, info, L, out, Unsafe.ARRAY_BYTE_BASE_OFFSET);
		return out;
	}
	public static void HKDF_expand(MessageAuthenticCode mac, byte[] PRK, DynByteBuf info, int L, Object ref, long address) {
		mac.setSignKey(PRK);

		if (info != null) mac.update(info);
		mac.update((byte) 0);
		byte[] io = mac.digestShared();

		int off = 0;
		int i = 1;
		while (off < L) {
			mac.update(io);
			if (info != null) mac.update(info);
			mac.update((byte) i++);

			FieldAccessor.u.copyMemory(mac.digestShared(), Unsafe.ARRAY_BYTE_BASE_OFFSET, ref, address+off, Math.min(io.length, L-off));

			off += io.length;
		}
	}

	public static byte[] Sha256ExpandKey(byte[] key, byte[] salt, int len) {
		try {
			return HKDF_expand(new HMAC(MessageDigest.getInstance("SHA-256")), key, salt == null ? ByteList.EMPTY : IOUtil.SharedCoder.get().wrap(salt), len);
		} catch (NoSuchAlgorithmException e) {
			throw OperationDone.NEVER;
		}
	}
}
