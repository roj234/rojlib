package roj.security;

import roj.crypt.MT19937;
import roj.util.ArrayGetter;
import roj.util.BitWriter;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.nio.charset.StandardCharsets;

import static roj.security.ConvolutionalCoder.popcount;

/**
 * <a href="https://www.youtube.com/watch?v=b3_lVSrPB6w">...</a>
 * @author Roj234
 * @since 2022/12/26 0026 14:06
 */
public class ConvolutionalCoder2 {
	static final int r12_6[] = {073, 061};
	static final int r12_7[] = {0161, 0127};
	static final int r12_8[] = {0225, 0373};
	static final int r12_9[] = {0767, 0545};
	static final int r13_6[] = {053, 075, 047};
	static final int r13_7[] = {0137, 0153, 0121};
	static final int r13_8[] = {0333, 0257, 0351};
	static final int r13_9[] = {0417, 0627, 0675};

	public static void main(String[] args) {
		ConvolutionalCoder2 coder = new ConvolutionalCoder2(r12_6, 2);

		ByteList test = ByteList.wrap(args[0].getBytes(StandardCharsets.UTF_8));
		System.out.println("data:"+test.dump());

		ByteList tmp = new ByteList();
		coder.begin(test, tmp);
		coder.encode();
		coder.encodeFinish();
		System.out.println("code:"+tmp.dump());

		test.clear();
		coder.begin(tmp, test);
		coder.decode();
		coder.decodeFinish();
		System.out.println("data:"+test.dump());

		System.out.println("随机翻转1%的bit...");
		int len = tmp.wIndex();
		byte[] list = tmp.list;
		MT19937 rnd = new MT19937(114514);
		for (int i = 0; i < len<<3; i++) {
			if (rnd.nextFloat() >= 0.99) {
				list[i>>>3] ^= 1<<(i&7);
			}
		}
		System.out.println("code:"+tmp.dump());

		test.clear();
		coder.begin(tmp, test);
		coder.decode();
		coder.decodeFinish();
		System.out.println("data:"+test.dump());
	}

	private final Object table;
	private final ArrayGetter access;
	private final int outBits, outMask;
	private int buffer;

	public ConvolutionalCoder2(int[] poly, int outBits) {
		if (poly.length <= 1) throw new IllegalArgumentException("outBits <= inBits");

		this.outBits = outBits;
		this.outMask = (1<<outBits)-1;

		if (outBits <= 8) {
			byte[] table = new byte[1 << outBits];
			for (int i = 0; i < table.length; i++) {
				table[i] = (byte) ioBit(i, poly);
			}

			this.table = table;
			this.access = ArrayGetter.BG;
		} else if (outBits <= 16) {
			char[] table = new char[1 << outBits];
			for (int i = 0; i < table.length; i++) {
				table[i] = (char) ioBit(i, poly);
			}

			this.table = table;
			this.access = ArrayGetter.CG;
		} else {
			int[] table = new int[1<<outBits];
			for (int i = 0; i < table.length; i++) {
				table[i] = ioBit(i, poly);
			}

			this.table = table;
			this.access = ArrayGetter.IG;
		}
	}
	private static int ioBit(int in, int[] poly) {
		int out = 0;
		int mask = 1;
		for (int i : poly) {
			out |= (popcount(in & i) & 1) != 0 ? mask : 0;
			mask <<= 1;
		}
		return out;
	}

	private final BitWriter ib = new BitWriter(), ob = new BitWriter();

	public void begin(DynByteBuf input, DynByteBuf output) {
		ib.reset(input); ob.reset(output);
	}
	public int getEncodedSize(int lengthBit) {
		return lengthBit*outBits;
	}
	public void encode() {
		int buf = buffer;

		for (int i = ib.readableBits(); i > 0; i--) {
			buf = ((buf<<1)&outMask) | ib.readBit1();

			// 每个codeword提供了outBits-1位的历史信息用于恢复数据
			ob.writeBit(outBits, access.get(table, buf));
		}

		buffer = buf;
	}
	public void encodeFinish() {
		encode();

		int buf = buffer;
		while (buf != 0) {
			buf = (buf<<1)&outMask;
			ob.writeBit(outBits, access.get(table, buf));
		}

		buffer = 0;
		ob.endBitWrite();
	}

	public int getDecodedSize(int lengthBit) {
		return ((lengthBit+1)/outBits);
	}
	private void decode() {

	}
	private void decodeFinish() {
	}
}