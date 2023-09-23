package roj.crypt;

import roj.io.IOUtil;
import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/11/12 0012 15:34
 */
public class AES extends RCipherSpi {
	public static final int AES_BLOCK_SIZE = 16;

	private byte[] lastKey;

	int[] encrypt_key, decrypt_key;
	int limit;

	boolean decrypt;

	public AES() {}
	public AES(AES aes, boolean decrypt) {
		this.encrypt_key = aes.encrypt_key;
		this.decrypt_key = aes.decrypt_key;
		this.limit = aes.limit;
		this.decrypt = decrypt;
	}

	@Override
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
		int i, j;

		ByteList bb = IOUtil.SharedCoder.get().wrap(key);
		i = 0;
		while (bb.isReadable()) tk[i++] = bb.readInt();

		int k = 0;
		for (j = 0; j < KC && k < ROUND_KEY_COUNT; j++, k++) {
			Ke[k] = tk[j];
			Kd[(limit - (k&~3)) | (k&3)] = tk[j];
		}

		int t, ri = 0;
		while (k < ROUND_KEY_COUNT) {
			// extrapolate using phi (the round key evolution function)
			t = tk[KC - 1];
			tk[0] ^=
				(S[(t >>> 16) & 0xFF]) << 24 ^
				(S[(t >>> 8) & 0xFF] & 0xFF) << 16 ^
				(S[t & 0xFF] & 0xFF) << 8 ^
				(S[(t >>> 24)] & 0xFF)^
				rcon[ri++] << 24;
			if (KC != 8)
				for (i = 1, j = 0; i < KC; i++, j++) tk[i] ^= tk[j];
			else {
				for (i = 1, j = 0; i < KC / 2; i++, j++) tk[i] ^= tk[j];
				t = tk[KC / 2 - 1];
				tk[KC / 2] ^=
					(S[t & 0xFF] & 0xFF) ^
					(S[(t >>> 8) & 0xFF] & 0xFF) << 8 ^
					(S[(t >>> 16) & 0xFF] & 0xFF) << 16 ^
					S[(t >>> 24)] << 24;
				for (j = KC / 2, i = j + 1; i < KC; i++, j++) tk[i] ^= tk[j];
			}

			for (j = 0; j < KC && k < ROUND_KEY_COUNT; j++, k++) {
				Ke[k] = tk[j];
				Kd[(limit - (k&~3)) | (k&3)] = tk[j];
			}
		}

		for (int r = 4; r < limit; r++) {
			t = Kd[r];
			Kd[r] = U1[(t >>> 24) & 0xFF] ^
				U2[(t >>> 16) & 0xFF] ^
				U3[(t >>>  8) & 0xFF] ^
				U4[ t  & 0xFF];
		}

		// shift right
		int a = Kd[ROUND_KEY_COUNT-4],b = Kd[ROUND_KEY_COUNT-3];
		int c = Kd[ROUND_KEY_COUNT-2],d = Kd[ROUND_KEY_COUNT-1];
		// noinspection all
		System.arraycopy(Kd, 0, Kd, 4, ROUND_KEY_COUNT-4);
		Kd[0] = a;Kd[1] = b;Kd[2] = c;Kd[3] = d;

		encrypt_key = Ke;
		decrypt_key = Kd;
		lastKey = key.clone();
	}

	@Override
	protected boolean isBareBlockCipher() { return true; }
	@Override
	public int engineGetBlockSize() { return 16; }

	@Override
	public void crypt(DynByteBuf in, DynByteBuf out) throws ShortBufferException {
		if (out.writableBytes() < in.readableBytes()) throw new ShortBufferException();

		while (in.readableBytes() >= AES_BLOCK_SIZE) cryptOneBlock(in, out);
	}
	@Override
	public void cryptOneBlock(DynByteBuf in, DynByteBuf out) {
		if (decrypt) aes_decrypt(decrypt_key, limit, in, out);
		else aes_encrypt(encrypt_key, limit, in, out);
	}
	@Override
	protected void cryptFinal1(DynByteBuf in, DynByteBuf out) throws ShortBufferException, BadPaddingException {
		if (in.isReadable()) throw new BadPaddingException();
	}

	static void aes_encrypt(int[] K, int len, DynByteBuf in, DynByteBuf out) {
		int a = in.readInt() ^ K[0];
		int b = in.readInt() ^ K[1];
		int c = in.readInt() ^ K[2];
		int d = in.readInt() ^ K[3];

		int kOff = 4;
		while (kOff < len) {
			int u, v, w;

			u = T1[(a >>> 24)] ^ T2[(b >>> 16) & 0xFF] ^ T3[(c >>> 8) & 0xFF] ^ T4[d & 0xFF] ^ K[kOff++];
			v = T1[(b >>> 24)] ^ T2[(c >>> 16) & 0xFF] ^ T3[(d >>> 8) & 0xFF] ^ T4[a & 0xFF] ^ K[kOff++];
			w = T1[(c >>> 24)] ^ T2[(d >>> 16) & 0xFF] ^ T3[(a >>> 8) & 0xFF] ^ T4[b & 0xFF] ^ K[kOff++];
			d = T1[(d >>> 24)] ^ T2[(a >>> 16) & 0xFF] ^ T3[(b >>> 8) & 0xFF] ^ T4[c & 0xFF] ^ K[kOff++];

			a = u;
			b = v;
			c = w;
		}

		int v;
		v = (S[a >>> 24         ] & 0xFF) << 24 |
			(S[(b >>> 16) & 0xFF] & 0xFF) << 16 |
			(S[(c >>> 8)  & 0xFF] & 0xFF) << 8  |
			(S[d          & 0xFF] & 0xFF);
		out.putInt(v ^ K[kOff++]);

		v = (S[b >>> 24         ] & 0xFF) << 24 |
			(S[(c >>> 16) & 0xFF] & 0xFF) << 16 |
			(S[(d >>> 8)  & 0xFF] & 0xFF) << 8  |
			(S[a          & 0xFF] & 0xFF);
		out.putInt(v ^ K[kOff++]);

		v = (S[c >>> 24         ] & 0xFF) << 24 |
			(S[(d >>> 16) & 0xFF] & 0xFF) << 16 |
			(S[(a >>> 8)  & 0xFF] & 0xFF) << 8  |
			(S[b          & 0xFF] & 0xFF);
		out.putInt(v ^ K[kOff++]);

		v = (S[d >>> 24         ] & 0xFF) << 24 |
			(S[(a >>> 16) & 0xFF] & 0xFF) << 16 |
			(S[(b >>> 8)  & 0xFF] & 0xFF) << 8  |
			(S[c          & 0xFF] & 0xFF);
		out.putInt(v ^ K[kOff]);
	}
	static void aes_decrypt(int[] K, int len, DynByteBuf in, DynByteBuf out) {
		int a = in.readInt() ^ K[4];
		int b = in.readInt() ^ K[5];
		int c = in.readInt() ^ K[6];
		int d = in.readInt() ^ K[7];

		int kOff = 8;
		len += 4;
		while (kOff < len) {
			int u, v, w;

			u = T5[(a >>> 24)] ^ T6[(d >>> 16) & 0xFF] ^ T7[(c >>> 8) & 0xFF] ^ T8[b & 0xFF] ^ K[kOff++];
			v = T5[(b >>> 24)] ^ T6[(a >>> 16) & 0xFF] ^ T7[(d >>> 8) & 0xFF] ^ T8[c & 0xFF] ^ K[kOff++];
			w = T5[(c >>> 24)] ^ T6[(b >>> 16) & 0xFF] ^ T7[(a >>> 8) & 0xFF] ^ T8[d & 0xFF] ^ K[kOff++];
			d = T5[(d >>> 24)] ^ T6[(c >>> 16) & 0xFF] ^ T7[(b >>> 8) & 0xFF] ^ T8[a & 0xFF] ^ K[kOff++];

			a = u;
			b = v;
			c = w;
		}

		// last round
		int v;
		v = (Si[a >>> 24         ] & 0xFF) << 24 |
			(Si[(d >>> 16) & 0xFF] & 0xFF) << 16 |
			(Si[(c >>> 8)  & 0xFF] & 0xFF) << 8  |
			(Si[b          & 0xFF] & 0xFF);
		out.putInt(v ^ K[0]);

		v = (Si[b >>> 24         ] & 0xFF) << 24 |
			(Si[(a >>> 16) & 0xFF] & 0xFF) << 16 |
			(Si[(d >>> 8)  & 0xFF] & 0xFF) << 8  |
			(Si[c          & 0xFF] & 0xFF);
		out.putInt(v ^ K[1]);

		v = (Si[c >>> 24         ] & 0xFF) << 24 |
			(Si[(b >>> 16) & 0xFF] & 0xFF) << 16 |
			(Si[(a >>> 8)  & 0xFF] & 0xFF) << 8  |
			(Si[d          & 0xFF] & 0xFF);
		out.putInt(v ^ K[2]);

		v = (Si[d >>> 24         ] & 0xFF) << 24 |
			(Si[(c >>> 16) & 0xFF] & 0xFF) << 16 |
			(Si[(b >>> 8)  & 0xFF] & 0xFF) << 8  |
			(Si[a          & 0xFF] & 0xFF);
		out.putInt(v ^ K[3]);
	}

	private static final byte[] S, Si;
	private static final int[] T1, T2, T3, T4, T5, T6, T7, T8, U1, U2, U3, U4;
	private static final byte[] rcon;
	static {
		// Round constant words
		rcon = ArrayUtil.unpackB("\1AAAAAAAA\7gggcWNN\flld\20\16\30\33[[L\34xvpce\2");

		// S-box and Inverse S-box (S is for Substitution)
		S = ArrayUtil.unpackB("2`\17x`JWpcM\1\27:0~XV^Z)\27&|{-R\177\13oSF0O*\17-\6`|\24\24\16H\200?1j&s}/\36BE+\5dIy2EY\f\33\4EQ\17\30-P3;C13ai7o.)\13$_[g*rLqF\37E\2n\21@\27\26\\,\30?\35SJFG@\"pV?i5jO\13F}APv\2s@))ii\t}K;9{p\27nR\5\"\200zuZQaPY`LR\3}&\36}>3\30$\30\34\3\3Po\tF*\5\"\16o]\6\34fq08a\32\17B%I\31I]b5vG\25G,e=zz\4<7\34V(+.F8TUf>,B\fTaK/\17*WM8$;u\20SxY]*a?[ZJ\1 Y\35b\33Vx\u00197\5<\37q\177\24\2\f(4\17K'di@(\35V\u00158rK\r%\34@t\21N\5\re[\20Y\26\u00181\1");
		Si = ArrayUtil.unpackB("*\3..*An&\u001d0i\13\35{\4tl\177pO\32g\6\34\30\200qt%:\7Ec8^\35[Rx\25\32*Y#\32x]MKCi0W\16\35\t\30)-cGeJ3<\27u%L7\30R\23]`\u00104\23\riM\6[K#t\31^3nS'dB\21Q\177|8\36Sy+G,jrZm\23\"YVA\22LgM\26xs\27\1\\FN\13\7i\f\4i\177)\177\20\u000216|i\r\3\24F\33h*\tF\3P4x\36*@L Oy.\35h\35[Yu\22:vT-\30Fz\34{\4H/~]HyGO\22j(\f\n8nm!v)2?\16\200\13ds0\16S=I\24._\4}ygW_B\177wQ4E\2yt\16E%\21-Jq\17c~AR@k$\34+)\33.s_Tz\37':pQ9\b5n9VvY3\36<Zs\7TMY#sY\22~;<vEo\f%)d+I\"A\1");

		// Transformations for encryption
		T1 = ArrayUtil.unpackI("d\31m;0by}C<Ox=hm|>d@\200\30I\34W6[x^t>`2Ir9V$\1a1)\1A\21\t\20\35h4k+cZ.|h\200@D\34/`/c'kv?82mwN$z-S\25@\3B(2\35O&\2{?`1\177\200lu\26Z\27,\37]:\17He\177\177\20\1.\4.W|\27>'RO`R)`U.?`k\22h\24L{OJ%|z\17(\25[8Aa\27o\\>`\6b\177\200$DmO(/'\nEgT1m7. Ht{\blx|AQ=g2\37i\33\16\fF\16\27Luiz=T(hdr\u00059O\30\rOXYm\35m$\nF'+\13F(qA\21\t\rKryv\23\31G$3h9=\33ya\31\r\13\7z5[C\13\3B\"s}k66\b\2aqJ\21%\23\34\7q\t\5p@cr\20:_`-MO\24Jn\30~KfNv\36/ZyI\23\n\16H19\35z1-\27\36\7BQi]7\16Gf^d:^3[\27L/soB!~j\13&\30Zm<\36T7~7ZC~Zmzf\22%S|oy}4sy_09EqI%_MT*~8\36\17FQ\1\1\1\1\r\u00108[-!\t\5\7\b\20z}\20_7\34\17#m\\.|;GT+~\16fsig>{~Z:\17(\25]R\25Kp'\nEgSaY-;\21]\177>\26<i5\16=0@_+(kV/08x|\f\"I5\37\u00175N'vmd\32N+\22Cb3IS\26\fPu\177@\22\1\21\5\3\4@Px}\7AQ)=\20\4br\t&Phx%^#RdR\25+ \33wH$\200!\t\5\7\1\f\20HcHz\25K[\"Oh8H\2aqIy~?Q$\17z=p^wl7\7`[n\36)#\n\6G!\t\5\7\u000f0\200\177\33\177}\u007f1v\200&S7a:]k11\r\7\6\5b\31MlDw<\6|s~@b\33fs{\25\"\tEg\fCr:g(Ec\26k[> f}@ Q(Ru{He\32\rKfj;^tMD\22J0Mt:f9\7\4\3A\32Aa4\ns> RRx\34H{\21E#4\26\6#Tyx\21I+b9E#\b\r$\22Z-@<]*6o\30\16\32!)\25\37*|ntgy_09CaY-<.nwon`\4A<3\rG&4Qu;(\6\2!Q{%J%wbA1\31\25I\23\n\16LCr:ePqY&nx(T8\21vKh@\tc2*H\32\rGQ2Kf5N \24I8z\u001f0\31_XPh\32#z\rC\16]8\34W<'l7o\2Gd2L\17X+eO\24Jn\23'T*q7\16GfSYW,?_@(Q\20Pv;E]T\26L0{\37P)s\37^/uE\2\1Ab`;^6?\bDc\21K\23J.vb9]s\35\b\4C#_N'y]wL'\u001f0Gd\25:?H!H\"ox0ODRj\35 \bds\rZ\27LoY8\\oq\34\fFbByU+\fa9\35\23\bby}C9n7]'2MgV%\n\5Ga\r\4\2B?\u00808Y\3\35\b\4C-\24\6D$6\16'V~:/X}[8\34OA0\7D%4\35\17\u00061;\17H%s={>:my=\24H0ry\nKt\nFME\22\tM{'L'x*mw/\1=;\36\n\32f\23K:78\34\\\20\4br\t,\bDeZ\37P%B\bh4J\33SV,\200)\13\6\bF\30@`>\1rIe>4\"Q\177\2\31M'\1\33\7D#w.\177\200[lz]d\r\22\5Cd5\16\7Fc\5B!qf\32Mga[\27L/rq=\37\22>m\27\r^\")U\177\u001c8<_YY\27\f\1\1");
		T2 = ArrayUtil.unpackI("SrM7\35\24q}?'>h<_\34w>_b`\200Lf>l\33n<\17z_p+%9]+BA1\31\1a!\t\7TO4ZpV2-W\32t\200`g\26X0Xt\u00146;]lYw<\u00122}W+; B!I\tO(\24\b~ 0R0@v{vmL\26O'\35H$C\200@\bDYBWl-|\37T*~0iU/S\27`0`ItJh^(%S&]H\24J8\34a1\31(._o\35q\200@[rw(\u00146\24\5c3jY7\34\u00110dz}\6v|~jy\u001f4\31]5\16\7P#GL&\u001b5=_)$trye}(\fFh,m7\u000b7\22Ec@\26\6#Qa!\t\5*&9}<\26\r$\22XT]\37\rQ1\r\7\25\24=[-\20\6\2!\\*?6\33\5DAq:YI\23\n'd9\5\1|`r9Emp0Wj(\ne}l\177f3P{OX)m%\n\5hDY\35\16iY\27\f\6d!i5.\34\7d<\27r]ox.\f&Xn8!Q>UF\23J\33w\36Om\34?\\.O?mw8[IS* 8=?\34F=0\30fc9%\24l'*Un\fOH#\1\1\1\1\3g\b\\n1\21\5\3\1\200H}\u007f3\20\34\16H\\7.Wxn$*UGGsz>L\37~?&]H\24O{)K&6\24\5c4R1-\27\n)/@\37l^u\33\3W\30`psTv+Q\\\\|~r1e\33\u00100\33'T+W2Mh\25\tb1]}*\13F\t;@ I\31\t\3\2!@h|\200b!)\25\tHBqz;\23ht\177\33/R)ziK\26\20z<$Rq\21\5\3\3\25\6HdvT}K&=\21h4UDAq9\3=?`/~H=_1/|6Zl0n7M5\22\5C1\21\5\3\2X\30\200\200\b@?@\u001c7\200Sj\24\21\35o5)\31\7\4\7S1M'0b|\36O\16z?`R\u000e3z?3\21E#\17&b9^0\24c2\37&.\37P\3\177 Pe<i{>W3\rG(\37u^/Kg\"Ig,g:]u\r\4\2B\31\ra1\36\rz\37P@i|Nd\32\t#\22 KCR+W<I%\u00111]#\22KG\22IcO ^oj[x\fBqQ\25\13\u001f5~w|F=0\30DR1-\27wWw|4_pBa,\32\7$\23:i;\36\bCAQ,8\23%S\"!a\31\rm%\n\5O&b9]/hy-\24;|Tj|i;f3NE2\31V\4MG$%\31f3T?PJeF}P\30JL,htQr=G\"3o\34Nw~T6\\\r\1d2W&H,Vj(\nex\2\24*Un\34\7d4v-,\26A\200 Ti&h{^+\177*KfH>\20(X&\20/X\7\3\1A$+p^/R\20\4bqp&\n%X\23q]/\23\17\4BhF0'T2o<&R$\30d2E=`$Q}Qx<Zh\"iu\21PDr\177w-L&x\r\34nw\r\16FcqQ}+\26\21q\35\17\5\24q}?c\u001d7\\.,\31g47\23\5C!\13\7\2Aa @\\m\23\17\4Bk\37\nCb0[GT0g]X,u\16\34Ng#\30D\"L\nO\b\3(\36\b$\\J\37>\37\u001d7=\37\tPXy}-f:Eag#\tE8>\24&SqUw<\31I\37\36\17TMsJ&Y\\\34NIHBq{%\26Dbe\rP(SJDtZp~*+V=\25\6\3DkL`pda9e4qZQi1\1M'\23\30\16\4\"^T\27\200@\31v}o7\33\tC\"/\33\7D$\b\3!Q7\3Mg3x.\f&R\ty\37\20f_w\f\bsQU+6N\\^mu-\f\1\1");
		T3 = ArrayUtil.unpackI("2j9g\34s\ny?\36t\37t^x\16|\37\177!p\200el_vN7~H=pcV\23\35*B!a\31\1!1\21\6P*h\32f8kYX\177\rz\200n<\13lXVzJ[\\[6m<3IY\177+\6\36\20aZ\25\5(\23~D\177P`QX`{-{w&K \24\17$}\2@`C\\m!l;G>P*#\177Xu+\200*\f0O0e:f\24oTS\35So$L\1\\Nq\27}\24Wp~\u000f9@Z\u001e9|\24\24\33JC2Z5m\34\20i\30r\200o\3{~zE}\20\u00195/\33\7K0R$&sN\33\u001f0E\22zy]3?\24H1tVw\7\26\34\tc\26 KCQ!1\21\5dUS]:\16KG\22qljo\r1)\31\7\23k\n_-\6\bCAZVU`\33\4C\"a9Im%\n!\u00142]\4E>py^378W(5TE|\u00176\u00803;h~()%7\23\5atbm\rYu-\f\4#rQ5\34\27ND7vL9o.<WFV\4w\\Q\25_k#IwN<\17{g\16`.4h 73Ln%*rP\\_\32>c_\30b\23r\35\22(vT\25{\27Fh#\1\1\1\1\17j4\4n\21\31\t\3\bq@d\177-:\bNF8n\\\27n,wRVL$$:<wfP?\35So$K,>\25&\24\33JC22iY\27\32uU\30 Q6o{\17z,\fpV:*{Xm.n~QyY3\16\34XN\24'6,\31h\6K\u00051U/?\25F}E\36 I\t\r\5\2 q t~\"qQ\25\bE$az ^\n4{H\16\30))}u&\16\20}^RQ\31\t\3\3 \13\3ds+j\177&\36_\t4TC\"a9{B\37 .t?d_.Y\30>\\5vXwE\27\33\tC\21\31\t\3\20ylL\200zD``\37J\\@j4*I\u000f5\31\25\r\4\u00034*\31(m\30q~F\200\7}`LiGZ;\24\32\t#\6h\23q`\tXJr\25\200\23WO\177B@\20dk\36u>3,\32\7#xP;/G&4\21fh\26t\35m\13\7\2B\2M\u00071\25\177G=Po u>b\nME\22\13Pf\"+\",\36e\22\t\31/\21Gf$\tor(\20o]5n<AQy)\u000b8P\33?z>c_\30B2iY\30\\<,<?\2p8a\32\26MD\22j\35u\36\3Db!*\24\\J\23!aQ1\r%7\23\5Fh\23q]b\u00184}\27N^>jl\36u\u001e2F'c\31S\33Bg$\26S\r3_\"`(e=c\177(P\35fVts\t9_!oZ8\16n\\?j\\\16G\u00012^,\23dV(5TEv(AJU\\\27ND2.{W\26_A@Pjk\23t~'.@\25f>$_HV<SHX\3\4\2\1#vV8oP\tHBq&8SEStJ9/\17\n\b\2f\34cXT.\31x\36T\rRLr^\3\u001f0R^?)<X%tQu\20I(b{0<\27&p<G\16w\30\7\7cr))?\25qI9\17\4s\ny?[r\17\34,\33VM4\23\34\n\3!\7\6\4\1\177a\20`m\17\n\b\2g\16\20\5b\33Xn$+`t/,o;\7Ng\16\22\fbY\26Eh\3\36\24ODZveP\37qO\34\37\20a(l}'\u00173]a#4\22\5.\34_JTZ9+<\31u%\20\17K*g:%om.NHE$a{\20\23\13b~\23\7(TO%bzf0\177UV\25\37\13\3G~v&pd\22q\u001d3Dy-i2\31\1'\23\16\fGB\\\177jL@t\r;\u007f3\f\16\5\"\33\30\16\4\"\4DB\u00114\34\2'3.<WFQyE=\20Y3p<\3Tz)+/{gnm-;\27\1\1");
		T4 = ArrayUtil.unpackI("2Yu]4rz\5}\36ozP:w|G~_0\u00118\177l6p;g|?d_cr+J\nAaQ1\1!\21\31\nOhUtF3\\v.\177\200\7=~?^F6Vk}elZn\33w3Z%-?\6\3OHz\35K\3\23~?b\u00800Xi,p-W><\23\36\20JH=\37\1`o\\.w\21;N$\37h#R@,{~@UFO(\30s\36\23Jx*]O*8\24\2A.gw|?\nl~\177H\35\32\35O]>\24\nN%bYm[7\20hu\f|px\2>:Mc?\u00075\33\30\16\13.\30iRsz'N\20Hc\t}]/\32 \f2Y:kg\24\13NE\26\13Pf!!\21\31\tdrk**\rGf$1y6uu1\31\25\r\23j6\5o\6\3Db*Ukk0\4Bb\21qI%7\23!\21\nYpFc\37x~?Z\34\33(\24[*l\26L\33\200;^4\177Q%\23\34\n!q:quY-;\27\4\"R9i\34\16L'gt;f].\27^l&\3B|.UK06\21w<'^[n4\7p4ZtPSJ&wSryhnj=_r0\"\21J9N'T{j[\36\f#s\1\1\1\1\17p5ZB\21\t\r\5\bty rm7\35Df7\\wnN'V|*LfRR\\v|3h\35O*8\23*\26_K\24\nN%b1Yu-\32}{+\fQi\33x?\200=VFV+]U\200pw\27wQi=-\32\33NlgG4\33VN\6Cf\3\25+\30 \13}\177#\17I\t\5\7\3 py\20z!Qy)\bDc\22r PoE[F$GL)U?;\26\17H\177/Q\t\r\5\3 \20F\u00023*\u00165\200\36Op\5\24Bb\21q{~!P\16sz`2nWm\f`6[;le\23\f\16\5\21\t\r\5\20\200}6fz}bpoL%n`t:Ue\5\31\r\13\7\u00032\32UNmw\fy6{\200D?Lfu$+\22\nME\6ctJ<\nE,eu{@J+\177@!`Djv\17{3\32\26MCv<h^\7$\23ZJgtKzM\7\6\4\2\2Ag\4\25{@$\37o8\20{\32\tEg#\13F(sS\"\21VOr\tE\r\27G$3ROx9TH]/\u001b7YQ)=\u00158\\hN\36=_r0\u00021Yu.\\n^V_\b\1x\\\32\rKg\"iuO;\3B\"qR\23JneAa1)\31%\23\34\n\6ctJ9b1LZ\177P'o_l\26O{\16EcT2\23\32\16!t\26Kj\7\37(\21pT=_2@\30 O3ks\32\5\35-o8-\\NWn`6\16Gd\1\36/VJ2(\24[*f'Ta%\\\16L'b-W~,\37P! hkv\n:w,\27`K>\37Rp&;^j$C\2\2ACv;k\\p\bE$a&\23\\j#r:e]\17\b\5DF\33Nr,n\27M<P\16G)f~\17B\20\30^o`\25\30$S:i\20He\24s.\30^L08^d\7\30\fD\u00042)U\25\37q9%\35\4rz\5}[n9H\f\32N+g\23\n\16EA\7\4\3B_p1\bo\17\b\5DG\f\7HC\33N,wS^0zX/8\36\4'\16\7IFy\35\13c3\36\17Jh*u{s(qy(\16Pdq\24vg\24\f\32-#\22\32IN\27Np&Zm]\26\31u;\23\bK&\25t\35o87\27HDc\22s\20\bJ\6>\37J\4\24Oh\u00131v+X\200+\25\13\20\6\7\200?{Sd\22Iy\17D\"}\u00172\31M\1\23\16\7Fd,~\u00805ft:G\36;\n\6GC\33\16\fGB\3BbaT\32NAS.\27^l!y=#\37Y-\u001a8[R*}U/x>45-\27\36\1\1");

		// Transformations for decryption
		T5 = ArrayUtil.unpackI(")~\25v\4z\3f*GC{'\ru(0&H;\\0\27 OR?\33hj2,&ya:\32\1b{+l/gp[\22M<%?Q\u00131KPsv\200M*,\30X\24\16)I\6VF$HxW\26S%L;\16Zi_Q;1^\u00801\35\35\32>k\3AT\37\2\u00156\16\30R[{@O\31\b\u00100zcZ\25s,@7_^:+J4[k0Q3kbi\"jS=\7J'\36Je\22\17]\25&VuH\u001f0\nKa}l\24o/\36n|CP\\=\22\13i`\23!W\32P]rkidpSD\17)id\3LU'7\4\n'\200#m-H@\3wlX\" i\16\1:z\5\13sH\3\"QYHR@RMT=mDUP@F_WtjIo%Y\fFd\20dkw3WW+Z;f\1:?lC\2bYX]l'8\5*'\3E e$`j7!\21\16V;wFQ&e,\37\35\26jp\34%Lg\bz=\ngP\13Cfn>\32Q1\27~ViN\r\"\177\23N\177F\16\6f\35wFt+i\1YX\5f%|;oQ]\20Y:!\31\36{Szd \u00040.b\3E}\"F?3ni\31|^ \u00026ep\33{GIV\22\\,H\t^\3B\1n$>AQ\13\200d\32HmJW_{3yK\2\bM4w4h>DQC_Br\31]$O\32.O\20\35H<8\"?\3Ixc\t j}\"\4mI\1\1\1\1\u00031\t5\re,wS\4b\fCYm.\35Jpi<\200|\bb(\u00062w^V\20\16FTJ\35\25\20mZ\16\6f\31D\34.V\33\23\"Y];\7\3M|\rN0h\bn\36j7I8\34IhQ\r\7\26\37bo\t\25&T^\27j\17\5D\"8\13(;\u00061\25\3X\25y#q\21c\"Y];\17\5D\"@\u00150\20.\27nV\fIQ>*e\26\177\22M\27_v\4T\36jOwx$@Y `9\5N /\35_\\c\22M<cW\u00808rS\27D\25^Z3\37\34:7w\200\16\fH\24cdlM<MS\nH\6\t\5ss\23\3\nG\tI\21UR\22|S^P@\13xee\22dK5\27iw=0&x\27$\bM\34\7*<\17}\20\20!,ZFNKLCt\32\tf\n\20S Se\22+ Ig\23Bq GKhj41#\32e\36y;&\36HmO\33\35\r4\26#\200'\33AZ[NvAtuXWyR[\\dEd~7\200%\27\17T_#Br\23\7[L}en)\177$\31_iiP\6\21m/\36\3rho_B1v\200-~<\1`\33;\n\34r`V\27k:s)KhIW'(2\1b|(u(\r7wmx<>t%hA&]Z\r>\36JV]\4\4(gV\u000f5V]fV@}hr\7zP\5<c_H\33vhNw*'z\\\36kPC;CNBzW\31j\27+z)\177$\31rUZ\"Al#41\17Evqp}B3Uo\5D\"1\32j|\u00020E\n\31&\21>NX^\200N)\4Cz\20Y_w'vRU ?aNg+JV(\23-\5ph[\u001c0\16\31kE\7y\23a\177qG3U0zk|U\5\1d\7Vpk\17u:\177b5\n;gh\17WS._J&j\t\26G7oZ\17\24N6m\31b_C\r>\27@\2%<W\24\37#:kJ ^81s'_\ttLn>\22w\24et&`-V?(\32}1\25h\37/=:_\177T|t>&\200wU\\pP.r$b\n\\D3V\200\35\7sic\20H\3\"QZCRQ\fr1x\7s_9EQb!yJF\200bZ+\5t)\1]\"\1fP>Y[:\24G#\\\4\21>sqG\17Uf79\23\16FdT\"9,A\1");
		T6 = ArrayUtil.unpackI(")\25?K;N}B3qd\">\23-;\24XZ4^.Xr\20h)[^4uYJS}1\33UA1~>VX47$\tg\36E`)\n\32}(z;~?\25VLA\nGU%?kcRS<l\13jO&^\7t\u00050)\36b/\200Y\1\27\r_v\n!*P\6\17\33GLrN> hO\4HXsR-K:l`\\0.UV%Z\27v\30i\37N1u\21K*\37\4&\n\17es\16(/\13\23y{$P\27]f1?oJx\30\16[~b(F\177\tF6NJ\21,\27Ho9u\u00192xj)\30\u0015521&k\24\33\26ET@y\27\27$_\n<6lDPu\7B\25}C\u00066DB\21i\rdi`m\37*_7.k(`aH,:u}(\23-\5XrHrsT\32,,\4m^3A\r`6b'Qm,oLT\34C\37$\2#\u00103\22pu\\Q\t\7k/<#i\22/\26P\17%UxNTbt\4}U\25t(FN3w_N)\31\f?\20u'G\25,\n'\200('CsOB#zV'!-,CvS>^4I/\bmV\21\r\17y\32=rPU8WqDs?\21cHZ7u\16/oPAU38N>[e+Ii\26dE/\\aA7T\177a)\6%BMdx\30l0>\35e&\1D<Z|\32vwbi\"#\1yM-rh\rW|8O$]HQ`\2/Lr\5\20e\177\21Bq\1\1\1\1!b\31\5\32\u00113\26|6BqFaO7\27O0`u\36\200,\4qTAy|/kJgcjfI\13\bw%\27CsNRNWkDR\21m/YD\2'9?'XtuWOu[=\34Ne*y\7\4\f#1x\5\7KjoL\f\b\3\"Q,F\24^:9\13\2*\7=\u00129\4R\21m/\f\b\3\"[pK\30H]L7kG!)\37Ub+\200\tf\u001a0;Bx?uh<~R`m\n\200]\3'_\30\u000f0/\26\tg\36N\f@\\zn\f\"K<MZ\20\ri\\<@G\36dJrf6g\36aB\5dCQ\3::\tB\5d\u00050Y+)Jyj/hR\16<s37rf\33\13-<\37\30}|L\22DY\16D\25[\b?\bGm\26mcjN&b:~\u00053EB\n\20j32\26\20e16!y\20|\u00064uZp\22\rs\r=\36\23Oaw(\16\20{\32KR\16T\16!0 '{a&\13,l='n.rk\"\177\\@s\f\b*i6!yJ'n&\u007f2EU@\22Y055(iI7\30\6r9tx{aY;~{?^A \16\36\5OSpkL7=z\25%<e,\24\u001b9A1~8;\24G\34p7<^C:S4dio-G\1\37ekp)BTt'0\33+o@+`\u007f1!D=hz^r0$4;tgzeT=nUv(b\u001e2'a}XM5L\22EU@\22M\31k-T\u00016R\32Gx#;z'\177!Z,\b\3\"Q\13Mu~C,c\5M>i\37gi\35\200gU\6r=Hn\16<\24;ek\20`1+4\26%o\200J\27\u00039tn\16U7M6#\30\r\n1?\200$\32+\21%v>k/A2D,Pv\b;\f`1[\u0006644H+*\27p%4uE\13b\u001c8-HG'[w\fip\"\7$L AS\24l\nP\36mv%O6\\Y:\37p\5:f\37\37I|\13g:SpPk`\24Ns\31\u000b4Xx\37\35pk*~zV[\200|+\u000b8hW}\32qEna:+\200M~:52\6DB\21i`b)i\b\21Y<D\u00070\35#--Q=%Q@qmVc:U\1<aA3h\35m.\35J\4\22.B1_z9$D+3\\\36\n\7cr\6Q]\1\1");
		T7 = ArrayUtil.unpackI("TU\13 $\26'\177!j\u00192Q^>\27\36\nn=ZoWFyHtVF/Z{\2ej? j+!\31\34?klZn\22E4\nC0U\6X\177\24}]_`\13+#!\5d.\17 62\27J\36vE7h\23oBjC\30VAqX@h)\f\u00070y\5Q\25e_H\16$?9g_N@h\2dtJ)W%{vpnVOk+SBL;Lr\b'Y;\33&\25P\4\21EH32\27TX\5z=>\22dt/3Y9x%|K?n?ql#\200\5#YgeI\b,$x\35K\r\31|t\r\fK\33\32Y\23v\f~\13c*^}\f\f\23^\5^[u\2h{\3,K?\"\7CbaI\177G2u,3\20\25p?\27v\24p'$V]a/\24J\30\20,yd{Z*MV\25\2w/_\t\u00070[_t)7\25\22&jNq\200\22AR&Z\txwVi\5\4B\30\36R1qX\13hn\23+<hfqzB}+\13:TugZ<,wU\r\u00062\b{\24({\26ET\25tT\":,!R=}\24\21\27\26l;j\37obe\30\4xkI\7\5}\r_9E\13\34l;\26z \t!dm\\;\6X8(lk\32\34gGn3\26#u\13rc6\16q!\31,\u00801\25 3!g2jLvXU\u001f3\23AP\36m~K\13|1u#r\1=&79tG>n\\h\21\13$i0A\200&yC\u00103@\t!\1\1\1\1\"Q1M\4[I\32\13o\13ay#s(\34\f0\200p{\17\35\26By/U=>X\17%t283e\6\4uc\f\"9Uigl3riI74m\"AX\35 \24,f[,(;$\37\16gyU}\4\1!R\31<E\\&5x\16\6DB\26i\26cJK]]\6\4AD\37\tCriI7\16\6DB\35>8f\fU/&\\6(\21\25\20\u00071V@E\17MX^<\\`;3a\177ips5\200o\2{p\fH\22p\13E4 GF`mSwFQyng-H}5.^`\f\17reo3[t\r\25!C2II\2\35]#!C2EHm\26\25>}5X4\21G^zQ\\9sJ>\27\36P\r\37>fJ&m\7b]>\4`\3\u00177\13w<MgSq%?C\32$\21EHud\31KHq\177\33Q=\6NCZ|\21xIG5w\37\17Jiq<\24F\f~\rf\3gjGS\4PT><cF\26v\17\u00147W|~\21\200.Oz\6DUI\33Q=4\u00147S~\rc+ C=\30[\u001c9u%\34\20;y]:X~1-\35\3~ /epGOA\\*8v#,\37=L\32\36s\26Hn\35!\u00192\\^\nfnx\\\36_\2\35j\u00192u8\u00177q\u001036\33U!jwt\30N\26t V0\177=\21\"_;\35oyW8Z^:gms*_\nk;TxgYT1Z,g\33\"\rc+ f\7\r6\26NA\33iXD<R\36KT@\21.\6DB\21m\6';=b\26r\u00037_u\u00102!\17@t?cy_$WG^J\\\u00036\bp'V\32KQ\24@eL.=:wG\21\34'\33D|G\5YR@RM_Q\23;_\33X!\31dRh{DQ&pY-;[ZZ{&\25L7W\32{#\u00059N\\W1d\24.92u8QF\22fPaz\nvEe\177w;TJ\33nm\u001f08C\35YP\20%?~4\35j\35hv0L\35z\r\6\7||P\20Nv\25\177{Sn@~8F\34too\ry#=q\35V@\t?][\7CbaIA0qU=\34I-\36\23D\30O\23'\27)\37&) y5\3r\35k\27>q!\32eO7\27M\rBIWC\u00190=^ZbV\32\30\17ED2/Ci\1\1");
		T8 = ArrayUtil.unpackI("{*k\6\13\6KT@\6uM\31iO_L\17V7_-x\36#}$\200ScX-rAs5ZDuV\21\36N`68\31wIc\1%b\30lfl\200\nsW00F\33R\21\u00034\13H\20[m,%O{u\u001c4J>!ub\f\177a9,S|U\6D'=\3)\13\u001b0$Gu\200\u001d4/\37`tAs*eU+n>;xv\23h6\26`!f^$QDT-9\16\23K(\24I#$Y)L*l\17=_\37FBzX\32]]<S@\6 7`#\26R@AB-43:dVR|`&\7\r2RG\6f)Mm\n;N\177F2\32\17\177\6FX/C/q\33\1t}\t\26f \25D\"1q#\200$\31\177zZ\bK\37\200\f;JhT\22kj1\30\ne HV}6.-UgvK\1|\26X\5\4\30r0:U\31o\tSuf\t@Ib@SmE1\34+u\3\f!LOo=9,F\u001f7J\26\35\20sy=n\37\26\6\36[{4-Q.|+\7\33\31D~\16\34~\13c\fK:jTgVQ)R/\nI\fwv^5I 1s\f1<v%\4G?\u00070\34C\6\16uD\13}P\\Q2w-?\3l\\_6v\rN+$7Z\17\22;\u00069B[Gy\22!\26\200Y\24\20Z\u00114>uf{e\3\20\32\nmhOw@\"\6>Y#29A\u001d3\\\35:z\17wns}\6\22u\25\21@S}C\bZ A\1\1\1\1!\21i\31%Xn%\rC\30\u00061=[:\24NAx\200x~CO\13a~<+\37\37L(\23:Y Z3\3LK2\6Q\\+54429u%\6\32w\21c`O\20J|Sn\26S8\22P\7y\r+?\2]\21)M\30;.S[\n\7CbeOu\13r)\6//\1Fa\"P\u000429u%\n\7Cbi_\37\\s\\+\30\23i{TI\13=$\31+^k\b',t\36np]\u00801@51\n\33@x:>8Fd\31xF#?pd#n\7*<#e=7t\30n\177\33\27o(FH9\31x\32.<\17\13\21\"&e%\1P\r\22\21\"\n#$w\f<\37\177\u001b0JI$/\25i.]=y_L\17mG\20\37s\rSw\u00049\37\37Bp4\f\34\u00068\6g4*K\23 \"\20&I#$\200\22M&$a@\16)\20Sgb-4I<e%K<\20\b\u001d59\36O+F\177G6\u000245dl\2hjP.r#L8H\n\\+n\177I@\36(=Cdc%\16)\30zJ\\*}G2\26\22R\37\fnY];\23\4P^=/bl\177Y\23w\2?Pu\u00138d(+.U\\eR\26P -MOz\2DwO\21O\31noBow|nJP\1O63\31{\34T,9\bYPN+\21:,:Lg\200zPkVt\37\t\21F>\u000f8@P\34moNt7:\26 \5v\36,\4t-*S-Vt\n}G2\26*3D\7\33Ega\16*lb^j\3f*`J\7CbaTw\3T\31\u00131Ky|\u001c0;\b\33Q\b s 2=/Nl$/o~B\33DV\24+MeY\n`s57_\35zV\t\16T\6B~d\3f)`ifx)\n\36G\16,Q\r\36it~\3i\23xnO\36.-|>\23K%\21,\r~\u001e3\35'nlY2JV\5\31{\34\177\3Ise'=E{v\23@<\35beN7rh\30\\b$m(HWL?ZO=O4{U*O=G\31t>~hxg{K@n*7`\37\\cN{\u00148\7=,\u007f9\17*RE /%D\"1r$!\30y\"o\16e\27r\n\"Lbr\24\f\25\u00043U\20\177Q\u00029O\"L\37y\u00125s(\34\u00067\7!efb\r\30ZKmqk\\\fH##qX\"\1\1");

		// Transformations for decryption key expansion
		U1 = ArrayUtil.unpackI("\1\1\1\1\u00019\23\16\6H\3\"QY%\34\fH(\3\"QY7\27O%s\"Y];\26\20e4\fB\21i- I\27+NY[:\24M&\34~\13I7\30\17E4\26#\200+ Ig\23joL5y\23\16\6D^\32oo\200I\27+NsFrv^FTJ\35l0>\32?\23N\177F3V\200\35\7\"Y];\24n\16XH\rf)`i\27\17`vU@\22M&\34l\2hn\36j7KvhNw<4^nxV\32.O\r:'CneI\26G8\r@\20,|#i\23t\177\16KQ?\21%b\26,:ue;,l=)\\Y:\24.fRE %k\u007f9?;_v\2\22u0u4\2'=\3$^.XL\33iMm\2\37sroK7\bgY\b\u00100zbY3LY OR?\22\rR\22{&ya:\33\30U\17M\26\177\22M\u00164y\13$O=:_\177~h\17WG\177U\\*1x\5\13\u00166mn|)pw\bwce<y\30wllX\26*\27<*[n*REQ\t=e\36\23Odx(a.<'8\5*9\23a\177rP\23EW?)id\3vEhIPe\21,K>a\u00135~\34g_RI?~B\33R{*k\6\200wU\\;qR\27Tp\30\u00051[;\n\34rPZP\36h[wU=4Tw\3S\16t\37\t\30n9!\31\27\26b\32\3q?be4\31~J\3z\20Y_w'vRXC\u00148\u00076\30zJ\\\22.BI\24N\37\26\6\1axkK(`co]:>8A1\27~V\5\4\27>qi0%bF\4kO!}\"F?'\3E e#\32e\36sbk<eL8!dmd)\f\34#Zrb/\nG\tI\26pJI$!y\20d,\30-Q\b_9EQh\33+o3{\24G\34<v\b;\35\177Itfr_YWJ\36\6b/Cb)i\6\u007f3UN+\21>NX^ fl\200\f`w\4CT|t>(O$^\\<q=>\4\u00186ug[{@O\31d%Z,h{n\177I.\\+\30\23\36\177L3\5a\21i\31\37\24\fE\u00063*eU8\34IhU\30a*\u00100;BjLl9!RZZdVUf1?6fk\24\34\3\13K\23 25\32&nR\33\31D~zv_\25gJP\1O\r_v\2;#\u00062PO\32.O\37Q\u00131L|\6Q&jW^D\rK8NI=\34\13|N#\32H5GFt+i\26PScX?qTwl\b\"\6>v(b\36(\23-\5p~RR\37\fr\5\u00103;,Zxr]Z\r\37=v%Poa)\6/,j\u00102\37\36\nlb^MVVKN\6f\31CC2b#\5d+\21\u001c0\u001d4q3Br\23\7CB~L\21\t\7k\34cbo\16e\31\u000b4P\24\u00120[I\37\t]\5\u001a9tx0\t\7\20SXZ\33\\A=*\36i\26qE,#\200u\7A\35k0Q3okp\17\u00143\26JJp\16&K\r\24N\27~4\23nuK)\7\4\13PH3:\5&\24nl;k`\u007f4{reyYwoprQ\r\6\32w\21\21\16V;\t\7\20[9y#qW\16\6f\35uu(0&F\4c'\27'\33R\21\bc\t j:\23a/\24\2!\26\200\\L\22HR\tg\36r*'z\\\35Y;\t;64uZY\34i\"HRgQ\u00029Kw241L;\16Zby*b-\2G\16,R\35]EA\17hFu[ Se\22\23=\7J&8|@\16\13`\24M\177\200gU\2h\17\22;\u00062xj\"D8-H\nvg\36g,39iq:3\37\34:F\u00163{\177yXRgqH\35_pM\20sy@\"p\200oOz[%V\30\6g4<6lQL,\13H\20PX1\fuF*\16[ajzV#\33GLA\1");
		U2 = ArrayUtil.unpackI("\1\1\1\1\1-\35\n\7FDB\21i;\23\16FfDB\21i(\34\f(\24R\21m/\31KHs\33baI5\25pe\f\26\35m.\35IW\23N\177u%\34\fH{\32KR2\26\20e4&5x&m\35\n\7CxoMx5pe\f\26.z#yzgcjeLvX_M,\n'\200!:+\200PR\21m/\35:wGl\177G3U0-\f\b0c+ Ig?NvAuWOu\\4;tgp>ZowqkMWgo\35T\"43%\13c`G HX\36R5\n\4\200\7f*0\t\u00131$V]{3F\26v_\33nm\35J{sic\4s6@\35\n\u001e0;D\31{\30{\23AT\37\rZoWla\u000e5'7uP:9vF\34\4tO\4HX~Am\32&r\20h)`QG)IJS}1\35b\fk\b\"+\200\tg\35Z}\6\30x\37\35p5?tH+L@+.R\31<C\b[[w7\u008058|\4B23\36~<|6v[KUL\36\rn7Ug#)\5\37\37\17J(,\\Tq\27LT\34C\30\r\n1@4hJ#)\30\u001552E{c4eTs\t\26h\177q\n\32\rNt0*U ?aQ)~\25v[\200|+6\u001e9)L\6xLC \16\36\5NohmhDdn<+\26\32j|\1B\7zP\2Lw]\22I\f\13qO\"9 1I\32M?fr=Hn\16<\24;i4b\n\\N[L}eBIWai*gP\13UA1<wv\24pr=/\35_^)\31\f?p\3\2L\37\r5\30S3\3Bv(s?\21c_$\2#\20p\22\rs\17\"1v\36\37f\\Q2[2U\6Kr-yqB\5d\5#\33xee\33Q=\bq>LW)\u00070\35#,\30N\u00168\34^\nd\16h{D^\20\20%:sJp-,%\27CqX0qU5\3T\32+'~i\37glyPsv}\26p|\2k*~z^\\h\22oi\36y\37\u001f6L[{2N> g_2S-UD~7\200:\27nV\f\22\17\200&BC1\t5!\20\nFf#Z\25s=\34Ne){\fq\25MX^!v\16v]\21.-mrl/3Y \31\23v\nN|\6&\n\17i[\rS iN\r\"'}{p\5t%hA\f\u00070;M~\22\3Yrh\rWe`)\n\31/~Ci\31el/bDf\34gek\16F>e2\rd[B#zV6;hj27`9*|nDQCk;TqO\200J\27\u00031_iiP\24yC\b`\16\26m|to-G\17w;S(sq\25\3RruHYNoEvqUg+ke\27CsM\21\"\31qR[2V\t\4\30O\32y6!yJ\32\2!\177f_\5\u00046\17\"1x\7z\r\6\32eBIXn\"P\5/\3y]:|V%\4\bi\4mN.b/\25Ou\30y#\26Rh{\4!\27v\30i\32\u001c68H\17z\13eebGSf\f\32gL?SJ7{%}\4\2F#$Z\35DWJwv`V0\200\32H9s=.\f88z2\7\3M|Q\t\7kTE\4\bn1=\u00129('CsO-;\24XR3Br\24\1\24\16)OLr\5\20r\35J1\30~AQ\13~\16fId\13E4\17zeT=nT-\36\5\36CZ{->\16u\21dEt)\1\\\6<\31ZO&^\7ka}\25q^\1d\7U\1O/#&H4cz\n\20j3\13*\37\4%i\\~`Hv0Jg\17@t+\1,H\t^\r\31|uQN\\W$s[t\17t\4Z\u001d5<MZ\20\16XcKZ?8=,iwy$O0Dg\bz?\200Qx\177&(=n\24\33LCt\5^[vi~VF$`(lY\6W#UGk\u00115}kH\16$\1\1");
		U3 = ArrayUtil.unpackI("\1\1\1\1\u00015\27\17\5GCbaI/\36\n\7gCbaI:\24NFSriI7\22M&$|\"1q%\32+8s\6eO7\27Pu,\n'];\23\16F\f~\rf$\31KHs.S[<u\27\17\5D<<x':+8s\6HW}R?%t25uf{lp{\26ET=q\35V?riI7\27_\35|$#\200$\32+\200W\6DG2\26\20e\b '{f[,(;8Z^:x8_mx796'+\f8\17*W\32Z\23\5>0d\20aLOi[+B\200D4\5XE\nT\22ko>r#K{eN7w\17\21~:5:bz\33`U\5OX`RM>\fq\n!*G_-x,41\7[\24H;(]`\33cNB@h\2dk/a7\rFyHtUHi$\25\2ej?\31;1Fv\u00071V@E*\17-\177\7||P\17;[ :cn&`V\21)M\36d8n.<\31 [\34~paYZ\20/^~[Zn&+&'G7\\\"t\22\25\3<\20\b%B6njy\22&jNd|G\5Y\23ZteT\r\fK\33\37#>2\32/*z\5\nT\u00809\7{G'zW\5k\20`()\25?KSn@~E[O]\25\22C|fepGOC=x4w;Rrw^Y\13Mu}y!D=b\1f|0\5e\6F<\b\21]\20w%\rg\37cy_$WG^J^]\32qEg'n&\177\7!e,9U\25t(`+!\31 \f;Jxv_\30\17,wU\r\6ZxB\1f\24\7\33\fk:\2!{\26z \t1\200\22AR\21xIG:p\21Y;\4\u00103ni-n\31k\b\u00069W=#!C2G\"\16<s@\16)\37\3A\37fl\23D\30O\24:LgKYnoErit~\"p\30HS\35[%xW\26;\f\"9!\30y+\u001b6*MV7_u\u001040}(z9\17\13x~Nv\25\177\177wntIru\17}\20T\33fn?9g_Of0\31j\30{\"\177\\)]L7k.IH@D!b\31\5/\21\bEh\23R-K$\37\16gb%>\6y\b',oR\3G{o\17WW7:zX\32-\rm\n;EI~CSF\u00185.\7c\20u'GyT?>=\3:S4U\6D\30VG?IB79tG*C0U\5B\30?b7\r36X7\"sNto6\7c^S\31G2,!R=l\13^4uP\\0]\25\26wbi\5v\36*y\24@eL\5y055>J}\"\b`GKw1zx\27#@<\36*\26:9\13\6Ey{$p\7x#;W+4\u00165c\f\":\6\t\21M9An\31kPBLh\rI\33Q=7-AQ@)p\3\2Y8\21Y<O=G\3Ok!e,q\21hC\30^=/\35\177KS\2Co\2w'X!X\13(.\f}\22\13\1t~\2BL;Lu9N[\\g(=F3)1d*<vMt&S*%\\=+?\2Ae\22\22mP.l%|:PkX\200cd]: GF\\[hYD\2'Vi\5\4?jc\2DCY\37\tUtT\":>\27\36\nj\31Z!yEA\nGQ\200&yC\u00029O%YX\177a)\7\37GsexF#\32Gms*_\"jW\17Cgb-~\7\37G{\t\6c:U\6NC^M7h\23oH\u00061?\u000b6/A2C\23\1(\30\30SdZt\21EHu[&\25P\2}5.\177o4{XeQ\b zVi\26dE&\7\r>{\35'nl8\32.:H$BmO\31ng-HLlr&/H\34_\26s|=\22hLbt\4\200\200@i;j\23T_8\32N&b/C/n;\35?kcA0Tvm7l\22+'V\t\33?0$GA\1");
		U4 = ArrayUtil.unpackI("\1\1\1\1\1%\33\f\b\5D\"1q7\30\17EED\"1q.\35Jgd29u% Ig\23S\"QY9\21-V\34z5s(\34\u000b8{\26Em/\36\n\7+F\177G@\22M&$^\27j.%\33\f\b\u00034^^|Q-V\34z\fdl?,(\23:Y_{3~6\34~\13c,\u007f9\17,29u%\33\u001c0\17>KR@RM\36@l\3\177$\31KHW\4PT<Sn\26TP\34moH<\\p73\\\35\33SN\6\\H\21,\rmI\177\37XrFQ&h5\34\26!\200aJC,ctjIv8WyR&1s'\\<$\t?]V}q}N>k\3()`ig\37My\5Q\33\\0\27<R\32Y\4.fd^\24m\20N2'\37`tAqf\u00181\34\36#}$z#$u\22rAs5`)\36\31#}$\31+`q\25H\u00179t>~hO\36.\20^*7Spo\t\25'\fZ\\wW`m\20n\16Ixq-.XX/\177S-wSV[T$\34!\21zIK&\36HDPA[wuo\tSufB~d\3&\n-zrRG\6f\nP\22\37Y!X\25}A%j\200`N>$\24>|\u00036\buTU\13 n*7`b#.(//\tb>u\u00138d(4\37<Zvn)y|(m\6'<g=\21\"[qA3},C3\3a~DI/2|\23\u00073 2=/Nl$/e'o\ry0t\u00147S\\\4\u00113\33=+\13:BpV\21\20@F^%s{p\fA.|+\7\7-|aA_JD\16\bV\35AQD\13}PF\t@Ia4I<e$UxI-oBHZ7Q\u00177M4dC],\r\22\21\" \24\21G^q GU\17Z!\u00103r\n\"LeV]ft(\r7x#\17u:\177RhLdj2.\23<lC^\6QRQ\f}\26*\33Ug<\u001c0;\bLX\177\24\200m\b\6<xg{K=H<7zay{\b?ljN3u\200\u001d4/J3XM6\\~\21\u00807U/&\\~\27e$A\"Q1M'\30\t\4eTJ)W8\22P\7pAS\37Cv\4T\26wqB$>4H,,\33a}lMU'7\5^\25%?b)3L[\u001752\b{\24l=*`\24\37\2\35j>k\3bE\13d %3\\\35:a%b\30k\f!L`><\7\32\33p\34\21z'\26x\33D4\17j\r$gVQ)`&F/Zb(nXoC\13|1PC;OUY\n`s$#=\30[\t\37e\177\u00184pd&3\31=|KJ ^OQ\13]]\3\37c=>\24XD<R,,\26\32LK2\6QdCE\t'U!7M9haftc%\16)\4|\27!)\16U8B\4]\34I-\25h\37$\3`6\u00113\u00129\t4bho_\30\r &*\u000368\1|\23<Q,F\tWF\177\t\16\1:\177`!f^&_\35'n,\24\24_#L\25\u00192[.{g:\\j\25S-\7\26 \1es\tIx\\WvS@}hv,Jr2o\36`d#m\u000b4m\"A\34+u\3\r 5r\1Fb-\20\fK:jQO_L\17HEM-Q6#!\5e\21@S}&A](\23A,\u00801\27$\20$:M<cR\16t7:\25u\21ul\bj41WQD\20$>!\3r\35mcgb/u\u001c4J1TCY \r\33X!\32:\n\1\24P\fj2p&I#$y\16\23K(w\177\33\27\177(\32~,N)\4P}#u\13rpSD\7\37Z\17\u00147M<MW]rRaw%=7t\27+fvyR0$Np\17:>_\t fqzD`\200`vt5J*o\fMgSlX\"\u00187\26\17 6/a\30j{S\u001c6ITt+E\16\16\30RA\1");
	}
}
