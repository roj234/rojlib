package roj.crypt;

import org.jetbrains.annotations.Nullable;
import roj.io.FastFailException;
import roj.io.source.Source;
import roj.ui.EasyProgressBar;
import roj.util.DynByteBuf;
import sun.misc.Unsafe;

import java.io.IOException;

import static roj.reflect.ReflectionUtils.u;

/**
 * <We should never use C language>
 * @author Roj234
 * @since 2024/12/19 0019 14:02
 */
public final class ReedSolomonECC {
	public void generateInterleavedCode(Source in, Source out, int stride, @Nullable EasyProgressBar bar) throws IOException {
		var codeword = dataSize();
		var matrix = new byte[stride][chunkSize()];
		var buffer = new byte[stride * Math.max(codeword, eccSize())];

		while (true) {
			int r = in.read(buffer, 0, stride * codeword);
			if (bar != null) bar.increment(r);
			if (r < stride * codeword) {
				if (r < 0) break;
				assert in.read() < 0 : "excepting EOF: "+in.getClass();

				stride = (r + codeword - 1) / codeword;
				for (; r < stride * codeword; r++) buffer[r] = 0;
			}

			int i = 0;
			for (int j = 0; j < codeword; j++) {
				for (int k = 0; k < stride; k++) {
					matrix[k][j] = buffer[i++];
				}
			}

			for (int j = 0; j < stride; j++) {
				generateCode(matrix[j]);
			}

			i = 0;
			for (int j = codeword; j < chunkSize(); j++) {
				for (int k = 0; k < stride; k++) {
					buffer[i++] = matrix[k][j];
				}
			}
			out.write(buffer, 0, i);
		}
	}

	public int interleavedErrorCorrection(Source file, long dataSize, int stride, @Nullable EasyProgressBar bar) throws IOException {
		var codeword = dataSize();
		var matrix = new byte[stride][chunkSize()];
		var buffer = new byte[stride * Math.max(codeword, eccSize())];

		var eccOffset = dataSize;
		var errorFixed = 0;

		while (true) {
			int r = stride * codeword;
			if (r > dataSize - file.position()) {
				r = (int) (dataSize - file.position());
				if (r == 0) break;

				stride = (r + codeword - 1) / codeword;
			}

			file.readFully(buffer, 0, r);
			if (bar != null) bar.increment(r);
			for (int i = stride * codeword - 1; i >= r; i--) buffer[i] = 0;

			int i = 0;
			for (int j = 0; j < codeword; j++) {
				for (int k = 0; k < stride; k++) {
					matrix[k][j] = buffer[i++];
				}
			}

			var pos = file.position();

			file.seek(eccOffset);
			r = stride * eccSize();
			file.readFully(buffer, 0, r);
			eccOffset += r;

			i = 0;
			for (int j = codeword; j < chunkSize(); j++) {
				for (int k = 0; k < stride; k++) {
					matrix[k][j] = buffer[i++];
				}
			}

			boolean hasError = false;
			for (int j = 0; j < stride; j++) {
				try {
					int found = errorCorrection(matrix[j]);
					if (found != 0) hasError = true;
					errorFixed += found;
				} catch (Exception e) {
					errorFixed |= Integer.MIN_VALUE;
					System.out.println("unrecoverable error at ["+j+"/"+ stride +"] of "+(pos + stride * (j - codeword))+": "+e.getMessage());
				}
			}

			if (hasError) {
				i = 0;
				for (int j = 0; j < codeword; j++) {
					for (int k = 0; k < stride; k++) {
						buffer[i++] = matrix[k][j];
					}
				}

				file.seek(pos - i);
				file.write(buffer, 0, i);
			} else {
				file.seek(pos);
			}
		}

		return errorFixed;
	}

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

	public void generateCode(DynByteBuf in, DynByteBuf out) {
		in.readFully(buf, 0, codeBytes);
		generateCode(buf);
		out.put(buf, codeBytes, gen.length - 1);
	}
	public void generateCode(byte[] buf) {
		for (int i = codeBytes; i < buf.length; i++) buf[i] = 0;
		var eccBytes = gen.length - 1;

		var ecc = polyModNC(polyNew(buf), gen, tmp);
		for (int x = 0; x < eccBytes; x++) {
			var modIndex = x + _eccLength/*ecc.length*/ - eccBytes;
			if (modIndex >= 0) buf[x + codeBytes] = ecc[modIndex];
		}
	}

	/**
	 * @return 发现并修复了n个错误
	 */
	public int errorCorrection(DynByteBuf data) {
		u.copyMemory(data.array(), data._unsafeAddr(), buf, Unsafe.ARRAY_BYTE_BASE_OFFSET, buf.length);
		var errorCount = errorCorrection(buf);
		if (errorCount == 0) return 0;
		u.copyMemory(buf, Unsafe.ARRAY_BYTE_BASE_OFFSET, data.array(), data._unsafeAddr(), buf.length);
		return errorCount;
	}
	public int errorCorrection(byte[] buf) {
		var poly = polyNew(buf);
		var eccSize = eccSize();
		var syndromeCoeff = tmp;

		int error = 0;
		for (var i = 0; i < eccSize; i++) {
			var val = polyEval(poly, EXP_TABLE[i]);
			syndromeCoeff[syndromeCoeff.length-1 - i] = (byte) val;
			error |= val;
		}
		if (error == 0) return 0;

		var syndromePoly = polyNew(syndromeCoeff);
		var sigmaOmega = runEuclideanAlgorithm(polyNewMono(eccSize, 1), syndromePoly, eccSize);

		var sigma = sigmaOmega[0];
		var errorCount = sigma.length-1;
		var errorLocations = new byte[errorCount];
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
		//System.out.println("检测到"+errorCount+"个字节发生了错误！");

		var omega = sigmaOmega[1];
		// use Forney's Formula to get magnitude diff
		for (var i = 0; i < errorCount; i++) {
			var pos = buf.length-1 - log(errorLocations[i]);
			if (pos < 0) throw new FastFailException("[ECC]Bad location "+errorLocations[i]);

			var invLoc = inv(errorLocations[i]);
			var denominator = 1;

			for (var j = 0; j < errorCount; j++) {
				if (i != j) {
					denominator = mul(denominator, 1 ^ mul(errorLocations[j], invLoc));
				}
			}

			buf[pos] ^= mul(polyEval(omega, invLoc), inv(denominator));
		}
		return errorCount;
	}

	private static byte[][] runEuclideanAlgorithm(byte[] a, byte[] b, int R) {
		// Assume a's degree is >= b's
		assert a.length >= b.length;

		byte[] rLast = a, r = b;
		byte[] tLast = P0, t = P1;

		// Run Euclidean algorithm until r's degree is less than R/2
		R = R / 2 + 1;
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
	private static int polyEval(byte[] poly, int i) {
		if (poly.length == 0) return 0;

		if (i == 0) return poly[poly.length - 1];

		int val = poly[0];
		if (i == 1) {
			for (var j = 1; j < poly.length; j++) {
				val ^= poly[j];
			}
		} else {
			for (var j = 1; j < poly.length; j++) {
				val = mul(i, val) ^ poly[j];
			}
		}
		return val;
	}
	//endregion
}
