package roj.crypt;

import org.jetbrains.annotations.Nullable;
import roj.util.FastFailException;
import roj.io.source.Source;
import roj.reflect.Unsafe;
import roj.text.logging.Logger;
import roj.ui.EasyProgressBar;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;

import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import static roj.reflect.Unsafe.U;

/**
 * <We should never use C language>
 * @author Roj234
 * @since 2024/12/19 14:02
 */
public final class ReedSolomonECC extends RCipher {
	private int _eccLength;
	private final byte[] buf, gen, tmp;
	private final int codeBytes;
	public ReedSolomonECC(int codeBytes, int ecBytes) {
		if (codeBytes+ecBytes > 255) throw new IllegalStateException("Max chunk=255 (not 256!)");
		this.gen = polyNewGenerator(ecBytes);
		this.buf = new byte[codeBytes+ecBytes];
		this.tmp = new byte[ecBytes];
		this.codeBytes = codeBytes;
	}
	public float dataRate() {return (float) codeBytes / buf.length;}
	public int chunkSize() {return buf.length;}
	public int dataSize() {return codeBytes;}
	public int eccSize() {return buf.length - codeBytes;}
	public int maxError() {return eccSize()/2;}

	//region RCipherSpi
	private boolean _cipherEncrypt;
	// 允许使用FeedbackCipher的ECB模式进行填充。
	@Override protected boolean isBareBlockCipher() {return true;}
	@Override public int engineGetBlockSize() { return dataSize(); }
	@Override public int engineGetOutputSize(int in) {return _cipherEncrypt ? in / dataSize() * chunkSize() : in / chunkSize() * dataSize();}

	@Override
	public void init(int mode, byte[] key, AlgorithmParameterSpec par, SecureRandom random) throws InvalidAlgorithmParameterException, InvalidKeyException {
		_cipherEncrypt = mode == ENCRYPT_MODE;
		assert key == null || key.length == 0;
	}

	@Override
	public void crypt(DynByteBuf in, DynByteBuf out) throws ShortBufferException {
		if (engineGetOutputSize(in.readableBytes()) > out.writableBytes()) throw new ShortBufferException();

		while (in.readableBytes() >= dataSize()) cryptOneBlock(in, out);
	}
	@Override
	public void cryptOneBlock(DynByteBuf in, DynByteBuf out) {
		if (_cipherEncrypt) generateCode(in, out.put(in, dataSize()));
		else {
			errorCorrection(in);
			out.put(in, dataSize());
			in.rIndex += chunkSize();
		}
	}
	//endregion

	public void generateCode(DynByteBuf in, DynByteBuf out) {
		in.readFully(buf, 0, codeBytes);
		generateCode(buf);
		out.put(buf, codeBytes, gen.length - 1);
	}
	public void generateCode(byte[] buf) {
		for (int i = codeBytes; i < buf.length; i++) buf[i] = 0;

		var ecc = polyModNC(polyNew(buf), gen, tmp);

		int eccLength = gen.length - 1;
		var zeroOff = eccLength - _eccLength/*real ecc length*/;

		for (int x = 0; x < zeroOff; x++) buf[x + codeBytes] = 0;
		System.arraycopy(ecc, 0, buf, zeroOff + codeBytes, eccLength - zeroOff);
	}

	/**
	 * @return 发现并修复了n个错误
	 */
	public int errorCorrection(DynByteBuf data) {
		U.copyMemory(data.array(), data._unsafeAddr(), buf, Unsafe.ARRAY_BYTE_BASE_OFFSET, buf.length);
		var errorCount = errorCorrection(buf);
		if (errorCount == 0) return 0;
		U.copyMemory(buf, Unsafe.ARRAY_BYTE_BASE_OFFSET, data.array(), data._unsafeAddr(), buf.length);
		return errorCount;
	}
	public int errorCorrection(byte[] buf) {return errorCorrection(buf, null);}

	/**
	 * 纠正错误
	 * @param errorPositions 已知错误的索引，如果不为null，那么必须是<b>所有</b>错误的索引
	 */
	public int errorCorrection(byte[] buf, @Nullable byte[] errorPositions) {
		var poly = polyNew(buf);
		var eccSize = eccSize();
		var syndromeCoeff = tmp;

		int error = 0;
		for (var i = 0; i < eccSize; i++) {
			var val = polyEval(poly, EXP_TABLE[i]);
			syndromeCoeff[syndromeCoeff.length-1 - i] = val;
			error |= val;
		}
		if (error == 0) return 0;

		byte[] sigma, omega, errorLocations;

		if (errorPositions != null) {
			sigma = P1;
			errorLocations = new byte[errorPositions.length];

			byte[] polyTmp = {0, 1};
			for (int i = 0; i < errorPositions.length; i++) {
				byte X_j = exp(buf.length - 1 - errorPositions[i]);
				errorLocations[i] = X_j;

				polyTmp[0] = X_j;
				sigma = polyMul(sigma, polyTmp);
			}

			// 计算 Ω(x) = S(x) * σ(x) mod x^eccSize
			omega = polyMul(syndromeCoeff, sigma);

			int start = Math.max(0, omega.length - eccSize);
			for (int i = 0; i < start; i++) omega[i] = 0;

			omega = polyNew(omega);
		} else {
			var syndromePoly = polyNew(syndromeCoeff);
			var sigmaOmega = runEuclideanAlgorithm(polyNewMono(eccSize, 1), syndromePoly, eccSize);

			sigma = sigmaOmega[0];
			omega = sigmaOmega[1];

			int errorCount = sigma.length-1;
			errorLocations = new byte[errorCount];
			// use Chien's search to find errored locations
			if (errorCount == 1) {
				errorLocations[0] = sigma[0];
			} else {
				assert errorCount != 0;

				int found = 0, bytes = 1;
				while (true) {
					if (polyEval(sigma, bytes) == 0) {
						errorLocations[found] = inv(bytes);
						if (++found >= errorCount) break;
					}

					if (++bytes > 255) throw new FastFailException("[ECC]错误数量太多 "+found+"/"+errorCount);
				}
			}
		}

		// use Forney's Formula to get magnitude diff
		int errorCount = errorLocations.length;
		for (var i = 0; i < errorCount; i++) {
			var pos = buf.length-1 - log(errorLocations[i]);
			if (pos < 0) throw new FastFailException("[ECC]Bad location "+errorLocations[i]);

			var invX = inv(errorLocations[i]);
			var denominator = 1;

			for (var j = 0; j < errorCount; j++) {
				if (i != j) {
					denominator = mul(denominator, 1 ^ mul(errorLocations[j], invX));
				}
			}

			buf[pos] ^= mul(polyEval(omega, invX), inv(denominator));
		}
		return errorCount;
	}

	private static byte[][] runEuclideanAlgorithm(byte[] a, byte[] b, int R) {
		// Assume a's degree is >= b's
		assert a.length >= b.length;

		byte[] rLast = a, r = b;
		byte[] tLast = P0, t = P1;

		// Run Euclidean algorithm until r's degree is less than R/2
		R = R / 2;
		while (r.length >= R) {
			var tmp = rLast;
			rLast = r;
			r = tmp;

			// Divide rLast by r, with quotient in q and remainder in r
			assert rLast.length != 0 : "rLast == 0";

			var quotient = P0;
			var denominatorLeadingTermInverse = inv(rLast[0]);

			int degreeDiff;
			while ((degreeDiff = r.length - rLast.length) >= 0) {
				var scale = mul(r[0], denominatorLeadingTermInverse);

				quotient = polyAddMonoNC(quotient, degreeDiff, scale);
				r = polyAdd(r, polyMulMono(rLast, degreeDiff, scale), false);
			}

			// 好孩子不要学我这么赋值哦
			t = polyAdd(tLast, polyMul(quotient, tLast = t), true);
		}

		var sigmaTildeAtZero = t[t.length - 1];
		if (sigmaTildeAtZero == 0) throw new FastFailException("[ECC]sigmaTilde(0) == 0");

		var inverse = inv(sigmaTildeAtZero);
		var sigma = polyMulScalarNC(t, inverse);
		var omega = polyMulScalarNC(r, inverse);
		return new byte[][] {sigma, omega};
	}

	private static final Logger LOGGER = Logger.getLogger("RECC");
	//region 交错和反交错
	/**
	 * 通过交错，使连续错误分散，进而使得原本只能纠正n个错误的RS码能纠正(n * stride)个连续错误
	 */
	public void generateInterleavedCode(InputStream in, OutputStream out, int stride, @Nullable EasyProgressBar bar) throws IOException {
		var codeword = dataSize();
		// 一个codeword行，stride列的矩阵
		var matrix = ArrayCache.getByteArray(Math.max(eccSize(), codeword) * stride, false);
		var poly = buf;

		while (true) {
			int r = in.read(matrix, 0, stride * codeword);
			if (bar != null) bar.increment(r);
			if (r < stride * codeword) {
				if (r < 0) break;
				assert in.read() < 0 : "excepting EOF: "+in.getClass();

				stride = (r + codeword - 1) / codeword;
				for (; r < stride * codeword; r++) matrix[r] = 0;
			}

			for (int i = 0; i < stride; i++) {
				int j = 0;
				for (; j < codeword; j++) poly[j] = matrix[j * stride + i];
				for (; j < poly.length; j++) poly[j] = 0;

				byte[] ecc = polyModNC(polyNew(poly), gen, tmp);

				int eccLength = gen.length - 1;
				var zeroOff = eccLength - _eccLength/*real ecc length*/;

				for (int x = 0; x < zeroOff; x++) matrix[x * stride + i] = 0;
				for (int x = zeroOff; x < eccLength; x++) matrix[x * stride + i] = ecc[x - zeroOff];
			}

			out.write(matrix, 0, eccSize() * stride);
		}

		ArrayCache.putArray(matrix);
	}

	public int interleavedErrorCorrection(Source file, long dataSize, int stride, @Nullable EasyProgressBar bar) throws IOException {
		var codeword = dataSize();
		var matrix = ArrayCache.getByteArray(chunkSize() * stride, false);
		var poly = buf;

		var eccFileOffset = dataSize;
		var errorFixed = 0;

		while (true) {
			int r = stride * codeword;
			if (r > dataSize - file.position()) {
				r = (int) (dataSize - file.position());
				if (r == 0) break;

				stride = (r + codeword - 1) / codeword;
				for (int i = r; i < stride * codeword; i++) matrix[i] = 0;
			}

			file.readFully(matrix, 0, r);
			if (bar != null) bar.increment(r);
			int fileDataLength = stride * codeword;

			var pos = file.position();

			file.seek(eccFileOffset);
			int eccr = stride * eccSize();
			file.readFully(matrix, fileDataLength, eccr);
			eccFileOffset += eccr;

			boolean hasError = false;
			for (int i = 0; i < stride; i++) {
				int j = 0;
				for (; j < codeword; j++) poly[j] = matrix[j * stride + i];
				for (; j < poly.length; j++) poly[j] = matrix[fileDataLength + (j-codeword) * stride + i];

				try {
					int found = errorCorrection(poly);
					if (found != 0) {
						hasError = true;

						for (int k = 0; k < codeword; k++) {
							matrix[k * stride + i] = poly[k];
						}
					}
					errorFixed += found;
				} catch (Exception e) {
					errorFixed |= Integer.MIN_VALUE;
					LOGGER.warn("分块["+i+"/"+stride+"]发生不可纠正的错误："+e.getMessage()+", 偏移量：{}", (pos - codeword * (stride - i)));
				}
			}

			if (hasError) {
				file.seek(pos - r);
				file.write(matrix, 0, r);
			} else {
				file.seek(pos);
			}
		}

		ArrayCache.putArray(matrix);
		return errorFixed;
	}
	//endregion
	//region GF(p = 283, n = 8)
	private static final byte[] EXP_TABLE = new byte[256], LOG_TABLE = new byte[256];
	static {
		for (var i = 0; i < 8; i++) EXP_TABLE[i] = (byte) (1 << i);
		for (var i = 8; i < 256; i++) EXP_TABLE[i] = (byte) (EXP_TABLE[i - 4] ^ EXP_TABLE[i - 5] ^ EXP_TABLE[i - 6] ^ EXP_TABLE[i - 8]);
		for (var i = 0; i < 255; i++) LOG_TABLE[EXP_TABLE[i]&255] = (byte) i;
	}
	private static int log(int v) {
		assert v != 0;
		return LOG_TABLE[v&255]&255;
	}
	private static byte exp(int v) {
		assert v >= 0;
		return EXP_TABLE[v%255];
	}
	private static int mul(int a, int b) {
		if (a == 0 || b == 0) return 0;
		if (a == 1) return b;
		if (b == 1) return a;
		return exp(log(a) + log(b));
	}
	private static byte inv(int v) {return EXP_TABLE[255 - log(v)];}
	//endregion
	//region GFPolynomial
	private static final byte[] P0 = {}, P1 = {1};
	private static byte[] polyNewGenerator(int size) {
		byte[] poly = P1;
		byte[] p2 = {1, 0};

		for (var i = 0; i < size; i++) {
			p2[1] = EXP_TABLE[i];
			poly = polyMul(poly, p2);
		}

		return poly;
	}
	private static byte[] polyNewMono(int degree, int coefficient) {
		assert degree >= 0;
		if (coefficient == 0) return P0;

		var coefficients = new byte[degree + 1];
		coefficients[0] = (byte) coefficient;
		return coefficients;
	}
	private static byte[] polyNew(byte[] poly) {
		if (poly[0] == 0) {
			int zero = 0;
			do {
				if (++zero == poly.length) return P0;
			} while (poly[zero] == 0);

			var tmp = new byte[poly.length - zero];
			System.arraycopy(poly, zero, tmp, 0, tmp.length);
			return tmp;
		}
		return poly;
	}
	private static byte[] polyMul(byte[] p1, byte[] p2) {
		byte[] p3 = new byte[p1.length + p2.length - 1];
		for (var i = 0; i < p1.length; i++) {
			for (var j = 0; j < p2.length; j++) {
				p3[i + j] ^= mul(p1[i], p2[j]);//exp(log(p1[i]) + log(p2[j]));
			}
		}
		return polyNew(p3);
	}
	/**
	 * polyMulMono(poly, 0, scalar)
	 * @return poly * mono(0, scalar)
	 */
	private static byte[] polyMulScalarNC(byte[] poly, int scalar) {
		if (scalar == 0) return P0;
		if (scalar == 1) return poly;

		var length = poly.length;
		for (var i = 0; i < length; i++) {
			poly[i] = (byte) mul(poly[i], scalar);
		}
		return polyNew(poly);
	}
	/**
	 * polyMul(poly, mono(degree, coefficient))
	 * @return poly * mono(degree, coefficient)
	 */
	private static byte[] polyMulMono(byte[] poly, int degree, int coefficient) {
		assert degree >= 0;
		if (coefficient == 0) return P0;
		if (coefficient == 1 && degree == 0) return poly;

		var length = poly.length;
		var out = new byte[length + degree];
		for (var i = 0; i < length; i++) {
			out[i] = (byte) mul(poly[i], coefficient);
		}
		return polyNew(out);
	}
	private byte[] polyModNC(byte[] p1, byte[] p2, byte[] output) {
		if (p1.length < p2.length) {
			_eccLength = p1.length;
			return p1;
		}

		int zero = 0;

		p1 = p1.clone();
		do {
			int ratio = log(p1[zero]) - log(p2[0]);

			for (var j = 0; j < p2.length; j++) {
				p1[j+zero] ^= exp(log(p2[j]) + ratio);
			}

			while (zero < p1.length && p1[zero] == 0) zero++;
		} while (p1.length - zero >= p2.length);

		_eccLength = p1.length - zero;
		System.arraycopy(p1, zero, output, 0, _eccLength);
		return output;
		//return polyNew(p1);
	}
	private static byte[] polyAdd(byte[] p1, byte[] p2, boolean copy) {
		if (p1.length == 0) return p2;
		if (p2.length == 0) return p1;

		byte[] sum;
		int offset = p1.length - p2.length;
		if (offset >= 0) {
			sum = copy ? p1.clone() : p1;
			p1 = p2;
		} else {
			offset = -offset;
			sum = p2.clone();
		}

		for (int i = 0; i < p1.length; i++) sum[i + offset] ^= p1[i];
		return polyNew(sum);
	}
	private static byte[] polyAddMonoNC(byte[] p1, int degree, int coefficient) {
		if (p1.length == 0) return polyNewMono(degree, coefficient);
		if (coefficient == 0) return p1;

		//p1 = p1.clone();
		p1[p1.length-1 - degree] ^= coefficient;
		return p1;
	}
	private static byte polyEval(byte[] poly, int i) {
		if (poly.length == 0) return 0;

		if (i == 0) return poly[poly.length - 1];

		int val = poly[0];
		if (i == 1) {
			for (var j = 1; j < poly.length; j++) {
				val ^= poly[j];
			}
		} else {
			for (var j = 1; j < poly.length; j++) {
				val = (byte) (mul(i, val) ^ poly[j]);
			}
		}
		return (byte) val;
	}
	//endregion
}
