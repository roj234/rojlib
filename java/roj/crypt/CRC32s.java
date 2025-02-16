package roj.crypt;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.reflect.Bypass;
import roj.reflect.Java22Workaround;
import roj.util.DynByteBuf;

import java.util.zip.CRC32;

import static roj.reflect.Unaligned.U;

/**
 * s is either software or stream
 * @author Roj233
 * @since 2024/3/19 14:08
 */
public class CRC32s {
	public static final int[] crcTab = new int[256];
	static {
		int poly = Integer.reverse(0x04C11DB7);
		for (int i = 255; i >= 0; i--) {
			int c = i;
			for (int bit = 7; bit >= 0; bit--) {
				c = (c & 1) != 0 ? (c >>> 1) ^ poly : (c >>> 1);
			}
			crcTab[i] = c;
		}
	}

	@Contract(pure = true)
	public static int once(DynByteBuf buf, int off, int len) {
		if ((off| len|(off+len)) < 0 || off + len > buf.readableBytes())
			throw new IndexOutOfBoundsException("off="+off+",len="+len+",cap="+buf.readableBytes());
		return buf.isDirect() ? CRC32s.once(buf.address() + off, len) : CRC32s.once(buf.array(), buf.arrayOffset() + off, len);
	}
	@Contract(pure = true)
	public static int once(@NotNull byte[] b) {return once(b, 0, b.length);}
	@Contract(pure = true)
	public static int once(@NotNull byte[] b, int off, int count) { return retVal(update(INIT_CRC, b, off, count)); }
	@Contract(pure = true)
	public static int once(long address, int count) { return retVal(update(INIT_CRC, address, count)); }

	@Contract(pure = true)
	public static int update(int crc, DynByteBuf buf) {
		int len = buf.readableBytes();
		return buf.hasArray()
			? CRC32s.update(crc, buf.array(), buf.arrayOffset() + buf.rIndex, len)
			: CRC32s.update(crc, buf.address(), len);
	}
	@Contract(pure = true)
	public static int update(int crc, long off, int len) {
		if (HWAC != null) return HWAC.updateByteBuffer(crc, off, 0, len);

		while (len-- > 0) crc = (crc >>> 8) ^ crcTab[(crc ^ (U.getByte(off++) & 0xFF)) & 0xFF];
		return crc;
	}

	@Contract(pure = true)
	public static int update(int crc, @NotNull byte[] b, int off, int len) {
		if (HWAC != null) return HWAC.updateBytes(crc, b, off, len);

		while (len-- > 0) crc = (crc >>> 8) ^ crcTab[(crc ^ (b[off++] & 0xFF)) & 0xFF];
		return crc;
	}

	@Contract(pure = true)
	public static int update(int crc, int b) { return HWAC != null ? HWAC.update(crc, b) : updateS(crc, b); }

	public static int updateS(int crc, int b) { return (crc >>> 8) ^ crcTab[(crc ^ (b & 0xFF)) & 0xFF]; }

	public static int retVal(int crc) { return HWAC != null ? crc : ~crc; }

	@Java22Workaround
	private interface CRC32h {
		int update(int crc, int b);
		int updateBytes(int crc, byte[] b, int off, int len);
		int updateByteBuffer(int alder, long addr, int off, int len);
	}
	private static final CRC32h HWAC;
	static {
		CRC32h hwac;
		try {
			hwac = Bypass.builder(CRC32h.class).inline().delegate(CRC32.class, "update", "updateBytes", "updateByteBuffer").build();
		} catch (Exception e) {
			e.printStackTrace();
			hwac = null;
		}
		HWAC = hwac;
	}

	public static final int INIT_CRC = HWAC != null ? 0 : -1;
}