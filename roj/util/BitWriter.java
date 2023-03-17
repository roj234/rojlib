package roj.util;

/**
 * @author Roj234
 * @since 2022/10/8 0008 8:22
 */
public class BitWriter {
	public DynByteBuf list;
	public BitWriter() {}
	public BitWriter(DynByteBuf list) {
		reset(list);
	}
	public BitWriter reset(DynByteBuf buf) {
		this.list = buf;
		this.bitIndex = 0;
		this.buf = 0;
		return this;
	}

	public byte bitIndex;
	public long buf;

	public int readBit1() {
		byte bi = this.bitIndex;
		int bit = ((list.get(list.rIndex) << (bi & 0x7)) >>> 7) & 0x1;

		list.rIndex += (++bi) >> 3;
		bitIndex = (byte) (bi & 0x7);
		return bit;
	}

	public int get0(int i) {
		return i >= list.wIndex ? 0 : list.getU(i);
	}

	public final int readBit(int numBits) {
		int d;
		int ri = list.rIndex;
		switch (numBits) {
			case 0: return 0;
			case 1:
				return readBit1();
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
				d = ((((list.getU(ri++) << 8) | get0(ri)) << bitIndex) & 0xFFFF) >>> 16 - numBits;
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
			case 16:
			case 17:
				d = ((((list.getU(ri++) << 16) | (get0(ri++) << 8) | get0(ri)) << bitIndex) & 0xFFFFFF) >>> 24 - numBits;
				break;
			case 18:
			case 19:
			case 20:
			case 21:
			case 22:
			case 23:
			case 24:
			case 25:
				d = (((list.getU(ri++) << 24) | (get0(ri++) << 16) | (get0(ri++) << 8) | get0(ri)) << bitIndex) >>> 32 - numBits;
				break;
			case 26:
			case 27:
			case 28:
			case 29:
			case 30:
			case 31:
			case 32:
			//case 33:
				d =
					(int) ((((((long) list.getU(ri++) << 32) | (get0(ri++) << 24) | (get0(ri++) << 16) | (get0(ri++) << 8) | get0(ri)) << bitIndex) & 0xFFFFFFFFFFL) >>> 40 - numBits);
				break;
			default:
				throw new IllegalArgumentException("count("+numBits+") must in [0,32]");
		}
		list.rIndex += (bitIndex += numBits) >> 3;
		bitIndex &= 0x7;
		return d;
	}

	public void skipBits(int i) {
		bitIndex += i;
		list.rIndex += bitIndex >> 3;
		this.bitIndex = (byte) (bitIndex & 0x7);
	}

	public void retractBits(int i) {
		list.rIndex -= i >> 3;
		i&=7;

		int idx = bitIndex - i;
		if (idx >= 0) {
			bitIndex = (byte) idx;
			return;
		}
		list.rIndex--;
		bitIndex = (byte) (idx + 8);
	}


	public int readableBits() {
		return (list.readableBytes() << 3) - bitIndex;
	}

	public void endBitRead() {
		if (bitIndex > 0) {
			bitIndex = 0;
			list.rIndex++;
		}
	}

	public final void writeBit(int bit, int val) {
		int len = bitIndex + bit;
		// max 32+7 bits in buffer
		long buf = (this.buf << bit) | (val & 0xFFFFFFFFL);

		while (len > 8) {
			len -= 8;
			list.put((byte) (buf >>> len));
		}

		this.buf = buf;
		bitIndex = (byte) len;
	}

	public void endBitWrite() {
		if (bitIndex > 0) {
			list.put((byte)(this.buf << (8 - bitIndex)));
			bitIndex = 0;
		}
		buf = 0;
	}
}
