package roj.crypt;

import roj.RojLib;
import roj.asmx.launcher.Autoload;
import roj.asmx.nixim.Copy;
import roj.asmx.nixim.Inject;
import roj.asmx.nixim.Nixim;
import roj.asmx.nixim.Shadow;
import roj.reflect.litasm.FastJNI;
import roj.reflect.litasm.Intrinsics;
import roj.reflect.litasm.ObjectField;
import roj.util.DynByteBuf;

import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2024/10/02 02:17
 */
@Autoload(value = Autoload.Target.NIXIM, intrinsic = RojLib.AES_NI)
@Nixim(altValue = AES.class)
final class nAES {
	public static final int AES_BLOCK_SIZE = 16;

	@Shadow private byte[] lastKey;
	@Shadow int[] encrypt_key, decrypt_key;
	@Shadow int rounds4;
	@Shadow boolean encrypt;

	@Inject
	public void init(int mode, byte[] key, AlgorithmParameterSpec par, SecureRandom random) throws InvalidAlgorithmParameterException, InvalidKeyException {
		if (par != null || random != null) throw new InvalidAlgorithmParameterException();

		this.encrypt = mode != Cipher.DECRYPT_MODE;
		if (Arrays.equals(lastKey, key)) return;

		switch (key.length) {
			case 16, 24, 32: break;
			default: throw new InvalidKeyException("AES key length must be 16, 24 or 32");
		}

		int ROUNDS = (key.length >> 2) + 6;
		rounds4 = ROUNDS;
		// java的int[]为8字节对齐
		// 结构: [8byte pad] [15*__m128i enckey] [15*__m128i deckey]
		int[] rk = encrypt_key = decrypt_key = new int[2 + ((ROUNDS+1) << 3)];
		lastKey = key.clone();
		IL_aes_init(lastKey, ROUNDS, rk);
	}

	@Inject
	public void crypt(DynByteBuf in, DynByteBuf out) throws ShortBufferException {
		if (out.writableBytes() < in.readableBytes()) throw new ShortBufferException();

		var blocks = in.readableBytes() / AES_BLOCK_SIZE;
		int i = out.wIndex();
		out.wIndex(i+AES_BLOCK_SIZE*blocks);

		if (encrypt) {
			IL_aes_encrypt(encrypt_key, rounds4, in.array(), in._unsafeAddr()+in.rIndex, out.array(), out._unsafeAddr()+i, blocks);
		} else {
			IL_aes_decrypt(decrypt_key, rounds4, in.array(), in._unsafeAddr()+in.rIndex, out.array(), out._unsafeAddr()+i, blocks);
		}

		in.rIndex += AES_BLOCK_SIZE*blocks;
	}

	@Inject
	static void aes_encrypt(int[] K, int len, DynByteBuf in, DynByteBuf out) {
		int i = out.wIndex();
		out.wIndex(i+AES_BLOCK_SIZE);
		IL_aes_encrypt(K, len, in.array(), in._unsafeAddr()+in.rIndex, out.array(), out._unsafeAddr()+i, 1);
		in.rIndex += AES_BLOCK_SIZE;
	}
	@Inject
	static void aes_decrypt(int[] K, int len, DynByteBuf in, DynByteBuf out) {
		int i = out.wIndex();
		out.wIndex(i+ AES_BLOCK_SIZE);
		IL_aes_decrypt(K, len, in.array(), in._unsafeAddr()+in.rIndex, out.array(), out._unsafeAddr()+i, 1);
		in.rIndex += AES_BLOCK_SIZE;
	}

	@Inject("<clinit>") static void __clinit() {Intrinsics.linkNative(RojLib.getLibrary(), AES.class);}

	@Copy
	@FastJNI
	private static native void IL_aes_init(byte[] k, int rounds, int[] Ke);
	@Copy
	@FastJNI
	private static native void IL_aes_encrypt(int[] K, int rounds, @ObjectField("") byte[] in, long in_off, @ObjectField("") byte[] out, long out_off, int blocks);
	@Copy
	@FastJNI
	private static native void IL_aes_decrypt(int[] K, int rounds, @ObjectField("") byte[] in, long in_off, @ObjectField("") byte[] out, long out_off, int blocks);
	@Copy
	@FastJNI
	private static native void IL_aes_CBC_encrypt(int[] K, int rounds, byte[] iv16, @ObjectField("") byte[] in, long in_off, @ObjectField("") byte[] out, long out_off, int blocks);
	@Copy
	@FastJNI
	private static native void IL_aes_CBC_decrypt(int[] K, int rounds, byte[] iv16, @ObjectField("") byte[] in, long in_off, @ObjectField("") byte[] out, long out_off, int blocks);
	@Copy
	@FastJNI
	private static native void IL_aes_CTR(int[] K, int rounds, byte[] iv16, byte[] nonce4, @ObjectField("") byte[] in, long in_off, @ObjectField("") byte[] out, long out_off, int blocks);
}
