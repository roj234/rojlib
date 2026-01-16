package roj.ecc;

import org.jetbrains.annotations.Nullable;
import roj.annotation.MayMutate;
import roj.io.source.Source;
import roj.text.logging.Logger;
import roj.ui.EasyProgressBar;
import roj.util.ArrayCache;
import roj.util.FastFailException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * 不要用C语言谢谢喵，灌注Java谢谢喵
 * @author Roj234
 * @since 2024/12/19 14:02
 */
public final class ReedSolomonCodec {
	private final byte[] fullBuf, ecBuf;
	private final short[] logGenerator;
	private final int dataBytes;
	public ReedSolomonCodec(int dataBytes, int ecBytes) {
		if (dataBytes+ecBytes > 255) throw new IllegalArgumentException("Max chunk=255 (not 256!)");
		if (ecBytes <= 0) throw new IllegalArgumentException("EC bytes must > 0");
		if (dataBytes <= 0) throw new IllegalArgumentException("Data bytes must > 0");
		var generator = polyNewGenerator(ecBytes);
		this.logGenerator = new short[ecBytes];
		for (int i = 0; i < ecBytes; i++) {
			// 注意: 这里没有log函数最后的&255
			logGenerator[i] = LOG_TABLE[generator[i + 1] & 255];
		}
		this.fullBuf = new byte[dataBytes+ecBytes];
		this.ecBuf = new byte[ecBytes];
		this.dataBytes = dataBytes;
	}
	public int chunkSize() {return fullBuf.length;}
	public int dataBytes() {return dataBytes;}
	public int ecBytes() {return logGenerator.length;}
	public int maxError() {return ecBytes()/2;}

	/**
	 * 输入一个chunkSize大小的数组，根据前dataBytes个字节填入后ecBytes数据
	 */
	public void generateCode(byte[] buf) {
		byte[] state = ecBuf;
		Arrays.fill(state, (byte)0);

		for (int i = 0; i < dataBytes; i++)
			lfsrUpdate(state, 0, buf[i]);

		System.arraycopy(state, 0, buf, dataBytes, ecBuf.length);
	}

	private void lfsrUpdate(byte[] state, int off, byte input) {
		// State(x) = (State(x) * x + input) % Generator(x)
		int feedback = (state[off] ^ input) & 0xFF;

		var lgGen = this.logGenerator;
		int ecBytes = lgGen.length;

		// 这虽然是一个条件跳转，但实测可以在随机数据集上提升~5%性能
		if (feedback == 0) {
			System.arraycopy(state, off + 1, state, off, ecBytes - 1);
			state[off + ecBytes - 1] = 0;
			return;
		}

		int logFeedback = LOG_TABLE[feedback & 255];

		int i = 0;
		for (; i < ecBytes - 1; i++) {
			// NewState[i] = OldState[i+1] ^ (Generator[i+1] * Feedback)
			state[off + i] = (byte) (state[off + i + 1] ^ exp( lgGen[i] + logFeedback));
		}

		state[off + i] = exp( lgGen[i] + logFeedback);
	}

	/**
	 * @return 发现并修复了n个错误
	 */
	public int errorCorrection(byte[] buf) {return errorCorrection(buf, null);}

	/**
	 * 纠正错误
	 * @param errorPositions 已知错误的索引，如果不为null，那么必须是<b>所有</b>错误的索引
	 */
	public int errorCorrection(byte[] buf, @Nullable byte[] errorPositions) {
		var poly = polyNew(buf);
		var syndromeCoeff = ecBuf;
		var eccSize = syndromeCoeff.length;

		int error = 0;
		for (var i = 0; i < eccSize; i++) {
			var val = polyEval(poly, EXP_TABLE[i]);
			syndromeCoeff[syndromeCoeff.length-1 - i] = val;
			error |= val;
		}
		if (error == 0) return 0;

		byte[] sigma, omega, errorLocations;
		int errorCount;

		if (errorPositions != null) {
			sigma = P1;

			errorCount = errorPositions.length;
			errorLocations = new byte[errorCount];

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

			// omega生成算法错在哪？
			/*sigma = runBerlekampMassey(syndromePoly);
			omega = polyMul(sigma, syndromePoly);

			int start = Math.max(0, omega.length - eccSize);
			for (int i = 0; i < start; i++) omega[i] = 0;

			omega = polyNew(omega);*/

			errorCount = sigma.length-1;
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

				quotient = polyAddMono(quotient, degreeDiff, scale);
				r = polyAdd(r, polyMulMono(rLast, degreeDiff, scale), false);
			}

			// 好孩子不要学我这么赋值哦
			t = polyAdd(tLast, polyMul(quotient, tLast = t), true);
		}

		var sigmaTildeAtZero = t[t.length - 1];
		if (sigmaTildeAtZero == 0) throw new FastFailException("[ECC]sigmaTilde(0) == 0");

		var inverse = inv(sigmaTildeAtZero);
		var sigma = polyMulScalar(t, inverse);
		var omega = polyMulScalar(r, inverse);
		return new byte[][] {sigma, omega};
	}

	private byte[] runBerlekampMassey(byte[] syndromes) {
		int n = syndromes.length;
		byte[] sigma = {1};
		byte[] bPoly = {1};
		int L = 0;
		int m = 1;
		int bValue = 1;

		for (int nIdx = 0; nIdx < n; nIdx++) {
			// 1. 计算偏差 d
			// d = S[n] + sum_{i=1}^{L} sigma[i] * S[n-i]
			int delta = syndromes[n - 1 - nIdx] & 0xFF;
			for (int i = Math.min(sigma.length - 1, L); i > 0; i--) {
				delta ^= mul(sigma[sigma.length - 1 - i], syndromes[n - 1 - (nIdx - i)]);
			}

			if (delta == 0) {
				// 预测正确，只需增加位移
				m++;
			} else {
				// 2. 预测失败，更新 sigma
				byte[] oldSigma = sigma.clone();

				// 计算 scale = d / bValue
				int scale = mul(delta, inv(bValue));

				// sigma = sigma - scale * x^m * bPoly
				// 注意：在 GF(2^8) 中，减法等于加法 (XOR)
				byte[] updateTerm = polyMulMono(bPoly, m, scale);
				sigma = polyAdd(sigma, updateTerm, false);

				if (2 * L <= nIdx) {
					// 3. 更新阶数和修正项
					L = nIdx + 1 - L;
					bPoly = oldSigma;
					bValue = delta;
					m = 1;
				} else {
					m++;
				}
			}
		}
		return polyNew(sigma);
	}

	//region 交错和反交错
	private static final Logger LOGGER = Logger.getLogger("RECC");

	/**
	 * 通过交错，使连续错误分散，进而使得原本只能纠正n个错误的RS码能纠正(n * lanes)个连续错误
	 */
	public void generateInterleavedCode(InputStream dataIn, OutputStream eccOut, int lanes, @Nullable EasyProgressBar bar) throws IOException {
		var dataSize = dataBytes();
		var eccSize = ecBytes();

		int laneIndex = 0;
		int laneRounds = 0;
		byte[] ecc = ArrayCache.getByteArray(lanes * eccSize, true);

		byte[] ioBuffer = ArrayCache.getIOBuffer();

		while (true) {
			int r = dataIn.read(ioBuffer);
			if (r < 0) break;

			int i = 0;
			while (true) {
				int rest = Math.min(r - i, lanes - laneIndex);
				if (rest == 0) break;

				while (rest > 0) {
					lfsrUpdate(ecc, laneIndex++ * eccSize, ioBuffer[i++]);
					rest--;
				}

				if (laneIndex == lanes) {
					if (++laneRounds == dataSize) {
						for (int j = 0; j < eccSize; j++) {
							for (int k = 0; k < lanes; k++) {
								eccOut.write(ecc[k * eccSize + j]);
							}
						}
						Arrays.fill(ecc, 0, lanes * eccSize, (byte) 0);

						laneRounds = 0;
					}

					laneIndex = 0;
				}
			}

			if (bar != null) bar.increment(r);
		}

		ArrayCache.putArray(ioBuffer);

		// 末尾零填充
		if ((laneIndex|laneRounds) != 0) {
			for (; laneIndex < lanes; laneIndex++) {
				lfsrUpdate(ecc, laneIndex * eccSize, (byte) 0);
			}

			if (laneRounds != dataSize) {
				for (int laneIndex1 = 0; laneIndex1 < lanes; laneIndex1++) {
					for (int j = laneRounds+1; j < dataSize; j++) {
						lfsrUpdate(ecc, laneIndex1 * eccSize, (byte) 0);
					}
				}
			}

			for (int j = 0; j < eccSize; j++) {
				for (int i = 0; i < lanes; i++) {
					eccOut.write(ecc[i * eccSize + j]);
				}
			}
		}

		ArrayCache.putArray(ecc);
	}

	public int interleavedErrorCorrection(Source file, long dataSize, int lanes, @Nullable EasyProgressBar bar) throws IOException {
		var codeword = dataBytes();
		var matrix = ArrayCache.getByteArray(chunkSize() * lanes, false);
		var poly = fullBuf;

		var eccFileOffset = dataSize;
		var errorFixed = 0;

		while (true) {
			int r = lanes * codeword;
			if (r > dataSize - file.position()) {
				r = (int) (dataSize - file.position());
				if (r == 0) break;

				for (int i = r; i < lanes * codeword; i++) matrix[i] = 0;
			}

			file.readFully(matrix, 0, r);
			if (bar != null) bar.increment(r);
			int fileDataLength = lanes * codeword;

			var pos = file.position();

			file.seek(eccFileOffset);
			int eccr = lanes * ecBytes();
			file.readFully(matrix, fileDataLength, eccr);
			eccFileOffset += eccr;

			boolean hasError = false;
			for (int i = 0; i < lanes; i++) {
				int j = 0;
				for (; j < codeword; j++) poly[j] = matrix[j * lanes + i];
				for (; j < poly.length; j++) poly[j] = matrix[fileDataLength + (j-codeword) * lanes + i];

				try {
					int found = errorCorrection(poly);
					if (found != 0) {
						hasError = true;

						for (int k = 0; k < codeword; k++) {
							matrix[k * lanes + i] = poly[k];
						}
					}
					errorFixed += found;
				} catch (Exception e) {
					errorFixed |= Integer.MIN_VALUE;
					LOGGER.warn("分块["+i+"/"+lanes+"]发生不可纠正的错误："+e.getMessage()+", 偏移量：{}", (pos - codeword * (lanes - i)));
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
	//region GF(p = x^8 + x^4 + x^3 + x^2 + 1, n = 8)
	private static final byte[] EXP_TABLE = new byte[511 + 512 + 512];
	private static final short[] LOG_TABLE = new short[256];
	static {
		for (var i = 0; i < 8; i++) EXP_TABLE[i] = (byte) (1 << i);
		for (var i = 8; i < 256; i++) EXP_TABLE[i] = (byte) (EXP_TABLE[i - 4] ^ EXP_TABLE[i - 5] ^ EXP_TABLE[i - 6] ^ EXP_TABLE[i - 8]);
		for (var i = 0; i < 255; i++) LOG_TABLE[EXP_TABLE[i]&255] = (short) i;

		// 分支预测优化: 消除exp中的条件跳转, 提升~50%的性能
		for (var i = 255; i < 511; i++) EXP_TABLE[i] = EXP_TABLE[i - 255];
		// 分支预测优化: 消除lfsrUpdate中的条件跳转, 提升~20%的性能
		LOG_TABLE[0] = 512;
		// 初始化数组已经填充0了
		//for (var i = 512; i < EXP_TABLE.length; i++) EXP_TABLE[i] = 0;
	}
	private static int log(int v) {
		assert v != 0;
		return LOG_TABLE[v&255]&255;
	}
	private static byte exp(int v) {
		assert v >= 0;
		// if (v >= 255) v -= 255;
		return EXP_TABLE[v];
	}
	private static int mul(int a, int b) {
		return EXP_TABLE[LOG_TABLE[a&255] + LOG_TABLE[b&255]];
		/*if (a == 0 || b == 0) return 0;
		if (a == 1) return b;
		if (b == 1) return a;
		return exp(log(a) + log(b));*/
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
	private static byte[] polyMulScalar(@MayMutate byte[] poly, int scalar) {
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
	private static int polyMod(@MayMutate byte[] p1, byte[] p2) {
		int p1Length = p1.length;

		int zero = 0;
		while (zero < p1Length && p1[zero] == 0) zero++;

		int degrees = p1Length - zero;
		if (degrees >= p2.length) {
			do {
				int ratio = log(p1[zero]) - log(p2[0]);

				for (var j = 0; j < p2.length; j++) {
					p1[j + zero] ^= exp(log(p2[j]) + ratio);
				}

				while (zero < p1Length && p1[zero] == 0) zero++;
			} while ((degrees = p1Length - zero) >= p2.length);
		}

		return degrees;
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
	private static byte[] polyAddMono(@MayMutate byte[] p1, int degree, int coefficient) {
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
