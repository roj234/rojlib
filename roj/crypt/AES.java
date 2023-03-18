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
		rcon = ArrayUtil.unpackB("\u0001AAAAAAAA\u0007gggcWNN\flld\u0010\u000e\u0018\u001b[[L\u001cxvpce\u0002");

		// S-box and Inverse S-box (S is for Substitution)
		S = ArrayUtil.unpackB("2`\u000fx`JWpcM\u0001\u0017:0~XV^Z)\u0017&|{-R\u007f\u000boSF0O*\u000f-\u0006`|\u0014\u0014\u000eH\u0080?1j&s}/\u001eBE+\u0005dIy2EY\f\u001b\u0004EQ\u000f\u0018-P3;C13ai7o.)\u000b$_[g*rLqF\u001fE\u0002n\u0011@\u0017\u0016\\,\u0018?\u001dSJFG@\"pV?i5jO\u000bF}APv\u0002s@))ii\t}K;9{p\u0017nR\u0005\"\u0080zuZQaPY`LR\u0003}&\u001e}>3\u0018$\u0018\u001c\u0003\u0003Po\tF*\u0005\"\u000eo]\u0006\u001cfq08a\u001a\u000fB%I\u0019I]b5vG\u0015G,e=zz\u0004<7\u001cV(+.F8TUf>,B\fTaK/\u000f*WM8$;u\u0010SxY]*a?[ZJ\u0001 Y\u001db\u001bVx\u00197\u0005<\u001fq\u007f\u0014\u0002\f(4\u000fK'di@(\u001dV\u00158rK\r%\u001c@t\u0011N\u0005\re[\u0010Y\u0016\u00181\u0001");
		Si = ArrayUtil.unpackB("*\u0003..*An&\u001d0i\u000b\u001d{\u0004tl\u007fpO\u001ag\u0006\u001c\u0018\u0080qt%:\u0007Ec8^\u001d[Rx\u0015\u001a*Y#\u001ax]MKCi0W\u000e\u001d\t\u0018)-cGeJ3<\u0017u%L7\u0018R\u0013]`\u00104\u0013\riM\u0006[K#t\u0019^3nS'dB\u0011Q\u007f|8\u001eSy+G,jrZm\u0013\"YVA\u0012LgM\u0016xs\u0017\u0001\\FN\u000b\u0007i\f\u0004i\u007f)\u007f\u0010\u000216|i\r\u0003\u0014F\u001bh*\tF\u0003P4x\u001e*@L Oy.\u001dh\u001d[Yu\u0012:vT-\u0018Fz\u001c{\u0004H/~]HyGO\u0012j(\f\n8nm!v)2?\u000e\u0080\u000bds0\u000eS=I\u0014._\u0004}ygW_B\u007fwQ4E\u0002yt\u000eE%\u0011-Jq\u000fc~AR@k$\u001c+)\u001b.s_Tz\u001f':pQ9\b5n9VvY3\u001e<Zs\u0007TMY#sY\u0012~;<vEo\f%)d+I\"A\u0001");

		// Transformations for encryption
		T1 = ArrayUtil.unpackI("d\u0019m;0by}C<Ox=hm|>d@\u0080\u0018I\u001cW6[x^t>`2Ir9V$\u0001a1)\u0001A\u0011\t\u0010\u001dh4k+cZ.|h\u0080@D\u001c/`/c'kv?82mwN$z-S\u0015@\u0003B(2\u001dO&\u0002{?`1\u007f\u0080lu\u0016Z\u0017,\u001f]:\u000fHe\u007f\u007f\u0010\u0001.\u0004.W|\u0017>'RO`R)`U.?`k\u0012h\u0014L{OJ%|z\u000f(\u0015[8Aa\u0017o\\>`\u0006b\u007f\u0080$DmO(/'\nEgT1m7. Ht{\blx|AQ=g2\u001fi\u001b\u000e\fF\u000e\u0017Luiz=T(hdr\u00059O\u0018\rOXYm\u001dm$\nF'+\u000bF(qA\u0011\t\rKryv\u0013\u0019G$3h9=\u001bya\u0019\r\u000b\u0007z5[C\u000b\u0003B\"s}k66\b\u0002aqJ\u0011%\u0013\u001c\u0007q\t\u0005p@cr\u0010:_`-MO\u0014Jn\u0018~KfNv\u001e/ZyI\u0013\n\u000eH19\u001dz1-\u0017\u001e\u0007BQi]7\u000eGf^d:^3[\u0017L/soB!~j\u000b&\u0018Zm<\u001eT7~7ZC~Zmzf\u0012%S|oy}4sy_09EqI%_MT*~8\u001e\u000fFQ\u0001\u0001\u0001\u0001\r\u00108[-!\t\u0005\u0007\b\u0010z}\u0010_7\u001c\u000f#m\\.|;GT+~\u000efsig>{~Z:\u000f(\u0015]R\u0015Kp'\nEgSaY-;\u0011]\u007f>\u0016<i5\u000e=0@_+(kV/08x|\f\"I5\u001f\u00175N'vmd\u001aN+\u0012Cb3IS\u0016\fPu\u007f@\u0012\u0001\u0011\u0005\u0003\u0004@Px}\u0007AQ)=\u0010\u0004br\t&Phx%^#RdR\u0015+ \u001bwH$\u0080!\t\u0005\u0007\u0001\f\u0010HcHz\u0015K[\"Oh8H\u0002aqIy~?Q$\u000fz=p^wl7\u0007`[n\u001e)#\n\u0006G!\t\u0005\u0007\u000f0\u0080\u007f\u001b\u007f}\u007f1v\u0080&S7a:]k11\r\u0007\u0006\u0005b\u0019MlDw<\u0006|s~@b\u001bfs{\u0015\"\tEg\fCr:g(Ec\u0016k[> f}@ Q(Ru{He\u001a\rKfj;^tMD\u0012J0Mt:f9\u0007\u0004\u0003A\u001aAa4\ns> RRx\u001cH{\u0011E#4\u0016\u0006#Tyx\u0011I+b9E#\b\r$\u0012Z-@<]*6o\u0018\u000e\u001a!)\u0015\u001f*|ntgy_09CaY-<.nwon`\u0004A<3\rG&4Qu;(\u0006\u0002!Q{%J%wbA1\u0019\u0015I\u0013\n\u000eLCr:ePqY&nx(T8\u0011vKh@\tc2*H\u001a\rGQ2Kf5N \u0014I8z\u001f0\u0019_XPh\u001a#z\rC\u000e]8\u001cW<'l7o\u0002Gd2L\u000fX+eO\u0014Jn\u0013'T*q7\u000eGfSYW,?_@(Q\u0010Pv;E]T\u0016L0{\u001fP)s\u001f^/uE\u0002\u0001Ab`;^6?\bDc\u0011K\u0013J.vb9]s\u001d\b\u0004C#_N'y]wL'\u001f0Gd\u0015:?H!H\"ox0ODRj\u001d \bds\rZ\u0017LoY8\\oq\u001c\fFbByU+\fa9\u001d\u0013\bby}C9n7]'2MgV%\n\u0005Ga\r\u0004\u0002B?\u00808Y\u0003\u001d\b\u0004C-\u0014\u0006D$6\u000e'V~:/X}[8\u001cOA0\u0007D%4\u001d\u000f\u00061;\u000fH%s={>:my=\u0014H0ry\nKt\nFME\u0012\tM{'L'x*mw/\u0001=;\u001e\n\u001af\u0013K:78\u001c\\\u0010\u0004br\t,\bDeZ\u001fP%B\bh4J\u001bSV,\u0080)\u000b\u0006\bF\u0018@`>\u0001rIe>4\"Q\u007f\u0002\u0019M'\u0001\u001b\u0007D#w.\u007f\u0080[lz]d\r\u0012\u0005Cd5\u000e\u0007Fc\u0005B!qf\u001aMga[\u0017L/rq=\u001f\u0012>m\u0017\r^\")U\u007f\u001c8<_YY\u0017\f\u0001\u0001");
		T2 = ArrayUtil.unpackI("SrM7\u001d\u0014q}?'>h<_\u001cw>_b`\u0080Lf>l\u001bn<\u000fz_p+%9]+BA1\u0019\u0001a!\t\u0007TO4ZpV2-W\u001at\u0080`g\u0016X0Xt\u00146;]lYw<\u00122}W+; B!I\tO(\u0014\b~ 0R0@v{vmL\u0016O'\u001dH$C\u0080@\bDYBWl-|\u001fT*~0iU/S\u0017`0`ItJh^(%S&]H\u0014J8\u001ca1\u0019(._o\u001dq\u0080@[rw(\u00146\u0014\u0005c3jY7\u001c\u00110dz}\u0006v|~jy\u001f4\u0019]5\u000e\u0007P#GL&\u001b5=_)$trye}(\fFh,m7\u000b7\u0012Ec@\u0016\u0006#Qa!\t\u0005*&9}<\u0016\r$\u0012XT]\u001f\rQ1\r\u0007\u0015\u0014=[-\u0010\u0006\u0002!\\*?6\u001b\u0005DAq:YI\u0013\n'd9\u0005\u0001|`r9Emp0Wj(\ne}l\u007ff3P{OX)m%\n\u0005hDY\u001d\u000eiY\u0017\f\u0006d!i5.\u001c\u0007d<\u0017r]ox.\f&Xn8!Q>UF\u0013J\u001bw\u001eOm\u001c?\\.O?mw8[IS* 8=?\u001cF=0\u0018fc9%\u0014l'*Un\fOH#\u0001\u0001\u0001\u0001\u0003g\b\\n1\u0011\u0005\u0003\u0001\u0080H}\u007f3\u0010\u001c\u000eH\\7.Wxn$*UGGsz>L\u001f~?&]H\u0014O{)K&6\u0014\u0005c4R1-\u0017\n)/@\u001fl^u\u001b\u0003W\u0018`psTv+Q\\\\|~r1e\u001b\u00100\u001b'T+W2Mh\u0015\tb1]}*\u000bF\t;@ I\u0019\t\u0003\u0002!@h|\u0080b!)\u0015\tHBqz;\u0013ht\u007f\u001b/R)ziK\u0016\u0010z<$Rq\u0011\u0005\u0003\u0003\u0015\u0006HdvT}K&=\u0011h4UDAq9\u0003=?`/~H=_1/|6Zl0n7M5\u0012\u0005C1\u0011\u0005\u0003\u0002X\u0018\u0080\u0080\b@?@\u001c7\u0080Sj\u0014\u0011\u001do5)\u0019\u0007\u0004\u0007S1M'0b|\u001eO\u000ez?`R\u000e3z?3\u0011E#\u000f&b9^0\u0014c2\u001f&.\u001fP\u0003\u007f Pe<i{>W3\rG(\u001fu^/Kg\"Ig,g:]u\r\u0004\u0002B\u0019\ra1\u001e\rz\u001fP@i|Nd\u001a\t#\u0012 KCR+W<I%\u00111]#\u0012KG\u0012IcO ^oj[x\fBqQ\u0015\u000b\u001f5~w|F=0\u0018DR1-\u0017wWw|4_pBa,\u001a\u0007$\u0013:i;\u001e\bCAQ,8\u0013%S\"!a\u0019\rm%\n\u0005O&b9]/hy-\u0014;|Tj|i;f3NE2\u0019V\u0004MG$%\u0019f3T?PJeF}P\u0018JL,htQr=G\"3o\u001cNw~T6\\\r\u0001d2W&H,Vj(\nex\u0002\u0014*Un\u001c\u0007d4v-,\u0016A\u0080 Ti&h{^+\u007f*KfH>\u0010(X&\u0010/X\u0007\u0003\u0001A$+p^/R\u0010\u0004bqp&\n%X\u0013q]/\u0013\u000f\u0004BhF0'T2o<&R$\u0018d2E=`$Q}Qx<Zh\"iu\u0011PDr\u007fw-L&x\r\u001cnw\r\u000eFcqQ}+\u0016\u0011q\u001d\u000f\u0005\u0014q}?c\u001d7\\.,\u0019g47\u0013\u0005C!\u000b\u0007\u0002Aa @\\m\u0013\u000f\u0004Bk\u001f\nCb0[GT0g]X,u\u000e\u001cNg#\u0018D\"L\nO\b\u0003(\u001e\b$\\J\u001f>\u001f\u001d7=\u001f\tPXy}-f:Eag#\tE8>\u0014&SqUw<\u0019I\u001f\u001e\u000fTMsJ&Y\\\u001cNIHBq{%\u0016Dbe\rP(SJDtZp~*+V=\u0015\u0006\u0003DkL`pda9e4qZQi1\u0001M'\u0013\u0018\u000e\u0004\"^T\u0017\u0080@\u0019v}o7\u001b\tC\"/\u001b\u0007D$\b\u0003!Q7\u0003Mg3x.\f&R\ty\u001f\u0010f_w\f\bsQU+6N\\^mu-\f\u0001\u0001");
		T3 = ArrayUtil.unpackI("2j9g\u001cs\ny?\u001et\u001ft^x\u000e|\u001f\u007f!p\u0080el_vN7~H=pcV\u0013\u001d*B!a\u0019\u0001!1\u0011\u0006P*h\u001af8kYX\u007f\rz\u0080n<\u000blXVzJ[\\[6m<3IY\u007f+\u0006\u001e\u0010aZ\u0015\u0005(\u0013~D\u007fP`QX`{-{w&K \u0014\u000f$}\u0002@`C\\m!l;G>P*#\u007fXu+\u0080*\f0O0e:f\u0014oTS\u001dSo$L\u0001\\Nq\u0017}\u0014Wp~\u000f9@Z\u001e9|\u0014\u0014\u001bJC2Z5m\u001c\u0010i\u0018r\u0080o\u0003{~zE}\u0010\u00195/\u001b\u0007K0R$&sN\u001b\u001f0E\u0012zy]3?\u0014H1tVw\u0007\u0016\u001c\tc\u0016 KCQ!1\u0011\u0005dUS]:\u000eKG\u0012qljo\r1)\u0019\u0007\u0013k\n_-\u0006\bCAZVU`\u001b\u0004C\"a9Im%\n!\u00142]\u0004E>py^378W(5TE|\u00176\u00803;h~()%7\u0013\u0005atbm\rYu-\f\u0004#rQ5\u001c\u0017ND7vL9o.<WFV\u0004w\\Q\u0015_k#IwN<\u000f{g\u000e`.4h 73Ln%*rP\\_\u001a>c_\u0018b\u0013r\u001d\u0012(vT\u0015{\u0017Fh#\u0001\u0001\u0001\u0001\u000fj4\u0004n\u0011\u0019\t\u0003\bq@d\u007f-:\bNF8n\\\u0017n,wRVL$$:<wfP?\u001dSo$K,>\u0015&\u0014\u001bJC22iY\u0017\u001auU\u0018 Q6o{\u000fz,\fpV:*{Xm.n~QyY3\u000e\u001cXN\u0014'6,\u0019h\u0006K\u00051U/?\u0015F}E\u001e I\t\r\u0005\u0002 q t~\"qQ\u0015\bE$az ^\n4{H\u000e\u0018))}u&\u000e\u0010}^RQ\u0019\t\u0003\u0003 \u000b\u0003ds+j\u007f&\u001e_\t4TC\"a9{B\u001f .t?d_.Y\u0018>\\5vXwE\u0017\u001b\tC\u0011\u0019\t\u0003\u0010ylL\u0080zD``\u001fJ\\@j4*I\u000f5\u0019\u0015\r\u0004\u00034*\u0019(m\u0018q~F\u0080\u0007}`LiGZ;\u0014\u001a\t#\u0006h\u0013q`\tXJr\u0015\u0080\u0013WO\u007fB@\u0010dk\u001eu>3,\u001a\u0007#xP;/G&4\u0011fh\u0016t\u001dm\u000b\u0007\u0002B\u0002M\u00071\u0015\u007fG=Po u>b\nME\u0012\u000bPf\"+\",\u001ee\u0012\t\u0019/\u0011Gf$\tor(\u0010o]5n<AQy)\u000b8P\u001b?z>c_\u0018B2iY\u0018\\<,<?\u0002p8a\u001a\u0016MD\u0012j\u001du\u001e\u0003Db!*\u0014\\J\u0013!aQ1\r%7\u0013\u0005Fh\u0013q]b\u00184}\u0017N^>jl\u001eu\u001e2F'c\u0019S\u001bBg$\u0016S\r3_\"`(e=c\u007f(P\u001dfVts\t9_!oZ8\u000en\\?j\\\u000eG\u00012^,\u0013dV(5TEv(AJU\\\u0017ND2.{W\u0016_A@Pjk\u0013t~'.@\u0015f>$_HV<SHX\u0003\u0004\u0002\u0001#vV8oP\tHBq&8SEStJ9/\u000f\n\b\u0002f\u001ccXT.\u0019x\u001eT\rRLr^\u0003\u001f0R^?)<X%tQu\u0010I(b{0<\u0017&p<G\u000ew\u0018\u0007\u0007cr))?\u0015qI9\u000f\u0004s\ny?[r\u000f\u001c,\u001bVM4\u0013\u001c\n\u0003!\u0007\u0006\u0004\u0001\u007fa\u0010`m\u000f\n\b\u0002g\u000e\u0010\u0005b\u001bXn$+`t/,o;\u0007Ng\u000e\u0012\fbY\u0016Eh\u0003\u001e\u0014ODZveP\u001fqO\u001c\u001f\u0010a(l}'\u00173]a#4\u0012\u0005.\u001c_JTZ9+<\u0019u%\u0010\u000fK*g:%om.NHE$a{\u0010\u0013\u000bb~\u0013\u0007(TO%bzf0\u007fUV\u0015\u001f\u000b\u0003G~v&pd\u0012q\u001d3Dy-i2\u0019\u0001'\u0013\u000e\fGB\\\u007fjL@t\r;\u007f3\f\u000e\u0005\"\u001b\u0018\u000e\u0004\"\u0004DB\u00114\u001c\u0002'3.<WFQyE=\u0010Y3p<\u0003Tz)+/{gnm-;\u0017\u0001\u0001");
		T4 = ArrayUtil.unpackI("2Yu]4rz\u0005}\u001eozP:w|G~_0\u00118\u007fl6p;g|?d_cr+J\nAaQ1\u0001!\u0011\u0019\nOhUtF3\\v.\u007f\u0080\u0007=~?^F6Vk}elZn\u001bw3Z%-?\u0006\u0003OHz\u001dK\u0003\u0013~?b\u00800Xi,p-W><\u0013\u001e\u0010JH=\u001f\u0001`o\\.w\u0011;N$\u001fh#R@,{~@UFO(\u0018s\u001e\u0013Jx*]O*8\u0014\u0002A.gw|?\nl~\u007fH\u001d\u001a\u001dO]>\u0014\nN%bYm[7\u0010hu\f|px\u0002>:Mc?\u00075\u001b\u0018\u000e\u000b.\u0018iRsz'N\u0010Hc\t}]/\u001a \f2Y:kg\u0014\u000bNE\u0016\u000bPf!!\u0011\u0019\tdrk**\rGf$1y6uu1\u0019\u0015\r\u0013j6\u0005o\u0006\u0003Db*Ukk0\u0004Bb\u0011qI%7\u0013!\u0011\nYpFc\u001fx~?Z\u001c\u001b(\u0014[*l\u0016L\u001b\u0080;^4\u007fQ%\u0013\u001c\n!q:quY-;\u0017\u0004\"R9i\u001c\u000eL'gt;f].\u0017^l&\u0003B|.UK06\u0011w<'^[n4\u0007p4ZtPSJ&wSryhnj=_r0\"\u0011J9N'T{j[\u001e\f#s\u0001\u0001\u0001\u0001\u000fp5ZB\u0011\t\r\u0005\bty rm7\u001dDf7\\wnN'V|*LfRR\\v|3h\u001dO*8\u0013*\u0016_K\u0014\nN%b1Yu-\u001a}{+\fQi\u001bx?\u0080=VFV+]U\u0080pw\u0017wQi=-\u001a\u001bNlgG4\u001bVN\u0006Cf\u0003\u0015+\u0018 \u000b}\u007f#\u000fI\t\u0005\u0007\u0003 py\u0010z!Qy)\bDc\u0012r PoE[F$GL)U?;\u0016\u000fH\u007f/Q\t\r\u0005\u0003 \u0010F\u00023*\u00165\u0080\u001eOp\u0005\u0014Bb\u0011q{~!P\u000esz`2nWm\f`6[;le\u0013\f\u000e\u0005\u0011\t\r\u0005\u0010\u0080}6fz}bpoL%n`t:Ue\u0005\u0019\r\u000b\u0007\u00032\u001aUNmw\fy6{\u0080D?Lfu$+\u0012\nME\u0006ctJ<\nE,eu{@J+\u007f@!`Djv\u000f{3\u001a\u0016MCv<h^\u0007$\u0013ZJgtKzM\u0007\u0006\u0004\u0002\u0002Ag\u0004\u0015{@$\u001fo8\u0010{\u001a\tEg#\u000bF(sS\"\u0011VOr\tE\r\u0017G$3ROx9TH]/\u001b7YQ)=\u00158\\hN\u001e=_r0\u00021Yu.\\n^V_\b\u0001x\\\u001a\rKg\"iuO;\u0003B\"qR\u0013JneAa1)\u0019%\u0013\u001c\n\u0006ctJ9b1LZ\u007fP'o_l\u0016O{\u000eEcT2\u0013\u001a\u000e!t\u0016Kj\u0007\u001f(\u0011pT=_2@\u0018 O3ks\u001a\u0005\u001d-o8-\\NWn`6\u000eGd\u0001\u001e/VJ2(\u0014[*f'Ta%\\\u000eL'b-W~,\u001fP! hkv\n:w,\u0017`K>\u001fRp&;^j$C\u0002\u0002ACv;k\\p\bE$a&\u0013\\j#r:e]\u000f\b\u0005DF\u001bNr,n\u0017M<P\u000eG)f~\u000fB\u0010\u0018^o`\u0015\u0018$S:i\u0010He\u0014s.\u0018^L08^d\u0007\u0018\fD\u00042)U\u0015\u001fq9%\u001d\u0004rz\u0005}[n9H\f\u001aN+g\u0013\n\u000eEA\u0007\u0004\u0003B_p1\bo\u000f\b\u0005DG\f\u0007HC\u001bN,wS^0zX/8\u001e\u0004'\u000e\u0007IFy\u001d\u000bc3\u001e\u000fJh*u{s(qy(\u000ePdq\u0014vg\u0014\f\u001a-#\u0012\u001aIN\u0017Np&Zm]\u0016\u0019u;\u0013\bK&\u0015t\u001do87\u0017HDc\u0012s\u0010\bJ\u0006>\u001fJ\u0004\u0014Oh\u00131v+X\u0080+\u0015\u000b\u0010\u0006\u0007\u0080?{Sd\u0012Iy\u000fD\"}\u00172\u0019M\u0001\u0013\u000e\u0007Fd,~\u00805ft:G\u001e;\n\u0006GC\u001b\u000e\fGB\u0003BbaT\u001aNAS.\u0017^l!y=#\u001fY-\u001a8[R*}U/x>45-\u0017\u001e\u0001\u0001");

		// Transformations for decryption
		T5 = ArrayUtil.unpackI(")~\u0015v\u0004z\u0003f*GC{'\ru(0&H;\\0\u0017 OR?\u001bhj2,&ya:\u001a\u0001b{+l/gp[\u0012M<%?Q\u00131KPsv\u0080M*,\u0018X\u0014\u000e)I\u0006VF$HxW\u0016S%L;\u000eZi_Q;1^\u00801\u001d\u001d\u001a>k\u0003AT\u001f\u0002\u00156\u000e\u0018R[{@O\u0019\b\u00100zcZ\u0015s,@7_^:+J4[k0Q3kbi\"jS=\u0007J'\u001eJe\u0012\u000f]\u0015&VuH\u001f0\nKa}l\u0014o/\u001en|CP\\=\u0012\u000bi`\u0013!W\u001aP]rkidpSD\u000f)id\u0003LU'7\u0004\n'\u0080#m-H@\u0003wlX\" i\u000e\u0001:z\u0005\u000bsH\u0003\"QYHR@RMT=mDUP@F_WtjIo%Y\fFd\u0010dkw3WW+Z;f\u0001:?lC\u0002bYX]l'8\u0005*'\u0003E e$`j7!\u0011\u000eV;wFQ&e,\u001f\u001d\u0016jp\u001c%Lg\bz=\ngP\u000bCfn>\u001aQ1\u0017~ViN\r\"\u007f\u0013N\u007fF\u000e\u0006f\u001dwFt+i\u0001YX\u0005f%|;oQ]\u0010Y:!\u0019\u001e{Szd \u00040.b\u0003E}\"F?3ni\u0019|^ \u00026ep\u001b{GIV\u0012\\,H\t^\u0003B\u0001n$>AQ\u000b\u0080d\u001aHmJW_{3yK\u0002\bM4w4h>DQC_Br\u0019]$O\u001a.O\u0010\u001dH<8\"?\u0003Ixc\t j}\"\u0004mI\u0001\u0001\u0001\u0001\u00031\t5\re,wS\u0004b\fCYm.\u001dJpi<\u0080|\bb(\u00062w^V\u0010\u000eFTJ\u001d\u0015\u0010mZ\u000e\u0006f\u0019D\u001c.V\u001b\u0013\"Y];\u0007\u0003M|\rN0h\bn\u001ej7I8\u001cIhQ\r\u0007\u0016\u001fbo\t\u0015&T^\u0017j\u000f\u0005D\"8\u000b(;\u00061\u0015\u0003X\u0015y#q\u0011c\"Y];\u000f\u0005D\"@\u00150\u0010.\u0017nV\fIQ>*e\u0016\u007f\u0012M\u0017_v\u0004T\u001ejOwx$@Y `9\u0005N /\u001d_\\c\u0012M<cW\u00808rS\u0017D\u0015^Z3\u001f\u001c:7w\u0080\u000e\fH\u0014cdlM<MS\nH\u0006\t\u0005ss\u0013\u0003\nG\tI\u0011UR\u0012|S^P@\u000bxee\u0012dK5\u0017iw=0&x\u0017$\bM\u001c\u0007*<\u000f}\u0010\u0010!,ZFNKLCt\u001a\tf\n\u0010S Se\u0012+ Ig\u0013Bq GKhj41#\u001ae\u001ey;&\u001eHmO\u001b\u001d\r4\u0016#\u0080'\u001bAZ[NvAtuXWyR[\\dEd~7\u0080%\u0017\u000fT_#Br\u0013\u0007[L}en)\u007f$\u0019_iiP\u0006\u0011m/\u001e\u0003rho_B1v\u0080-~<\u0001`\u001b;\n\u001cr`V\u0017k:s)KhIW'(2\u0001b|(u(\r7wmx<>t%hA&]Z\r>\u001eJV]\u0004\u0004(gV\u000f5V]fV@}hr\u0007zP\u0005<c_H\u001bvhNw*'z\\\u001ekPC;CNBzW\u0019j\u0017+z)\u007f$\u0019rUZ\"Al#41\u000fEvqp}B3Uo\u0005D\"1\u001aj|\u00020E\n\u0019&\u0011>NX^\u0080N)\u0004Cz\u0010Y_w'vRU ?aNg+JV(\u0013-\u0005ph[\u001c0\u000e\u0019kE\u0007y\u0013a\u007fqG3U0zk|U\u0005\u0001d\u0007Vpk\u000fu:\u007fb5\n;gh\u000fWS._J&j\t\u0016G7oZ\u000f\u0014N6m\u0019b_C\r>\u0017@\u0002%<W\u0014\u001f#:kJ ^81s'_\ttLn>\u0012w\u0014et&`-V?(\u001a}1\u0015h\u001f/=:_\u007fT|t>&\u0080wU\\pP.r$b\n\\D3V\u0080\u001d\u0007sic\u0010H\u0003\"QZCRQ\fr1x\u0007s_9EQb!yJF\u0080bZ+\u0005t)\u0001]\"\u0001fP>Y[:\u0014G#\\\u0004\u0011>sqG\u000fUf79\u0013\u000eFdT\"9,A\u0001");
		T6 = ArrayUtil.unpackI(")\u0015?K;N}B3qd\">\u0013-;\u0014XZ4^.Xr\u0010h)[^4uYJS}1\u001bUA1~>VX47$\tg\u001eE`)\n\u001a}(z;~?\u0015VLA\nGU%?kcRS<l\u000bjO&^\u0007t\u00050)\u001eb/\u0080Y\u0001\u0017\r_v\n!*P\u0006\u000f\u001bGLrN> hO\u0004HXsR-K:l`\\0.UV%Z\u0017v\u0018i\u001fN1u\u0011K*\u001f\u0004&\n\u000fes\u000e(/\u000b\u0013y{$P\u0017]f1?oJx\u0018\u000e[~b(F\u007f\tF6NJ\u0011,\u0017Ho9u\u00192xj)\u0018\u0015521&k\u0014\u001b\u0016ET@y\u0017\u0017$_\n<6lDPu\u0007B\u0015}C\u00066DB\u0011i\rdi`m\u001f*_7.k(`aH,:u}(\u0013-\u0005XrHrsT\u001a,,\u0004m^3A\r`6b'Qm,oLT\u001cC\u001f$\u0002#\u00103\u0012pu\\Q\t\u0007k/<#i\u0012/\u0016P\u000f%UxNTbt\u0004}U\u0015t(FN3w_N)\u0019\f?\u0010u'G\u0015,\n'\u0080('CsOB#zV'!-,CvS>^4I/\bmV\u0011\r\u000fy\u001a=rPU8WqDs?\u0011cHZ7u\u000e/oPAU38N>[e+Ii\u0016dE/\\aA7T\u007fa)\u0006%BMdx\u0018l0>\u001de&\u0001D<Z|\u001avwbi\"#\u0001yM-rh\rW|8O$]HQ`\u0002/Lr\u0005\u0010e\u007f\u0011Bq\u0001\u0001\u0001\u0001!b\u0019\u0005\u001a\u00113\u0016|6BqFaO7\u0017O0`u\u001e\u0080,\u0004qTAy|/kJgcjfI\u000b\bw%\u0017CsNRNWkDR\u0011m/YD\u0002'9?'XtuWOu[=\u001cNe*y\u0007\u0004\f#1x\u0005\u0007KjoL\f\b\u0003\"Q,F\u0014^:9\u000b\u0002*\u0007=\u00129\u0004R\u0011m/\f\b\u0003\"[pK\u0018H]L7kG!)\u001fUb+\u0080\tf\u001a0;Bx?uh<~R`m\n\u0080]\u0003'_\u0018\u000f0/\u0016\tg\u001eN\f@\\zn\f\"K<MZ\u0010\ri\\<@G\u001edJrf6g\u001eaB\u0005dCQ\u0003::\tB\u0005d\u00050Y+)Jyj/hR\u000e<s37rf\u001b\u000b-<\u001f\u0018}|L\u0012DY\u000eD\u0015[\b?\bGm\u0016mcjN&b:~\u00053EB\n\u0010j32\u0016\u0010e16!y\u0010|\u00064uZp\u0012\rs\r=\u001e\u0013Oaw(\u000e\u0010{\u001aKR\u000eT\u000e!0 '{a&\u000b,l='n.rk\"\u007f\\@s\f\b*i6!yJ'n&\u007f2EU@\u0012Y055(iI7\u0018\u0006r9tx{aY;~{?^A \u000e\u001e\u0005OSpkL7=z\u0015%<e,\u0014\u001b9A1~8;\u0014G\u001cp7<^C:S4dio-G\u0001\u001fekp)BTt'0\u001b+o@+`\u007f1!D=hz^r0$4;tgzeT=nUv(b\u001e2'a}XM5L\u0012EU@\u0012M\u0019k-T\u00016R\u001aGx#;z'\u007f!Z,\b\u0003\"Q\u000bMu~C,c\u0005M>i\u001fgi\u001d\u0080gU\u0006r=Hn\u000e<\u0014;ek\u0010`1+4\u0016%o\u0080J\u0017\u00039tn\u000eU7M6#\u0018\r\n1?\u0080$\u001a+\u0011%v>k/A2D,Pv\b;\f`1[\u0006644H+*\u0017p%4uE\u000bb\u001c8-HG'[w\fip\"\u0007$L AS\u0014l\nP\u001emv%O6\\Y:\u001fp\u0005:f\u001f\u001fI|\u000bg:SpPk`\u0014Ns\u0019\u000b4Xx\u001f\u001dpk*~zV[\u0080|+\u000b8hW}\u001aqEna:+\u0080M~:52\u0006DB\u0011i`b)i\b\u0011Y<D\u00070\u001d#--Q=%Q@qmVc:U\u0001<aA3h\u001dm.\u001dJ\u0004\u0012.B1_z9$D+3\\\u001e\n\u0007cr\u0006Q]\u0001\u0001");
		T7 = ArrayUtil.unpackI("TU\u000b $\u0016'\u007f!j\u00192Q^>\u0017\u001e\nn=ZoWFyHtVF/Z{\u0002ej? j+!\u0019\u001c?klZn\u0012E4\nC0U\u0006X\u007f\u0014}]_`\u000b+#!\u0005d.\u000f 62\u0017J\u001evE7h\u0013oBjC\u0018VAqX@h)\f\u00070y\u0005Q\u0015e_H\u000e$?9g_N@h\u0002dtJ)W%{vpnVOk+SBL;Lr\b'Y;\u001b&\u0015P\u0004\u0011EH32\u0017TX\u0005z=>\u0012dt/3Y9x%|K?n?ql#\u0080\u0005#YgeI\b,$x\u001dK\r\u0019|t\r\fK\u001b\u001aY\u0013v\f~\u000bc*^}\f\f\u0013^\u0005^[u\u0002h{\u0003,K?\"\u0007CbaI\u007fG2u,3\u0010\u0015p?\u0017v\u0014p'$V]a/\u0014J\u0018\u0010,yd{Z*MV\u0015\u0002w/_\t\u00070[_t)7\u0015\u0012&jNq\u0080\u0012AR&Z\txwVi\u0005\u0004B\u0018\u001eR1qX\u000bhn\u0013+<hfqzB}+\u000b:TugZ<,wU\r\u00062\b{\u0014({\u0016ET\u0015tT\":,!R=}\u0014\u0011\u0017\u0016l;j\u001fobe\u0018\u0004xkI\u0007\u0005}\r_9E\u000b\u001cl;\u0016z \t!dm\\;\u0006X8(lk\u001a\u001cgGn3\u0016#u\u000brc6\u000eq!\u0019,\u00801\u0015 3!g2jLvXU\u001f3\u0013AP\u001em~K\u000b|1u#r\u0001=&79tG>n\\h\u0011\u000b$i0A\u0080&yC\u00103@\t!\u0001\u0001\u0001\u0001\"Q1M\u0004[I\u001a\u000bo\u000bay#s(\u001c\f0\u0080p{\u000f\u001d\u0016By/U=>X\u000f%t283e\u0006\u0004uc\f\"9Uigl3riI74m\"AX\u001d \u0014,f[,(;$\u001f\u000egyU}\u0004\u0001!R\u0019<E\\&5x\u000e\u0006DB\u0016i\u0016cJK]]\u0006\u0004AD\u001f\tCriI7\u000e\u0006DB\u001d>8f\fU/&\\6(\u0011\u0015\u0010\u00071V@E\u000fMX^<\\`;3a\u007fips5\u0080o\u0002{p\fH\u0012p\u000bE4 GF`mSwFQyng-H}5.^`\f\u000freo3[t\r\u0015!C2II\u0002\u001d]#!C2EHm\u0016\u0015>}5X4\u0011G^zQ\\9sJ>\u0017\u001eP\r\u001f>fJ&m\u0007b]>\u0004`\u0003\u00177\u000bw<MgSq%?C\u001a$\u0011EHud\u0019KHq\u007f\u001bQ=\u0006NCZ|\u0011xIG5w\u001f\u000fJiq<\u0014F\f~\rf\u0003gjGS\u0004PT><cF\u0016v\u000f\u00147W|~\u0011\u0080.Oz\u0006DUI\u001bQ=4\u00147S~\rc+ C=\u0018[\u001c9u%\u001c\u0010;y]:X~1-\u001d\u0003~ /epGOA\\*8v#,\u001f=L\u001a\u001es\u0016Hn\u001d!\u00192\\^\nfnx\\\u001e_\u0002\u001dj\u00192u8\u00177q\u001036\u001bU!jwt\u0018N\u0016t V0\u007f=\u0011\"_;\u001doyW8Z^:gms*_\nk;TxgYT1Z,g\u001b\"\rc+ f\u0007\r6\u0016NA\u001biXD<R\u001eKT@\u0011.\u0006DB\u0011m\u0006';=b\u0016r\u00037_u\u00102!\u000f@t?cy_$WG^J\\\u00036\bp'V\u001aKQ\u0014@eL.=:wG\u0011\u001c'\u001bD|G\u0005YR@RM_Q\u0013;_\u001bX!\u0019dRh{DQ&pY-;[ZZ{&\u0015L7W\u001a{#\u00059N\\W1d\u0014.92u8QF\u0012fPaz\nvEe\u007fw;TJ\u001bnm\u001f08C\u001dYP\u0010%?~4\u001dj\u001dhv0L\u001dz\r\u0006\u0007||P\u0010Nv\u0015\u007f{Sn@~8F\u001ctoo\ry#=q\u001dV@\t?][\u0007CbaIA0qU=\u001cI-\u001e\u0013D\u0018O\u0013'\u0017)\u001f&) y5\u0003r\u001dk\u0017>q!\u001aeO7\u0017M\rBIWC\u00190=^ZbV\u001a\u0018\u000fED2/Ci\u0001\u0001");
		T8 = ArrayUtil.unpackI("{*k\u0006\u000b\u0006KT@\u0006uM\u0019iO_L\u000fV7_-x\u001e#}$\u0080ScX-rAs5ZDuV\u0011\u001eN`68\u0019wIc\u0001%b\u0018lfl\u0080\nsW00F\u001bR\u0011\u00034\u000bH\u0010[m,%O{u\u001c4J>!ub\f\u007fa9,S|U\u0006D'=\u0003)\u000b\u001b0$Gu\u0080\u001d4/\u001f`tAs*eU+n>;xv\u0013h6\u0016`!f^$QDT-9\u000e\u0013K(\u0014I#$Y)L*l\u000f=_\u001fFBzX\u001a]]<S@\u0006 7`#\u0016R@AB-43:dVR|`&\u0007\r2RG\u0006f)Mm\n;N\u007fF2\u001a\u000f\u007f\u0006FX/C/q\u001b\u0001t}\t\u0016f \u0015D\"1q#\u0080$\u0019\u007fzZ\bK\u001f\u0080\f;JhT\u0012kj1\u0018\ne HV}6.-UgvK\u0001|\u0016X\u0005\u0004\u0018r0:U\u0019o\tSuf\t@Ib@SmE1\u001c+u\u0003\f!LOo=9,F\u001f7J\u0016\u001d\u0010sy=n\u001f\u0016\u0006\u001e[{4-Q.|+\u0007\u001b\u0019D~\u000e\u001c~\u000bc\fK:jTgVQ)R/\nI\fwv^5I 1s\f1<v%\u0004G?\u00070\u001cC\u0006\u000euD\u000b}P\\Q2w-?\u0003l\\_6v\rN+$7Z\u000f\u0012;\u00069B[Gy\u0012!\u0016\u0080Y\u0014\u0010Z\u00114>uf{e\u0003\u0010\u001a\nmhOw@\"\u0006>Y#29A\u001d3\\\u001d:z\u000fwns}\u0006\u0012u\u0015\u0011@S}C\bZ A\u0001\u0001\u0001\u0001!\u0011i\u0019%Xn%\rC\u0018\u00061=[:\u0014NAx\u0080x~CO\u000ba~<+\u001f\u001fL(\u0013:Y Z3\u0003LK2\u0006Q\\+54429u%\u0006\u001aw\u0011c`O\u0010J|Sn\u0016S8\u0012P\u0007y\r+?\u0002]\u0011)M\u0018;.S[\n\u0007CbeOu\u000br)\u0006//\u0001Fa\"P\u000429u%\n\u0007Cbi_\u001f\\s\\+\u0018\u0013i{TI\u000b=$\u0019+^k\b',t\u001enp]\u00801@51\n\u001b@x:>8Fd\u0019xF#?pd#n\u0007*<#e=7t\u0018n\u007f\u001b\u0017o(FH9\u0019x\u001a.<\u000f\u000b\u0011\"&e%\u0001P\r\u0012\u0011\"\n#$w\f<\u001f\u007f\u001b0JI$/\u0015i.]=y_L\u000fmG\u0010\u001fs\rSw\u00049\u001f\u001fBp4\f\u001c\u00068\u0006g4*K\u0013 \"\u0010&I#$\u0080\u0012M&$a@\u000e)\u0010Sgb-4I<e%K<\u0010\b\u001d59\u001eO+F\u007fG6\u000245dl\u0002hjP.r#L8H\n\\+n\u007fI@\u001e(=Cdc%\u000e)\u0018zJ\\*}G2\u0016\u0012R\u001f\fnY];\u0013\u0004P^=/bl\u007fY\u0013w\u0002?Pu\u00138d(+.U\\eR\u0016P -MOz\u0002DwO\u0011O\u0019noBow|nJP\u0001O63\u0019{\u001cT,9\bYPN+\u0011:,:Lg\u0080zPkVt\u001f\t\u0011F>\u000f8@P\u001cmoNt7:\u0016 \u0005v\u001e,\u0004t-*S-Vt\n}G2\u0016*3D\u0007\u001bEga\u000e*lb^j\u0003f*`J\u0007CbaTw\u0003T\u0019\u00131Ky|\u001c0;\b\u001bQ\b s 2=/Nl$/o~B\u001bDV\u0014+MeY\n`s57_\u001dzV\t\u000eT\u0006B~d\u0003f)`ifx)\n\u001eG\u000e,Q\r\u001eit~\u0003i\u0013xnO\u001e.-|>\u0013K%\u0011,\r~\u001e3\u001d'nlY2JV\u0005\u0019{\u001c\u007f\u0003Ise'=E{v\u0013@<\u001dbeN7rh\u0018\\b$m(HWL?ZO=O4{U*O=G\u0019t>~hxg{K@n*7`\u001f\\cN{\u00148\u0007=,\u007f9\u000f*RE /%D\"1r$!\u0018y\"o\u000ee\u0017r\n\"Lbr\u0014\f\u0015\u00043U\u0010\u007fQ\u00029O\"L\u001fy\u00125s(\u001c\u00067\u0007!efb\r\u0018ZKmqk\\\fH##qX\"\u0001\u0001");

		// Transformations for decryption key expansion
		U1 = ArrayUtil.unpackI("\u0001\u0001\u0001\u0001\u00019\u0013\u000e\u0006H\u0003\"QY%\u001c\fH(\u0003\"QY7\u0017O%s\"Y];\u0016\u0010e4\fB\u0011i- I\u0017+NY[:\u0014M&\u001c~\u000bI7\u0018\u000fE4\u0016#\u0080+ Ig\u0013joL5y\u0013\u000e\u0006D^\u001aoo\u0080I\u0017+NsFrv^FTJ\u001dl0>\u001a?\u0013N\u007fF3V\u0080\u001d\u0007\"Y];\u0014n\u000eXH\rf)`i\u0017\u000f`vU@\u0012M&\u001cl\u0002hn\u001ej7KvhNw<4^nxV\u001a.O\r:'CneI\u0016G8\r@\u0010,|#i\u0013t\u007f\u000eKQ?\u0011%b\u0016,:ue;,l=)\\Y:\u0014.fRE %k\u007f9?;_v\u0002\u0012u0u4\u0002'=\u0003$^.XL\u001biMm\u0002\u001fsroK7\bgY\b\u00100zbY3LY OR?\u0012\rR\u0012{&ya:\u001b\u0018U\u000fM\u0016\u007f\u0012M\u00164y\u000b$O=:_\u007f~h\u000fWG\u007fU\\*1x\u0005\u000b\u00166mn|)pw\bwce<y\u0018wllX\u0016*\u0017<*[n*REQ\t=e\u001e\u0013Odx(a.<'8\u0005*9\u0013a\u007frP\u0013EW?)id\u0003vEhIPe\u0011,K>a\u00135~\u001cg_RI?~B\u001bR{*k\u0006\u0080wU\\;qR\u0017Tp\u0018\u00051[;\n\u001crPZP\u001eh[wU=4Tw\u0003S\u000et\u001f\t\u0018n9!\u0019\u0017\u0016b\u001a\u0003q?be4\u0019~J\u0003z\u0010Y_w'vRXC\u00148\u00076\u0018zJ\\\u0012.BI\u0014N\u001f\u0016\u0006\u0001axkK(`co]:>8A1\u0017~V\u0005\u0004\u0017>qi0%bF\u0004kO!}\"F?'\u0003E e#\u001ae\u001esbk<eL8!dmd)\f\u001c#Zrb/\nG\tI\u0016pJI$!y\u0010d,\u0018-Q\b_9EQh\u001b+o3{\u0014G\u001c<v\b;\u001d\u007fItfr_YWJ\u001e\u0006b/Cb)i\u0006\u007f3UN+\u0011>NX^ fl\u0080\f`w\u0004CT|t>(O$^\\<q=>\u0004\u00186ug[{@O\u0019d%Z,h{n\u007fI.\\+\u0018\u0013\u001e\u007fL3\u0005a\u0011i\u0019\u001f\u0014\fE\u00063*eU8\u001cIhU\u0018a*\u00100;BjLl9!RZZdVUf1?6fk\u0014\u001c\u0003\u000bK\u0013 25\u001a&nR\u001b\u0019D~zv_\u0015gJP\u0001O\r_v\u0002;#\u00062PO\u001a.O\u001fQ\u00131L|\u0006Q&jW^D\rK8NI=\u001c\u000b|N#\u001aH5GFt+i\u0016PScX?qTwl\b\"\u0006>v(b\u001e(\u0013-\u0005p~RR\u001f\fr\u0005\u00103;,Zxr]Z\r\u001f=v%Poa)\u0006/,j\u00102\u001f\u001e\nlb^MVVKN\u0006f\u0019CC2b#\u0005d+\u0011\u001c0\u001d4q3Br\u0013\u0007CB~L\u0011\t\u0007k\u001ccbo\u000ee\u0019\u000b4P\u0014\u00120[I\u001f\t]\u0005\u001a9tx0\t\u0007\u0010SXZ\u001b\\A=*\u001ei\u0016qE,#\u0080u\u0007A\u001dk0Q3okp\u000f\u00143\u0016JJp\u000e&K\r\u0014N\u0017~4\u0013nuK)\u0007\u0004\u000bPH3:\u0005&\u0014nl;k`\u007f4{reyYwoprQ\r\u0006\u001aw\u0011\u0011\u000eV;\t\u0007\u0010[9y#qW\u000e\u0006f\u001duu(0&F\u0004c'\u0017'\u001bR\u0011\bc\t j:\u0013a/\u0014\u0002!\u0016\u0080\\L\u0012HR\tg\u001er*'z\\\u001dY;\t;64uZY\u001ci\"HRgQ\u00029Kw241L;\u000eZby*b-\u0002G\u000e,R\u001d]EA\u000fhFu[ Se\u0012\u0013=\u0007J&8|@\u000e\u000b`\u0014M\u007f\u0080gU\u0002h\u000f\u0012;\u00062xj\"D8-H\nvg\u001eg,39iq:3\u001f\u001c:F\u00163{\u007fyXRgqH\u001d_pM\u0010sy@\"p\u0080oOz[%V\u0018\u0006g4<6lQL,\u000bH\u0010PX1\fuF*\u000e[ajzV#\u001bGLA\u0001");
		U2 = ArrayUtil.unpackI("\u0001\u0001\u0001\u0001\u0001-\u001d\n\u0007FDB\u0011i;\u0013\u000eFfDB\u0011i(\u001c\f(\u0014R\u0011m/\u0019KHs\u001bbaI5\u0015pe\f\u0016\u001dm.\u001dIW\u0013N\u007fu%\u001c\fH{\u001aKR2\u0016\u0010e4&5x&m\u001d\n\u0007CxoMx5pe\f\u0016.z#yzgcjeLvX_M,\n'\u0080!:+\u0080PR\u0011m/\u001d:wGl\u007fG3U0-\f\b0c+ Ig?NvAuWOu\\4;tgp>ZowqkMWgo\u001dT\"43%\u000bc`G HX\u001eR5\n\u0004\u0080\u0007f*0\t\u00131$V]{3F\u0016v_\u001bnm\u001dJ{sic\u0004s6@\u001d\n\u001e0;D\u0019{\u0018{\u0013AT\u001f\rZoWla\u000e5'7uP:9vF\u001c\u0004tO\u0004HX~Am\u001a&r\u0010h)`QG)IJS}1\u001db\fk\b\"+\u0080\tg\u001dZ}\u0006\u0018x\u001f\u001dp5?tH+L@+.R\u0019<C\b[[w7\u008058|\u0004B23\u001e~<|6v[KUL\u001e\rn7Ug#)\u0005\u001f\u001f\u000fJ(,\\Tq\u0017LT\u001cC\u0018\r\n1@4hJ#)\u0018\u001552E{c4eTs\t\u0016h\u007fq\n\u001a\rNt0*U ?aQ)~\u0015v[\u0080|+6\u001e9)L\u0006xLC \u000e\u001e\u0005NohmhDdn<+\u0016\u001aj|\u0001B\u0007zP\u0002Lw]\u0012I\f\u000bqO\"9 1I\u001aM?fr=Hn\u000e<\u0014;i4b\n\\N[L}eBIWai*gP\u000bUA1<wv\u0014pr=/\u001d_^)\u0019\f?p\u0003\u0002L\u001f\r5\u0018S3\u0003Bv(s?\u0011c_$\u0002#\u0010p\u0012\rs\u000f\"1v\u001e\u001ff\\Q2[2U\u0006Kr-yqB\u0005d\u0005#\u001bxee\u001bQ=\bq>LW)\u00070\u001d#,\u0018N\u00168\u001c^\nd\u000eh{D^\u0010\u0010%:sJp-,%\u0017CqX0qU5\u0003T\u001a+'~i\u001fglyPsv}\u0016p|\u0002k*~z^\\h\u0012oi\u001ey\u001f\u001f6L[{2N> g_2S-UD~7\u0080:\u0017nV\f\u0012\u000f\u0080&BC1\t5!\u0010\nFf#Z\u0015s=\u001cNe){\fq\u0015MX^!v\u000ev]\u0011.-mrl/3Y \u0019\u0013v\nN|\u0006&\n\u000fi[\rS iN\r\"'}{p\u0005t%hA\f\u00070;M~\u0012\u0003Yrh\rWe`)\n\u0019/~Ci\u0019el/bDf\u001cgek\u000eF>e2\rd[B#zV6;hj27`9*|nDQCk;TqO\u0080J\u0017\u00031_iiP\u0014yC\b`\u000e\u0016m|to-G\u000fw;S(sq\u0015\u0003RruHYNoEvqUg+ke\u0017CsM\u0011\"\u0019qR[2V\t\u0004\u0018O\u001ay6!yJ\u001a\u0002!\u007ff_\u0005\u00046\u000f\"1x\u0007z\r\u0006\u001aeBIXn\"P\u0005/\u0003y]:|V%\u0004\bi\u0004mN.b/\u0015Ou\u0018y#\u0016Rh{\u0004!\u0017v\u0018i\u001a\u001c68H\u000fz\u000beebGSf\f\u001agL?SJ7{%}\u0004\u0002F#$Z\u001dDWJwv`V0\u0080\u001aH9s=.\f88z2\u0007\u0003M|Q\t\u0007kTE\u0004\bn1=\u00129('CsO-;\u0014XR3Br\u0014\u0001\u0014\u000e)OLr\u0005\u0010r\u001dJ1\u0018~AQ\u000b~\u000efId\u000bE4\u000fzeT=nT-\u001e\u0005\u001eCZ{->\u000eu\u0011dEt)\u0001\\\u0006<\u0019ZO&^\u0007ka}\u0015q^\u0001d\u0007U\u0001O/#&H4cz\n\u0010j3\u000b*\u001f\u0004%i\\~`Hv0Jg\u000f@t+\u0001,H\t^\r\u0019|uQN\\W$s[t\u000ft\u0004Z\u001d5<MZ\u0010\u000eXcKZ?8=,iwy$O0Dg\bz?\u0080Qx\u007f&(=n\u0014\u001bLCt\u0005^[vi~VF$`(lY\u0006W#UGk\u00115}kH\u000e$\u0001\u0001");
		U3 = ArrayUtil.unpackI("\u0001\u0001\u0001\u0001\u00015\u0017\u000f\u0005GCbaI/\u001e\n\u0007gCbaI:\u0014NFSriI7\u0012M&$|\"1q%\u001a+8s\u0006eO7\u0017Pu,\n'];\u0013\u000eF\f~\rf$\u0019KHs.S[<u\u0017\u000f\u0005D<<x':+8s\u0006HW}R?%t25uf{lp{\u0016ET=q\u001dV?riI7\u0017_\u001d|$#\u0080$\u001a+\u0080W\u0006DG2\u0016\u0010e\b '{f[,(;8Z^:x8_mx796'+\f8\u000f*W\u001aZ\u0013\u0005>0d\u0010aLOi[+B\u0080D4\u0005XE\nT\u0012ko>r#K{eN7w\u000f\u0011~:5:bz\u001b`U\u0005OX`RM>\fq\n!*G_-x,41\u0007[\u0014H;(]`\u001bcNB@h\u0002dk/a7\rFyHtUHi$\u0015\u0002ej?\u0019;1Fv\u00071V@E*\u000f-\u007f\u0007||P\u000f;[ :cn&`V\u0011)M\u001ed8n.<\u0019 [\u001c~paYZ\u0010/^~[Zn&+&'G7\\\"t\u0012\u0015\u0003<\u0010\b%B6njy\u0012&jNd|G\u0005Y\u0013ZteT\r\fK\u001b\u001f#>2\u001a/*z\u0005\nT\u00809\u0007{G'zW\u0005k\u0010`()\u0015?KSn@~E[O]\u0015\u0012C|fepGOC=x4w;Rrw^Y\u000bMu}y!D=b\u0001f|0\u0005e\u0006F<\b\u0011]\u0010w%\rg\u001fcy_$WG^J^]\u001aqEg'n&\u007f\u0007!e,9U\u0015t(`+!\u0019 \f;Jxv_\u0018\u000f,wU\r\u0006ZxB\u0001f\u0014\u0007\u001b\fk:\u0002!{\u0016z \t1\u0080\u0012AR\u0011xIG:p\u0011Y;\u0004\u00103ni-n\u0019k\b\u00069W=#!C2G\"\u000e<s@\u000e)\u001f\u0003A\u001ffl\u0013D\u0018O\u0014:LgKYnoErit~\"p\u0018HS\u001d[%xW\u0016;\f\"9!\u0018y+\u001b6*MV7_u\u001040}(z9\u000f\u000bx~Nv\u0015\u007f\u007fwntIru\u000f}\u0010T\u001bfn?9g_Of0\u0019j\u0018{\"\u007f\\)]L7k.IH@D!b\u0019\u0005/\u0011\bEh\u0013R-K$\u001f\u000egb%>\u0006y\b',oR\u0003G{o\u000fWW7:zX\u001a-\rm\n;EI~CSF\u00185.\u0007c\u0010u'GyT?>=\u0003:S4U\u0006D\u0018VG?IB79tG*C0U\u0005B\u0018?b7\r36X7\"sNto6\u0007c^S\u0019G2,!R=l\u000b^4uP\\0]\u0015\u0016wbi\u0005v\u001e*y\u0014@eL\u0005y055>J}\"\b`GKw1zx\u0017#@<\u001e*\u0016:9\u000b\u0006Ey{$p\u0007x#;W+4\u00165c\f\":\u0006\t\u0011M9An\u0019kPBLh\rI\u001bQ=7-AQ@)p\u0003\u0002Y8\u0011Y<O=G\u0003Ok!e,q\u0011hC\u0018^=/\u001d\u007fKS\u0002Co\u0002w'X!X\u000b(.\f}\u0012\u000b\u0001t~\u0002BL;Lu9N[\\g(=F3)1d*<vMt&S*%\\=+?\u0002Ae\u0012\u0012mP.l%|:PkX\u0080cd]: GF\\[hYD\u0002'Vi\u0005\u0004?jc\u0002DCY\u001f\tUtT\":>\u0017\u001e\nj\u0019Z!yEA\nGQ\u0080&yC\u00029O%YX\u007fa)\u0007\u001fGsexF#\u001aGms*_\"jW\u000fCgb-~\u0007\u001fG{\t\u0006c:U\u0006NC^M7h\u0013oH\u00061?\u000b6/A2C\u0013\u0001(\u0018\u0018SdZt\u0011EHu[&\u0015P\u0002}5.\u007fo4{XeQ\b zVi\u0016dE&\u0007\r>{\u001d'nl8\u001a.:H$BmO\u0019ng-HLlr&/H\u001c_\u0016s|=\u0012hLbt\u0004\u0080\u0080@i;j\u0013T_8\u001aN&b/C/n;\u001d?kcA0Tvm7l\u0012+'V\t\u001b?0$GA\u0001");
		U4 = ArrayUtil.unpackI("\u0001\u0001\u0001\u0001\u0001%\u001b\f\b\u0005D\"1q7\u0018\u000fEED\"1q.\u001dJgd29u% Ig\u0013S\"QY9\u0011-V\u001cz5s(\u001c\u000b8{\u0016Em/\u001e\n\u0007+F\u007fG@\u0012M&$^\u0017j.%\u001b\f\b\u00034^^|Q-V\u001cz\fdl?,(\u0013:Y_{3~6\u001c~\u000bc,\u007f9\u000f,29u%\u001b\u001c0\u000f>KR@RM\u001e@l\u0003\u007f$\u0019KHW\u0004PT<Sn\u0016TP\u001cmoH<\\p73\\\u001d\u001bSN\u0006\\H\u0011,\rmI\u007f\u001fXrFQ&h5\u001c\u0016!\u0080aJC,ctjIv8WyR&1s'\\<$\t?]V}q}N>k\u0003()`ig\u001fMy\u0005Q\u001b\\0\u0017<R\u001aY\u0004.fd^\u0014m\u0010N2'\u001f`tAqf\u00181\u001c\u001e#}$z#$u\u0012rAs5`)\u001e\u0019#}$\u0019+`q\u0015H\u00179t>~hO\u001e.\u0010^*7Spo\t\u0015'\fZ\\wW`m\u0010n\u000eIxq-.XX/\u007fS-wSV[T$\u001c!\u0011zIK&\u001eHDPA[wuo\tSufB~d\u0003&\n-zrRG\u0006f\nP\u0012\u001fY!X\u0015}A%j\u0080`N>$\u0014>|\u00036\buTU\u000b n*7`b#.(//\tb>u\u00138d(4\u001f<Zvn)y|(m\u0006'<g=\u0011\"[qA3},C3\u0003a~DI/2|\u0013\u00073 2=/Nl$/e'o\ry0t\u00147S\\\u0004\u00113\u001b=+\u000b:BpV\u0011\u0010@F^%s{p\fA.|+\u0007\u0007-|aA_JD\u000e\bV\u001dAQD\u000b}PF\t@Ia4I<e$UxI-oBHZ7Q\u00177M4dC],\r\u0012\u0011\" \u0014\u0011G^q GU\u000fZ!\u00103r\n\"LeV]ft(\r7x#\u000fu:\u007fRhLdj2.\u0013<lC^\u0006QRQ\f}\u0016*\u001bUg<\u001c0;\bLX\u007f\u0014\u0080m\b\u0006<xg{K=H<7zay{\b?ljN3u\u0080\u001d4/J3XM6\\~\u0011\u00807U/&\\~\u0017e$A\"Q1M'\u0018\t\u0004eTJ)W8\u0012P\u0007pAS\u001fCv\u0004T\u0016wqB$>4H,,\u001ba}lMU'7\u0005^\u0015%?b)3L[\u001752\b{\u0014l=*`\u0014\u001f\u0002\u001dj>k\u0003bE\u000bd %3\\\u001d:a%b\u0018k\f!L`><\u0007\u001a\u001bp\u001c\u0011z'\u0016x\u001bD4\u000fj\r$gVQ)`&F/Zb(nXoC\u000b|1PC;OUY\n`s$#=\u0018[\t\u001fe\u007f\u00184pd&3\u0019=|KJ ^OQ\u000b]]\u0003\u001fc=>\u0014XD<R,,\u0016\u001aLK2\u0006QdCE\t'U!7M9haftc%\u000e)\u0004|\u0017!)\u000eU8B\u0004]\u001cI-\u0015h\u001f$\u0003`6\u00113\u00129\t4bho_\u0018\r &*\u000368\u0001|\u0013<Q,F\tWF\u007f\t\u000e\u0001:\u007f`!f^&_\u001d'n,\u0014\u0014_#L\u0015\u00192[.{g:\\j\u0015S-\u0007\u0016 \u0001es\tIx\\WvS@}hv,Jr2o\u001e`d#m\u000b4m\"A\u001c+u\u0003\r 5r\u0001Fb-\u0010\fK:jQO_L\u000fHEM-Q6#!\u0005e\u0011@S}&A](\u0013A,\u00801\u0017$\u0010$:M<cR\u000et7:\u0015u\u0011ul\bj41WQD\u0010$>!\u0003r\u001dmcgb/u\u001c4J1TCY \r\u001bX!\u001a:\n\u0001\u0014P\fj2p&I#$y\u000e\u0013K(w\u007f\u001b\u0017\u007f(\u001a~,N)\u0004P}#u\u000brpSD\u0007\u001fZ\u000f\u00147M<MW]rRaw%=7t\u0017+fvyR0$Np\u000f:>_\t fqzD`\u0080`vt5J*o\fMgSlX\"\u00187\u0016\u000f 6/a\u0018j{S\u001c6ITt+E\u000e\u000e\u0018RA\u0001");
	}
}
