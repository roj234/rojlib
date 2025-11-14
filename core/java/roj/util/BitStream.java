package roj.util;

/**
 * @author Roj234
 * @since 2022/10/8
 * @revised 2025/12/27
 * @version 2.0
 */
public sealed class BitStream {
	private static int MASK(int nbits) {return nbits == 32 ? -1 : (1 << nbits) - 1;}

	public DynByteBuf bytes;
	public long buffer;
	public int bitCount;

	public BitStream() {}
	public BitStream(DynByteBuf data) {this.bytes = data;}
	public BitStream init(DynByteBuf data) {
		bytes = data;
		bitCount = 0;
		buffer = 0;
		return this;
	}

	public int readableBits() {return bitCount + bytes.readableBytes() * 8;}
	public boolean isReadable() {return bitCount > 0 || bytes.isReadable();}
	public boolean isBigEndian() {return true;}

	public final int read1Bit() {
		ensure(1);
		return (int) (buffer >>> (bitCount -= 1));
	}

	public int peekBits(int nbits) {
		if (nbits == 0) return 0;
		ensure(nbits);
		return (int) (buffer >>> (bitCount - nbits)) & MASK(nbits);
	}

	public int readBits(int nbits) {
		if (nbits == 0) return 0;
		ensure(nbits);
		return (int) (buffer >>> (bitCount -= nbits)) & MASK(nbits);
	}

	public int peekOptionalBits(int nbits) {
		if (nbits == 0) return 0;
		nbits = effort(nbits);
		return (int) (buffer >>> (bitCount - nbits)) & MASK(nbits);
	}

	public int readOptionalBits(int nbits) {
		if (nbits == 0) return 0;
		nbits = effort(nbits);
		return (int) (buffer >>> (bitCount -= nbits)) & MASK(nbits);
	}

	private void ensure(int nbits) {
		while (bitCount < nbits) {
			// throw if not enough
			buffer = (buffer << 8) | bytes.readUnsignedByte();
			bitCount += 8;
		}
	}

	private int effort(int nbits) {
		while (bitCount < nbits) {
			if (!bytes.isReadable()) {
				nbits = bitCount;
				break;
			}

			buffer = (buffer << 8) | bytes.readUnsignedByte();
			bitCount += 8;
		}
		return nbits;
	}

	/**
	 * 跳过指定的位数。
	 * @param nbits 正数为向后跳过，负数为向前回退
	 */
	public void skipBits(int nbits) {
		if (nbits >= 0 && nbits <= bitCount) {
			bitCount -= nbits;
			return;
		}

		long bitPos = ((long) bytes.rIndex * 8) - bitCount;
		bitPos += nbits;
		if (bitPos < 0) bitPos = 0;

		int byteIdx = (int) (bitPos >>> 3);
		int bitOffset = (int) bitPos & 7;

		bytes.rIndex(byteIdx);
		buffer = 0;
		bitCount = 0;

		if (bitOffset > 0) readBits(bitOffset);
	}

	public void writeBit(int bit, int val) {
		int len = bitCount + bit;
		// max 32+7 bits in buffer
		long buf = (buffer << bit) | (val & 0xFFFFFFFFL);

		while (len > 8) {
			len -= 8;
			bytes.put((byte) (buf >>> len));
		}

		buffer = buf;
		bitCount = (byte) len;
	}
	public void endBitWrite() {
		if (bitCount > 0) {
			bytes.put((byte)(buffer << (8 - bitCount)));
			bitCount = 0;
		}
		buffer = 0;
	}

	public static final class LittleEndian extends BitStream {
		public LittleEndian() { super(); }
		public LittleEndian(DynByteBuf data) { super(data); }

		@Override
		public boolean isBigEndian() { return false; }

		@Override
		public int peekBits(int nbits) {
			if (nbits == 0) return 0;
			ensure(nbits);
			// 小端序：直接从 buffer 的低位截取
			return (int) (buffer & MASK(nbits));
		}

		@Override
		public int readBits(int nbits) {
			if (nbits == 0) return 0;
			ensure(nbits);
			int result = (int) (buffer & MASK(nbits));
			// 右移消耗掉已读的低位
			buffer >>>= nbits;
			bitCount -= nbits;
			return result;
		}

		@Override
		public int peekOptionalBits(int nbits) {
			if (nbits == 0) return 0;
			nbits = effort(nbits);
			return (int) (buffer & MASK(nbits));
		}

		@Override
		public int readOptionalBits(int nbits) {
			if (nbits == 0) return 0;
			nbits = effort(nbits);
			int result = (int) (buffer & MASK(nbits));
			buffer >>>= nbits;
			bitCount -= nbits;
			return result;
		}

		private void ensure(int nbits) {
			while (bitCount < nbits) {
				buffer |= ((long) bytes.readUnsignedByte() << bitCount);
				bitCount += 8;
			}
		}

		private int effort(int nbits) {
			while (bitCount < nbits) {
				if (!bytes.isReadable()) {
					nbits = bitCount;
					break;
				}

				buffer |= ((long) bytes.readUnsignedByte() << bitCount);
				bitCount += 8;
			}
			return nbits;
		}

		@Override
		public void skipBits(int nbits) {
			if (nbits >= 0 && nbits <= bitCount) {
				buffer >>>= nbits;
				bitCount -= nbits;
				return;
			}

			super.skipBits(nbits);
		}

		@Override
		public final void writeBit(int bit, int val) {
			// 将新位放入 buffer 的高位
			buffer |= ((long) (val & MASK(bit)) << bitCount);
			bitCount += bit;

			// 当积攒超过 8 位时，从低位吐出字节
			while (bitCount >= 8) {
				bytes.put((byte) buffer);
				buffer >>>= 8;
				bitCount -= 8;
			}
		}

		@Override
		public final void endBitWrite() {
			if (bitCount > 0) {
				bytes.put((byte) buffer);
				bitCount = 0;
			}
			buffer = 0;
		}
	}
}