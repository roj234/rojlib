package roj.net.mss.crypto;

import roj.compiler.runtime.RtUtil;
import roj.crypt.KeyExchange;

import java.math.BigInteger;
import java.util.function.Supplier;

/**
 * @author Roj234
 * @since 2023/4/3 12:23
 */
public final class DHGroup implements Supplier<KeyExchange> {
	public static final DHGroup ffdhe3072 = new DHGroup("ffdhe3072", 2,
			"\1@\200\200\200\200\200\200\200\200v`CR2#^ST+\177" +
					"r-!\24P(P\17ctFBtFT5WTb\34\21#G\"PxMJh:c%m~z?L}7" +
					"\34\r\31vm>Q\34\21\13^E1_[>y|,V\177Z-\23\"Pk 0BN" +
					"n\5\26Gf+P>RXOkX\nXPv?%l\31(D\17\17\b\33\27xr*R" +
					"\36XP`i:\b?\26F\5n.tN'\13g*\37I\36\u001f0,a+d\3Z" +
					"J 7\17E\22*&mZ\17 oskn.m\16@RQ\36(QU>rf\34uX\26^" +
					"_<\16f<\rCa{?$]\u000b7]\24a\30tDN|y^M\2Q\nN\25" +
					"\16\6w\rFf\177O;1Y \177\35P\n\r^p\25\7\bD\200\17" +
					"d5d?[t\36n@Lb<\5!\3r2p\r\16{\27BmYp\33)NBZ<\200)" +
					"D.\u000591Hz~gyk\\\36Z$\21\33oy5{8`\32a\tqb[\34z" +
					"\u001e8\34\23\b>7$S5Hpxx\u00172L\bl\4\7mH\26pqM" +
					"\24\6\neO\f!M\32\24_\21p\25(T{E*9[uoNJsr\bO +0" +
					"\177]X!V:exn\37u\34&=*\tm;\7kyS\r_7\16v7D\\}j\6V" +
					"c\23\naVt!l~\25|_zq7!x\20{ZlqLe\17Kmm29p\200\200" +
					"\200\200\200\200\200\200\200", 275);

	public final String name;
	public final BigInteger p, g;
	public final int expSize;

	private DHGroup(String name, int generator, String prime, int expSize) {
		this.name = name;
		this.p = new BigInteger(RtUtil.unpackB(prime));
		this.g = BigInteger.valueOf(generator);
		this.expSize = expSize;
	}

	public KeyExchange get() { return new DH(this); }
}
