package roj.crypt;

import roj.io.IOUtil;
import roj.util.ByteList;

import java.util.zip.Checksum;

/**
 * @author Roj233
 * @since 2022/10/11 0:22
 */
public class CRCAny implements Checksum {
	//    {4, 0x03, 0x00, true,  true,   0x00},    //CRC4_ITU
	//    {5, 0x09, 0x09, false, false,  0x00},    //CRC5_EPC
	//
	//    {5, 0x15, 0x00, true,  true,   0x00},    //CRC5_ITU
	//    {5, 0x05, 0x1f, true,  true,   0x1f},    //CRC5_USB
	//    {6, 0x03, 0x00, true,  true,   0x00},    //CRC6_ITU
	//    {7, 0x09, 0x00, false, false,  0x00},    //CRC7_MMC
	//
	//    //crc8
	//    {8, 0x07, 0x00, false, false,  0x00},    //CRC8
	//    {8, 0x07, 0x00, false, false,  0x55},    //CRC8_ITU
	//    {8, 0x07, 0xff, true,  true,   0x00},    //CRC8_ROHC
	//    {8, 0x31, 0x00, true,  true,   0x00},    //CRC8_MAXIM
	//
	//    //crc16
	//    {16, 0x8005, 0x0000, true,  true,   0x0000},   //CRC16_IBM
	//    {16, 0x8005, 0x0000, true,  true,   0xffff},   //CRC16_MAXIN
	//    {16, 0x8005, 0xffff, true,  true,   0xffff},   //CRC16_USB
	//    {16, 0x8005, 0xffff, true,  true,   0x0000},   //CRC16_MODBUS
	//    {16, 0x1021, 0x0000, true,  true,   0x0000},   //CRC16_CCITT
	//    {16, 0x1021, 0xffff, false, false,  0x0000},   //CRC16_CCITT_FALSE
	//    {16, 0x1021, 0xffff, true,  true,   0xffff},   //CRC16_X25
	//    {16, 0x1021, 0x0000, false, false,  0x0000},   //CRC16_XMODEM
	//    {16, 0x3D65, 0x0000, true,  true,   0xffff},   //CRC16_DNP
	//
	//    //crc32
	//    {32, 0x04C11DB7, 0xffffffff, true,  true,   0xffffffff},   //CRC32
	//    {32, 0x04C11DB7, 0xffffffff, false, false,  0x00000000},   //CRC32_MPEG

	public static final CrcConfig CRC_32 = new CrcConfig(32, 0x04C11DB7, 0xffffffff, true,  true,  0xffffffff);

	public static final class CrcConfig {
		final byte width;
		final int poly, init, xor;
		final boolean reverseIn, reverseOut;

		int[] table;

		public CrcConfig(int width, int poly, int init, boolean reverseIn, boolean reverseOut, int xor) {
			this.width = (byte) width;
			this.poly = poly;
			this.init = init;
			this.xor = xor;
			this.reverseIn = reverseIn;
			this.reverseOut = reverseOut;
		}

		@Override
		public String toString() {
			ByteList buf = IOUtil.getSharedByteBuf();
			getTable();
			for (int i = 0; i < 256; i++) buf.putInt(table[i]);
			return buf.toString();
		}

		public int[] getTable() {
			if (table != null) return table;

			int[] table = this.table = new int[256];
			int mask = (2 << (width - 1)) - 1;

			if (reverseIn) {
				int poly = reverse(width, this.poly);

				for (int i = 255; i >= 0; i--) {
					int c = i;
					for (int bit = 7; bit >= 0; bit--) {
						c = (c & 1) != 0 ? (c >>> 1) ^ poly : (c >>> 1);
					}
					table[i] = c & mask;
				}
			} else {
				//如果位数小于8，poly要左移到最高位
				int poly = width < 8 ? this.poly << (8 - width) : this.poly;
				int significant = width > 8 ? 1 << (width - 1) : 0x80;

				for (int i = 255; i >= 0; i--) {
					int c = width > 8 ? i << (width - 8) : i;

					for (int bit = 7; bit >= 0; bit--) {
						c = (c & bit) != 0 ? (c << 1) ^ poly : (c << 1);
					}

					//如果width < 8，那么实际上，crc是在高width位的，需要右移 8 - width
					//但是为了方便后续异或（还是要移位到最高位与*ptr的bit7对齐），所以不处理

					if (width >= 8) c &= mask;
					table[i] = c;
				}
			}
			return table;
		}

		// see Integer.reverse
		static int reverse(int bits, int in) {
			int out = 0;
			while (bits-- > 0) {
				out <<= 1;
				if ((in & 1) != 0) out |= 1;
				in >>>= 1;
			}
			return out;
		}

		public int defVal() {
			if (reverseIn) return reverse(width, init);
			if (width > 8) return init;
			return init << (8 - width);
		}

		public int update(int crc, byte[] b, int off, int len) {
			int[] TAB = getTable();

			if (reverseIn) {
				// 减少if
				if (width > 8) {
					while (len-- > 0) crc = (crc >>> 8) ^ TAB[crc&0xFF ^ b[off++]&0xFF];
				} else {
					while (len-- > 0) crc = TAB[crc ^ b[off++]&0xFF];
				}
			} else {
				if (width > 8) {
					while (len-- > 0) {
						int high = crc >>> (width - 8);
						crc = (crc << 8) ^ TAB[high ^ b[off++]&0xFF];
					}
				} else {
					while (len-- > 0) crc = TAB[crc ^ b[off++]&0xFF];
				}
			}
			return crc;
		}

		public int update(int crc, int b) {
			b &= 0xFF;

			int[] TAB = getTable();
			if (reverseIn) {
				// 减少if
				crc = width > 8 ? (crc >>> 8) ^ TAB[crc&0xff ^ b] :
					TAB[crc ^ b];
			} else {
				crc = width > 8 ? (crc << 8) ^ TAB[(crc >>> (width - 8)) ^ b] :
					(TAB[crc ^ b] >>>= 8 - width);
			}
			return crc;
		}

		public int retVal(int crc) {
			if (!reverseIn && width < 8) {
				//位数小于8时，data在高width位
				crc >>>= 8 - width;
			}

			// 逆序输出
			if (reverseIn != reverseOut)
				crc = reverse(width, crc);

			// 异或输出
			crc ^= xor;

			// mask
			return crc & ((2 << (width - 1)) - 1);
		}
	}

	private final CrcConfig cfg;
	private int v;

	public CRCAny(CrcConfig cfg) {
		this.cfg = cfg;
		this.v = cfg.defVal();
	}

	@Override
	public void update(int b) {
		v = cfg.update(v, (byte) b);
	}

	@Override
	public void update(byte[] b, int off, int len) {
		v = cfg.update(v, b, off, len);
	}

	@Override
	public long getValue() {
		return cfg.retVal(v) & 0xFFFFFFFFL;
	}

	@Override
	public void reset() {
		v = cfg.defVal();
	}
}
