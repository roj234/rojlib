package roj.crypt;

import roj.config.ParseException;
import roj.util.BitBuffer;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.crypto.ShortBufferException;
import java.nio.charset.StandardCharsets;
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
	public static void main(String[] args) throws ParseException {
		ConvolutionalECC cecc = new ConvolutionalECC(24,25);
		var rsecc = new ReedSolomonECC(255-32, 4);

		ByteList test = ByteList.wrap(
			("This is a very long string, and there are "+(100d*(1-(float)cecc.inBits/cecc.outBits))+"% redundant data guarded by ConvolutionECC, "+100.0d*(1-rsecc.dataRate())+"% redundant data guarded by ReedSolomon ECC")
				.getBytes(StandardCharsets.UTF_8));
		System.out.println("\""+test+"\"");
		test.wIndex(rsecc.dataSize());
		rsecc.generateCode(test, test);
		test.rIndex = 0;
		System.out.print("| rsecc="+test.wIndex());

		ByteList tmp = new ByteList();
		cecc.init(tmp);
		cecc.encode(test);
		cecc.encodeFinish();
		System.out.println("| cecc="+tmp.wIndex());

		float rate = Float.parseFloat(args[0]);
		System.out.println("\n随机翻转"+(rate*100)+"%的bit, SNR="+10*Math.log10((1-rate)/rate)+"dB\n");
		test.rIndex = 0;
		test.wIndex(0x10);
		tmp.rIndex = 0;
		shuffle(tmp, rate);
		shuffle(test, rate);
		System.out.println("example if no protect="+test.dump());
		System.out.println();

		test.clear();
		cecc.init(test);
		cecc.decode(tmp);
		System.out.println(test.dump());
		int i = rsecc.errorCorrection(test);
		test.wIndex(rsecc.dataSize());
		System.out.println("\""+test+"\"\nCECC errorRate "+cecc.symbolError+"/"+cecc.symbolCount+"\nRSECC errorRate "+i+"/231");
	}

	private static void shuffle(ByteList tmp, float rate) {
		int len = tmp.wIndex();
		byte[] list = tmp.list;
		MT19937 rnd = new MT19937();
		for (int i = 0; i < len<<3; i++) {
			if (rnd.nextFloat() < rate)
				list[i>>>3] ^= (rnd.nextBoolean()?1:0)<<(i&7);
		}
	}

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

	private BitBuffer ob = new BitBuffer();

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