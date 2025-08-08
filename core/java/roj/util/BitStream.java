package roj.util;

/**
 * @author Roj234
 * @since 2022/10/8 8:22
 */
public final class BitStream {
	public DynByteBuf byteBuffer;

	public BitStream() {}
	public BitStream(DynByteBuf byteBuffer) { init(byteBuffer); }
	public BitStream init(DynByteBuf buf) {
		byteBuffer = buf;
		bitPos = 0;
		bitBuffer = 0;
		return this;
	}

	public byte bitPos;

	// region read
	public final int readableBits() { return (byteBuffer.readableBytes() << 3) - bitPos; }

	public final int readBit1() {
		byte bi = bitPos;
		int bit = ((byteBuffer.get(byteBuffer.rIndex) << (bi & 0x7)) >>> 7) & 0x1;

		byteBuffer.rIndex += (++bi) >> 3;
		bitPos = (byte) (bi & 0x7);
		return bit;
	}
	public final int readBit(int numBits) {
		int d;
		int ri = byteBuffer.rIndex;
		switch (numBits) {
			case 0: return 0;
			case 1: return readBit1();
			case 2, 3, 4, 5, 6, 7, 8, 9:
				d = ((((byteBuffer.getU(ri++) << 8) | get0(ri)) << bitPos) & 0xFFFF) >>> 16 - numBits;
			break;
			case 10, 11, 12, 13, 14, 15, 16, 17:
				d = ((((byteBuffer.getU(ri++) << 16) | (get0(ri++) << 8) | get0(ri)) << bitPos) & 0xFFFFFF) >>> 24 - numBits;
			break;
			case 18, 19, 20, 21, 22, 23, 24, 25:
				d = (((byteBuffer.getU(ri++) << 24) | (get0(ri++) << 16) | (get0(ri++) << 8) | get0(ri)) << bitPos) >>> 32 - numBits;
			break;
			case 26, 27, 28, 29, 30, 31, 32: //case 33:
				d = (int) ((((((long) byteBuffer.getU(ri++) << 32) | (get0(ri++) << 24) | (get0(ri++) << 16) | (get0(ri++) << 8) | get0(ri)) << bitPos) & 0xFFFFFFFFFFL) >>> 40 - numBits);
			break;
			default: throw new IllegalArgumentException("count("+numBits+") must in [0,32]");
		}
		next(numBits);
		return d;
	}
	private int get0(int i) { return i >= byteBuffer.wIndex ? 0 : byteBuffer.getU(i); }
	private void next(int count) {
		int idx = bitPos + count;
		byteBuffer.rIndex += idx >> 3;
		bitPos = (byte) (idx & 0x7);
	}

	public final void skipBits(int count) {
		if (count < 0) retractBits(-count);
		else next(count);
	}
	public final void retractBits(int i) {
		byteBuffer.rIndex -= i >> 3;
		i &= 7;

		int idx = bitPos - i;
		if (idx >= 0) {
			bitPos = (byte) idx;
			return;
		}
		byteBuffer.rIndex--;
		bitPos = (byte) (idx + 8);
	}

	public final void endBitRead() {
		if (bitPos > 0) {
			bitPos = 0;
			byteBuffer.rIndex++;
		}
	}
	// endregion
	// region write
	public long bitBuffer;

	public final void writeBit(int bit, int val) {
		int len = bitPos + bit;
		// max 32+7 bits in buffer
		long buf = (bitBuffer << bit) | (val & 0xFFFFFFFFL);

		while (len > 8) {
			len -= 8;
			byteBuffer.put((byte) (buf >>> len));
		}

		this.bitBuffer = buf;
		bitPos = (byte) len;
	}
	public final void endBitWrite() {
		if (bitPos > 0) {
			byteBuffer.put((byte)(bitBuffer << (8 - bitPos)));
			bitPos = 0;
		}
		bitBuffer = 0;
	}
	// endregion
}