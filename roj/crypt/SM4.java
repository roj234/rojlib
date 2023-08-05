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

import static java.lang.Integer.rotateLeft;

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
	protected boolean isBareBlockCipher() { return true; }
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

	private static final byte[] SBOX =  ArrayUtil.unpackB("l%\36 w4C>\\FWb'\tR|\27\2&7=jm+`\2\31;S\21''%bAjMr\5Q{%>zDigU\6Q~]~2FeZH\26\35I$R\26A8sPTW\37@T\22a{@tgt\foQ6Jq4gCTv\7D/\u000439Z\34)`dW\20&]\13jjU=%\b\30M6GGE&\22 \b1\n\6r\bk\1\tf=\200'(*\24\7a\30\37BEe(^,},%AdO\27[ `fO}Y#[\20\3]^S'gBSW\\\24\32\r\37YfHG\36|9Fi\24\34\25aa\13%;Y6'O8v<4;\30>~H\fa@|*en7\27k\31io`\23^x8HyH3]!Hc\6Wa\26B\31c\25]lo{.;5\3,H\27j1E[3uQ3-x@\u001a8 \t(\13od\"\4\20\4xY;o\24%\bP:??lsh\25\t");
	private static final int[] CK = ArrayUtil.unpackI("\1\2bb)qG+\31O\bu36)\\2\33/\b<{\f\rJgU\33F?n>c3{.P\4Po{\200\u00011QE1 \24\f'D[\n\23Q,XMWdNv\2E$sjn\23X3]q\31}wX:dv=@\20y\31\33\25\16IF\24\2]}F'\25l'\f\"_w?b\22:\25gB(X.8M\37,$Ypz^`(m\5\n\t\6dc*1g;!S\nv3vIl:\37\2");
	private static final int[] FK = {0xa3b1bac6,0x56aa3350,0x677d9197,0xb27022dc};

	private static int sm4_Lt(int ia) {
		int b = morph(ia);
		return b ^ rotateLeft(b, 2) ^ rotateLeft(b, 10) ^ rotateLeft(b, 18) ^ rotateLeft(b, 24);
	}
	private static int sm4_iRK(int ia) {
		int b = morph(ia);
		return b ^ rotateLeft(b, 13) ^ rotateLeft(b, 23);
	}
	private static int morph(int i) {
		return ((SBOX[(i>>24)&0xFF]&0xFF)<<24) | ((SBOX[(i>>16)&0xFF]&0xFF)<<16) | ((SBOX[(i>>8)&0xFF]&0xFF)<<8) | (SBOX[i&0xFF]&0xFF);
	}
}
