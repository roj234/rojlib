package roj.crypt;

import roj.concurrent.OperationDone;
import roj.io.IOUtil;
import roj.math.S128i;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.PrimitiveIterator;

/**
 * 基于FF1的格式保留加密(FPE)算法，用于高效随机抽样。
 * <p>
 * 虽然现代密码学认为FPE算法存在安全隐患（不适用于加密场景），但它在随机抽样领域具有显著的空间效率优势。
 * 本算法生成不重复的随机数组仅需O(log n)的空间复杂度，而常规算法（如Knuth Shuffle）需要O(n)的额外空间。这使得它特别适合对大规模数据集进行随机抽样。
 *
 * @author Roj234
 * @since 2025/07/16 02:58
 */
public class FPE {
	private static final S128i TWO = S128i.valueOf(2);
	private static final int MAX_LENGTH_BYTES = 16;
	private static final int ROUNDS = 3;

	private final S128i a, b;
	private final MessageAuthenticCode mac;
	private final byte[] hash;
	private S128i prime, primeEnc;

	/**
	 * 生成指定长度的随机排列迭代器
	 *
	 * @param length (uint64) 待抽样集合总大小 (3 ≤ length ≤ 2<sup>64</sup>-1)
	 * @param seed   32位随机种子，相同种子生成相同排列
	 * @return 返回索引的随机排列迭代器，每次调用产生[0, length-1]范围内的不重复数值
	 */
	public static PrimitiveIterator.OfInt shuffle(long length, int seed) {
		return new PrimitiveIterator.OfInt() {
			private final S128i i = new S128i();
			private final FPE fpe = new FPE(new byte[] {(byte) (seed >> 24), (byte) (seed >> 16), (byte) (seed >> 8), (byte) seed}, S128i.valueOf(length), ByteList.EMPTY);

			@Override public boolean hasNext() {return i.longValueExact() < length;}
			@Override public int nextInt() {
				S128i shuffledIndex = fpe.fei_encrypt(i);
				i.add(1, i);
				return (int) shuffledIndex.longValueExact();
			}
		};
	}

	public FPE(byte[] seed, S128i length, DynByteBuf salt) {
		try {
			mac = new HMAC(MessageDigest.getInstance("SHA-256"));
		} catch (NoSuchAlgorithmException e) {
			throw OperationDone.NEVER;
		}
		mac.init(seed);

		// 如果换成BigInteger，可以支持完整的128位长度，不过不可变对象会浪费不少内存
		if (length.getHigh() != 0) throw new IllegalArgumentException("该实现使用了128位整数，所以它只支持64位以内的长度(n.high must be 0)");
		mac.update(IOUtil.getSharedByteBuf().putLong(length.longValueExact()));
		mac.update(salt);

		hash = mac.digest();

		a = new S128i();
		b = new S128i();
		factor(length);
	}

	private static final int[] PRIMES = {
			2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71,
			73, 79, 83, 89, 97, 101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163, 167, 173,
			179, 181, 191, 193, 197, 199, 211, 223, 227, 229, 233, 239, 241, 251, 257, 263, 269, 271, 277, 281,
			283, 293, 307, 311, 313, 317, 331, 337, 347, 349, 353, 359, 367, 373, 379, 383, 389, 397, 401, 409,
			419, 421, 431, 433, 439, 443, 449, 457, 461, 463, 467, 479, 487, 491, 499, 503, 509, 521, 523, 541
	};

	/**
	 * 分解n为a和b（a >= b 且尽可能接近）
	 */
	private void factor(S128i original_n) {
		if (original_n.compareTo(TWO) <= 0) throw new IllegalArgumentException("N must > 2");
		int lowZeroCount = original_n.numberOfTrailingZeros();

		S128i a = S128i.valueOf(1);
		S128i b = S128i.valueOf(1);
		S128i n = new S128i(original_n);

		a.shiftLeft(lowZeroCount / 2);
		b.shiftLeft(lowZeroCount - (lowZeroCount / 2));
		n.shiftRight(lowZeroCount);

		for (int prime : PRIMES) {
			S128i primeBig = S128i.valueOf(prime);
			while (n.mod(primeBig).isZero()) {
				a.multiply(prime, a);
				if (a.compareTo(b) > 0) {
					var temp = a;
					a = b;
					b = temp;
				}

				n = n.divide(primeBig);
			}
		}

		a.multiply(n, a);
		if (a.compareTo(b) < 0) {
			var temp = a;
			a = b;
			b = temp;
		}

		if (b.getHigh() == 0 && b.longValue() == 1) {
			factor(n.set(original_n).add(1, n));
			primeEnc = fei_encrypt(original_n);
			prime = original_n;
			return;
		}

		this.a.set(a);
		this.b.set(b);
	}

	private S128i F(int round_no, S128i R) {
		mac.update(hash);
		mac.update(IOUtil.getSharedByteBuf().putInt(round_no).putLong(R.getHigh()).putLong(R.longValue()));

		ByteList wrap = DynByteBuf.wrap(mac.digest());
		return new S128i(wrap.readLong(), wrap.readLong());
	}

	public S128i fei_encrypt(S128i x) {
		for (int i = 0; i < ROUNDS; i++) {
			S128i[] divRem = x.divideAndRemainder(b);
			S128i L = divRem[0];
			S128i R = divRem[1];

			// W = (L + F(i, R)) mod a
			// X = a*R + W

			S128i W = L.add(F(i, R), L).mod(a);
			x = a.multiply(R, R).add(W, W);
		}

		if (x.equals(prime)) return x.set(primeEnc);
		return x;
	}

	public S128i fei_decrypt(S128i x) {
		if (x.equals(primeEnc)) x = prime;

		for (int i = ROUNDS-1; i >= 0; i--) {
			S128i[] divRem = x.divideAndRemainder(a);
			S128i R = divRem[0];
			S128i W = divRem[1];

			// L = (W - F(i, R)) mod a
			// X = b*L + R

			S128i L = W.sub(F(i, R), W).mod(a);
			x = b.multiply(L, L).add(R, R);
		}

		return x;
	}
}
