package roj.crypt;

import roj.io.IOUtil;
import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

/**
 * 国密SM4 - 对称加解密
 */
public final class SM4 extends RCipherSpi {
	private final int[] sKey = new int[32];
	private final int[] temp = new int[4];

	public SM4() {}

	@Override
	public void init(int mode, byte[] key, AlgorithmParameterSpec par, SecureRandom random) throws InvalidAlgorithmParameterException, InvalidKeyException {
		if (key.length != 16) throw new IllegalArgumentException("128bit key is required");

		int i = 0;
		ByteList bb = IOUtil.SharedCoder.get().wrap(key);
		while (bb.isReadable()) temp[i] = bb.readInt() ^ FK[i++];

		sKey[0] = temp[0] ^ sm4_iRK(temp[1] ^ temp[2] ^ temp[3] ^ CK[0]);
		sKey[1] = temp[1] ^ sm4_iRK(temp[2] ^ temp[3] ^ sKey[0] ^ CK[1]);
		sKey[2] = temp[2] ^ sm4_iRK(temp[3] ^ sKey[0] ^ sKey[1] ^ CK[2]);
		sKey[3] = temp[3] ^ sm4_iRK(sKey[0] ^ sKey[1] ^ sKey[2] ^ CK[3]);

		for (i = 4; i < 32; i++) {
			sKey[i] = sKey[i-4] ^ sm4_iRK(sKey[i-3] ^ sKey[i-2] ^ sKey[i-1] ^ CK[i]);
		}

		if (mode == Cipher.ENCRYPT_MODE) Conv.reverse(sKey, 0, 32);
	}

	@Override
	public int engineGetBlockSize() { return 16; }

	@Override
	public void crypt(DynByteBuf in, DynByteBuf out) throws ShortBufferException {
		if (out.writableBytes() < in.readableBytes()) throw new ShortBufferException();

		while (in.readableBytes() >= 16) cryptOneBlock(in, out);
	}

	@Override
	public void cryptOneBlock(DynByteBuf in, DynByteBuf out) {
		int[] T = temp, key = sKey;

		T[0] = in.readInt();T[1] = in.readInt();T[2] = in.readInt();T[3] = in.readInt();

		for(int i = 0; i < 32; i += 4) {
			T[0] ^= sm4_Lt(T[1] ^ T[2] ^ T[3] ^ key[i]);
			T[1] ^= sm4_Lt(T[2] ^ T[3] ^ T[0] ^ key[i+1]);
			T[2] ^= sm4_Lt(T[3] ^ T[0] ^ T[1] ^ key[i+2]);
			T[3] ^= sm4_Lt(T[0] ^ T[1] ^ T[2] ^ key[i+3]);
		}

		out.putInt(T[3]).putInt(T[2]).putInt(T[1]).putInt(T[0]);
	}

	private static final byte[] SBOX = ArrayUtil.unpackB("l%\u001e w4C>\\FWb'\tR|\u0017\u0002&7=jm+`\u0002\u0019;S\u0011''%bAjMr\u0005Q{%>zDigU\u0006Q~]~2FeZH\u0016\u001dI$R\u0016A8sPTW\u001f@T\u0012a{@tgt\foQ6Jq4gCTv\u0007D/\u000439Z\u001c)`dW\u0010&]\u000bjjU=%\b\u0018M6GGE&\u0012 \b1\n\u0006r\bk\u0001\tf=\u0080'(*\u0014\u0007a\u0018\u001fBEe(^,},%AdO\u0017[ `fO}Y#[\u0010\u0003]^S'gBSW\\\u0014\u001a\r\u001fYfHG\u001e|9Fi\u0014\u001c\u0015aa\u000b%;Y6'O8v<4;\u0018>~H\fa@|*en7\u0017k\u0019io`\u0013^x8HyH3]!Hc\u0006Wa\u0016B\u0019c\u0015]lo{.;5\u0003,H\u0017j1E[3uQ3-x@\u001a8 \t(\u000bod\"\u0004\u0010\u0004xY;o\u0014%\bP:??lsh\u0015\t");
	private static final int[] CK = ArrayUtil.unpackI("\u0001\u0002bb)qG+\u0019O\bu36)\\2\u001b/\b<{\f\rJgU\u001bF?n>c3{.P\u0004Po{\u0080\u00011QE1 \u0014\f'D[\n\u0013Q,XMWdNv\u0002E$sjn\u0013X3]q\u0019}wX:dv=@\u0010y\u0019\u001b\u0015\u000eIF\u0014\u0002]}F'\u0015l'\f\"_w?b\u0012:\u0015gB(X.8M\u001f,$Ypz^`(m\u0005\n\t\u0006dc*1g;!S\nv3vIl:\u001f\u0002");
	private static final int[] FK = {0xa3b1bac6,0x56aa3350,0x677d9197,0xb27022dc};

	private static int sm4_Lt(int ia) {
		int b = morph(ia);
		return b ^ IRL(b, 2) ^ IRL(b, 10) ^ IRL(b, 18) ^ IRL(b, 24);
	}
	private static int sm4_iRK(int ia) {
		int b = morph(ia);
		return b ^ IRL(b, 13) ^ IRL(b, 23);
	}
	private static int morph(int i) {
		return ((SBOX[(i>>24)&0xFF]&0xFF)<<24) | ((SBOX[(i>>16)&0xFF]&0xFF)<<16) | ((SBOX[(i>>8)&0xFF]&0xFF)<<8) | (SBOX[i&0xFF]&0xFF);
	}

	private static int IRL(int n, int bit) { return (n << bit) | (n >>> -bit); }
}
