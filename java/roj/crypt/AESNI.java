package roj.crypt;

import roj.RojLib;
import roj.asmx.nixim.Copy;
import roj.asmx.nixim.Inject;
import roj.asmx.nixim.Nixim;
import roj.asmx.nixim.Shadow;
import roj.io.IOUtil;
import roj.util.DynByteBuf;
import roj.util.NativeMemory;

import javax.crypto.Cipher;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2024/10/02 02:17
 */
@Nixim("roj/crypt/AES")
final class AESNI {
	@Shadow private byte[] lastKey;
	@Shadow int[] encrypt_key, decrypt_key;
	@Shadow int limit;
	@Shadow boolean decrypt;

	@Inject
	public void init(int mode, byte[] key, AlgorithmParameterSpec par, SecureRandom random) throws InvalidAlgorithmParameterException, InvalidKeyException {
		if (par != null) throw new InvalidAlgorithmParameterException();

		this.decrypt = mode == Cipher.DECRYPT_MODE;
		if (Arrays.equals(lastKey, key)) return;

		switch (key.length) {
			case 16: case 24: case 32: break;
			default: throw new InvalidKeyException("AES key length must be 16, 24 or 32");
		}

		int ROUNDS = (key.length >> 2) + 6;
		limit = ROUNDS << 2;
		int ROUND_KEY_COUNT = limit + 4;

		int[] Ke = new int[ROUND_KEY_COUNT]; // encryption round keys
		int[] Kd = new int[ROUND_KEY_COUNT]; // decryption round keys

		int KC = key.length/4; // keylen in 32-bit elements

		int[] tk = new int[KC];
		var bb = IOUtil.SharedCoder.get().wrap(key);
		int i = 0;
		while (bb.isReadable()) tk[i++] = bb.readInt();

		aes_key_expand(tk, limit, Ke, Kd);

		encrypt_key = Ke;
		decrypt_key = Kd;
		lastKey = key.clone();
	}

	@Inject
	public void cryptOneBlock(DynByteBuf in, DynByteBuf out) {
		if (decrypt) aes_decrypt(decrypt_key, limit, in, out);
		else aes_encrypt(encrypt_key, limit, in, out);
	}

	@Copy static native void aes_key_expand(int[] tk, int len, int[] Ke, int[] Kd);
	@Inject static native void aes_encrypt(int[] K, int len, DynByteBuf in, DynByteBuf out);
	@Inject static native void aes_decrypt(int[] K, int len, DynByteBuf in, DynByteBuf out);

	@Inject(at = Inject.At.REMOVE, value = "<clinit>") static native void __clinit();


	public static void main(String[] args) throws Exception {
		System.out.println("FastJNI Test");

		var referent = new AESNI();
		NativeMemory.createCleaner(referent, () -> System.out.println("GC Working!"));

		assert RojLib.hasNative(0);

		Thread running = new Thread(() -> {
			System.out.println("InfLoop");
			infLoopFastJNI();
			System.out.println("InfLoop Exit");
		});

		running.setDaemon(true);
		running.start();
		referent = null;

		Thread.sleep(1);
		System.gc();

		int[] lastArray;
		System.out.println("BigNumA="+checkA());
		System.out.println("BigNumB="+checkB());
		int i = 1;
		while (i++ != 12345) {
			lastArray = new int[1049587];
		}
		System.out.println("GC Test OK");
	}

	private static long checkA() {
		long bigNum = 0;
		long t = System.nanoTime();
		for (int i = 0; i < 100000000; i++) {
			bigNum += i * 233;
		}
		System.out.println("Java Cost="+(System.nanoTime()-t)/100000000+"ns/op");
		return bigNum;
	}
	private static long checkB() {
		long bigNum = 0;
		long t = System.nanoTime();
		for (int i = 0; i < 100000000; i++) {
			bigNum += callNativeFastJNI(i);
		}
		System.out.println("JNI Cost="+(System.nanoTime()-t)/100000000+"ns/op");
		return bigNum;
	}

	static native int callNativeFastJNI(int i);
	static native void infLoopFastJNI();
}
