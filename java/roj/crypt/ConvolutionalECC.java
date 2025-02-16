package roj.crypt;

import roj.util.BitBuffer;
import roj.util.DynByteBuf;

import javax.crypto.ShortBufferException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.Random;

/**
 * <a href="https://www.youtube.com/watch?v=b3_lVSrPB6w">...</a>
 * @author Roj234
 * @since 2022/12/26 0026 14:06
 */
public class ConvolutionalECC extends RCipherSpi {
	private final int inBits, outBits, outMask;

	private int buffer;
	private final int[] history;
	private final int historyCapacity;
	private final byte[] decision;
	private Random rnd = new MT19937();

	public ConvolutionalECC(int inBits, int outBits) {
		this.inBits = inBits;
		this.outBits = outBits;
		this.outMask = (1<<outBits)-1;

		int historySize = 0;
		int readBits = 0;
		while (readBits+inBits < outBits) {
			historySize++;
			readBits += inBits;
		}

		if (historySize == 0) historySize++;
		this.historyCapacity = historySize;
		if (readBits != outBits) historySize++;
		this.history = new int[historySize];
		this.decision = new byte[inBits];
	}

	public int getInBits() {return inBits;}
	public int getOutBits() {return outBits;}

	public int getSymbolCount() {return symbolCount;}
	public int getSymbolError() {return symbolError;}

	private final BitBuffer ob = new BitBuffer();

	//region RCipherSpi
	private boolean _cipherEncrypt;
	@Override public int engineGetOutputSize(int in) {return _cipherEncrypt ? in*8 / inBits * outBits : in / outBits * inBits;}

	@Override
	public void init(int mode, byte[] key, AlgorithmParameterSpec par, SecureRandom random) throws InvalidAlgorithmParameterException, InvalidKeyException {
		_cipherEncrypt = mode == ENCRYPT_MODE;
		buffer = 0;
		rnd = random == null ? new Random(42L) : random;
		assert key == null || key.length == 0;
	}

	@Override
	public void crypt(DynByteBuf in, DynByteBuf out) throws ShortBufferException {
		if (ob.list != out) ob.init(out);

		if (engineGetOutputSize(in.readableBytes()) > out.writableBytes()) throw new ShortBufferException();

		if (_cipherEncrypt) encode(in);
		else decode(in);
	}
	@Override protected void cryptFinal1(DynByteBuf in, DynByteBuf out) {if(_cipherEncrypt) encodeFinish();}
	//endregion

	public void init(DynByteBuf output) {ob.init(output);buffer = 0;rnd.setSeed(42L);}
	public void encode(DynByteBuf data) {
		var ib = new BitBuffer(data);
		int history = buffer;

		for (int i = (ib.readableBits() + inBits - 1) / inBits; i > 0; i--) {
			history = (history << inBits) | ib.readBit(inBits);
			// 每个codeword提供了[outBits-inBits]位的历史信息用于恢复数据
			ob.writeBit(outBits, encodeSymbol(history&outMask));
		}

		buffer = history;
	}
	public void encodeFinish() {
		int buf = buffer;
		do {
			buf = (buf << inBits) & outMask;
			ob.writeBit(outBits, encodeSymbol(buf));
		} while (buf != 0);

		buffer = 0;
		ob.endBitWrite();
		ob.list = null;
	}

	private int symbolCount, symbolError;
	public void decode(DynByteBuf data) {
		var ib = new BitBuffer(data);
		int hbPos = buffer;

		for (int i = (ib.readableBits() + outBits - 1) / outBits; i > 0; i--) {
			var exceptingBuf = decodeSymbol(ib.readBit(outBits));

			history[hbPos] = exceptingBuf;
			if (hbPos == history.length-1) {
				// keep a sliding window, 足以容纳第一个字节开始发送，至第一个字节的所有数据从历史缓冲区中消失的所有输入位
				// 之后从这些历史输入中，对每位，所有历史信息中占比更多的那个被假定为其真实结果
				Arrays.fill(decision, (byte) 0);

				for (int j = 0; j < historyCapacity; j++) {
					var a = history[j] >>> (inBits * j);

					for (int bit = 0; bit < inBits; bit++) {
						if ((a & (1 << bit)) != 0) decision[bit]++;
					}
				}
				System.arraycopy(history, 1, history, 0, history.length-1);

				var input = 0;
				for (int bit = 0; bit < inBits; bit++) {
					byte b = decision[bit];
					if (b > historyCapacity/2) input |= 1 << bit;

					if (b != 0 && b != historyCapacity) symbolError++;
					symbolCount++;
				}

				ob.writeBit(inBits, input);
			} else {
				hbPos++;
			}
		}

		buffer = hbPos;
	}

	private int encodeSymbol(int i) {
		var sh = rnd.nextInt(outBits);
		var xor = rnd.nextInt(1 << outBits);

		i ^= xor;
		i = (i >>> sh) | (i << (outBits-sh));
		return i & outMask;
	}
	private int decodeSymbol(int i) {
		var sh = rnd.nextInt(outBits);
		var xor = rnd.nextInt(1 << outBits);

		i = (i << sh) | (i >>> (outBits-sh));
		i ^= xor;
		return i & outMask;
	}
}