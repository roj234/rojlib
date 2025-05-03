package roj.util;

/**
 * @author Roj234
 * @since 2022/10/8 8:22
 */
public class BitBuffer {
	public DynByteBuf list;
	public BitBuffer() {}
	public BitBuffer(DynByteBuf list) { init(list); }
	public BitBuffer init(DynByteBuf buf) {
		list = buf;
		bitPos = 0;
		ob = 0;
		return this;
	}

	public byte bitPos;

	// region read
	public final int readableBits() { return (list.readableBytes() << 3) - bitPos; }

	public final int readBit1() {
		byte bi = bitPos;
		int bit = ((list.get(list.rIndex) << (bi & 0x7)) >>> 7) & 0x1;

		list.rIndex += (++bi) >> 3;
		bitPos = (byte) (bi & 0x7);
		return bit;
	}
	public final int readBit(int numBits) {
		int d;
		int ri = list.rIndex;
		switch (numBits) {
			case 0: return 0;
			case 1: return readBit1();
			case 2, 3, 4, 5, 6, 7, 8, 9:
				d = ((((list.getU(ri++) << 8) | get0(ri)) << bitPos) & 0xFFFF) >>> 16 - numBits;
			break;
			case 10, 11, 12, 13, 14, 15, 16, 17:
				d = ((((list.getU(ri++) << 16) | (get0(ri++) << 8) | get0(ri)) << bitPos) & 0xFFFFFF) >>> 24 - numBits;
			break;
			case 18, 19, 20, 21, 22, 23, 24, 25:
				d = (((list.getU(ri++) << 24) | (get0(ri++) << 16) | (get0(ri++) << 8) | get0(ri)) << bitPos) >>> 32 - numBits;
			break;
			case 26, 27, 28, 29, 30, 31, 32: //case 33:
				d = (int) ((((((long) list.getU(ri++) << 32) | (get0(ri++) << 24) | (get0(ri++) << 16) | (get0(ri++) << 8) | get0(ri)) << bitPos) & 0xFFFFFFFFFFL) >>> 40 - numBits);
			break;
			default: throw new IllegalArgumentException("count("+numBits+") must in [0,32]");
		}
		next(numBits);
		return d;
	}
	private int get0(int i) { return i >= list.wIndex ? 0 : list.getU(i); }
	private void next(int count) {
		int idx = bitPos + count;
		list.rIndex += idx >> 3;
		bitPos = (byte) (idx & 0x7);
	}

	public final void skipBits(int count) {
		if (count < 0) retractBits(-count);
		else next(count);
	}
	public final void retractBits(int i) {
		list.rIndex -= i >> 3;
		i &= 7;

		int idx = bitPos - i;
		if (idx >= 0) {
			bitPos = (byte) idx;
			return;
		}
		list.rIndex--;
		bitPos = (byte) (idx + 8);
	}

	public final void endBitRead() {
		if (bitPos > 0) {
			bitPos = 0;
			list.rIndex++;
		}
	}
	// endregion
	// region write
	public long ob;

	public final void writeBit(int bit, int val) {
		int len = bitPos + bit;
		// max 32+7 bits in buffer
		long buf = (ob << bit) | (val & 0xFFFFFFFFL);

		while (len > 8) {
			len -= 8;
			list.put((byte) (buf >>> len));
		}

		this.ob = buf;
		bitPos = (byte) len;
	}
	public final void endBitWrite() {
		if (bitPos > 0) {
			list.put((byte)(ob << (8 - bitPos)));
			bitPos = 0;
		}
		ob = 0;
	}
	// endregion
}