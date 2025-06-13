package roj.crypt;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.reflect.Bypass;
import roj.reflect.Java22Workaround;
import roj.util.ArrayUtil;
import roj.util.DynByteBuf;

import static roj.reflect.Unaligned.U;

/**
 * s is either software or stream
 * @author Roj233
 * @since 2024/3/19 14:08
 */
public class CRC32 {
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

	@Java22Workaround
	private interface hw {
		int update(int crc, int b);
		int updateBytes(int crc, byte[] b, int off, int len);
		int updateByteBuffer(int alder, long addr, int off, int len);
	}
	private static final hw intrinsic;

	static {
		hw intr;
		try {
			intr = Bypass.builder(hw.class).inline().delegate(java.util.zip.CRC32.class, "update", "updateBytes", "updateByteBuffer").build();
		} catch (Exception e) {
			e.printStackTrace();
			intr = null;
		}
		intrinsic = intr;
	}

	@Contract(pure = true) public static int crc32(DynByteBuf buf) {
		return buf.isDirect()
				? crc32(buf.address() + buf.rIndex, buf.readableBytes())
				: crc32(buf.array(), buf.arrayOffset() + buf.rIndex, buf.readableBytes());
	}
	@Contract(pure = true) public static int crc32(DynByteBuf buf, int off, int len) {
		ArrayUtil.checkRange(buf.wIndex(), off, len);
		return buf.isDirect()
				? crc32(buf.address() + off, len)
				: crc32(buf.array(), buf.arrayOffset() + off, len);
	}
	@Contract(pure = true) public static int crc32(@NotNull byte[] b) {return crc32(b, 0, b.length);}
	@Contract(pure = true) public static int crc32(@NotNull byte[] b, int off, int count) { return finish(update(initial, b, off, count)); }
	@Contract(pure = true) public static int crc32(long address, int count) { return finish(update(initial, address, count)); }

	public static final int initial = intrinsic != null ? 0 : -1;

	@Contract(pure = true) public static int updateS(int crc, int b) { return (crc >>> 8) ^ crcTab[(crc ^ (b & 0xFF)) & 0xFF]; }
	@Contract(pure = true) public static int update(int crc, int b) { return intrinsic != null ? intrinsic.update(crc, b) : updateS(crc, b); }

	@Contract(pure = true) public static int finish(int crc) { return intrinsic != null ? crc : ~crc; }

	@Contract(pure = true) public static int update(int crc, DynByteBuf buf) {
		int len = buf.readableBytes();
		return buf.hasArray()
			? update(crc, buf.array(), buf.arrayOffset() + buf.rIndex, len)
			: update(crc, buf.address() + buf.rIndex, len);
	}
	@Contract(pure = true) public static int update(int crc, long off, int len) {
		if (intrinsic != null) return intrinsic.updateByteBuffer(crc, off, 0, len);

		while (len-- > 0) crc = (crc >>> 8) ^ crcTab[(crc ^ (U.getByte(off++) & 0xFF)) & 0xFF];
		return crc;
	}
	@Contract(pure = true) public static int update(int crc, @NotNull byte[] b, int off, int len) {
		if (intrinsic != null) return intrinsic.updateBytes(crc, b, off, len);

		while (len-- > 0) crc = (crc >>> 8) ^ crcTab[(crc ^ (b[off++] & 0xFF)) & 0xFF];
		return crc;
	}
}